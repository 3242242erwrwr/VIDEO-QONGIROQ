package com.example.videoqongiroq.data

enum class UserStatus(val labelUz: String, val colorHex: Long) {
    AVAILABLE("Onlayn", 0xFF4CAF50),
    BUSY("Band", 0xFFFF9800),
    IN_CALL("Qo'ng'iroqda", 0xFF2196F3),
    OFFLINE("Oflayn", 0xFF9E9E9E)
}

data class User(
    val phone: String,
    val name: String,
    val status: UserStatus = UserStatus.AVAILABLE,
    val isLocalUser: Boolean = false
) {
    val formattedPhone: String
        get() {
            return if (phone.length == 12 && phone.startsWith("998")) {
                "+998 (${phone.substring(3, 5)}) ${phone.substring(5, 8)}-${phone.substring(8, 10)}-${phone.substring(10, 12)}"
            } else if (phone.startsWith("+")) {
                phone
            } else {
                "+$phone"
            }
        }
}

sealed class CallState {
    object Idle : CallState()
    
    data class OutgoingCall(
        val targetUser: User,
        val isVideo: Boolean = true,
        val roomId: String = ""
    ) : CallState()

    data class IncomingCall(
        val callerUser: User,
        val isVideo: Boolean = true,
        val roomId: String = ""
    ) : CallState()

    data class InCall(
        val peerUser: User,
        val isVideo: Boolean = true,
        val isMuted: Boolean = false,
        val isCameraOff: Boolean = false,
        val isFrontCamera: Boolean = true,
        val durationSeconds: Int = 0,
        val roomId: String = "",
        val isCaller: Boolean = false
    ) : CallState()

    data class CallEnded(
        val reason: String
    ) : CallState()
}

sealed class SignalingEvent {
    data class UserJoined(val user: User) : SignalingEvent()
    data class UserLeft(val phone: String) : SignalingEvent()
    data class UserStatusChanged(val phone: String, val status: UserStatus) : SignalingEvent()
    data class Offer(val senderPhone: String, val receiverPhone: String, val sdp: String) : SignalingEvent()
    data class Answer(val senderPhone: String, val receiverPhone: String, val sdp: String) : SignalingEvent()
    data class IceCandidate(
        val senderPhone: String,
        val receiverPhone: String,
        val sdpMid: String,
        val sdpMLineIndex: Int,
        val sdpCandidate: String
    ) : SignalingEvent()
    data class CallRequest(val senderPhone: String, val receiverPhone: String, val isVideo: Boolean) : SignalingEvent()
    data class CallAccepted(val senderPhone: String, val receiverPhone: String) : SignalingEvent()
    data class CallRejected(val senderPhone: String, val receiverPhone: String, val reason: String) : SignalingEvent()
    data class CallEndedSignal(val senderPhone: String, val receiverPhone: String) : SignalingEvent()
}
