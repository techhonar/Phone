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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CallMade
import androidx.compose.material.icons.automirrored.filled.CallMissed
import androidx.compose.material.icons.automirrored.filled.CallReceived
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Voicemail
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.CallLogEntity
import com.example.rust.RustPhoneBridge
import com.example.ui.PhoneViewModel
import com.example.ui.RecentsFilter
import com.example.ui.components.ContactAvatar
import com.example.ui.components.OneUiHeader
import com.example.ui.components.PermissionEmptyStateCard
import com.example.ui.theme.SamsungBlue
import com.example.ui.theme.SamsungGreen
import com.example.ui.theme.SamsungOrange
import com.example.ui.theme.SamsungRed
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@Composable
fun RecentsScreen(
    viewModel: PhoneViewModel,
    onRequestPermissions: () -> Unit,
    modifier: Modifier = Modifier
) {
    val filter by viewModel.recentsFilter.collectAsState()
    val searchQuery by viewModel.recentsSearchQuery.collectAsState()
    val filteredCallLogs by viewModel.filteredCallLogs.collectAsState()
    val allCallLogs by viewModel.allCallLogs.collectAsState()
    val expandedLogId by viewModel.expandedLogId.collectAsState()
    val blockedNumbers by viewModel.blockedNumbers.collectAsState()
    val permissionStatus by viewModel.permissionStatus.collectAsState()
    val isSyncing by viewModel.isSyncingCallLogs.collectAsState()
    val lastSyncTime by viewModel.lastCallSyncTimestamp.collectAsState()
    val displayedLogs = filteredCallLogs

    val blockedSet = remember(blockedNumbers) {
        blockedNumbers.map { RustPhoneBridge.normalizeForMatch(it.phoneNumber) }.toSet()
    }

    val groupedLogs = remember(displayedLogs) {
        groupCallLogsByDate(displayedLogs)
    }

    val subtitleText = remember(permissionStatus.hasReadCallLog, displayedLogs.size, isSyncing, lastSyncTime) {
        when {
            isSyncing -> "Syncing with system CallLog..."
            permissionStatus.hasReadCallLog -> "${displayedLogs.size} calls • Synced with CallLog"
            allCallLogs.isNotEmpty() -> "${displayedLogs.size} saved in Room"
            else -> "Call Log access needed"
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        OneUiHeader(
            title = "Recents",
            subtitle = subtitleText,
            onMenuSpeedDial = { viewModel.openSpeedDialDialog() },
            onMenuSettings = { viewModel.openSettingsDialog() },
            onMenuStats = { viewModel.openCallStatsScreen() },
            showStatsHeaderIcon = true,
            onMenuClearLogs = if (permissionStatus.hasWriteCallLog || allCallLogs.isNotEmpty()) {
                { viewModel.clearAllCallHistory() }
            } else null
        )

        if (!permissionStatus.hasReadCallLog && allCallLogs.isEmpty()) {
            PermissionEmptyStateCard(
                icon = Icons.Default.History,
                title = "Access Device Call History",
                description = "Allow Call Log permission to view and persist incoming, outgoing, and missed calls synced with your phone.",
                buttonText = "Allow Call Log Access",
                onGrantClick = onRequestPermissions
            )
            return
        }

        if (!permissionStatus.hasReadCallLog && allCallLogs.isNotEmpty()) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = SamsungBlue.copy(alpha = 0.12f),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 4.dp)
                    .clickable { onRequestPermissions() }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Sync,
                        contentDescription = null,
                        tint = SamsungBlue,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Showing saved Room history. Tap to enable Call Log sync.",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = SamsungBlue,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        // Search Bar + Sync Trigger
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 4.dp),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surfaceVariant
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = "Search call logs",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.width(6.dp))
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { viewModel.setRecentsSearchQuery(it) },
                    placeholder = { Text("Search call history") },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color.Transparent,
                        unfocusedBorderColor = Color.Transparent
                    ),
                    singleLine = true,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("recents_search_input")
                )
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { viewModel.setRecentsSearchQuery("") }) {
                        Icon(
                            imageVector = Icons.Default.Clear,
                            contentDescription = "Clear search",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                if (permissionStatus.hasReadCallLog) {
                    IconButton(
                        onClick = { viewModel.syncCallHistoryNow(showFeedback = true) },
                        modifier = Modifier.testTag("recents_sync_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Sync,
                            contentDescription = "Sync with device CallLog",
                            tint = SamsungGreen
                        )
                    }
                }
            }
        }

        // Horizontal Filter Chips
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(RecentsFilter.entries) { f ->
                val isSelected = filter == f
                FilterChip(
                    selected = isSelected,
                    onClick = { viewModel.setRecentsFilter(f) },
                    label = { Text(f.label, fontWeight = FontWeight.SemiBold) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        selectedLabelColor = if (f == RecentsFilter.MISSED || f == RecentsFilter.BLOCKED) SamsungRed else SamsungGreen
                    ),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.testTag("filter_chip_${f.name.lowercase()}")
                )
            }
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
                        text = if (searchQuery.isNotBlank()) "No matching calls" else "No ${filter.label.lowercase()} calls",
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
                contentPadding = PaddingValues(vertical = 8.dp)
            ) {
                groupedLogs.forEach { (sectionLabel, logsInSection) ->
                    item(key = "section_$sectionLabel") {
                        Text(
                            text = sectionLabel.uppercase(),
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp
                            ),
                            color = SamsungGreen,
                            modifier = Modifier.padding(start = 8.dp, top = 8.dp, bottom = 2.dp)
                        )
                    }

                    items(logsInSection, key = { it.id }) { log ->
                        val isBlocked = RustPhoneBridge.normalizeForMatch(log.phoneNumber).let {
                            it.isNotEmpty() && it in blockedSet
                        }
                        CallLogCard(
                            log = log,
                            isExpanded = expandedLogId == log.id,
                            isBlocked = isBlocked,
                            onItemClick = { viewModel.toggleExpandLog(log.id) },
                            onCallClick = { viewModel.startCall(log.phoneNumber) },
                            onMessageClick = { viewModel.sendMessage(log.phoneNumber) },
                            onAddContactClick = { viewModel.openCreateContact(log.phoneNumber) },
                            onToggleBlockClick = {
                                if (isBlocked) viewModel.unblockNumber(log.phoneNumber)
                                else viewModel.blockNumber(log.phoneNumber, log.contactName ?: "Blocked Caller")
                            },
                            onDeleteClick = { viewModel.deleteCallLog(log) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CallLogCard(
    log: CallLogEntity,
    isExpanded: Boolean,
    isBlocked: Boolean,
    onItemClick: () -> Unit,
    onCallClick: () -> Unit,
    onMessageClick: () -> Unit,
    onAddContactClick: () -> Unit,
    onToggleBlockClick: () -> Unit,
    onDeleteClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val timeFormatted = remember(log.timestamp) {
        val sdf = SimpleDateFormat("MMM d • h:mm a", Locale.getDefault())
        sdf.format(Date(log.timestamp))
    }

    Surface(
        shape = RoundedCornerShape(20.dp),
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
                Box(contentAlignment = Alignment.BottomEnd) {
                    ContactAvatar(
                        name = log.contactName ?: log.phoneNumber,
                        photoUri = log.photoUri,
                        size = 44
                    )
                    CallTypeMiniBadge(callType = log.callType)
                }

                Spacer(modifier = Modifier.width(14.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = log.contactName ?: log.phoneNumber,
                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                        color = if (log.callType == "MISSED" || log.callType == "BLOCKED") SamsungRed else MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = if (log.contactName != null) log.phoneNumber else log.callType.lowercase().replaceFirstChar { it.uppercase() },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (log.durationSeconds > 0) {
                            Text(
                                text = " • ${formatSeconds(log.durationSeconds)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Text(
                        text = buildString {
                            append(timeFormatted)
                            if (!log.geocodedLocation.isNullOrBlank()) {
                                append(" • ${log.geocodedLocation}")
                            }
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                IconButton(
                    onClick = onCallClick,
                    modifier = Modifier
                        .defaultMinSize(48.dp, 48.dp)
                        .background(SamsungGreen.copy(alpha = 0.14f), CircleShape)
                        .testTag("recents_quick_call_${log.id}")
                ) {
                    Icon(
                        imageVector = Icons.Default.Call,
                        contentDescription = "Call ${log.contactName ?: log.phoneNumber}",
                        tint = SamsungGreen,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            AnimatedVisibility(
                visible = isExpanded,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Column {
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        ActionIconPill(
                            icon = Icons.AutoMirrored.Filled.Chat,
                            label = "Message",
                            tint = SamsungBlue,
                            onClick = onMessageClick,
                            testTag = "recents_message_action_${log.id}"
                        )
                        if (log.contactName == null && log.phoneNumber != "Private / Unknown") {
                            ActionIconPill(
                                icon = Icons.Default.PersonAdd,
                                label = "Add",
                                tint = SamsungGreen,
                                onClick = onAddContactClick,
                                testTag = "recents_add_contact_${log.id}"
                            )
                        }
                        ActionIconPill(
                            icon = Icons.Default.Block,
                            label = if (isBlocked) "Unblock" else "Block",
                            tint = SamsungOrange,
                            onClick = onToggleBlockClick,
                            testTag = "recents_block_action_${log.id}"
                        )
                        ActionIconPill(
                            icon = Icons.Default.Delete,
                            label = "Delete",
                            tint = SamsungRed,
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
private fun CallTypeMiniBadge(callType: String) {
    val (icon, tint) = when (callType) {
        "OUTGOING" -> Icons.AutoMirrored.Filled.CallMade to SamsungGreen
        "INCOMING" -> Icons.AutoMirrored.Filled.CallReceived to SamsungBlue
        "MISSED" -> Icons.AutoMirrored.Filled.CallMissed to SamsungRed
        "VOICEMAIL" -> Icons.Default.Voicemail to SamsungOrange
        else -> Icons.Default.Block to SamsungRed
    }

    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 2.dp,
        modifier = Modifier.size(20.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = callType,
                tint = tint,
                modifier = Modifier.size(12.dp)
            )
        }
    }
}

@Composable
private fun ActionIconPill(
    icon: ImageVector,
    label: String,
    tint: Color,
    onClick: () -> Unit,
    testTag: String
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .defaultMinSize(minWidth = 56.dp, minHeight = 48.dp)
            .clickable { onClick() }
            .padding(horizontal = 6.dp, vertical = 4.dp)
            .testTag(testTag)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(38.dp)
                .background(tint.copy(alpha = 0.14f), CircleShape)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = tint,
                modifier = Modifier.size(19.dp)
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

private fun groupCallLogsByDate(logs: List<CallLogEntity>): List<Pair<String, List<CallLogEntity>>> {
    if (logs.isEmpty()) return emptyList()
    val cal = Calendar.getInstance()
    cal.set(Calendar.HOUR_OF_DAY, 0)
    cal.set(Calendar.MINUTE, 0)
    cal.set(Calendar.SECOND, 0)
    cal.set(Calendar.MILLISECOND, 0)
    val startOfToday = cal.timeInMillis
    val startOfYesterday = startOfToday - 24L * 3600L * 1000L

    val today = mutableListOf<CallLogEntity>()
    val yesterday = mutableListOf<CallLogEntity>()
    val earlier = mutableListOf<CallLogEntity>()

    for (log in logs) {
        when {
            log.timestamp >= startOfToday -> today.add(log)
            log.timestamp >= startOfYesterday -> yesterday.add(log)
            else -> earlier.add(log)
        }
    }

    return buildList {
        if (today.isNotEmpty()) add("Today" to today)
        if (yesterday.isNotEmpty()) add("Yesterday" to yesterday)
        if (earlier.isNotEmpty()) add("Earlier" to earlier)
    }
}

private fun formatSeconds(sec: Long): String {
    val h = sec / 3600
    val m = (sec % 3600) / 60
    val s = sec % 60
    return when {
        h > 0 -> "${h}h ${m}m"
        m > 0 -> "${m}m ${s}s"
        else -> "${s}s"
    }
}
