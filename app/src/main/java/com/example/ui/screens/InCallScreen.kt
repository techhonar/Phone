package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Dialpad
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.ActiveCallState
import com.example.ui.PhoneViewModel
import com.example.ui.components.ContactAvatar
import com.example.ui.components.SamsungDialButton
import com.example.ui.theme.InCallEndCallRed
import com.example.ui.theme.InCallGradientBottom
import com.example.ui.theme.InCallGradientMid
import com.example.ui.theme.InCallGradientTop
import com.example.ui.theme.InCallTileActiveBg
import com.example.ui.theme.InCallTileBg
import com.example.ui.theme.SamsungGreen
import com.example.ui.theme.SamsungRed

@Composable
fun InCallScreen(
    callState: ActiveCallState,
    viewModel: PhoneViewModel,
    modifier: Modifier = Modifier
) {
    var showInCallKeypad by remember { mutableStateOf(false) }
    var inCallDigits by remember { mutableStateOf("") }

    val (number, name, statusText, isMuted, isSpeaker, isHeld, isRecording) = when (callState) {
        is ActiveCallState.Calling -> CallViewData(
            number = callState.number,
            name = callState.name,
            status = "Calling...",
            isMuted = false,
            isSpeaker = false,
            isHeld = false,
            isRecording = false
        )
        is ActiveCallState.Connected -> CallViewData(
            number = callState.number,
            name = callState.name,
            status = if (callState.isHeld) "On Hold" else formatDuration(callState.seconds),
            isMuted = callState.isMuted,
            isSpeaker = callState.isSpeaker,
            isHeld = callState.isHeld,
            isRecording = callState.isRecording
        )
        is ActiveCallState.Ended -> CallViewData(
            number = callState.number,
            name = callState.name,
            status = "${callState.reason} (${formatDuration(callState.totalSeconds)})",
            isMuted = false,
            isSpeaker = false,
            isHeld = false,
            isRecording = false
        )
        else -> return
    }

    // Samsung Dark Teal/Slate Gradient Backdrop
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        InCallGradientTop,
                        InCallGradientMid,
                        InCallGradientBottom
                    )
                )
            )
            .testTag("in_call_screen")
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp)
                .padding(top = 56.dp, bottom = 48.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Status & SIM tag
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = Color.White.copy(alpha = 0.15f)
            ) {
                Text(
                    text = "SIM 1 • HD Voice",
                    color = Color.White.copy(alpha = 0.85f),
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Contact Name / Number
            Text(
                text = name ?: number,
                style = MaterialTheme.typography.headlineLarge.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 32.sp
                ),
                color = Color.White,
                textAlign = TextAlign.Center
            )

            if (name != null) {
                Text(
                    text = number,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.7f),
                    textAlign = TextAlign.Center
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Call status (e.g. 00:15, Calling..., On Hold)
            Text(
                text = statusText,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 18.sp
                ),
                color = if (isHeld) SamsungRed else SamsungGreen
            )

            if (isRecording) {
                Spacer(modifier = Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val transition = rememberInfiniteTransition(label = "rec")
                    val pulse by transition.animateFloat(
                        initialValue = 0.4f,
                        targetValue = 1f,
                        animationSpec = infiniteRepeatable(tween(600), RepeatMode.Reverse),
                        label = "pulse"
                    )
                    Icon(
                        imageVector = Icons.Default.FiberManualRecord,
                        contentDescription = "Recording",
                        tint = SamsungRed.copy(alpha = pulse),
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "REC",
                        color = SamsungRed,
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
                    )
                }
            }

            Spacer(modifier = Modifier.weight(0.2f))

            // Large avatar with soft glow ring
            Box(contentAlignment = Alignment.Center) {
                Box(
                    modifier = Modifier
                        .size(118.dp)
                        .background(Color.White.copy(alpha = 0.08f), CircleShape)
                )
                ContactAvatar(name = name ?: number, size = 96)
            }

            Spacer(modifier = Modifier.weight(0.3f))

            // 6 Circular Call Action Tiles
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                // Row 1: Mute, Keypad, Speaker
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    InCallTile(
                        icon = if (isMuted) Icons.Default.MicOff else Icons.Default.Mic,
                        label = if (isMuted) "Unmute" else "Mute",
                        isActive = isMuted,
                        onClick = { viewModel.toggleMute() }
                    )
                    InCallTile(
                        icon = Icons.Default.Dialpad,
                        label = "Keypad",
                        isActive = showInCallKeypad,
                        onClick = { showInCallKeypad = !showInCallKeypad }
                    )
                    InCallTile(
                        icon = Icons.Default.VolumeUp,
                        label = "Speaker",
                        isActive = isSpeaker,
                        onClick = { viewModel.toggleSpeaker() }
                    )
                }

                // Row 2: Hold, Record, Bluetooth
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    InCallTile(
                        icon = if (isHeld) Icons.Default.PlayArrow else Icons.Default.Pause,
                        label = if (isHeld) "Resume" else "Hold",
                        isActive = isHeld,
                        onClick = { viewModel.toggleHold() }
                    )
                    InCallTile(
                        icon = Icons.Default.FiberManualRecord,
                        label = if (isRecording) "Recording" else "Record",
                        isActive = isRecording,
                        onClick = { viewModel.toggleRecord() }
                    )
                    InCallTile(
                        icon = Icons.Default.Bluetooth,
                        label = "Bluetooth",
                        isActive = false,
                        onClick = { /* Toggle Bluetooth */ }
                    )
                }
            }

            Spacer(modifier = Modifier.height(36.dp))

            // Large Red End Call Button
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(76.dp)
                    .shadow(elevation = 8.dp, shape = CircleShape, spotColor = InCallEndCallRed)
                    .background(InCallEndCallRed, CircleShape)
                    .clip(CircleShape)
                    .clickable { viewModel.endCall() }
                    .testTag("end_call_button")
            ) {
                Icon(
                    imageVector = Icons.Default.CallEnd,
                    contentDescription = "End Call",
                    tint = Color.White,
                    modifier = Modifier.size(36.dp)
                )
            }
        }

        // Slide-up In-Call Keypad (for entering IVR options / extension numbers)
        AnimatedVisibility(
            visible = showInCallKeypad,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            Surface(
                shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "In-Call Keypad: $inCallDigits",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        IconButton(onClick = { showInCallKeypad = false }) {
                            Text(
                                text = "Done",
                                color = SamsungGreen,
                                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Mini 3x4 dialpad for IVR
                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        listOf(
                            listOf('1', '2', '3'),
                            listOf('4', '5', '6'),
                            listOf('7', '8', '9'),
                            listOf('*', '0', '#')
                        ).forEach { row ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceEvenly
                            ) {
                                row.forEach { digit ->
                                    SamsungDialButton(
                                        digit = digit,
                                        onClick = {
                                            inCallDigits += digit
                                            viewModel.sendInCallDtmf(digit)
                                        },
                                        modifier = Modifier.size(64.dp)
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                }
            }
        }
    }
}

@Composable
private fun InCallTile(
    icon: ImageVector,
    label: String,
    isActive: Boolean,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable { onClick() }
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(62.dp)
                .background(
                    if (isActive) InCallTileActiveBg else InCallTileBg,
                    CircleShape
                )
                .clip(CircleShape)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = Color.White,
                modifier = Modifier.size(28.dp)
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = label,
            color = Color.White.copy(alpha = 0.9f),
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold)
        )
    }
}

private data class CallViewData(
    val number: String,
    val name: String?,
    val status: String,
    val isMuted: Boolean,
    val isSpeaker: Boolean,
    val isHeld: Boolean,
    val isRecording: Boolean
)

private fun formatDuration(seconds: Long): String {
    val m = seconds / 60
    val s = seconds % 60
    return String.format("%02d:%02d", m, s)
}
