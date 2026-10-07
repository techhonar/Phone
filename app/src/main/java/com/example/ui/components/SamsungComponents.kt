package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Dialpad
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Voicemail
import androidx.compose.material.icons.outlined.Dialpad
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.PhoneTab
import com.example.ui.theme.SamsungGreen
import com.example.ui.theme.SamsungGreenDark
import com.example.ui.theme.SamsungRed

/**
 * Samsung One UI Collapsing Reachability Header
 */
@Composable
fun OneUiHeader(
    title: String,
    subtitle: String? = null,
    onMenuSpeedDial: () -> Unit,
    onMenuSettings: () -> Unit,
    onMenuClearLogs: (() -> Unit)? = null,
    onAddContact: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var menuExpanded by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-0.5).sp
                    ),
                    color = MaterialTheme.colorScheme.onBackground
                )
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                if (onAddContact != null) {
                    IconButton(
                        onClick = onAddContact,
                        modifier = Modifier.testTag("add_contact_button")
                    ) {
                        Text(
                            text = "+",
                            style = MaterialTheme.typography.titleLarge.copy(fontSize = 28.sp),
                            color = SamsungGreen
                        )
                    }
                }

                Box {
                    IconButton(
                        onClick = { menuExpanded = true },
                        modifier = Modifier.testTag("menu_overflow_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "Menu",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false },
                        shape = RoundedCornerShape(18.dp)
                    ) {
                        DropdownMenuItem(
                            text = { Text("Speed dial numbers") },
                            onClick = {
                                menuExpanded = false
                                onMenuSpeedDial()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Settings") },
                            onClick = {
                                menuExpanded = false
                                onMenuSettings()
                            }
                        )
                        if (onMenuClearLogs != null) {
                            DropdownMenuItem(
                                text = { Text("Delete all call logs", color = SamsungRed) },
                                onClick = {
                                    menuExpanded = false
                                    onMenuClearLogs()
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Samsung Keypad Button with Large Digit & Subtitle Letters
 */
@Composable
fun SamsungDialButton(
    digit: Char,
    subLetters: String = "",
    isVoicemail: Boolean = false,
    onClick: () -> Unit,
    onLongClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    var isPressed by remember { mutableStateOf(false) }

    val bgColor by animateColorAsState(
        targetValue = if (isPressed) {
            MaterialTheme.colorScheme.surfaceVariant
        } else {
            MaterialTheme.colorScheme.surface
        },
        label = "keyBg"
    )

    Surface(
        shape = CircleShape,
        color = bgColor,
        shadowElevation = if (isPressed) 0.dp else 1.dp,
        modifier = modifier
            .size(76.dp)
            .testTag("dial_key_$digit")
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        isPressed = true
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        tryAwaitRelease()
                        isPressed = false
                    },
                    onTap = { onClick() },
                    onLongPress = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onLongClick()
                    }
                )
            }
    ) {
        Column(
            modifier = Modifier.padding(2.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = digit.toString(),
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 30.sp
                ),
                color = MaterialTheme.colorScheme.onSurface
            )

            if (isVoicemail) {
                Icon(
                    imageVector = Icons.Default.Voicemail,
                    contentDescription = "Voicemail",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(14.dp)
                )
            } else if (subLetters.isNotEmpty()) {
                Text(
                    text = subLetters,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.2.sp,
                        fontSize = 10.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Spacer(modifier = Modifier.height(14.dp))
            }
        }
    }
}

/**
 * Green Samsung Call Button with handset icon and SIM badge
 */
@Composable
fun SamsungCallButton(
    simSlot: Int,
    onCallClick: () -> Unit,
    onSimToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(74.dp)
                .shadow(elevation = 6.dp, shape = CircleShape, spotColor = SamsungGreen)
                .background(SamsungGreen, CircleShape)
                .clip(CircleShape)
                .clickable {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onCallClick()
                }
                .testTag("call_action_button")
        ) {
            Icon(
                imageVector = Icons.Default.Call,
                contentDescription = "Call",
                tint = Color.White,
                modifier = Modifier.size(34.dp)
            )

            // SIM badge
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = SamsungGreenDark,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 4.dp, bottom = 4.dp)
                    .clickable { onSimToggle() }
            ) {
                Text(
                    text = "SIM $simSlot",
                    color = Color.White,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                )
            }
        }
    }
}

/**
 * Contact Avatar circle with colored background and initials
 */
@Composable
fun ContactAvatar(
    name: String,
    avatarColorHex: String = "#27C064",
    size: Int = 44,
    modifier: Modifier = Modifier
) {
    val parsedColor = remember(avatarColorHex) {
        try {
            Color(android.graphics.Color.parseColor(avatarColorHex))
        } catch (e: Exception) {
            SamsungGreen
        }
    }

    val initials = remember(name) {
        val parts = name.trim().split(" ")
        if (parts.size >= 2) {
            "${parts[0].firstOrNull() ?: '?'}${parts[1].firstOrNull() ?: ' '}".trim().uppercase()
        } else {
            (name.firstOrNull() ?: '?').toString().uppercase()
        }
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(size.dp)
            .background(parsedColor, CircleShape)
            .clip(CircleShape)
    ) {
        Text(
            text = initials,
            color = Color.White,
            style = MaterialTheme.typography.titleMedium.copy(
                fontWeight = FontWeight.Bold,
                fontSize = (size * 0.42).sp
            )
        )
    }
}

/**
 * Samsung Bottom Navigation Bar
 */
@Composable
fun SamsungBottomNavBar(
    selectedTab: PhoneTab,
    onTabSelected: (PhoneTab) -> Unit,
    missedCallsCount: Int = 0,
    modifier: Modifier = Modifier
) {
    NavigationBar(
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 8.dp,
        modifier = modifier.testTag("bottom_nav_bar")
    ) {
        val items = listOf(
            Triple(PhoneTab.KEYPAD, "Keypad", Pair(Icons.Default.Dialpad, Icons.Outlined.Dialpad)),
            Triple(PhoneTab.RECENTS, "Recents", Pair(Icons.Default.Schedule, Icons.Outlined.Schedule)),
            Triple(PhoneTab.CONTACTS, "Contacts", Pair(Icons.Default.Person, Icons.Outlined.Person)),
            Triple(PhoneTab.PLACES, "Places", Pair(Icons.Default.Place, Icons.Outlined.Place))
        )

        items.forEach { (tab, label, icons) ->
            val isSelected = selectedTab == tab
            NavigationBarItem(
                selected = isSelected,
                onClick = { onTabSelected(tab) },
                icon = {
                    Box {
                        Icon(
                            imageVector = if (isSelected) icons.first else icons.second,
                            contentDescription = label,
                            modifier = Modifier.size(24.dp)
                        )
                        if (tab == PhoneTab.RECENTS && missedCallsCount > 0) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .size(16.dp)
                                    .background(SamsungRed, CircleShape)
                            ) {
                                Text(
                                    text = missedCallsCount.toString(),
                                    color = Color.White,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                },
                label = {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                        )
                    )
                },
                colors = NavigationBarItemDefaults.colors(
                    indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                    selectedIconColor = SamsungGreen,
                    selectedTextColor = SamsungGreen,
                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                ),
                modifier = Modifier.testTag("nav_tab_${tab.name.lowercase()}")
            )
        }
    }
}
