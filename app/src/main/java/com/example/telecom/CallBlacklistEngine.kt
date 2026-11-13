package com.example.telecom

import com.example.data.AppDatabase
import com.example.data.BlockedNumberEntity
import com.example.data.CallLogEntity
import com.example.rust.RustPhoneBridge
import kotlinx.coroutines.flow.first

data class BlacklistEvaluation(
    val isBlocked: Boolean,
    val matchedRule: BlockedNumberEntity? = null,
    val normalizedNumber: String = ""
)

/**
 * Evaluates incoming caller IDs against the local Room database blacklist,
 * increments per-rule block telemetry, and records rejected calls in Room CallHistoryDao.
 */
class CallBlacklistEngine(private val database: AppDatabase) {

    suspend fun evaluateIncomingCaller(
        callerNumber: String?,
        callerName: String? = null,
        recordBlockedCall: Boolean = true
    ): BlacklistEvaluation {
        val raw = callerNumber?.trim().orEmpty()
        if (raw.isEmpty()) {
            return BlacklistEvaluation(isBlocked = false)
        }

        val normalized = RustPhoneBridge.normalizeForMatch(raw).ifBlank { raw }
        val dao = database.phoneDao()

        val matched = dao.findBlockedNumber(normalizedPhone = normalized, rawPhone = raw)
            ?: if (normalized.length >= 7) {
                dao.getAllBlockedNumbers().first().firstOrNull { entry ->
                    entry.normalizedNumber.length >= 7 &&
                        (entry.normalizedNumber.endsWith(normalized) || normalized.endsWith(entry.normalizedNumber))
                }
            } else {
                null
            }

        if (matched == null) {
            return BlacklistEvaluation(isBlocked = false, normalizedNumber = normalized)
        }

        if (recordBlockedCall) {
            val now = System.currentTimeMillis()
            dao.incrementBlockedCount(matched.normalizedNumber, now)
            database.callHistoryDao().insertCallRecord(
                CallLogEntity(
                    phoneNumber = RustPhoneBridge.formatPhoneNumber(raw),
                    normalizedNumber = normalized,
                    contactName = callerName?.takeIf { it.isNotBlank() }
                        ?: matched.label.takeIf { it.isNotBlank() && it != "Blocked Number" },
                    callType = "BLOCKED",
                    timestamp = now,
                    durationSeconds = 0L,
                    isSystemLog = false
                )
            )
        }

        return BlacklistEvaluation(
            isBlocked = true,
            matchedRule = matched,
            normalizedNumber = normalized
        )
    }
}
