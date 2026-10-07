package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CallMade
import androidx.compose.material.icons.automirrored.filled.CallMissed
import androidx.compose.material.icons.automirrored.filled.CallReceived
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.CallLogEntity
import com.example.ui.PhoneViewModel
import com.example.ui.RecentsFilter
import com.example.ui.components.ContactAvatar
import com.example.ui.components.OneUiHeader
import com.example.ui.theme.SamsungBlue
import com.example.ui.theme.SamsungGreen
import com.example.ui.theme.SamsungRed
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun RecentsScreen(
    viewModel: PhoneViewModel,
    modifier: Modifier = Modifier
) {
    val filter by viewModel.recentsFilter.collectAsState()
    val allLogs by viewModel.allCallLogs.collectAsState()
    val missedLogs by viewModel.missedCallLogs.collectAsState()
    val expandedLogId by viewModel.expandedLogId.collectAsState()

    val displayedLogs = if (filter == RecentsFilter.MISSED) missedLogs else allLogs

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // One UI Header
        OneUiHeader(
            title = "Recents",
            subtitle = "${displayedLogs.size} calls",
            onMenuSpeedDial = { viewModel.openSpeedDialDialog() },
            onMenuSettings = { viewModel.openSettingsDialog() },
            onMenuClearLogs = { viewModel.clearAllCallHistory() }
        )

        // Filter chips: "All" / "Missed"
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = filter == RecentsFilter.ALL,
                onClick = { viewModel.setRecentsFilter(RecentsFilter.ALL) },
                label = { Text("All", fontWeight = FontWeight.SemiBold) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    selectedLabelColor = SamsungGreen
                ),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.testTag("filter_chip_all")
            )

            FilterChip(
                selected = filter == RecentsFilter.MISSED,
                onClick = { viewModel.setRecentsFilter(RecentsFilter.MISSED) },
                label = { Text("Missed", fontWeight = FontWeight.SemiBold) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    selectedLabelColor = SamsungRed
                ),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.testTag("filter_chip_missed")
            )
        }

        if (displayedLogs.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.CallMissed,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                        modifier = Modifier.size(64.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = if (filter == RecentsFilter.MISSED) "No missed calls" else "No call history yet",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 8.dp)
            ) {
                items(displayedLogs, key = { it.id }) { log ->
                    CallLogCard(
                        log = log,
                        isExpanded = expandedLogId == log.id,
                        onItemClick = { viewModel.toggleExpandLog(log.id) },
                        onCallClick = { viewModel.startCall(log.phoneNumber, log.contactName) },
                        onDeleteClick = { viewModel.deleteCallLog(log.id) }
                    )
                }
            }
        }
    }
}

@Composable
private fun CallLogCard(
    log: CallLogEntity,
    isExpanded: Boolean,
    onItemClick: () -> Unit,
    onCallClick: () -> Unit,
    onDeleteClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val timeFormatted = remember(log.timestamp) {
        val sdf = SimpleDateFormat("h:mm a", Locale.getDefault())
        sdf.format(Date(log.timestamp))
    }

    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 1.dp,
        modifier = modifier
            .fillMaxWidth()
            .clickable { onItemClick() }
            .testTag("call_log_${log.id}")
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Call status icon
                CallTypeBadge(callType = log.callType)

                Spacer(modifier = Modifier.width(14.dp))

                // Caller info
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = log.contactName ?: log.phoneNumber,
                        style = MaterialTheme.typography.bodyLarge.copy(
                            fontWeight = FontWeight.Bold
                        ),
                        color = if (log.callType == "MISSED") SamsungRed else MaterialTheme.colorScheme.onSurface
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = if (log.contactName != null) log.phoneNumber else "Mobile",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = " • SIM ${log.simSlot}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                        )
                        if (log.durationSeconds > 0) {
                            Text(
                                text = " • ${formatSeconds(log.durationSeconds)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // Time
                Text(
                    text = timeFormatted,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Samsung Expanding Quick Actions Bar
            AnimatedVisibility(
                visible = isExpanded,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Column {
                    Spacer(modifier = Modifier.height(14.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Call action
                        ActionPill(
                            icon = Icons.Default.Call,
                            label = "Call",
                            bgColor = SamsungGreen,
                            contentColor = Color.White,
                            onClick = onCallClick,
                            testTag = "recents_call_action_${log.id}"
                        )

                        // Message action
                        ActionPill(
                            icon = Icons.Default.Chat,
                            label = "Message",
                            bgColor = SamsungBlue,
                            contentColor = Color.White,
                            onClick = { /* simulated SMS */ },
                            testTag = "recents_message_action_${log.id}"
                        )

                        // Delete from history
                        ActionPill(
                            icon = Icons.Default.Delete,
                            label = "Delete",
                            bgColor = MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = SamsungRed,
                            onClick = onDeleteClick,
                            testTag = "recents_delete_action_${log.id}"
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CallTypeBadge(callType: String) {
    val (icon, tint) = when (callType) {
        "OUTGOING" -> Pair(Icons.AutoMirrored.Filled.CallMade, SamsungGreen)
        "INCOMING" -> Pair(Icons.AutoMirrored.Filled.CallReceived, SamsungBlue)
        "MISSED" -> Pair(Icons.AutoMirrored.Filled.CallMissed, SamsungRed)
        else -> Pair(Icons.Default.Block, SamsungRed)
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(38.dp)
            .background(tint.copy(alpha = 0.12f), CircleShape)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = callType,
            tint = tint,
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
private fun ActionPill(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    bgColor: Color,
    contentColor: Color,
    onClick: () -> Unit,
    testTag: String
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = bgColor,
        modifier = Modifier
            .clickable { onClick() }
            .testTag(testTag)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = contentColor,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = label,
                color = contentColor,
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
            )
        }
    }
}

private fun formatSeconds(sec: Long): String {
    val m = sec / 60
    val s = sec % 60
    return if (m > 0) "${m}m ${s}s" else "${s}s"
}
