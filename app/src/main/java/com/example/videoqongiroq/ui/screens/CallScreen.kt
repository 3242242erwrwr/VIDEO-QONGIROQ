package com.example.videoqongiroq.ui.screens

import androidx.compose.runtime.Composable
import com.example.videoqongiroq.data.CallState
import com.example.videoqongiroq.data.User
import com.example.videoqongiroq.webrtc.NexusCallEngine

@Composable
fun CallScreen(
    currentUser: User?,
    callState: CallState,
    nexusCallEngine: NexusCallEngine?,
    onAcceptCall: () -> Unit,
    onRejectCall: () -> Unit,
    onEndCall: () -> Unit,
    onToggleMute: (Boolean) -> Unit,
    onToggleCamera: (Boolean) -> Unit,
    onSwitchCamera: () -> Unit
) {
    NexusCallScreen(
        currentUser = currentUser,
        callState = callState,
        callEngine = nexusCallEngine,
        onAcceptCall = onAcceptCall,
        onRejectCall = onRejectCall,
        onEndCall = onEndCall,
        onToggleMute = onToggleMute,
        onToggleCamera = onToggleCamera,
        onSwitchCamera = onSwitchCamera
    )
}
