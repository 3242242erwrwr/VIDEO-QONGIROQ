package com.example.videoqongiroq.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.videoqongiroq.data.CallState
import com.example.videoqongiroq.data.User
import com.example.videoqongiroq.ui.components.PermissionHandler
import com.example.videoqongiroq.ui.screens.CyberLumenCallScreen
import com.example.videoqongiroq.ui.screens.CyberLumenLoginScreen
import com.example.videoqongiroq.ui.screens.CyberLumenUsersScreen
import com.example.videoqongiroq.utils.AutoUpdateManager
import com.example.videoqongiroq.webrtc.NexusCallEngine
import com.example.videoqongiroq.webrtc.SignalingClient
import kotlinx.coroutines.launch

@Composable
fun VideoQongiroqApp() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val signalingClient = remember { SignalingClient() }
    val nexusCallEngine = remember { NexusCallEngine(context) }
    val autoUpdateManager = remember { AutoUpdateManager(context) }

    val currentUser by signalingClient.currentUser.collectAsState()
    val onlineUsers by signalingClient.onlineUsers.collectAsState()
    val callState by signalingClient.callState.collectAsState()
    val serverUrl by signalingClient.serverUrl.collectAsState()

    var permissionsGranted by remember { mutableStateOf(false) }
    var updateAvailableVersion by remember { mutableStateOf<String?>(null) }
    var updateDownloadUrl by remember { mutableStateOf<String?>(null) }
    var downloadProgress by remember { mutableStateOf<Int?>(null) }

    // Auto connect on launch if user details are saved & check for in-app updates
    LaunchedEffect(permissionsGranted) {
        if (permissionsGranted) {
            signalingClient.autoConnectIfSaved(context)
            autoUpdateManager.checkAndAutoUpdate { verName, url ->
                updateAvailableVersion = verName
                updateDownloadUrl = url
            }
        }
    }

    // Wire Nexus Call Engine callbacks with WebSocket Signaling Client
    LaunchedEffect(Unit) {
        // When peer accepts our call, create WebRTC offer
        signalingClient.onCallAcceptedReceived = { targetPhone, roomId ->
            val peerUser = signalingClient.onlineUsers.value.find { it.phone == targetPhone }
                ?: User(phone = targetPhone, name = targetPhone)
            signalingClient.transitionToInCall(peerUser, roomId = roomId, isVideo = true)

            nexusCallEngine.createOffer { sdp ->
                signalingClient.sendOffer(targetPhone, sdp)
            }
        }

        // When offer is received from peer
        signalingClient.onOfferReceived = { senderPhone, offerSdp ->
            nexusCallEngine.setRemoteDescription(offerSdp) {
                nexusCallEngine.createAnswer { answerSdp ->
                    signalingClient.sendAnswer(senderPhone, answerSdp)
                }
            }
        }

        // When answer is received from peer
        signalingClient.onAnswerReceived = { _, answerSdp ->
            nexusCallEngine.setRemoteDescription(answerSdp)
        }

        // When ICE candidate is received
        signalingClient.onIceCandidateReceived = { _, candidate ->
            nexusCallEngine.addIceCandidate(candidate)
        }

        // Local WebRTC ICE candidate generated callback
        nexusCallEngine.onIceCandidateReady = { candidate ->
            val currentCall = signalingClient.callState.value
            val targetPhone = when (currentCall) {
                is CallState.InCall -> currentCall.peerUser.phone
                is CallState.OutgoingCall -> currentCall.targetUser.phone
                is CallState.IncomingCall -> currentCall.callerUser.phone
                else -> ""
            }
            if (targetPhone.isNotEmpty()) {
                signalingClient.sendIceCandidate(targetPhone, candidate)
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        if (!permissionsGranted) {
            PermissionHandler(
                onPermissionsGranted = {
                    permissionsGranted = true
                }
            )
        } else {
            if (currentUser == null) {
                CyberLumenLoginScreen(
                    onLoginSuccess = { phone, name ->
                        signalingClient.login(context, phone, name)
                    }
                )
            } else {
                when (callState) {
                    CallState.Idle -> {
                        CyberLumenUsersScreen(
                            currentUser = currentUser,
                            onlineUsers = onlineUsers,
                            serverUrl = serverUrl,
                            onUpdateServerUrl = { newUrl ->
                                signalingClient.setServerUrl(context, newUrl)
                            },
                            onLogout = {
                                signalingClient.logout(context)
                            },
                            onStartCall = { targetUser ->
                                signalingClient.startCall(targetUser, isVideo = true)
                            },
                            onAddContact = { phone, name ->
                                signalingClient.addContact(phone, name)
                            },
                            onSimulateIncomingCall = {
                                val demoUser = onlineUsers.firstOrNull()
                                    ?: User(
                                        phone = "998909876543",
                                        name = "Akmal Vokhidov"
                                    )
                                signalingClient.simulateIncomingCall(demoUser)
                            }
                        )
                    }
                    else -> {
                        CyberLumenCallScreen(
                            currentUser = currentUser,
                            callState = callState,
                            callEngine = nexusCallEngine,
                            onAcceptCall = {
                                if (callState is CallState.IncomingCall) {
                                    val caller = (callState as CallState.IncomingCall).callerUser
                                    signalingClient.acceptIncomingCall(caller, isVideo = true)
                                }
                            },
                            onRejectCall = {
                                if (callState is CallState.IncomingCall) {
                                    val caller = (callState as CallState.IncomingCall).callerUser
                                    signalingClient.rejectIncomingCall(caller)
                                    nexusCallEngine.stopCall()
                                }
                            },
                            onEndCall = {
                                signalingClient.endCall("Qo'ng'iroq yakunlandi")
                                nexusCallEngine.stopCall()
                            },
                            onToggleMute = { isMuted ->
                                nexusCallEngine.toggleMute(isMuted)
                                if (callState is CallState.InCall) {
                                    val state = callState as CallState.InCall
                                    signalingClient.updateCallControls(
                                        isMuted = isMuted,
                                        isCameraOff = state.isCameraOff,
                                        isFrontCamera = state.isFrontCamera
                                    )
                                }
                            },
                            onToggleCamera = { isCameraOff ->
                                nexusCallEngine.toggleCamera(isCameraOff)
                                if (callState is CallState.InCall) {
                                    val state = callState as CallState.InCall
                                    signalingClient.updateCallControls(
                                        isMuted = state.isMuted,
                                        isCameraOff = isCameraOff,
                                        isFrontCamera = state.isFrontCamera
                                    )
                                }
                            },
                            onSwitchCamera = {
                                nexusCallEngine.switchCamera()
                                if (callState is CallState.InCall) {
                                    val state = callState as CallState.InCall
                                    signalingClient.updateCallControls(
                                        isMuted = state.isMuted,
                                        isCameraOff = state.isCameraOff,
                                        isFrontCamera = !state.isFrontCamera
                                    )
                                }
                            }
                        )
                    }
                }
            }
        }

        // Auto Update Dialog
        updateAvailableVersion?.let { version ->
            AlertDialog(
                onDismissRequest = { updateAvailableVersion = null },
                title = { Text("Yangi Versiya Mavjud (v$version)") },
                text = {
                    Column {
                        Text("Ilovaning yangi versiyasi tayyor. Avtomatik yuklanib o'rnatilsinmi?")
                        downloadProgress?.let { progress ->
                            Spacer(modifier = Modifier.height(12.dp))
                            LinearProgressIndicator(
                                progress = { progress / 100f },
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Yuklanmoqda: $progress%",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            val url = updateDownloadUrl ?: "https://video-qongiroq.onrender.com/download"
                            scope.launch {
                                autoUpdateManager.downloadAndInstallApk(url) { prg ->
                                    downloadProgress = prg
                                }
                            }
                        },
                        enabled = downloadProgress == null
                    ) {
                        Text("Yangilash va O'rnatish")
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = { updateAvailableVersion = null },
                        enabled = downloadProgress == null
                    ) {
                        Text("Keyinroq")
                    }
                }
            )
        }
    }
}
