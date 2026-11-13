package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Dialpad
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Voicemail
import androidx.compose.material.icons.outlined.Dialpad
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.ui.PhoneTab
import com.example.ui.theme.SamsungBlue
import com.example.ui.theme.SamsungGreen
import com.example.ui.theme.SamsungGreenDark
import com.example.ui.theme.SamsungRed

/**
 * Samsung One UI 7 Header with clean spacing and overflow actions
 */
@Composable
fun OneUiHeader(
    title: String,
    subtitle: String? = null,
    onMenuSpeedDial: () -> Unit,
    onMenuSettings: () -> Unit,
    onMenuStats: (() -> Unit)? = null,
    showStatsHeaderIcon: Boolean = false,
    onMenuClearLogs: (() -> Unit)? = null,
    onAddContact: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var menuExpanded by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-0.5).sp
                    ),
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                if (showStatsHeaderIcon && onMenuStats != null) {
                    IconButton(
                        onClick = onMenuStats,
                        modifier = Modifier
                            .defaultMinSize(48.dp, 48.dp)
                            .testTag("header_stats_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.BarChart,
                            contentDescription = "Call Statistics",
                            tint = SamsungBlue,
                            modifier = Modifier.size(23.dp)
                        )
                    }
                }

                IconButton(
                    onClick = onMenuSpeedDial,
                    modifier = Modifier
                        .defaultMinSize(48.dp, 48.dp)
                        .testTag("header_speed_dial_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Bolt,
                        contentDescription = "Speed Dial",
                        tint = SamsungGreen,
                        modifier = Modifier.size(23.dp)
                    )
                }

                if (onAddContact != null) {
                    IconButton(
                        onClick = onAddContact,
                        modifier = Modifier
                            .defaultMinSize(48.dp, 48.dp)
                            .testTag("add_contact_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Add Contact",
                            tint = SamsungGreen,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                }

                Box {
                    IconButton(
                        onClick = { menuExpanded = true },
                        modifier = Modifier
                            .defaultMinSize(48.dp, 48.dp)
                            .testTag("menu_overflow_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "More options",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false },
                        shape = RoundedCornerShape(20.dp)
                    ) {
                        if (onMenuStats != null) {
                            DropdownMenuItem(
                                text = { Text("Call statistics") },
                                onClick = {
                                    menuExpanded = false
                                    onMenuStats()
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
                            text = { Text("Call settings") },
                            onClick = {
                                menuExpanded = false
                                onMenuSettings()
                            }
                        )
                        if (onMenuClearLogs != null) {
                            DropdownMenuItem(
                                text = { Text("Clear call history", color = SamsungRed) },
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
 * Compact One UI banner prompting to set Default Dialer or grant Telephony permissions
 */
@Composable
fun SetupBannerCard(
    title: String,
    actionLabel: String,
    accentColor: Color = SamsungBlue,
    onAction: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = accentColor.copy(alpha = 0.12f),
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 14.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Shield,
                contentDescription = null,
                tint = accentColor,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            TextButton(
                onClick = onAction,
                modifier = Modifier.testTag("setup_banner_action")
            ) {
                Text(
                    text = actionLabel,
                    color = accentColor,
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                )
            }
        }
    }
}

/**
 * Friendly One UI permission prompt card shown when Contacts or Call Log permission is not granted yet
 */
@Composable
fun PermissionEmptyStateCard(
    icon: ImageVector,
    title: String,
    description: String,
    buttonText: String,
    onGrantClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(28.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            shape = RoundedCornerShape(26.dp),
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 2.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(64.dp)
                        .background(SamsungGreen.copy(alpha = 0.14f), CircleShape)
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = SamsungGreen,
                        modifier = Modifier.size(32.dp)
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(20.dp))
                Button(
                    onClick = onGrantClick,
                    colors = ButtonDefaults.buttonColors(containerColor = SamsungGreen),
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier
                        .defaultMinSize(minHeight = 48.dp)
                        .testTag("grant_permission_button")
                ) {
                    Text(
                        text = buttonText,
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                        color = Color.White
                    )
                }
            }
        }
    }
}

/**
 * Samsung Keypad Button with Large Digit, Subtitle Letters, and tactile spring press animation
 */
@Composable
fun SamsungDialButton(
    digit: Char,
    subLetters: String = "",
    isVoicemail: Boolean = false,
    speedDialName: String? = null,
    hapticEnabled: Boolean = true,
    buttonSize: Dp = 74.dp,
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
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.93f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "keyScale"
    )

    val a11yDescription = buildString {
        append("Digit $digit")
        if (isVoicemail) append(", Voicemail")
        else if (subLetters.isNotEmpty()) append(", $subLetters")
        if (!speedDialName.isNullOrBlank()) append(", Speed dial $speedDialName")
    }

    Surface(
        shape = CircleShape,
        color = bgColor,
        shadowElevation = if (isPressed) 0.dp else 1.dp,
        modifier = modifier
            .size(buttonSize)
            .scale(scale)
            .testTag("dial_key_$digit")
            .semantics { contentDescription = a11yDescription }
            .pointerInput(hapticEnabled) {
                detectTapGestures(
                    onPress = {
                        isPressed = true
                        if (hapticEnabled) {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        }
                        tryAwaitRelease()
                        isPressed = false
                    },
                    onTap = { onClick() },
                    onLongPress = {
                        if (hapticEnabled) {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        }
                        onLongClick()
                    }
                )
            }
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            if (!speedDialName.isNullOrBlank()) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 10.dp, end = 14.dp)
                        .size(7.dp)
                        .background(SamsungGreen, CircleShape)
                        .testTag("dial_key_speed_badge_$digit")
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(2.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = digit.toString(),
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 29.sp
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
                } else if (!speedDialName.isNullOrBlank()) {
                    Text(
                        text = speedDialName.trim().substringBefore(" "),
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.sp
                        ),
                        color = SamsungGreen,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 6.dp)
                    )
                } else if (subLetters.isNotEmpty()) {
                    Text(
                        text = subLetters,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.1.sp,
                            fontSize = 10.sp
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Spacer(modifier = Modifier.height(13.dp))
                }
            }
        }
    }
}

/**
 * Green Samsung Call Button with handset icon and SIM badge
 */
@Composable
fun SamsungCallButton(
    simLabel: String,
    showSimBadge: Boolean = true,
    onCallClick: () -> Unit,
    onSimToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier.size(76.dp)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(72.dp)
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
                contentDescription = "Place Call",
                tint = Color.White,
                modifier = Modifier.size(32.dp)
            )
        }

        if (showSimBadge) {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = SamsungGreenDark,
                shadowElevation = 2.dp,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .clickable { onSimToggle() }
                    .testTag("sim_badge_button")
            ) {
                Text(
                    text = simLabel,
                    color = Color.White,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                )
            }
        }
    }
}

/**
 * Contact Avatar circle supporting real Android Contact photoUri via Coil, with monogram fallback
 */
@Composable
fun ContactAvatar(
    name: String,
    photoUri: String? = null,
    avatarColorHex: String = "#27C064",
    size: Int = 44,
    modifier: Modifier = Modifier
) {
    val parsedColor = remember(avatarColorHex) {
        try {
            Color(android.graphics.Color.parseColor(avatarColorHex))
        } catch (_: Exception) {
            SamsungGreen
        }
    }

    val initials = remember(name) {
        val parts = name.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        when {
            parts.size >= 2 -> {
                val c1 = parts[0].firstOrNull { it.isLetterOrDigit() } ?: parts[0].first()
                val c2 = parts[1].firstOrNull { it.isLetterOrDigit() } ?: parts[1].first()
                "$c1$c2".uppercase()
            }
            parts.size == 1 -> {
                (parts[0].firstOrNull { it.isLetterOrDigit() } ?: parts[0].first()).toString().uppercase()
            }
            else -> "?"
        }
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(size.dp)
            .clip(CircleShape)
            .background(parsedColor, CircleShape)
    ) {
        Text(
            text = initials,
            color = Color.White,
            style = MaterialTheme.typography.titleMedium.copy(
                fontWeight = FontWeight.Bold,
                fontSize = (size * 0.38).sp
            )
        )
        if (!photoUri.isNullOrBlank()) {
            AsyncImage(
                model = photoUri,
                contentDescription = name,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(CircleShape)
            )
        }
    }
}

/**
 * Samsung One UI Bottom Navigation Bar
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
        tonalElevation = 6.dp,
        modifier = modifier.testTag("bottom_nav_bar")
    ) {
        val items = listOf(
            Triple(PhoneTab.KEYPAD, "Keypad", Pair(Icons.Default.Dialpad, Icons.Outlined.Dialpad)),
            Triple(PhoneTab.RECENTS, "Recents", Pair(Icons.Default.Schedule, Icons.Outlined.Schedule)),
            Triple(PhoneTab.CONTACTS, "Contacts", Pair(Icons.Default.Person, Icons.Outlined.Person)),
            Triple(PhoneTab.PLACES, "Quick", Pair(Icons.Default.Place, Icons.Outlined.Place))
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
                                    .defaultMinSize(minWidth = 16.dp, minHeight = 16.dp)
                                    .background(SamsungRed, CircleShape)
                                    .padding(horizontal = 3.dp)
                            ) {
                                Text(
                                    text = if (missedCallsCount > 99) "99+" else missedCallsCount.toString(),
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
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
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
