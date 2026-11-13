package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.CallStatisticsSnapshot
import com.example.data.CallStatsEngine
import com.example.data.CallStatsRange
import com.example.data.DailyCallStat
import com.example.data.TopContactSortMode
import com.example.data.TopContactStat
import com.example.ui.PhoneViewModel
import com.example.ui.components.ContactAvatar
import com.example.ui.theme.SamsungBlue
import com.example.ui.theme.SamsungGreen
import com.example.ui.theme.SamsungGreenDark
import com.example.ui.theme.SamsungOrange
import com.example.ui.theme.SamsungRed

@Composable
fun CallStatsScreen(
    viewModel: PhoneViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val snapshot by viewModel.callStatistics.collectAsState()
    val selectedRange by viewModel.callStatsRange.collectAsState()
    val sortMode by viewModel.topContactSortMode.collectAsState()

    CallStatsContent(
        snapshot = snapshot,
        selectedRange = selectedRange,
        sortMode = sortMode,
        onRangeChange = { viewModel.setCallStatsRange(it) },
        onSortModeChange = { viewModel.setTopContactSortMode(it) },
        onCallNumber = { phone -> viewModel.startCall(phone) },
        onBack = onBack,
        modifier = modifier
    )
}

@Composable
fun CallStatsContent(
    snapshot: CallStatisticsSnapshot,
    selectedRange: CallStatsRange,
    sortMode: TopContactSortMode,
    onRangeChange: (CallStatsRange) -> Unit,
    onSortModeChange: (TopContactSortMode) -> Unit,
    onCallNumber: (String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler { onBack() }

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        containerColor = MaterialTheme.colorScheme.background,
        modifier = modifier
            .fillMaxSize()
            .testTag("call_stats_screen")
    ) { innerPadding ->
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.TopCenter
        ) {
            val useExpandedLayout = maxWidth >= 700.dp

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 960.dp)
            ) {
                // One UI Top Bar + Range Filter Chips
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier
                            .defaultMinSize(48.dp, 48.dp)
                            .testTag("call_stats_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = MaterialTheme.colorScheme.onBackground
                        )
                    }

                    Spacer(modifier = Modifier.width(6.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.call_stats_title),
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Text(
                            text = stringResource(R.string.call_stats_subtitle),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Time Range Selector Pills
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 18.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    CallStatsRange.entries.forEach { range ->
                        val isSelected = selectedRange == range
                        FilterChip(
                            selected = isSelected,
                            onClick = { onRangeChange(range) },
                            label = {
                                Text(
                                    text = when (range) {
                                        CallStatsRange.DAYS_7 -> "Last 7 Days"
                                        CallStatsRange.DAYS_30 -> "Last 30 Days"
                                        CallStatsRange.ALL_TIME -> "All Time"
                                    },
                                    fontWeight = FontWeight.SemiBold
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = SamsungGreen.copy(alpha = 0.16f),
                                selectedLabelColor = SamsungGreen
                            ),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.testTag("stats_range_${range.name.lowercase()}")
                        )
                    }
                }

                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("stats_lazy_list"),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Hero Summary KPI Banner
                    item {
                        HeroSummaryCard(snapshot = snapshot)
                    }

                    if (snapshot.totalCalls == 0) {
                        item {
                            EmptyStatsCard(range = selectedRange)
                        }
                    } else {
                        if (useExpandedLayout) {
                            item {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    DailyDurationChartCard(
                                        dailyStats = snapshot.dailyStats,
                                        modifier = Modifier.weight(1.35f)
                                    )
                                    CallBreakdownDonutCard(
                                        snapshot = snapshot,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        } else {
                            item {
                                DailyDurationChartCard(dailyStats = snapshot.dailyStats)
                            }
                            item {
                                CallBreakdownDonutCard(snapshot = snapshot)
                            }
                        }

                        // Top Contacts Leaderboard
                        item {
                            val topContacts = if (sortMode == TopContactSortMode.DURATION) {
                                snapshot.topContactsByDuration
                            } else {
                                snapshot.topContactsByCount
                            }
                            TopContactsLeaderboardCard(
                                topContacts = topContacts,
                                sortMode = sortMode,
                                onSortModeChange = onSortModeChange,
                                onCallNumber = onCallNumber
                            )
                        }
                    }

                    item {
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun HeroSummaryCard(snapshot: CallStatisticsSnapshot) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        SamsungGreen.copy(alpha = 0.18f),
                        SamsungBlue.copy(alpha = 0.12f)
                    )
                )
            )
            .padding(18.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "TOTAL TALK TIME",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        ),
                        color = SamsungGreen
                    )
                    Text(
                        text = CallStatsEngine.formatDurationReadable(snapshot.totalDurationSeconds),
                        style = MaterialTheme.typography.headlineLarge.copy(
                            fontWeight = FontWeight.ExtraBold,
                            fontFeatureSettings = "tnum"
                        ),
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.testTag("stats_total_duration")
                    )
                }

                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(48.dp)
                        .background(SamsungGreen, CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Default.TrendingUp,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                SummaryStatTile(
                    label = "Total Calls",
                    value = snapshot.totalCalls.toString(),
                    accent = SamsungBlue,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("stats_total_calls")
                )
                SummaryStatTile(
                    label = "Avg Length",
                    value = CallStatsEngine.formatDurationReadable(snapshot.averageDurationSeconds),
                    accent = SamsungGreen,
                    modifier = Modifier.weight(1f)
                )
                SummaryStatTile(
                    label = "Answered",
                    value = "${snapshot.answeredRatePercent}%",
                    accent = SamsungOrange,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("stats_answer_rate")
                )
            }
        }
    }
}

@Composable
private fun SummaryStatTile(
    label: String,
    value: String,
    accent: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
        modifier = modifier.semantics(mergeDescendants = true) {}
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    fontFeatureSettings = "tnum"
                ),
                color = accent,
                maxLines = 1
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun DailyDurationChartCard(
    dailyStats: List<DailyCallStat>,
    modifier: Modifier = Modifier
) {
    val defaultIndex = remember(dailyStats) {
        val maxIdx = dailyStats.indices.maxByOrNull { dailyStats[it].totalDurationSeconds }
        if (maxIdx != null && dailyStats[maxIdx].totalDurationSeconds > 0L) maxIdx
        else (dailyStats.size - 1).coerceAtLeast(0)
    }
    var selectedIndex by remember(dailyStats) { mutableIntStateOf(defaultIndex) }
    val clampedIndex = selectedIndex.coerceIn(0, (dailyStats.size - 1).coerceAtLeast(0))
    val selectedDay = dailyStats.getOrNull(clampedIndex)
    val maxDuration = remember(dailyStats) {
        (dailyStats.maxOfOrNull { it.totalDurationSeconds } ?: 0L).coerceAtLeast(60L)
    }

    Surface(
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 2.dp,
        modifier = modifier
            .fillMaxWidth()
            .testTag("daily_duration_chart_card")
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Schedule,
                        contentDescription = null,
                        tint = SamsungGreen,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Call Duration Per Day",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                }

                Text(
                    text = "Peak ${CallStatsEngine.formatDurationReadable(maxDuration)}",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (selectedDay != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = SamsungGreen.copy(alpha = 0.12f)
                ) {
                    Text(
                        text = "${selectedDay.fullDateLabel} • ${
                            CallStatsEngine.formatDurationReadable(selectedDay.totalDurationSeconds)
                        } • ${selectedDay.callCount} ${if (selectedDay.callCount == 1) "call" else "calls"}",
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.SemiBold,
                            fontFeatureSettings = "tnum"
                        ),
                        color = SamsungGreenDark,
                        modifier = Modifier
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                            .testTag("selected_day_detail")
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            val gridColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)
            val useHorizontalScroll = dailyStats.size > 10
            val scrollState = rememberScrollState()

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(176.dp)
            ) {
                // Horizontal reference grid drawn behind bars
                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(142.dp)
                ) {
                    val dash = PathEffect.dashPathEffect(floatArrayOf(8f, 8f), 0f)
                    listOf(0.15f, 0.55f, 0.95f).forEach { fraction ->
                        val y = size.height * fraction
                        drawLine(
                            color = gridColor,
                            start = Offset(0f, y),
                            end = Offset(size.width, y),
                            strokeWidth = 1.dp.toPx(),
                            pathEffect = dash
                        )
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .then(
                            if (useHorizontalScroll) Modifier.horizontalScroll(scrollState)
                            else Modifier
                        ),
                    horizontalArrangement = if (useHorizontalScroll) {
                        Arrangement.spacedBy(8.dp)
                    } else {
                        Arrangement.SpaceBetween
                    },
                    verticalAlignment = Alignment.Bottom
                ) {
                    dailyStats.forEachIndexed { index, day ->
                        val isSelected = index == clampedIndex
                        val targetFraction = (day.totalDurationSeconds.toFloat() / maxDuration.toFloat())
                            .coerceIn(0f, 1f)
                        val animatedFraction by animateFloatAsState(
                            targetValue = targetFraction,
                            animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                            label = "daily_bar_$index"
                        )

                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Bottom,
                            modifier = Modifier
                                .then(if (useHorizontalScroll) Modifier.width(34.dp) else Modifier.weight(1f))
                                .fillMaxHeight()
                                .defaultMinSize(minWidth = 34.dp, minHeight = 48.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .clickable { selectedIndex = index }
                                .testTag("daily_bar_$index")
                        ) {
                            Canvas(
                                modifier = Modifier
                                    .width(if (useHorizontalScroll) 18.dp else 24.dp)
                                    .weight(1f)
                                    .padding(top = 8.dp, bottom = 6.dp)
                            ) {
                                val minVisiblePx = if (day.totalDurationSeconds > 0L) 6.dp.toPx() else 3.dp.toPx()
                                val barHeight = (size.height * animatedFraction).coerceAtLeast(minVisiblePx)
                                val topY = size.height - barHeight
                                val radius = 6.dp.toPx()

                                val brush = when {
                                    day.totalDurationSeconds == 0L -> Brush.verticalGradient(
                                        listOf(gridColor.copy(alpha = 0.35f), gridColor.copy(alpha = 0.35f))
                                    )
                                    isSelected -> Brush.verticalGradient(
                                        listOf(SamsungBlue, SamsungGreen)
                                    )
                                    else -> Brush.verticalGradient(
                                        listOf(SamsungGreen, SamsungGreenDark)
                                    )
                                }

                                drawRoundRect(
                                    brush = brush,
                                    topLeft = Offset(0f, topY),
                                    size = Size(size.width, barHeight),
                                    cornerRadius = CornerRadius(radius, radius)
                                )
                            }

                            Text(
                                text = day.shortLabel,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    fontSize = 10.sp
                                ),
                                color = if (isSelected) SamsungGreen else MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CallBreakdownDonutCard(
    snapshot: CallStatisticsSnapshot,
    modifier: Modifier = Modifier
) {
    val segments = remember(snapshot) {
        listOf(
            Triple("Outgoing", snapshot.outgoingCount, SamsungGreen),
            Triple("Incoming", snapshot.incomingCount, SamsungBlue),
            Triple("Missed", snapshot.missedCount, SamsungOrange),
            Triple("Blocked", snapshot.blockedCount, SamsungRed)
        ).filter { it.second > 0 }
    }
    val total = segments.sumOf { it.second }.coerceAtLeast(1)
    val trackColor = MaterialTheme.colorScheme.surfaceVariant

    Surface(
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 2.dp,
        modifier = modifier
            .fillMaxWidth()
            .testTag("call_breakdown_donut_card")
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.BarChart,
                    contentDescription = null,
                    tint = SamsungBlue,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Call Distribution",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.size(124.dp)
                ) {
                    Canvas(modifier = Modifier.fillMaxSize().padding(10.dp)) {
                        val strokePx = 15.dp.toPx()
                        val stroke = Stroke(width = strokePx, cap = StrokeCap.Butt)
                        drawArc(
                            color = trackColor,
                            startAngle = 0f,
                            sweepAngle = 360f,
                            useCenter = false,
                            style = stroke
                        )

                        var startAngle = -90f
                        segments.forEach { (_, count, color) ->
                            val sweep = (count.toFloat() / total.toFloat()) * 360f
                            drawArc(
                                color = color,
                                startAngle = startAngle,
                                sweepAngle = (sweep - 2f).coerceAtLeast(2f),
                                useCenter = false,
                                style = stroke
                            )
                            startAngle += sweep
                        }
                    }

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = snapshot.totalCalls.toString(),
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.Bold,
                                fontFeatureSettings = "tnum"
                            ),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Calls",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.width(16.dp))

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(
                        Triple("Outgoing", snapshot.outgoingCount, SamsungGreen),
                        Triple("Incoming", snapshot.incomingCount, SamsungBlue),
                        Triple("Missed", snapshot.missedCount, SamsungOrange),
                        Triple("Blocked", snapshot.blockedCount, SamsungRed)
                    ).forEach { (label, count, color) ->
                        val pct = if (snapshot.totalCalls > 0) (count * 100) / snapshot.totalCalls else 0
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .background(color, CircleShape)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = label,
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = "$count ($pct%)",
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontFeatureSettings = "tnum"
                                ),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TopContactsLeaderboardCard(
    topContacts: List<TopContactStat>,
    sortMode: TopContactSortMode,
    onSortModeChange: (TopContactSortMode) -> Unit,
    onCallNumber: (String) -> Unit
) {
    Surface(
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 2.dp,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("top_contacts_card")
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Star,
                        contentDescription = null,
                        tint = SamsungOrange,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Top Contacts",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    TopContactSortMode.entries.forEach { mode ->
                        val selected = sortMode == mode
                        FilterChip(
                            selected = selected,
                            onClick = { onSortModeChange(mode) },
                            label = {
                                Text(
                                    text = mode.label,
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold)
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = SamsungGreen.copy(alpha = 0.16f),
                                selectedLabelColor = SamsungGreen
                            ),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.testTag("top_contacts_sort_${mode.name.lowercase()}")
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            if (topContacts.isEmpty()) {
                Text(
                    text = "No contact call metrics available in this window.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                val maxMetric = remember(topContacts, sortMode) {
                    when (sortMode) {
                        TopContactSortMode.DURATION ->
                            (topContacts.maxOfOrNull { it.totalDurationSeconds } ?: 1L).coerceAtLeast(1L).toFloat()
                        TopContactSortMode.CALL_COUNT ->
                            (topContacts.maxOfOrNull { it.callCount } ?: 1).coerceAtLeast(1).toFloat()
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    topContacts.forEachIndexed { index, contact ->
                        val value = if (sortMode == TopContactSortMode.DURATION) {
                            contact.totalDurationSeconds.toFloat()
                        } else {
                            contact.callCount.toFloat()
                        }
                        val ratio = (value / maxMetric).coerceIn(0.08f, 1f)

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
                                .padding(horizontal = 12.dp, vertical = 10.dp)
                                .testTag("top_contact_row_$index"),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "#${index + 1}",
                                style = MaterialTheme.typography.labelLarge.copy(
                                    fontWeight = FontWeight.ExtraBold,
                                    fontFeatureSettings = "tnum"
                                ),
                                color = if (index == 0) SamsungGreen else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.width(26.dp)
                            )

                            ContactAvatar(
                                name = contact.displayName,
                                photoUri = contact.photoUri,
                                avatarColorHex = contact.avatarColorHex,
                                size = 40
                            )

                            Spacer(modifier = Modifier.width(10.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = contact.displayName,
                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier
                                            .weight(1f)
                                            .testTag("top_contact_name_$index")
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = CallStatsEngine.formatDurationReadable(contact.totalDurationSeconds),
                                        style = MaterialTheme.typography.labelMedium.copy(
                                            fontWeight = FontWeight.Bold,
                                            fontFeatureSettings = "tnum"
                                        ),
                                        color = SamsungGreen
                                    )
                                }

                                Spacer(modifier = Modifier.height(4.dp))

                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(6.dp)
                                        .clip(RoundedCornerShape(3.dp))
                                        .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth(ratio)
                                            .fillMaxHeight()
                                            .clip(RoundedCornerShape(3.dp))
                                            .background(
                                                Brush.horizontalGradient(
                                                    listOf(SamsungGreen, SamsungBlue)
                                                )
                                            )
                                    )
                                }

                                Spacer(modifier = Modifier.height(4.dp))

                                Text(
                                    text = "${contact.callCount} calls (${contact.outgoingCount} out • ${contact.incomingCount} in • ${contact.missedCount} missed)",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            Spacer(modifier = Modifier.width(6.dp))

                            IconButton(
                                onClick = { onCallNumber(contact.phoneNumber) },
                                modifier = Modifier
                                    .defaultMinSize(48.dp, 48.dp)
                                    .testTag("top_contact_call_$index")
                            ) {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier
                                        .size(34.dp)
                                        .background(SamsungGreen.copy(alpha = 0.14f), CircleShape)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Call,
                                        contentDescription = "Call ${contact.displayName}",
                                        tint = SamsungGreen,
                                        modifier = Modifier.size(17.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyStatsCard(range: CallStatsRange) {
    Surface(
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 1.dp,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("stats_empty_state")
    ) {
        Column(
            modifier = Modifier.padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(56.dp)
                    .background(SamsungGreen.copy(alpha = 0.14f), CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Default.BarChart,
                    contentDescription = null,
                    tint = SamsungGreen,
                    modifier = Modifier.size(28.dp)
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "No call activity yet",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Place or receive calls in the ${range.label} window to visualize daily talk time, answer rates, and top contacts.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}
