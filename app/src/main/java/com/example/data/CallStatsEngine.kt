package com.example.data

import com.example.rust.RustPhoneBridge
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.math.roundToInt

enum class CallStatsRange(val label: String, val days: Int?) {
    DAYS_7("7D", 7),
    DAYS_30("30D", 30),
    ALL_TIME("All", null)
}

enum class TopContactSortMode(val label: String) {
    DURATION("Talk Time"),
    CALL_COUNT("Most Calls")
}

data class DailyCallStat(
    val dayStartMillis: Long,
    val shortLabel: String,
    val fullDateLabel: String,
    val totalDurationSeconds: Long,
    val incomingDurationSeconds: Long,
    val outgoingDurationSeconds: Long,
    val callCount: Int,
    val missedCount: Int
)

data class TopContactStat(
    val key: String,
    val displayName: String,
    val phoneNumber: String,
    val photoUri: String?,
    val avatarColorHex: String,
    val callCount: Int,
    val totalDurationSeconds: Long,
    val incomingCount: Int,
    val outgoingCount: Int,
    val missedCount: Int
)

data class CallStatisticsSnapshot(
    val range: CallStatsRange,
    val totalCalls: Int,
    val totalDurationSeconds: Long,
    val averageDurationSeconds: Long,
    val incomingCount: Int,
    val outgoingCount: Int,
    val missedCount: Int,
    val blockedCount: Int,
    val answeredRatePercent: Int,
    val longestCallSeconds: Long,
    val dailyStats: List<DailyCallStat>,
    val topContactsByDuration: List<TopContactStat>,
    val topContactsByCount: List<TopContactStat>
)

/**
 * Pure aggregator that transforms Room [CallLogEntity] and [ContactEntity] lists
 * into daily call-duration series, call-direction breakdowns, and top-contact rankings.
 */
object CallStatsEngine {

    private const val DAY_MS = 24L * 60L * 60L * 1000L

    fun compute(
        logs: List<CallLogEntity>,
        contacts: List<ContactEntity>,
        range: CallStatsRange,
        nowMillis: Long = System.currentTimeMillis(),
        timeZone: TimeZone = TimeZone.getDefault(),
        locale: Locale = Locale.getDefault()
    ): CallStatisticsSnapshot {
        val todayStart = startOfDayMillis(nowMillis, timeZone)
        val rangeStart = range.days?.let { days ->
            addDaysMillis(todayStart, -(days - 1), timeZone)
        }

        val filteredLogs = if (rangeStart != null) {
            logs.filter { it.timestamp >= rangeStart && it.timestamp <= nowMillis + DAY_MS }
        } else {
            logs
        }

        var totalDuration = 0L
        var connectedCalls = 0
        var incomingCount = 0
        var outgoingCount = 0
        var missedCount = 0
        var blockedCount = 0
        var longestCall = 0L

        for (log in filteredLogs) {
            val dur = log.durationSeconds.coerceAtLeast(0L)
            totalDuration += dur
            if (dur > longestCall) longestCall = dur
            if (dur > 0L) connectedCalls++

            when (log.callType) {
                "INCOMING" -> incomingCount++
                "OUTGOING" -> outgoingCount++
                "MISSED" -> missedCount++
                "BLOCKED", "REJECTED" -> blockedCount++
            }
        }

        val avgDuration = if (connectedCalls > 0) totalDuration / connectedCalls else 0L
        val nonBlockedTotal = incomingCount + outgoingCount + missedCount
        val answeredRate = if (nonBlockedTotal > 0) {
            (((incomingCount + outgoingCount).toFloat() / nonBlockedTotal.toFloat()) * 100f)
                .roundToInt()
                .coerceIn(0, 100)
        } else {
            0
        }

        val dailyStats = buildDailyStats(
            logs = filteredLogs,
            range = range,
            todayStart = todayStart,
            timeZone = timeZone,
            locale = locale
        )

        val contactsByNormalized = contacts.associateBy { contact ->
            RustPhoneBridge.normalizeForMatch(contact.phoneNumber).ifBlank { contact.phoneNumber }
        }

        val groupedContacts = mutableMapOf<String, MutableList<CallLogEntity>>()
        for (log in filteredLogs) {
            if (log.phoneNumber.isBlank() || log.phoneNumber == "Private / Unknown") continue
            val norm = log.normalizedNumber.ifBlank {
                RustPhoneBridge.normalizeForMatch(log.phoneNumber).ifBlank { log.phoneNumber }
            }
            groupedContacts.getOrPut(norm) { mutableListOf() }.add(log)
        }

        val topStats = groupedContacts.map { (key, contactLogs) ->
            val matchedContact = contactsByNormalized[key]
            val bestName = matchedContact?.name
                ?: contactLogs.firstOrNull { !it.contactName.isNullOrBlank() }?.contactName
                ?: contactLogs.first().phoneNumber
            val formattedPhone = matchedContact?.phoneNumber
                ?: RustPhoneBridge.formatPhoneNumber(contactLogs.first().phoneNumber)
            val photo = matchedContact?.photoUri
                ?: contactLogs.firstOrNull { !it.photoUri.isNullOrBlank() }?.photoUri
            val avatarColor = matchedContact?.avatarColorHex ?: "#27C064"

            var contactDuration = 0L
            var inCount = 0
            var outCount = 0
            var missCount = 0

            for (entry in contactLogs) {
                contactDuration += entry.durationSeconds.coerceAtLeast(0L)
                when (entry.callType) {
                    "INCOMING" -> inCount++
                    "OUTGOING" -> outCount++
                    "MISSED" -> missCount++
                }
            }

            TopContactStat(
                key = key,
                displayName = bestName,
                phoneNumber = formattedPhone,
                photoUri = photo,
                avatarColorHex = avatarColor,
                callCount = contactLogs.size,
                totalDurationSeconds = contactDuration,
                incomingCount = inCount,
                outgoingCount = outCount,
                missedCount = missCount
            )
        }

        val byDuration = topStats.sortedWith(
            compareByDescending<TopContactStat> { it.totalDurationSeconds }
                .thenByDescending { it.callCount }
                .thenBy { it.displayName.lowercase(locale) }
        ).take(8)

        val byCount = topStats.sortedWith(
            compareByDescending<TopContactStat> { it.callCount }
                .thenByDescending { it.totalDurationSeconds }
                .thenBy { it.displayName.lowercase(locale) }
        ).take(8)

        return CallStatisticsSnapshot(
            range = range,
            totalCalls = filteredLogs.size,
            totalDurationSeconds = totalDuration,
            averageDurationSeconds = avgDuration,
            incomingCount = incomingCount,
            outgoingCount = outgoingCount,
            missedCount = missedCount,
            blockedCount = blockedCount,
            answeredRatePercent = answeredRate,
            longestCallSeconds = longestCall,
            dailyStats = dailyStats,
            topContactsByDuration = byDuration,
            topContactsByCount = byCount
        )
    }

    private fun buildDailyStats(
        logs: List<CallLogEntity>,
        range: CallStatsRange,
        todayStart: Long,
        timeZone: TimeZone,
        locale: Locale
    ): List<DailyCallStat> {
        val logsByDay = mutableMapOf<Long, MutableList<CallLogEntity>>()
        for (log in logs) {
            val dayStart = startOfDayMillis(log.timestamp, timeZone)
            logsByDay.getOrPut(dayStart) { mutableListOf() }.add(log)
        }

        val dayCount = when (range) {
            CallStatsRange.DAYS_7 -> 7
            CallStatsRange.DAYS_30 -> 30
            CallStatsRange.ALL_TIME -> {
                val earliest = logs.minOfOrNull { startOfDayMillis(it.timestamp, timeZone) } ?: todayStart
                val diffDays = ((todayStart - earliest) / DAY_MS).toInt() + 1
                diffDays.coerceIn(7, 30)
            }
        }

        val shortFmt = SimpleDateFormat(if (dayCount <= 7) "EEE" else "M/d", locale).apply {
            this.timeZone = timeZone
        }
        val fullFmt = SimpleDateFormat("EEE, MMM d", locale).apply {
            this.timeZone = timeZone
        }

        return (0 until dayCount).map { offsetFromStart ->
            val dayOffsetFromToday = offsetFromStart - (dayCount - 1)
            val dayStart = addDaysMillis(todayStart, dayOffsetFromToday, timeZone)
            val dayLogs = logsByDay[dayStart].orEmpty()

            var totalDur = 0L
            var inDur = 0L
            var outDur = 0L
            var missed = 0

            for (entry in dayLogs) {
                val d = entry.durationSeconds.coerceAtLeast(0L)
                totalDur += d
                when (entry.callType) {
                    "INCOMING" -> inDur += d
                    "OUTGOING" -> outDur += d
                    "MISSED" -> missed++
                }
            }

            DailyCallStat(
                dayStartMillis = dayStart,
                shortLabel = shortFmt.format(Date(dayStart)),
                fullDateLabel = fullFmt.format(Date(dayStart)),
                totalDurationSeconds = totalDur,
                incomingDurationSeconds = inDur,
                outgoingDurationSeconds = outDur,
                callCount = dayLogs.size,
                missedCount = missed
            )
        }
    }

    private fun startOfDayMillis(timestamp: Long, timeZone: TimeZone): Long {
        val cal = Calendar.getInstance(timeZone)
        cal.timeInMillis = timestamp
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    private fun addDaysMillis(dayStartMillis: Long, deltaDays: Int, timeZone: TimeZone): Long {
        val cal = Calendar.getInstance(timeZone)
        cal.timeInMillis = dayStartMillis
        cal.add(Calendar.DAY_OF_YEAR, deltaDays)
        return cal.timeInMillis
    }

    fun formatDurationReadable(totalSeconds: Long): String {
        val safe = totalSeconds.coerceAtLeast(0L)
        val hours = safe / 3600
        val minutes = (safe % 3600) / 60
        val seconds = safe % 60
        return when {
            hours > 0 -> "${hours}h ${minutes}m"
            minutes > 0 -> "${minutes}m ${seconds}s"
            else -> "${seconds}s"
        }
    }
}
