package com.example.videoqongiroq.webrtc

import android.content.Context
import android.util.Log
import com.example.videoqongiroq.data.CallState
import com.example.videoqongiroq.data.User
import com.example.videoqongiroq.data.UserPreferences
import com.example.videoqongiroq.data.UserStatus
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.*
import org.json.JSONArray
import org.json.JSONObject
import org.webrtc.IceCandidate
import org.webrtc.SessionDescription
import java.net.URLEncoder

class SignalingClient {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val _currentUser = MutableStateFlow<User?>(null)
    val currentUser: StateFlow<User?> = _currentUser.asStateFlow()

    private val _onlineUsers = MutableStateFlow<List<User>>(emptyList())
    val onlineUsers: StateFlow<List<User>> = _onlineUsers.asStateFlow()

    private val _callState = MutableStateFlow<CallState>(CallState.Idle)
    val callState: StateFlow<CallState> = _callState.asStateFlow()

    private val _serverUrl = MutableStateFlow("wss://video-qongiroq.onrender.com/ws/")
    val serverUrl: StateFlow<String> = _serverUrl.asStateFlow()

    private val okHttpClient = OkHttpClient.Builder().build()
    private var webSocket: WebSocket? = null

    var onOfferReceived: ((senderPhone: String, sdp: SessionDescription) -> Unit)? = null
    var onAnswerReceived: ((senderPhone: String, sdp: SessionDescription) -> Unit)? = null
    var onIceCandidateReceived: ((senderPhone: String, candidate: IceCandidate) -> Unit)? = null
    var onCallAcceptedReceived: ((senderPhone: String, roomId: String) -> Unit)? = null

    companion object {
        private const val TAG = "SignalingClient"
    }

    fun setServerUrl(context: Context, newUrl: String) {
        val trimmed = newUrl.trim()
        if (trimmed.isNotEmpty()) {
            _serverUrl.value = if (trimmed.endsWith("/")) trimmed else "$trimmed/"
            val user = _currentUser.value
            if (user != null) {
                login(context, user.phone, user.name)
            }
        }
    }

    fun autoConnectIfSaved(context: Context): Boolean {
        val saved = UserPreferences(context).getUser()
        if (saved != null) {
            _currentUser.value = saved
            connectWebSocket(saved)
            return true
        }
        return false
    }

    fun login(context: Context, phone: String, name: String) {
        val cleanDigits = phone.filter { it.isDigit() }
        val fullPhone = if (cleanDigits.startsWith("998")) cleanDigits else "998$cleanDigits"
        val user = User(
            phone = if (fullPhone.length >= 10) fullPhone else "998901234567",
            name = if (name.isNotBlank()) name else "Foydalanuvchi",
            status = UserStatus.AVAILABLE,
            isLocalUser = true
        )
        UserPreferences(context).saveUser(user.phone, user.name)
        _currentUser.value = user

        connectWebSocket(user)
    }

    fun logout(context: Context) {
        UserPreferences(context).clearUser()
        close()
        _currentUser.value = null
        _callState.value = CallState.Idle
    }

    private fun connectWebSocket(user: User) {
        webSocket?.close(1000, "Reconnecting")
        webSocket = null

        val baseUrl = _serverUrl.value
        val encodedName = try {
            URLEncoder.encode(user.name, "UTF-8")
        } catch (e: Exception) {
            "Abonent"
        }

        val fullUrl = "${baseUrl}${user.phone}?name=$encodedName"
        Log.d(TAG, "Connecting to WebSocket: $fullUrl")

        val request = Request.Builder()
            .url(fullUrl)
            .build()

        webSocket = okHttpClient.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.d(TAG, "WebSocket Connected successfully")
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                Log.d(TAG, "WebSocket Message: $text")
                handleIncomingMessage(text)
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.e(TAG, "WebSocket Failure: ${t.message}", t)
                scope.launch {
                    delay(2000)
                    _currentUser.value?.let { connectWebSocket(it) }
                }
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                Log.d(TAG, "WebSocket Closed: $reason")
                if (code != 1000) {
                    scope.launch {
                        delay(2000)
                        _currentUser.value?.let { connectWebSocket(it) }
                    }
                }
            }
        })
    }

    private fun handleIncomingMessage(jsonText: String) {
        try {
            val json = JSONObject(jsonText)
            val type = json.optString("type")
            val senderPhone = json.optString("senderPhone")
            val senderName = json.optString("senderName", "Abonent")
            val roomId = json.optString("roomId", "room_${System.currentTimeMillis()}")

            when (type) {
                "user_list" -> {
                    val array: JSONArray = json.optJSONArray("users") ?: JSONArray()
                    val list = mutableListOf<User>()
                    val localPhone = _currentUser.value?.phone ?: ""

                    for (i in 0 until array.length()) {
                        val obj = array.getJSONObject(i)
                        val phone = obj.optString("phone")
                        val name = obj.optString("name")
                        val statusStr = obj.optString("status", "AVAILABLE")

                        if (phone != localPhone) {
                            val status = when (statusStr) {
                                "BUSY" -> UserStatus.BUSY
                                "IN_CALL" -> UserStatus.IN_CALL
                                "OFFLINE" -> UserStatus.OFFLINE
                                else -> UserStatus.AVAILABLE
                            }
                            list.add(User(phone = phone, name = name, status = status))
                        }
                    }
                    _onlineUsers.value = list
                }

                "call_request" -> {
                    val caller = User(phone = senderPhone, name = senderName, status = UserStatus.IN_CALL)
                    _callState.value = CallState.IncomingCall(callerUser = caller, isVideo = true, roomId = roomId)
                }

                "call_accept" -> {
                    onCallAcceptedReceived?.invoke(senderPhone, roomId)
                }

                "call_reject" -> {
                    _callState.value = CallState.CallEnded("Qo'ng'iroq rad etildi")
                    scope.launch {
                        delay(2000)
                        _callState.value = CallState.Idle
                    }
                }

                "call_end" -> {
                    _callState.value = CallState.CallEnded("Qo'ng'iroq tugatildi")
                    scope.launch {
                        delay(2000)
                        _callState.value = CallState.Idle
                    }
                }

                "offer" -> {
                    val sdpText = json.optString("sdp")
                    val sdp = SessionDescription(SessionDescription.Type.OFFER, sdpText)
                    onOfferReceived?.invoke(senderPhone, sdp)
                }

                "answer" -> {
                    val sdpText = json.optString("sdp")
                    val sdp = SessionDescription(SessionDescription.Type.ANSWER, sdpText)
                    onAnswerReceived?.invoke(senderPhone, sdp)
                }

                "ice_candidate" -> {
                    val sdpMid = json.optString("sdpMid")
                    val sdpMLineIndex = json.optInt("sdpMLineIndex")
                    val sdpCandidate = json.optString("sdpCandidate")
                    val candidate = IceCandidate(sdpMid, sdpMLineIndex, sdpCandidate)
                    onIceCandidateReceived?.invoke(senderPhone, candidate)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing JSON message: $jsonText", e)
        }
    }

    fun startCall(targetUser: User, isVideo: Boolean = true) {
        val generatedRoomId = "room_${System.currentTimeMillis()}"
        _callState.value = CallState.OutgoingCall(targetUser = targetUser, isVideo = isVideo, roomId = generatedRoomId)
        val msg = JSONObject().apply {
            put("type", "call_request")
            put("receiverPhone", targetUser.phone)
            put("isVideo", isVideo)
            put("roomId", generatedRoomId)
        }
        sendJson(msg)
    }

    fun acceptIncomingCall(callerUser: User, isVideo: Boolean = true) {
        val current = _callState.value
        val currentRoomId = if (current is CallState.IncomingCall) current.roomId else "room_${System.currentTimeMillis()}"
        _callState.value = CallState.InCall(peerUser = callerUser, isVideo = isVideo, roomId = currentRoomId, isCaller = false)
        val msg = JSONObject().apply {
            put("type", "call_accept")
            put("receiverPhone", callerUser.phone)
            put("roomId", currentRoomId)
        }
        sendJson(msg)
    }

    fun transitionToInCall(peerUser: User, roomId: String, isVideo: Boolean = true) {
        _callState.value = CallState.InCall(peerUser = peerUser, isVideo = isVideo, roomId = roomId, isCaller = true)
    }

    fun rejectIncomingCall(callerUser: User) {
        _callState.value = CallState.CallEnded("Rad etildi")
        val msg = JSONObject().apply {
            put("type", "call_reject")
            put("receiverPhone", callerUser.phone)
        }
        sendJson(msg)
        scope.launch {
            delay(1500)
            _callState.value = CallState.Idle
        }
    }

    fun endCall(reason: String = "Qo'ng'iroq yakunlandi") {
        val current = _callState.value
        val peerPhone = when (current) {
            is CallState.InCall -> current.peerUser.phone
            is CallState.OutgoingCall -> current.targetUser.phone
            is CallState.IncomingCall -> current.callerUser.phone
            else -> ""
        }

        if (peerPhone.isNotEmpty()) {
            val msg = JSONObject().apply {
                put("type", "call_end")
                put("receiverPhone", peerPhone)
            }
            sendJson(msg)
        }

        _callState.value = CallState.CallEnded(reason)
        scope.launch {
            delay(1500)
            _callState.value = CallState.Idle
        }
    }

    fun sendOffer(receiverPhone: String, sdp: SessionDescription) {
        val msg = JSONObject().apply {
            put("type", "offer")
            put("receiverPhone", receiverPhone)
            put("sdp", sdp.description)
        }
        sendJson(msg)
    }

    fun sendAnswer(receiverPhone: String, sdp: SessionDescription) {
        val msg = JSONObject().apply {
            put("type", "answer")
            put("receiverPhone", receiverPhone)
            put("sdp", sdp.description)
        }
        sendJson(msg)
    }

    fun sendIceCandidate(receiverPhone: String, candidate: IceCandidate) {
        val msg = JSONObject().apply {
            put("type", "ice_candidate")
            put("receiverPhone", receiverPhone)
            put("sdpMid", candidate.sdpMid)
            put("sdpMLineIndex", candidate.sdpMLineIndex)
            put("sdpCandidate", candidate.sdp)
        }
        sendJson(msg)
    }

    fun addContact(phone: String, name: String) {
        val cleanPhone = phone.filter { it.isDigit() }
        if (cleanPhone.isBlank()) return
        val newUser = User(
            phone = cleanPhone,
            name = if (name.isNotBlank()) name else "Abonent",
            status = UserStatus.AVAILABLE
        )
        val currentList = _onlineUsers.value.toMutableList()
        currentList.removeAll { it.phone == cleanPhone }
        currentList.add(0, newUser)
        _onlineUsers.value = currentList
    }

    fun simulateIncomingCall(callerUser: User) {
        _callState.value = CallState.IncomingCall(callerUser, isVideo = true, roomId = "demo_room_${System.currentTimeMillis()}")
    }

    fun updateCallControls(isMuted: Boolean, isCameraOff: Boolean, isFrontCamera: Boolean) {
        val current = _callState.value
        if (current is CallState.InCall) {
            _callState.value = current.copy(
                isMuted = isMuted,
                isCameraOff = isCameraOff,
                isFrontCamera = isFrontCamera
            )
        }
    }

    private fun sendJson(json: JSONObject) {
        val text = json.toString()
        Log.d(TAG, "Sending JSON: $text")
        webSocket?.send(text)
    }

    fun close() {
        webSocket?.close(1000, "User logged out")
        webSocket = null
        scope.cancel()
    }
}
