package com.example.videoqongiroq.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.videoqongiroq.data.CallState
import com.example.videoqongiroq.data.User
import com.example.videoqongiroq.webrtc.NexusCallEngine
import kotlinx.coroutines.delay
import org.webrtc.SurfaceViewRenderer
import java.util.Locale

@Composable
fun CyberLumenCallScreen(
    currentUser: User?,
    callState: CallState,
    callEngine: NexusCallEngine?,
    onAcceptCall: () -> Unit,
    onRejectCall: () -> Unit,
    onEndCall: () -> Unit,
    onToggleMute: (Boolean) -> Unit,
    onToggleCamera: (Boolean) -> Unit,
    onSwitchCamera: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF050811))
    ) {
        when (callState) {
            is CallState.IncomingCall -> {
                CyberIncomingCallContent(
                    callState = callState,
                    onAcceptCall = onAcceptCall,
                    onRejectCall = onRejectCall
                )
            }
            is CallState.OutgoingCall -> {
                CyberOutgoingCallContent(
                    callState = callState,
                    onEndCall = onEndCall
                )
            }
            is CallState.InCall -> {
                CyberActiveCallContent(
                    callState = callState,
                    callEngine = callEngine,
                    onEndCall = onEndCall,
                    onToggleMute = onToggleMute,
                    onToggleCamera = onToggleCamera,
                    onSwitchCamera = onSwitchCamera
                )
            }
            is CallState.CallEnded -> {
                CyberCallEndedContent(reason = callState.reason)
            }
            CallState.Idle -> {}
        }
    }
}

@Composable
private fun CyberIncomingCallContent(
    callState: CallState.IncomingCall,
    onAcceptCall: () -> Unit,
    onRejectCall: () -> Unit
) {
    val limeGreen = Color(0xFF00FF66)
    val electricIndigo = Color(0xFF6366F1)

    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.25f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Spacer(modifier = Modifier.height(40.dp))

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .size(120.dp)
                    .scale(pulseScale)
                    .clip(CircleShape)
                    .background(
                        Brush.linearGradient(colors = listOf(limeGreen, electricIndigo))
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = callState.callerUser.name.take(1).uppercase(),
                    fontSize = 48.sp,
                    fontWeight = FontWeight.Black,
                    color = Color.Black
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = callState.callerUser.name,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = callState.callerUser.formattedPhone,
                style = MaterialTheme.typography.titleMedium,
                color = Color.LightGray
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "Kiruvchi video qo'ng'iroq...",
                style = MaterialTheme.typography.bodyLarge,
                color = limeGreen,
                fontWeight = FontWeight.Bold
            )
        }

        // Action Buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Reject Button
            IconButton(
                onClick = onRejectCall,
                modifier = Modifier
                    .size(72.dp)
                    .background(Color(0xFFFF3B30), CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Default.CallEnd,
                    contentDescription = "Rad etish",
                    tint = Color.White,
                    modifier = Modifier.size(36.dp)
                )
            }

            // Accept Button
            IconButton(
                onClick = onAcceptCall,
                modifier = Modifier
                    .size(72.dp)
                    .background(limeGreen, CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Default.Videocam,
                    contentDescription = "Qabul qilish",
                    tint = Color.Black,
                    modifier = Modifier.size(36.dp)
                )
            }
        }
    }
}

@Composable
private fun CyberOutgoingCallContent(
    callState: CallState.OutgoingCall,
    onEndCall: () -> Unit
) {
    val limeGreen = Color(0xFF00FF66)
    val electricIndigo = Color(0xFF6366F1)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Spacer(modifier = Modifier.height(40.dp))

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .size(110.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.linearGradient(colors = listOf(limeGreen, electricIndigo))
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = callState.targetUser.name.take(1).uppercase(),
                    fontSize = 44.sp,
                    fontWeight = FontWeight.Black,
                    color = Color.Black
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = callState.targetUser.name,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = callState.targetUser.formattedPhone,
                style = MaterialTheme.typography.titleMedium,
                color = Color.LightGray
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Chaqirilmoqda...",
                style = MaterialTheme.typography.bodyLarge,
                color = limeGreen,
                fontWeight = FontWeight.Bold
            )
        }

        // Cancel Call Button
        IconButton(
            onClick = onEndCall,
            modifier = Modifier
                .size(72.dp)
                .background(Color(0xFFFF3B30), CircleShape)
        ) {
            Icon(
                imageVector = Icons.Default.CallEnd,
                contentDescription = "Bekor qilish",
                tint = Color.White,
                modifier = Modifier.size(36.dp)
            )
        }
    }
}

@Composable
private fun CyberActiveCallContent(
    callState: CallState.InCall,
    callEngine: NexusCallEngine?,
    onEndCall: () -> Unit,
    onToggleMute: (Boolean) -> Unit,
    onToggleCamera: (Boolean) -> Unit,
    onSwitchCamera: () -> Unit
) {
    var callSeconds by remember { mutableStateOf(0) }
    val limeGreen = Color(0xFF00FF66)

    LaunchedEffect(Unit) {
        callEngine?.startLocalVideoAndAudio()
        callEngine?.initPeerConnection()

        while (true) {
            delay(1000)
            callSeconds++
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // Remote Video View (Full Screen)
        if (callEngine != null) {
            AndroidView(
                factory = { ctx ->
                    SurfaceViewRenderer(ctx).apply {
                        callEngine.bindRemoteView(this)
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFF050811)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Abonentga ulanilmoqda...",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // Local Video View (PIP Overlay Top Right)
        if (callEngine != null && !callState.isCameraOff) {
            Box(
                modifier = Modifier
                    .padding(top = 48.dp, end = 16.dp)
                    .size(110.dp, 160.dp)
                    .align(Alignment.TopEnd)
                    .clip(RoundedCornerShape(20.dp))
                    .border(2.dp, limeGreen, RoundedCornerShape(20.dp))
            ) {
                AndroidView(
                    factory = { ctx ->
                        SurfaceViewRenderer(ctx).apply {
                            callEngine.bindLocalView(this)
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        // Top Header Info Badge
        Card(
            modifier = Modifier
                .padding(top = 48.dp, start = 16.dp)
                .align(Alignment.TopStart),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A).copy(alpha = 0.85f)),
            shape = RoundedCornerShape(16.dp)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(limeGreen)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = callState.peerUser.name,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                    val minutes = callSeconds / 60
                    val seconds = callSeconds % 60
                    Text(
                        text = String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds),
                        color = limeGreen,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // Bottom Controls Toolbar
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .padding(bottom = 32.dp, start = 24.dp, end = 24.dp),
            shape = RoundedCornerShape(28.dp),
            color = Color(0xFF0F172A).copy(alpha = 0.9f)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Mute Mic Toggle
                IconButton(
                    onClick = { onToggleMute(!callState.isMuted) },
                    modifier = Modifier
                        .size(52.dp)
                        .background(
                            if (callState.isMuted) Color(0xFFFF3B30) else Color.White.copy(alpha = 0.15f),
                            CircleShape
                        )
                ) {
                    Icon(
                        imageVector = if (callState.isMuted) Icons.Default.MicOff else Icons.Default.Mic,
                        contentDescription = "Mic",
                        tint = Color.White
                    )
                }

                // Camera On/Off Toggle
                IconButton(
                    onClick = { onToggleCamera(!callState.isCameraOff) },
                    modifier = Modifier
                        .size(52.dp)
                        .background(
                            if (callState.isCameraOff) Color(0xFFFF3B30) else Color.White.copy(alpha = 0.15f),
                            CircleShape
                        )
                ) {
                    Icon(
                        imageVector = if (callState.isCameraOff) Icons.Default.VideocamOff else Icons.Default.Videocam,
                        contentDescription = "Video",
                        tint = Color.White
                    )
                }

                // Switch Camera Front/Back
                IconButton(
                    onClick = onSwitchCamera,
                    modifier = Modifier
                        .size(52.dp)
                        .background(Color.White.copy(alpha = 0.15f), CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Default.SwitchCamera,
                        contentDescription = "Switch Camera",
                        tint = Color.White
                    )
                }

                // End Call Button
                IconButton(
                    onClick = onEndCall,
                    modifier = Modifier
                        .size(56.dp)
                        .background(Color(0xFFFF3B30), CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Default.CallEnd,
                        contentDescription = "End Call",
                        tint = Color.White
                    )
                }
            }
        }
    }
}

@Composable
private fun CyberCallEndedContent(reason: String) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = Icons.Default.CallEnd,
                contentDescription = "Tugatildi",
                modifier = Modifier.size(64.dp),
                tint = Color(0xFFFF3B30)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = reason,
                style = MaterialTheme.typography.titleLarge,
                color = Color.White,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
