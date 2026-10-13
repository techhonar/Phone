package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
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
import com.example.ui.PhoneViewModel
import com.example.ui.components.ContactAvatar
import com.example.ui.PhoneTab
import com.example.ui.components.GoogleDialerTopBar
import com.example.ui.components.SamsungCallButton
import com.example.ui.components.SamsungDialButton
import com.example.ui.theme.SamsungBlue
import com.example.ui.theme.SamsungGreen
import com.example.ui.theme.SamsungGreenDark

@Composable
fun KeypadScreen(
    viewModel: PhoneViewModel,
    modifier: Modifier = Modifier
) {
    val dialedDigits by viewModel.dialedDigits.collectAsState()
    val formattedDigits by viewModel.formattedDigits.collectAsState()
    val t9Matches by viewModel.t9Results.collectAsState()
    val simSlot by viewModel.simSlot.collectAsState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Google Dialer top bar: search + overflow, no title (reference screenshot)
        GoogleDialerTopBar(
            onMenuSpeedDial = { viewModel.openSpeedDialDialog() },
            onMenuSettings = { viewModel.openSettingsDialog() },
            onAddContact = { viewModel.openCreateContact(dialedDigits) },
            onSearch = { viewModel.selectTab(PhoneTab.CONTACTS) }
        )

        // Large empty space above the keypad, as in the screenshot.
        // The dialed number (when present) sits at the bottom of this space.
        Spacer(modifier = Modifier.weight(1f))

        // Dialed Digits Display Area
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .height(72.dp),
            contentAlignment = Alignment.Center
        ) {
            if (dialedDigits.isNotEmpty()) {
                Text(
                    text = formattedDigits.ifEmpty { dialedDigits },
                    style = MaterialTheme.typography.headlineLarge.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = if (dialedDigits.length > 12) 28.sp else 36.sp,
                        letterSpacing = 1.sp
                    ),
                    color = MaterialTheme.colorScheme.onBackground,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.testTag("dialed_number_text")
                )
            }
        }

        // Action Pills when number entered ("Add to contacts")
        AnimatedVisibility(
            visible = dialedDigits.isNotEmpty(),
            enter = fadeIn() + slideInVertically(),
            exit = fadeOut()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.Center,
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
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = null,
                            tint = SamsungGreen,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Add to contacts",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = SamsungGreen
                        )
                    }
                }
            }
        }

        // T9 Matching Suggestions Bar
        AnimatedVisibility(
            visible = t9Matches.isNotEmpty(),
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
            ) {
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp)
                ) {
                    items(t9Matches, key = { it.contactId }) { match ->
                        T9ContactCard(
                            match = match,
                            query = dialedDigits,
                            onClick = {
                                viewModel.startCall(match.phone, match.name)
                            }
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Samsung 3x4 Dialpad Grid
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Row 1: 1, 2, 3
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                SamsungDialButton(
                    digit = '1',
                    isVoicemail = true,
                    onClick = { viewModel.onDigitPress('1') },
                    onLongClick = { viewModel.onDigitLongPress('1') }
                )
                SamsungDialButton(
                    digit = '2',
                    subLetters = "ABC",
                    onClick = { viewModel.onDigitPress('2') },
                    onLongClick = { viewModel.onDigitLongPress('2') }
                )
                SamsungDialButton(
                    digit = '3',
                    subLetters = "DEF",
                    onClick = { viewModel.onDigitPress('3') },
                    onLongClick = { viewModel.onDigitLongPress('3') }
                )
            }

            // Row 2: 4, 5, 6
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                SamsungDialButton(
                    digit = '4',
                    subLetters = "GHI",
                    onClick = { viewModel.onDigitPress('4') },
                    onLongClick = { viewModel.onDigitLongPress('4') }
                )
                SamsungDialButton(
                    digit = '5',
                    subLetters = "JKL",
                    onClick = { viewModel.onDigitPress('5') },
                    onLongClick = { viewModel.onDigitLongPress('5') }
                )
                SamsungDialButton(
                    digit = '6',
                    subLetters = "MNO",
                    onClick = { viewModel.onDigitPress('6') },
                    onLongClick = { viewModel.onDigitLongPress('6') }
                )
            }

            // Row 3: 7, 8, 9
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                SamsungDialButton(
                    digit = '7',
                    subLetters = "PQRS",
                    onClick = { viewModel.onDigitPress('7') },
                    onLongClick = { viewModel.onDigitLongPress('7') }
                )
                SamsungDialButton(
                    digit = '8',
                    subLetters = "TUV",
                    onClick = { viewModel.onDigitPress('8') },
                    onLongClick = { viewModel.onDigitLongPress('8') }
                )
                SamsungDialButton(
                    digit = '9',
                    subLetters = "WXYZ",
                    onClick = { viewModel.onDigitPress('9') },
                    onLongClick = { viewModel.onDigitLongPress('9') }
                )
            }

            // Row 4: *, 0, #
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                SamsungDialButton(
                    digit = '*',
                    subLetters = "",
                    onClick = { viewModel.onDigitPress('*') },
                    onLongClick = { viewModel.onDigitLongPress('*') }
                )
                SamsungDialButton(
                    digit = '0',
                    subLetters = "+",
                    onClick = { viewModel.onDigitPress('0') },
                    onLongClick = { viewModel.onDigitLongPress('0') }
                )
                SamsungDialButton(
                    digit = '#',
                    subLetters = "",
                    onClick = { viewModel.onDigitPress('#') },
                    onLongClick = { viewModel.onDigitLongPress('#') }
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Call button centred on its own, as in the screenshot.
        // Backspace (when a number is entered) sits flat at the end of the
        // same row so the call button stays centred: tap = delete,
        // long-press = clear all. Long-press the call button to switch SIM.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 32.dp, vertical = 8.dp)
                .height(64.dp)
        ) {
            SamsungCallButton(
                simSlot = simSlot,
                onCallClick = {
                    if (dialedDigits.isNotEmpty()) {
                        viewModel.startCall(dialedDigits, null)
                    }
                },
                onSimToggle = { viewModel.toggleSimSlot() },
                modifier = Modifier.align(Alignment.Center)
            )

            if (dialedDigits.isNotEmpty()) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
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
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Backspace,
                        contentDescription = "Backspace",
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))
    }
}

/**
 * T9 Recommendation Card with Highlighted Matching Letters
 */
@Composable
private fun T9ContactCard(
    match: T9Match,
    query: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 2.dp,
        modifier = modifier
            .clickable { onClick() }
            .testTag("t9_match_${match.contactId}")
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ContactAvatar(name = match.name, size = 36)
            Spacer(modifier = Modifier.width(10.dp))
            Column {
                // Highlight matching substring
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
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = match.phone,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
