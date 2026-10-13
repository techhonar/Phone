package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CallMade
import androidx.compose.material.icons.automirrored.filled.CallMissed
import androidx.compose.material.icons.automirrored.filled.CallReceived
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.CallLogEntity
import com.example.ui.PhoneViewModel
import com.example.ui.RecentsFilter
import com.example.ui.components.GooglePhoneHeader
import com.example.ui.theme.GoogleMeetYellow
import com.example.ui.theme.GoogleSelectionBlue
import com.example.ui.theme.SamsungBlue
import com.example.ui.theme.SamsungGreen
import com.example.ui.theme.SamsungRed
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Recents, restyled after the reference video:
 * "Phone" header with filter / search / overflow, call logs grouped by
 * day into one large rounded card per day, a "Filter calls" bottom
 * sheet, an expanded-log action row, and long-press multi-select with
 * a floating Delete pill.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecentsScreen(
    viewModel: PhoneViewModel,
    modifier: Modifier = Modifier
) {
    val filter by viewModel.recentsFilter.collectAsState()
    val allLogs by viewModel.allCallLogs.collectAsState()
    val expandedLogId by viewModel.expandedLogId.collectAsState()

    var showFilterSheet by remember { mutableStateOf(false) }
    var searchVisible by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var selectionMode by remember { mutableStateOf(false) }
    var selectedIds by remember { mutableStateOf<Set<String>>(emptySet()) }

    val filteredByType = remember(allLogs, filter) {
        when (filter) {
            RecentsFilter.ALL -> allLogs
            RecentsFilter.MISSED -> allLogs.filter { it.callType == "MISSED" }
            RecentsFilter.REJECTED -> allLogs.filter { it.callType == "REJECTED" }
            RecentsFilter.OUTGOING -> allLogs.filter { it.callType == "OUTGOING" }
            RecentsFilter.INCOMING -> allLogs.filter { it.callType == "INCOMING" }
            RecentsFilter.VOICEMAIL -> allLogs.filter { it.callType == "VOICEMAIL" }
        }
    }

    val displayedLogs = remember(filteredByType, searchQuery) {
        if (searchQuery.isBlank()) {
            filteredByType
        } else {
            val q = searchQuery.trim().lowercase()
            filteredByType.filter {
                (it.contactName?.lowercase()?.contains(q) == true) ||
                    it.phoneNumber.contains(q)
            }
        }
    }

    val groupedLogs = remember(displayedLogs) {
        displayedLogs.groupBy { dayLabel(it.timestamp) }
    }

    fun toggleSelection(id: String) {
        selectedIds = if (selectedIds.contains(id)) selectedIds - id else selectedIds + id
        if (selectedIds.isEmpty()) selectionMode = false
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            if (selectionMode) {
                // Selection header from the video: "N selected"
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 12.dp)
                ) {
                    Text(
                        text = "${selectedIds.size} selected",
                        style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clickable {
                                    selectedIds = if (selectedIds.size == displayedLogs.size) {
                                        emptySet()
                                    } else {
                                        displayedLogs.map { it.id }.toSet()
                                    }
                                    if (selectedIds.isEmpty()) selectionMode = false
                                }
                                .testTag("recents_select_all")
                        ) {
                            SelectionCircle(selected = selectedIds.size == displayedLogs.size && displayedLogs.isNotEmpty())
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "All",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                        }
                        Spacer(modifier = Modifier.weight(1f))
                        TextButton(
                            onClick = {
                                selectionMode = false
                                selectedIds = emptySet()
                            },
                            modifier = Modifier.testTag("recents_selection_cancel")
                        ) {
                            Text("Cancel", color = MaterialTheme.colorScheme.onBackground)
                        }
                        IconButton(onClick = { searchVisible = !searchVisible }) {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = "Search",
                                tint = MaterialTheme.colorScheme.onBackground
                            )
                        }
                    }
                }
            } else {
                GooglePhoneHeader(
                    onMenuSpeedDial = { viewModel.openSpeedDialDialog() },
                    onMenuSettings = { viewModel.openSettingsDialog() },
                    onMenuClearLogs = { viewModel.clearAllCallHistory() },
                    onFilter = { showFilterSheet = true },
                    onSearch = { searchVisible = !searchVisible }
                )
            }

            if (searchVisible) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 6.dp),
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
                            contentDescription = "Search",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = { Text("Search name or number") },
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
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(
                                    imageVector = Icons.Default.Clear,
                                    contentDescription = "Clear search",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
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
                            text = if (filter == RecentsFilter.ALL) "No call history yet" else "No calls match this filter",
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
                    contentPadding = PaddingValues(vertical = 8.dp)
                ) {
                    groupedLogs.forEach { (day, logsForDay) ->
                        item(key = "day_header_$day") {
                            Text(
                                text = day,
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Normal),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(start = 8.dp, top = 14.dp, bottom = 6.dp)
                            )
                        }

                        item(key = "day_card_$day") {
                            Surface(
                                shape = RoundedCornerShape(28.dp),
                                color = MaterialTheme.colorScheme.surface,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column {
                                    logsForDay.forEachIndexed { index, log ->
                                        if (index > 0) {
                                            HorizontalDivider(
                                                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f),
                                                modifier = Modifier.padding(start = 52.dp, end = 16.dp)
                                            )
                                        }
                                        CallLogRow(
                                            log = log,
                                            isExpanded = expandedLogId == log.id && !selectionMode,
                                            selectionMode = selectionMode,
                                            isSelected = selectedIds.contains(log.id),
                                            onTap = {
                                                if (selectionMode) {
                                                    toggleSelection(log.id)
                                                } else {
                                                    viewModel.toggleExpandLog(log.id)
                                                }
                                            },
                                            onLongPress = {
                                                if (!selectionMode) {
                                                    selectionMode = true
                                                    selectedIds = setOf(log.id)
                                                }
                                            },
                                            onCallClick = { viewModel.startCall(log.phoneNumber, log.contactName) },
                                            onDeleteClick = { viewModel.deleteCallLog(log.id) },
                                            onAddToContacts = { viewModel.openCreateContact(log.phoneNumber) },
                                            onBlock = { viewModel.blockNumber(log.phoneNumber, log.contactName ?: log.phoneNumber) }
                                        )
                                    }
                                }
                            }
                        }
                    }

                    item { Spacer(modifier = Modifier.height(72.dp)) }
                }
            }
        }

        // Floating Delete pill in selection mode (reference video)
        if (selectionMode && selectedIds.isNotEmpty()) {
            Surface(
                shape = RoundedCornerShape(28.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                shadowElevation = 6.dp,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 18.dp)
                    .clickable {
                        viewModel.deleteCallLogs(selectedIds)
                        selectionMode = false
                        selectedIds = emptySet()
                    }
                    .testTag("recents_delete_selected_button")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 22.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete selected",
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Delete",
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }

    if (showFilterSheet) {
        FilterCallsSheet(
            currentFilter = filter,
            onDismiss = { showFilterSheet = false },
            onApply = { chosen ->
                viewModel.setRecentsFilter(chosen)
                showFilterSheet = false
            }
        )
    }
}

@Composable
private fun SelectionCircle(selected: Boolean, modifier: Modifier = Modifier) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(24.dp)
            .background(if (selected) GoogleSelectionBlue else Color.Transparent, CircleShape)
            .then(
                if (selected) Modifier else Modifier.border(1.5.dp, Color.Gray, CircleShape)
            )
    ) {
        if (selected) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = "Selected",
                tint = Color.White,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

@Composable
private fun CallLogRow(
    log: CallLogEntity,
    isExpanded: Boolean,
    selectionMode: Boolean,
    isSelected: Boolean,
    onTap: () -> Unit,
    onLongPress: () -> Unit,
    onCallClick: () -> Unit,
    onDeleteClick: () -> Unit,
    onAddToContacts: () -> Unit,
    onBlock: () -> Unit,
    modifier: Modifier = Modifier
) {
    val timeFormatted = remember(log.timestamp) {
        val sdf = SimpleDateFormat("h:mm a", Locale.getDefault())
        sdf.format(Date(log.timestamp))
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .pointerInput(log.id, selectionMode) {
                detectTapGestures(
                    onTap = { onTap() },
                    onLongPress = { onLongPress() }
                )
            }
            .testTag("call_log_${log.id}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (selectionMode) {
                SelectionCircle(selected = isSelected)
            } else {
                CallTypeIcon(callType = log.callType)
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = log.contactName ?: log.phoneNumber,
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 15.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = subtitleFor(log),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Text(
                text = timeFormatted,
                style = MaterialTheme.typography.labelMedium,
                color = if (log.callType == "MISSED") SamsungRed else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // Expanded actions, as in the video: a row of round action
        // buttons, then Add to Contacts / Block split by a divider.
        AnimatedVisibility(
            visible = isExpanded,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            Column {
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f),
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ExpandedActionCircle(
                        icon = Icons.Default.Call,
                        label = "Call",
                        bgColor = MaterialTheme.colorScheme.surfaceVariant,
                        iconTint = SamsungGreen,
                        onClick = onCallClick,
                        testTag = "recents_call_action_${log.id}"
                    )
                    ExpandedActionCircle(
                        icon = Icons.Default.Chat,
                        label = "Message",
                        bgColor = MaterialTheme.colorScheme.surfaceVariant,
                        iconTint = SamsungBlue,
                        onClick = { /* simulated SMS */ },
                        testTag = "recents_message_action_${log.id}"
                    )
                    ExpandedActionCircle(
                        icon = Icons.Default.Videocam,
                        label = "Video",
                        bgColor = Color.White,
                        iconTint = GoogleMeetYellow,
                        onClick = onCallClick,
                        testTag = "recents_video_action_${log.id}"
                    )
                    ExpandedActionCircle(
                        icon = Icons.Default.Delete,
                        label = "Delete",
                        bgColor = MaterialTheme.colorScheme.surfaceVariant,
                        iconTint = SamsungRed,
                        onClick = onDeleteClick,
                        testTag = "recents_delete_action_${log.id}"
                    )
                }
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f),
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Add to Contacts",
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier
                            .weight(1f)
                            .clickable { onAddToContacts() }
                            .padding(vertical = 13.dp)
                            .testTag("recents_add_contact_${log.id}"),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                    Box(
                        modifier = Modifier
                            .width(1.dp)
                            .height(24.dp)
                            .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))
                    )
                    Text(
                        text = "Block",
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier
                            .weight(1f)
                            .clickable { onBlock() }
                            .padding(vertical = 13.dp)
                            .testTag("recents_block_${log.id}"),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }
        }
    }
}

@Composable
private fun CallTypeIcon(callType: String) {
    val (icon, tint) = when (callType) {
        "OUTGOING" -> Pair(Icons.AutoMirrored.Filled.CallMade, SamsungGreen)
        "INCOMING" -> Pair(Icons.AutoMirrored.Filled.CallReceived, SamsungBlue)
        "MISSED" -> Pair(Icons.AutoMirrored.Filled.CallMissed, SamsungRed)
        else -> Pair(Icons.Default.Block, SamsungRed)
    }

    Icon(
        imageVector = icon,
        contentDescription = callType,
        tint = tint,
        modifier = Modifier.size(18.dp)
    )
}

@Composable
private fun ExpandedActionCircle(
    icon: ImageVector,
    label: String,
    bgColor: Color,
    iconTint: Color,
    onClick: () -> Unit,
    testTag: String
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(52.dp)
            .background(bgColor, CircleShape)
            .clip(CircleShape)
            .clickable { onClick() }
            .testTag(testTag)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = iconTint,
            modifier = Modifier.size(24.dp)
        )
    }
}

/** "Filter calls" bottom sheet from the reference video. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FilterCallsSheet(
    currentFilter: RecentsFilter,
    onDismiss: () -> Unit,
    onApply: (RecentsFilter) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var pending by remember(currentFilter) { mutableStateOf(currentFilter) }

    val options = listOf(
        RecentsFilter.ALL to "All calls",
        RecentsFilter.MISSED to "Missed calls",
        RecentsFilter.REJECTED to "Rejected calls",
        RecentsFilter.OUTGOING to "Outgoing calls",
        RecentsFilter.INCOMING to "Incoming calls",
        RecentsFilter.VOICEMAIL to "Direct voicemail"
    )

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 28.dp)
        ) {
            Text(
                text = "Filter calls",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(12.dp))

            options.forEach { (option, label) ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { pending = option }
                        .padding(vertical = 6.dp)
                        .testTag("filter_option_${option.name.lowercase()}")
                ) {
                    RadioButton(
                        selected = pending == option,
                        onClick = { pending = option },
                        colors = RadioButtonDefaults.colors(selectedColor = GoogleSelectionBlue)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = label,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.testTag("filter_cancel_button")
                ) {
                    Text("Cancel", color = MaterialTheme.colorScheme.onSurface)
                }
                TextButton(
                    onClick = { onApply(pending) },
                    modifier = Modifier.testTag("filter_ok_button")
                ) {
                    Text("OK", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

private fun dayLabel(timestamp: Long): String {
    val sdf = SimpleDateFormat("EEEE, MMMM d", Locale.getDefault())
    return sdf.format(Date(timestamp))
}

private fun subtitleFor(log: CallLogEntity): String {
    val base = when (log.callType) {
        "OUTGOING" -> "Outgoing call"
        "INCOMING" -> "Incoming call"
        "MISSED" -> "Missed call"
        "REJECTED" -> "Rejected call"
        "CANCELLED" -> "Cancelled call"
        "VOICEMAIL" -> "Direct voicemail"
        else -> log.callType.lowercase().replaceFirstChar { it.uppercase() }
    }
    return if (log.durationSeconds > 0) "$base • ${formatSeconds(log.durationSeconds)}" else base
}

private fun formatSeconds(sec: Long): String {
    val m = sec / 60
    val s = sec % 60
    return if (m > 0) "${m}m ${s}s" else "${s}s"
}
