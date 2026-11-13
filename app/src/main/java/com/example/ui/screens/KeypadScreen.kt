package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.SimCard
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.rust.T9Match
import com.example.ui.PhoneTab
import com.example.ui.PhoneViewModel
import com.example.ui.components.ContactAvatar
import com.example.ui.components.OneUiHeader
import com.example.ui.components.SamsungCallButton
import com.example.ui.components.SamsungDialButton
import com.example.ui.components.SetupBannerCard
import com.example.ui.theme.SamsungBlue
import com.example.ui.theme.SamsungGreen

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun KeypadScreen(
    viewModel: PhoneViewModel,
    onRequestDefaultDialer: () -> Unit,
    onRequestPermissions: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val dialedDigits by viewModel.dialedDigits.collectAsState()
    val formattedDigits by viewModel.formattedDigits.collectAsState()
    val t9Matches by viewModel.t9Results.collectAsState()
    val simAccounts by viewModel.simAccounts.collectAsState()
    val selectedSimIdx by viewModel.selectedSimIndex.collectAsState()
    val hapticsEnabled by viewModel.keypadHapticsEnabled.collectAsState()
    val permissionStatus by viewModel.permissionStatus.collectAsState()
    val telephonyStatus by viewModel.telephonyStatus.collectAsState()
    val speedDials by viewModel.speedDials.collectAsState()
    val speedDialsByDigit = remember(speedDials) { speedDials.associateBy { it.keyDigit } }

    var showNumberMenu by remember { mutableStateOf(false) }

    val activeSim = simAccounts.getOrNull(selectedSimIdx)
    val simBadgeText = when {
        activeSim?.isConnectionServiceAccount == true -> "TEL"
        activeSim != null -> "SIM ${activeSim.slotNumber}"
        else -> "SIM ${(selectedSimIdx % 2) + 1}"
    }
    val headerSubtitle = buildString {
        if (telephonyStatus.carrierName.isNotBlank()) {
            append(telephonyStatus.carrierName)
            append(" • ")
            append(telephonyStatus.networkTypeLabel)
        } else {
            append(telephonyStatus.simStateLabel)
            if (activeSim != null) {
                append(" • ")
                append(activeSim.label)
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        OneUiHeader(
            title = "Phone",
            subtitle = headerSubtitle,
            onMenuSpeedDial = { viewModel.openSpeedDialDialog() },
            onMenuSettings = { viewModel.openSettingsDialog() },
            onMenuStats = { viewModel.openCallStatsScreen() },
            onAddContact = { viewModel.openCreateContact(dialedDigits) }
        )

        // Prompt banner if permissions or default dialer role are missing
        if (!permissionStatus.hasCoreTelephony) {
            SetupBannerCard(
                title = "Enable Phone & Contacts permissions for direct calling",
                actionLabel = "Allow",
                accentColor = SamsungGreen,
                onAction = onRequestPermissions
            )
        } else if (!permissionStatus.isDefaultDialer) {
            SetupBannerCard(
                title = "Set as Default Phone app for full call screen & screening",
                actionLabel = "Set Default",
                accentColor = SamsungBlue,
                onAction = onRequestDefaultDialer
            )
        }

        BoxWithConstraints(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .widthIn(max = 560.dp),
            contentAlignment = Alignment.BottomCenter
        ) {
            val compactHeight = maxHeight < 540.dp
            val keySize = if (compactHeight) 64.dp else 74.dp
            val rowSpacing = if (compactHeight) 8.dp else 12.dp

            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(modifier = Modifier.weight(0.15f))

                // Active SIM / Telecom ConnectionService Account Pill
                if (simAccounts.isNotEmpty()) {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier
                            .clickable { viewModel.toggleSimSlot() }
                            .testTag("sim_selector_pill")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.SimCard,
                                contentDescription = null,
                                tint = SamsungGreen,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = activeSim?.label ?: simBadgeText,
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                }

                // Dialed Digits Display Area (Long-press for Copy / Paste)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp)
                        .height(if (compactHeight) 56.dp else 68.dp)
                        .combinedClickable(
                            onClick = {},
                            onLongClick = { showNumberMenu = true }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (dialedDigits.isNotEmpty()) {
                        Text(
                            text = formattedDigits.ifEmpty { dialedDigits },
                            style = MaterialTheme.typography.headlineLarge.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = when {
                                    dialedDigits.length > 14 -> 24.sp
                                    dialedDigits.length > 10 -> 30.sp
                                    else -> 36.sp
                                },
                                letterSpacing = 0.8.sp
                            ),
                            color = MaterialTheme.colorScheme.onBackground,
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.testTag("dialed_number_text")
                        )
                    } else {
                        Text(
                            text = "Enter number or T9 name",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f)
                        )
                    }

                    DropdownMenu(
                        expanded = showNumberMenu,
                        onDismissRequest = { showNumberMenu = false }
                    ) {
                        if (dialedDigits.isNotEmpty()) {
                            DropdownMenuItem(
                                text = { Text("Copy number") },
                                leadingIcon = { Icon(Icons.Default.ContentCopy, contentDescription = null) },
                                onClick = {
                                    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                    cm?.setPrimaryClip(ClipData.newPlainText("Phone Number", dialedDigits))
                                    showNumberMenu = false
                                }
                            )
                        }
                        DropdownMenuItem(
                            text = { Text("Paste") },
                            leadingIcon = { Icon(Icons.Default.ContentPaste, contentDescription = null) },
                            onClick = {
                                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                val clipText = cm?.primaryClip?.getItemAt(0)?.text?.toString().orEmpty()
                                val cleaned = clipText.filter { it.isDigit() || it == '+' || it == '*' || it == '#' }
                                if (cleaned.isNotEmpty()) {
                                    viewModel.setDialedNumber(dialedDigits + cleaned)
                                }
                                showNumberMenu = false
                            }
                        )
                    }
                }

                // Quick Action Pills ("Add to contacts", "Send message")
                AnimatedVisibility(
                    visible = dialedDigits.isNotEmpty(),
                    enter = fadeIn() + slideInVertically(),
                    exit = fadeOut()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 2.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier
                                .clickable { viewModel.openCreateContact(dialedDigits) }
                                .testTag("add_number_to_contacts_pill")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Add,
                                    contentDescription = null,
                                    tint = SamsungGreen,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Add contact",
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                                    color = SamsungGreen
                                )
                            }
                        }

                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier
                                .clickable { viewModel.sendMessage(dialedDigits) }
                                .testTag("send_message_pill")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.Chat,
                                    contentDescription = null,
                                    tint = SamsungBlue,
                                    modifier = Modifier.size(15.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Message",
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                                    color = SamsungBlue
                                )
                            }
                        }
                    }
                }

                // T9 Matching Contacts Strip
                AnimatedVisibility(
                    visible = t9Matches.isNotEmpty(),
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    LazyRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        contentPadding = PaddingValues(horizontal = 18.dp)
                    ) {
                        items(t9Matches, key = { "${it.contactId}_${it.phone}" }) { match ->
                            T9ContactCard(
                                match = match,
                                onCallClick = { viewModel.startCall(match.phone) },
                                onInfoClick = {
                                    viewModel.selectTab(PhoneTab.CONTACTS)
                                    viewModel.openContactDetailById(match.contactId)
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.weight(0.25f))

                // 3x4 Dialpad Grid
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 32.dp),
                    verticalArrangement = Arrangement.spacedBy(rowSpacing),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        SamsungDialButton(
                            digit = '1',
                            isVoicemail = true,
                            hapticEnabled = hapticsEnabled,
                            buttonSize = keySize,
                            onClick = { viewModel.onDigitPress('1') },
                            onLongClick = { viewModel.onDigitLongPress('1') }
                        )
                        SamsungDialButton(
                            digit = '2',
                            subLetters = "ABC",
                            speedDialName = speedDialsByDigit[2]?.contactName,
                            hapticEnabled = hapticsEnabled,
                            buttonSize = keySize,
                            onClick = { viewModel.onDigitPress('2') },
                            onLongClick = { viewModel.onDigitLongPress('2') }
                        )
                        SamsungDialButton(
                            digit = '3',
                            subLetters = "DEF",
                            speedDialName = speedDialsByDigit[3]?.contactName,
                            hapticEnabled = hapticsEnabled,
                            buttonSize = keySize,
                            onClick = { viewModel.onDigitPress('3') },
                            onLongClick = { viewModel.onDigitLongPress('3') }
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        SamsungDialButton(
                            digit = '4',
                            subLetters = "GHI",
                            speedDialName = speedDialsByDigit[4]?.contactName,
                            hapticEnabled = hapticsEnabled,
                            buttonSize = keySize,
                            onClick = { viewModel.onDigitPress('4') },
                            onLongClick = { viewModel.onDigitLongPress('4') }
                        )
                        SamsungDialButton(
                            digit = '5',
                            subLetters = "JKL",
                            speedDialName = speedDialsByDigit[5]?.contactName,
                            hapticEnabled = hapticsEnabled,
                            buttonSize = keySize,
                            onClick = { viewModel.onDigitPress('5') },
                            onLongClick = { viewModel.onDigitLongPress('5') }
                        )
                        SamsungDialButton(
                            digit = '6',
                            subLetters = "MNO",
                            speedDialName = speedDialsByDigit[6]?.contactName,
                            hapticEnabled = hapticsEnabled,
                            buttonSize = keySize,
                            onClick = { viewModel.onDigitPress('6') },
                            onLongClick = { viewModel.onDigitLongPress('6') }
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        SamsungDialButton(
                            digit = '7',
                            subLetters = "PQRS",
                            speedDialName = speedDialsByDigit[7]?.contactName,
                            hapticEnabled = hapticsEnabled,
                            buttonSize = keySize,
                            onClick = { viewModel.onDigitPress('7') },
                            onLongClick = { viewModel.onDigitLongPress('7') }
                        )
                        SamsungDialButton(
                            digit = '8',
                            subLetters = "TUV",
                            speedDialName = speedDialsByDigit[8]?.contactName,
                            hapticEnabled = hapticsEnabled,
                            buttonSize = keySize,
                            onClick = { viewModel.onDigitPress('8') },
                            onLongClick = { viewModel.onDigitLongPress('8') }
                        )
                        SamsungDialButton(
                            digit = '9',
                            subLetters = "WXYZ",
                            speedDialName = speedDialsByDigit[9]?.contactName,
                            hapticEnabled = hapticsEnabled,
                            buttonSize = keySize,
                            onClick = { viewModel.onDigitPress('9') },
                            onLongClick = { viewModel.onDigitLongPress('9') }
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        SamsungDialButton(
                            digit = '*',
                            hapticEnabled = hapticsEnabled,
                            buttonSize = keySize,
                            onClick = { viewModel.onDigitPress('*') },
                            onLongClick = { viewModel.onDigitLongPress('*') }
                        )
                        SamsungDialButton(
                            digit = '0',
                            subLetters = "+",
                            hapticEnabled = hapticsEnabled,
                            buttonSize = keySize,
                            onClick = { viewModel.onDigitPress('0') },
                            onLongClick = { viewModel.onDigitLongPress('0') }
                        )
                        SamsungDialButton(
                            digit = '#',
                            hapticEnabled = hapticsEnabled,
                            buttonSize = keySize,
                            onClick = { viewModel.onDigitPress('#') },
                            onLongClick = { viewModel.onDigitLongPress('#') }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(if (compactHeight) 10.dp else 16.dp))

                // Call Action & Backspace Row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 36.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Left: SMS shortcut when number entered
                    Box(
                        modifier = Modifier.size(52.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        if (dialedDigits.isNotEmpty()) {
                            IconButton(
                                onClick = { viewModel.sendMessage(dialedDigits) },
                                modifier = Modifier
                                    .size(48.dp)
                                    .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape)
                                    .testTag("sms_action_button")
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.Chat,
                                    contentDescription = "Send text message",
                                    tint = SamsungBlue,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                    }

                    // Center: Green Samsung Call Button
                    SamsungCallButton(
                        simLabel = simBadgeText,
                        showSimBadge = simAccounts.size > 1,
                        onCallClick = { viewModel.handleCallButtonPress() },
                        onSimToggle = { viewModel.toggleSimSlot() }
                    )

                    // Right: Backspace (Tap = delete 1, Long Press = clear all)
                    Box(
                        modifier = Modifier.size(52.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        if (dialedDigits.isNotEmpty()) {
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(CircleShape)
                                    .pointerInput(Unit) {
                                        detectTapGestures(
                                            onTap = { viewModel.onBackspace() },
                                            onLongPress = { viewModel.onClearAllDigits() }
                                        )
                                    }
                                    .testTag("backspace_button")
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.Backspace,
                                        contentDescription = "Backspace",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun T9ContactCard(
    match: T9Match,
    onCallClick: () -> Unit,
    onInfoClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 2.dp,
        modifier = modifier
            .clickable { onCallClick() }
            .testTag("t9_match_${match.contactId}")
    ) {
        Row(
            modifier = Modifier.padding(start = 10.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ContactAvatar(name = match.name, photoUri = match.photoUri, size = 38)
            Spacer(modifier = Modifier.width(10.dp))
            Column {
                val annotatedName = buildAnnotatedString {
                    val name = match.name
                    if (match.matchType == "name" && match.matchStart >= 0 && match.matchStart + match.matchLength <= name.length) {
                        append(name.substring(0, match.matchStart))
                        withStyle(style = SpanStyle(color = SamsungGreen, fontWeight = FontWeight.ExtraBold)) {
                            append(name.substring(match.matchStart, match.matchStart + match.matchLength))
                        }
                        append(name.substring(match.matchStart + match.matchLength))
                    } else {
                        append(name)
                    }
                }
                Text(
                    text = annotatedName,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = match.phone,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
            IconButton(onClick = onInfoClick, modifier = Modifier.size(36.dp)) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = "Contact details",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}
