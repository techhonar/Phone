package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.material.icons.filled.PhoneForwarded
import androidx.compose.material.icons.filled.Search
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
import com.example.ui.theme.GoogleCallGreen
import com.example.ui.theme.GoogleNavContainerDark
import com.example.ui.theme.GoogleNavContainerLight
import com.example.ui.theme.GoogleNavSelectedDark
import com.example.ui.theme.GoogleNavSelectedLight
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
 * Google Dialer style top bar (reference screenshot):
 * no title, just Search + overflow menu actions aligned to the end.
 * The overflow menu keeps the existing Speed dial / Settings / Add contact actions.
 */
@Composable
fun GoogleDialerTopBar(
    onMenuSpeedDial: () -> Unit,
    onMenuSettings: () -> Unit,
    onAddContact: (() -> Unit)? = null,
    onSearch: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var menuExpanded by remember { mutableStateOf(false) }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (onSearch != null) {
            IconButton(
                onClick = onSearch,
                modifier = Modifier.testTag("search_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = "Search contacts",
                    tint = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.size(24.dp)
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
                    tint = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.size(24.dp)
                )
            }

            DropdownMenu(
                expanded = menuExpanded,
                onDismissRequest = { menuExpanded = false },
                shape = RoundedCornerShape(18.dp)
            ) {
                if (onAddContact != null) {
                    DropdownMenuItem(
                        text = { Text("Add to contacts") },
                        onClick = {
                            menuExpanded = false
                            onAddContact()
                        }
                    )
                }
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
            }
        }
    }
}

/**
 * Google Dialer style flat keypad button (reference screenshot):
 * no circle / card background, just a large white digit with small
 * grey sub-letters underneath. Press state is a soft rounded highlight.
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
            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
        } else {
            Color.Transparent
        },
        label = "keyBg"
    )

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(width = 104.dp, height = 68.dp)
            .clip(RoundedCornerShape(34.dp))
            .background(bgColor)
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
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = digit.toString(),
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.Normal,
                    fontSize = 36.sp
                ),
                color = MaterialTheme.colorScheme.onSurface
            )

            if (isVoicemail) {
                Icon(
                    imageVector = Icons.Default.Voicemail,
                    contentDescription = "Voicemail",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(15.dp)
                )
            } else if (subLetters.isNotEmpty()) {
                Text(
                    text = subLetters,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Normal,
                        letterSpacing = 1.6.sp,
                        fontSize = if (subLetters == "+") 14.sp else 10.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Spacer(modifier = Modifier.height(15.dp))
            }
        }
    }
}

/**
 * Google Dialer style call button (reference screenshot):
 * a plain green circle with a white handset, no shadow and no SIM badge.
 * Long-press keeps the old SIM-slot toggle that used to live on the badge.
 */
@Composable
fun SamsungCallButton(
    simSlot: Int,
    onCallClick: () -> Unit,
    onSimToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(64.dp)
            .background(GoogleCallGreen, CircleShape)
            .clip(CircleShape)
            .combinedClickable(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onCallClick()
                },
                onLongClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onSimToggle()
                }
            )
            .testTag("call_action_button")
    ) {
        Icon(
            imageVector = Icons.Default.Call,
            contentDescription = "Call (SIM $simSlot)",
            tint = Color.White,
            modifier = Modifier.size(30.dp)
        )
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
 * Google Dialer style bottom navigation (reference screenshot):
 * a floating rounded pill with three tabs - Keypad, Recents, Contacts.
 * The selected tab sits on a lighter pill inside the container.
 * (Places is no longer shown here, to match the screenshot; its
 * screen and PhoneTab.PLACES entry are left intact.)
 */
@Composable
fun SamsungBottomNavBar(
    selectedTab: PhoneTab,
    onTabSelected: (PhoneTab) -> Unit,
    missedCallsCount: Int = 0,
    modifier: Modifier = Modifier
) {
    val isDark = isSystemInDarkTheme()
    val containerColor = if (isDark) GoogleNavContainerDark else GoogleNavContainerLight
    val selectedColor = if (isDark) GoogleNavSelectedDark else GoogleNavSelectedLight
    val selectedContent = MaterialTheme.colorScheme.onSurface
    val unselectedContent = MaterialTheme.colorScheme.onSurfaceVariant

    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 52.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            shape = RoundedCornerShape(36.dp),
            color = containerColor,
            modifier = Modifier.testTag("bottom_nav_bar")
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(6.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val items = listOf(
                    Triple(PhoneTab.KEYPAD, "Keypad", Icons.Default.Dialpad),
                    Triple(PhoneTab.RECENTS, "Recents", Icons.Default.PhoneForwarded),
                    Triple(PhoneTab.CONTACTS, "Contacts", Icons.Outlined.Person)
                )

                items.forEach { (tab, label, icon) ->
                    val isSelected = selectedTab == tab
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(28.dp))
                            .background(if (isSelected) selectedColor else Color.Transparent)
                            .clickable { onTabSelected(tab) }
                            .padding(vertical = 7.dp)
                            .testTag("nav_tab_${tab.name.lowercase()}")
                    ) {
                        Box {
                            Icon(
                                imageVector = icon,
                                contentDescription = label,
                                tint = if (isSelected) selectedContent else unselectedContent,
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
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                fontSize = 11.sp
                            ),
                            color = if (isSelected) selectedContent else unselectedContent
                        )
                    }
                }
            }
        }
    }
}
