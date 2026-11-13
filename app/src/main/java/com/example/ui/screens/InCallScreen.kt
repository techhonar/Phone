package com.example.ui.screens

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CallMerge
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.AddIcCall
import androidx.compose.material.icons.filled.BluetoothAudio
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Dialpad
import androidx.compose.material.icons.filled.Headset
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PhoneInTalk
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SwapCalls
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.telecom.AudioRoute
import com.example.telecom.CallLifecycleState
import com.example.telecom.CallManager
import com.example.telecom.CallUiModel
import com.example.telecom.TelecomUiState
import com.example.ui.components.ContactAvatar
import com.example.ui.components.SamsungDialButton
import com.example.ui.theme.InCallEndCallRed
import com.example.ui.theme.InCallGradientBottom
import com.example.ui.theme.InCallGradientMid
import com.example.ui.theme.InCallGradientTop
import com.example.ui.theme.InCallTileActiveBg
import com.example.ui.theme.InCallTileBg
import com.example.ui.theme.SamsungBlue
import com.example.ui.theme.SamsungGreen
import com.example.ui.theme.SamsungOrange
import com.example.ui.theme.SamsungRed
import java.util.Locale
import kotlinx.coroutines.delay

@Composable
fun InCallScreen(
    telecomState: TelecomUiState,
    onAddCall: () -> Unit,
    modifier: Modifier = Modifier
) {
    val primaryCall = telecomState.primaryCall ?: return
    val secondaryCall = telecomState.secondaryCall
    val audioState = telecomState.audioState

    var showInCallKeypad by remember { mutableStateOf(false) }
    var inCallDigits by remember { mutableStateOf("") }
    var showAudioPicker by remember { mutableStateOf(false) }
    var showQuickMessages by remember { mutableStateOf(false) }

    BackHandler(enabled = showInCallKeypad || showAudioPicker || showQuickMessages) {
        showInCallKeypad = false
        showAudioPicker = false
        showQuickMessages = false
    }

    // Live elapsed timer from real Telecom connectTimeMillis
    var elapsedSeconds by remember(primaryCall.id, primaryCall.connectTimeMillis, primaryCall.state) {
        mutableLongStateOf(
            if (primaryCall.connectTimeMillis > 0L && primaryCall.state == CallLifecycleState.ACTIVE) {
                ((System.currentTimeMillis() - primaryCall.connectTimeMillis) / 1000L).coerceAtLeast(0L)
            } else {
                0L
            }
        )
    }

    LaunchedEffect(primaryCall.id, primaryCall.connectTimeMillis, primaryCall.state) {
        if (primaryCall.state == CallLifecycleState.ACTIVE && primaryCall.connectTimeMillis > 0L) {
            while (true) {
                elapsedSeconds = ((System.currentTimeMillis() - primaryCall.connectTimeMillis) / 1000L).coerceAtLeast(0L)
                delay(1000L)
            }
        }
    }

    val statusText = when (primaryCall.state) {
        CallLifecycleState.CONNECTING -> "Connecting..."
        CallLifecycleState.DIALING -> "Dialing..."
        CallLifecycleState.RINGING -> "Incoming call"
        CallLifecycleState.ACTIVE -> if (primaryCall.connectTimeMillis > 0L) formatDuration(elapsedSeconds) else "00:00"
        CallLifecycleState.HOLDING -> "On hold"
        CallLifecycleState.DISCONNECTING -> "Ending call..."
        CallLifecycleState.DISCONNECTED -> primaryCall.disconnectReason ?: "Call ended"
        CallLifecycleState.IDLE -> ""
    }

    val statusColor = when (primaryCall.state) {
        CallLifecycleState.HOLDING -> SamsungOrange
        CallLifecycleState.DISCONNECTED, CallLifecycleState.DISCONNECTING -> SamsungRed
        else -> SamsungGreen
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(InCallGradientTop, InCallGradientMid, InCallGradientBottom)
                )
            )
            .statusBarsPadding()
            .navigationBarsPadding()
            .testTag("in_call_screen"),
        contentAlignment = Alignment.Center
    ) {
        val isCompactHeight = maxHeight < 620.dp

        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 520.dp)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = if (isCompactHeight) 12.dp else 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Secondary / Call-Waiting Header Card
            if (secondaryCall != null) {
                SecondaryCallCard(
                    secondaryCall = secondaryCall,
                    canMerge = primaryCall.canMerge || secondaryCall.canMerge,
                    onSwap = { CallManager.swapCalls() },
                    onMerge = { CallManager.mergeCalls() }
                )
                Spacer(modifier = Modifier.height(if (isCompactHeight) 8.dp else 14.dp))
            }

            // SIM / ConnectionService Pill
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = Color.White.copy(alpha = 0.14f),
                modifier = Modifier.testTag("in_call_service_badge")
            ) {
                Text(
                    text = buildString {
                        append(primaryCall.simLabel?.takeIf { it.isNotBlank() } ?: "Cellular")
                        append(" • ")
                        append(
                            when {
                                primaryCall.isIncoming -> "Incoming"
                                telecomState.activeConnectionCount > 0 -> "ConnectionService Active"
                                else -> "HD Voice"
                            }
                        )
                    },
                    color = Color.White.copy(alpha = 0.9f),
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
                )
            }

            Spacer(modifier = Modifier.height(if (isCompactHeight) 10.dp else 18.dp))

            // Caller Name & Number
            Text(
                text = primaryCall.displayName ?: primaryCall.number,
                style = MaterialTheme.typography.headlineLarge.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = if (isCompactHeight) 26.sp else 31.sp
                ),
                color = Color.White,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.testTag("in_call_contact_name")
            )

            if (primaryCall.displayName != null) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = primaryCall.number,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.72f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.testTag("in_call_phone_number")
                )
            }

            Spacer(modifier = Modifier.height(if (isCompactHeight) 6.dp else 10.dp))

            Text(
                text = statusText,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 18.sp,
                    fontFeatureSettings = "tnum"
                ),
                color = statusColor,
                modifier = Modifier.testTag("in_call_status_duration")
            )

            Spacer(modifier = Modifier.height(if (isCompactHeight) 12.dp else 24.dp))

            // Caller Avatar with pulsing ring during RINGING / DIALING
            CallerPulsingAvatar(
                name = primaryCall.displayName ?: primaryCall.number,
                photoUri = primaryCall.photoUri,
                isPulsing = primaryCall.state == CallLifecycleState.RINGING || primaryCall.state == CallLifecycleState.DIALING,
                compact = isCompactHeight
            )

            Spacer(modifier = Modifier.height(if (isCompactHeight) 14.dp else 28.dp))

            if (primaryCall.state == CallLifecycleState.RINGING) {
                // Incoming Call Actions: Send Message + Answer + Decline
                IncomingCallControls(
                    onAnswer = { CallManager.answerCall(primaryCall.id) },
                    onDecline = { CallManager.rejectCall(primaryCall.id) },
                    onOpenQuickMessages = { showQuickMessages = true }
                )
            } else {
                // Active / Dialing / Holding 6-Tile Control Grid
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(if (isCompactHeight) 12.dp else 20.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        InCallTile(
                            icon = Icons.Default.AddIcCall,
                            label = "Add call",
                            isActive = false,
                            testTag = "in_call_add_call_button",
                            onClick = onAddCall
                        )
                        InCallTile(
                            icon = if (primaryCall.state == CallLifecycleState.HOLDING) Icons.Default.PlayArrow else Icons.Default.Pause,
                            label = if (primaryCall.state == CallLifecycleState.HOLDING) "Resume" else "Hold",
                            isActive = primaryCall.state == CallLifecycleState.HOLDING,
                            testTag = "in_call_hold_button",
                            onClick = { CallManager.toggleHold(primaryCall.id) }
                        )
                        val routeIcon = when (audioState.currentRoute) {
                            AudioRoute.BLUETOOTH -> Icons.Default.BluetoothAudio
                            AudioRoute.WIRED_HEADSET -> Icons.Default.Headset
                            else -> Icons.AutoMirrored.Filled.VolumeUp
                        }
                        val routeLabel = when (audioState.currentRoute) {
                            AudioRoute.BLUETOOTH -> "Bluetooth"
                            AudioRoute.WIRED_HEADSET -> "Headset"
                            AudioRoute.SPEAKER -> "Speaker"
                            AudioRoute.EARPIECE -> "Audio"
                        }
                        InCallTile(
                            icon = routeIcon,
                            label = routeLabel,
                            isActive = audioState.currentRoute == AudioRoute.BLUETOOTH || audioState.currentRoute == AudioRoute.WIRED_HEADSET,
                            testTag = "in_call_audio_button",
                            onClick = { showAudioPicker = true }
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        InCallTile(
                            icon = Icons.AutoMirrored.Filled.VolumeUp,
                            label = "Speaker",
                            isActive = audioState.currentRoute == AudioRoute.SPEAKER,
                            testTag = "in_call_speaker_button",
                            onClick = { CallManager.toggleSpeaker() }
                        )
                        InCallTile(
                            icon = if (audioState.isMuted) Icons.Default.MicOff else Icons.Default.Mic,
                            label = if (audioState.isMuted) "Unmute" else "Mute",
                            isActive = audioState.isMuted,
                            testTag = "in_call_mute_button",
                            onClick = { CallManager.toggleMute() }
                        )
                        InCallTile(
                            icon = Icons.Default.Dialpad,
                            label = "Keypad",
                            isActive = showInCallKeypad,
                            testTag = "in_call_keypad_button",
                            onClick = { showInCallKeypad = !showInCallKeypad }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(if (isCompactHeight) 14.dp else 32.dp))

                // End Call Button
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(if (isCompactHeight) 66.dp else 76.dp)
                        .testTag("end_call_button")
                        .shadow(elevation = 8.dp, shape = CircleShape, spotColor = InCallEndCallRed)
                        .background(InCallEndCallRed, CircleShape)
                        .clip(CircleShape)
                        .clickable { CallManager.disconnectCall(primaryCall.id) }
                ) {
                    Icon(
                        imageVector = Icons.Default.CallEnd,
                        contentDescription = "End Call",
                        tint = Color.White,
                        modifier = Modifier.size(34.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
        }

        // Slide-up In-Call DTMF Keypad
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
                            text = if (inCallDigits.isEmpty()) "In-Call Keypad" else inCallDigits,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = { showInCallKeypad = false }) {
                            Text(
                                text = "Hide",
                                color = SamsungGreen,
                                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

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
                                        buttonSize = 64.dp,
                                        onClick = {
                                            inCallDigits += digit
                                            CallManager.playInCallDtmf(digit)
                                        }
                                    )
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        }

        // Slide-up Audio Route Picker Sheet
        AnimatedVisibility(
            visible = showAudioPicker,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            Surface(
                shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Select Audio Output",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                        TextButton(onClick = { showAudioPicker = false }) {
                            Text("Close", color = SamsungGreen)
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    val options = buildList {
                        add(Triple(AudioRoute.EARPIECE, "Phone Earpiece", Icons.Default.PhoneInTalk))
                        add(Triple(AudioRoute.SPEAKER, "Speakerphone", Icons.AutoMirrored.Filled.VolumeUp))
                        if (audioState.hasBluetooth) {
                            add(Triple(AudioRoute.BLUETOOTH, "Bluetooth Audio", Icons.Default.BluetoothAudio))
                        }
                        if (audioState.hasWiredHeadset) {
                            add(Triple(AudioRoute.WIRED_HEADSET, "Wired Headset", Icons.Default.Headset))
                        }
                    }
                    options.forEach { (route, label, icon) ->
                        val selected = audioState.currentRoute == route
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = if (selected) SamsungGreen.copy(alpha = 0.14f) else MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .clickable {
                                    CallManager.setAudioRoute(route)
                                    showAudioPicker = false
                                }
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = icon,
                                    contentDescription = null,
                                    tint = if (selected) SamsungGreen else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                                    modifier = Modifier.weight(1f)
                                )
                                if (selected) {
                                    Icon(imageVector = Icons.Default.Check, contentDescription = "Selected", tint = SamsungGreen)
                                }
                            }
                        }
                    }
                }
            }
        }

        // Slide-up Quick-Decline with SMS Reply Sheet
        AnimatedVisibility(
            visible = showQuickMessages,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            Surface(
                shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Decline & Send Message",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                        TextButton(onClick = { showQuickMessages = false }) {
                            Text("Cancel", color = SamsungRed)
                        }
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    val replies = listOf(
                        "Can't talk right now. What's up?",
                        "I'm in a meeting. Call you back shortly.",
                        "I'm driving right now, I'll call you later.",
                        "Can you send me a text instead?"
                    )
                    replies.forEach { msg ->
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .clickable {
                                    showQuickMessages = false
                                    CallManager.rejectCall(primaryCall.id, msg)
                                }
                        ) {
                            Text(
                                text = msg,
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                                modifier = Modifier.padding(14.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SecondaryCallCard(
    secondaryCall: CallUiModel,
    canMerge: Boolean,
    onSwap: () -> Unit,
    onMerge: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = Color.White.copy(alpha = 0.14f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = secondaryCall.displayName ?: secondaryCall.number,
                    color = Color.White,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = if (secondaryCall.state == CallLifecycleState.HOLDING) "On hold" else secondaryCall.state.name.lowercase().replaceFirstChar { it.uppercase() },
                    color = SamsungOrange,
                    style = MaterialTheme.typography.labelSmall
                )
            }
            IconButton(onClick = onSwap, modifier = Modifier.defaultMinSize(48.dp, 48.dp)) {
                Icon(imageVector = Icons.Default.SwapCalls, contentDescription = "Swap calls", tint = Color.White)
            }
            if (canMerge) {
                IconButton(onClick = onMerge, modifier = Modifier.defaultMinSize(48.dp, 48.dp)) {
                    Icon(imageVector = Icons.AutoMirrored.Filled.CallMerge, contentDescription = "Merge conference", tint = SamsungGreen)
                }
            }
        }
    }
}

@Composable
private fun CallerPulsingAvatar(
    name: String,
    photoUri: String?,
    isPulsing: Boolean,
    compact: Boolean = false
) {
    val transition = rememberInfiniteTransition(label = "avatarPulse")
    val pulseScale by transition.animateFloat(
        initialValue = 1f,
        targetValue = if (isPulsing) 1.18f else 1.04f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = "pulseScale"
    )

    val outerSize = if (compact) 86.dp else 134.dp
    val midSize = if (compact) 74.dp else 114.dp
    val avatarDp = if (compact) 62 else 96

    Box(contentAlignment = Alignment.Center) {
        Box(
            modifier = Modifier
                .size(outerSize)
                .scale(pulseScale)
                .background(Color.White.copy(alpha = 0.08f), CircleShape)
        )
        Box(
            modifier = Modifier
                .size(midSize)
                .background(Color.White.copy(alpha = 0.12f), CircleShape)
        )
        ContactAvatar(name = name, photoUri = photoUri, size = avatarDp)
    }
}

@Composable
private fun IncomingCallControls(
    onAnswer: () -> Unit,
    onDecline: () -> Unit,
    onOpenQuickMessages: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = Color.White.copy(alpha = 0.15f),
            modifier = Modifier
                .clickable { onOpenQuickMessages() }
                .testTag("quick_decline_message_pill")
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Chat,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Send message",
                    color = Color.White,
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold)
                )
            }
        }

        Spacer(modifier = Modifier.height(28.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(74.dp)
                        .shadow(elevation = 8.dp, shape = CircleShape, spotColor = InCallEndCallRed)
                        .background(InCallEndCallRed, CircleShape)
                        .clip(CircleShape)
                        .clickable { onDecline() }
                        .testTag("decline_call_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.CallEnd,
                        contentDescription = "Decline Call",
                        tint = Color.White,
                        modifier = Modifier.size(34.dp)
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text("Decline", color = Color.White.copy(alpha = 0.9f), style = MaterialTheme.typography.labelMedium)
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(74.dp)
                        .shadow(elevation = 8.dp, shape = CircleShape, spotColor = SamsungGreen)
                        .background(SamsungGreen, CircleShape)
                        .clip(CircleShape)
                        .clickable { onAnswer() }
                        .testTag("answer_call_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Call,
                        contentDescription = "Answer Call",
                        tint = Color.White,
                        modifier = Modifier.size(34.dp)
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text("Answer", color = Color.White.copy(alpha = 0.9f), style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

@Composable
private fun InCallTile(
    icon: ImageVector,
    label: String,
    isActive: Boolean,
    testTag: String,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .minimumInteractiveComponentSize()
            .defaultMinSize(minWidth = 64.dp, minHeight = 64.dp)
            .testTag(testTag)
            .clip(RoundedCornerShape(16.dp))
            .clickable { onClick() }
            .padding(4.dp)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(62.dp)
                .clip(CircleShape)
                .background(
                    if (isActive) InCallTileActiveBg else InCallTileBg,
                    CircleShape
                )
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
            color = Color.White.copy(alpha = 0.92f),
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold)
        )
    }
}

private fun formatDuration(seconds: Long): String {
    val hrs = seconds / 3600
    val m = (seconds % 3600) / 60
    val s = seconds % 60
    return if (hrs > 0) {
        String.format(Locale.US, "%d:%02d:%02d", hrs, m, s)
    } else {
        String.format(Locale.US, "%02d:%02d", m, s)
    }
}
