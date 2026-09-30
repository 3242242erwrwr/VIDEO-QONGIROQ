package com.example.videoqongiroq.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.example.videoqongiroq.data.CallState
import com.example.videoqongiroq.data.User
import com.example.videoqongiroq.ui.components.PermissionHandler
import com.example.videoqongiroq.ui.screens.CallScreen
import com.example.videoqongiroq.ui.screens.LoginScreen
import com.example.videoqongiroq.ui.screens.UsersListScreen
import com.example.videoqongiroq.webrtc.SignalingClient
import com.example.videoqongiroq.webrtc.WebRTCManager

@Composable
fun VideoQongiroqApp() {
    val context = LocalContext.current

    val signalingClient = remember { SignalingClient() }
    val webRTCManager = remember { WebRTCManager(context) }

    val currentUser by signalingClient.currentUser.collectAsState()
    val onlineUsers by signalingClient.onlineUsers.collectAsState()
    val callState by signalingClient.callState.collectAsState()
    val serverUrl by signalingClient.serverUrl.collectAsState()

    var permissionsGranted by remember { mutableStateOf(false) }

    // Wire WebRTC Manager callbacks with WebSocket Signaling Client
    LaunchedEffect(Unit) {
        // When peer accepts our call, create WebRTC offer
        signalingClient.onCallAcceptedReceived = { targetPhone ->
            val peerUser = User(phone = targetPhone, name = targetPhone)
            signalingClient.acceptIncomingCall(peerUser, isVideo = true)

            webRTCManager.createPeerConnection()
            webRTCManager.createOffer { sdp ->
                signalingClient.sendOffer(targetPhone, sdp)
            }
        }

        // When offer is received from peer
        signalingClient.onOfferReceived = { senderPhone, offerSdp ->
            webRTCManager.createPeerConnection()
            webRTCManager.setRemoteDescription(offerSdp)
            webRTCManager.createAnswer { answerSdp ->
                signalingClient.sendAnswer(senderPhone, answerSdp)
            }
        }

        // When answer is received from peer
        signalingClient.onAnswerReceived = { _, answerSdp ->
            webRTCManager.setRemoteDescription(answerSdp)
        }

        // When ICE candidate is received
        signalingClient.onIceCandidateReceived = { _, candidate ->
            webRTCManager.addIceCandidate(candidate)
        }

        // Local WebRTC ICE candidate generated callback
        webRTCManager.onIceCandidateGenerated = { candidate ->
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
                LoginScreen(
                    onLoginSuccess = { phone, name ->
                        signalingClient.login(phone, name)
                    }
                )
            } else {
                when (callState) {
                    CallState.Idle -> {
                        UsersListScreen(
                            currentUser = currentUser,
                            onlineUsers = onlineUsers,
                            serverUrl = serverUrl,
                            onUpdateServerUrl = { newUrl ->
                                signalingClient.setServerUrl(newUrl)
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
                        CallScreen(
                            callState = callState,
                            webRTCManager = webRTCManager,
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
                                }
                            },
                            onEndCall = {
                                signalingClient.endCall("Qo'ng'iroq yakunlandi")
                            },
                            onToggleMute = { isMuted ->
                                webRTCManager.toggleMute(isMuted)
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
                                webRTCManager.toggleCamera(isCameraOff)
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
                                webRTCManager.switchCamera()
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
    }
}
