package com.example.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface CallHistoryDao {

    @Query("SELECT * FROM call_logs ORDER BY timestamp DESC")
    fun getAllCallHistory(): Flow<List<CallLogEntity>>

    @Query("SELECT * FROM call_logs WHERE callType = 'MISSED' ORDER BY timestamp DESC")
    fun getMissedCallHistory(): Flow<List<CallLogEntity>>

    @Query("SELECT * FROM call_logs WHERE callType = :callType ORDER BY timestamp DESC")
    fun getCallHistoryByType(callType: String): Flow<List<CallLogEntity>>

    @Query("SELECT * FROM call_logs WHERE normalizedNumber = :normalizedNumber ORDER BY timestamp DESC")
    fun getCallHistoryForNumber(normalizedNumber: String): Flow<List<CallLogEntity>>

    @Query(
        "SELECT * FROM call_logs " +
            "WHERE contactName LIKE '%' || :query || '%' " +
            "OR phoneNumber LIKE '%' || :query || '%' " +
            "OR normalizedNumber LIKE '%' || :query || '%' " +
            "ORDER BY timestamp DESC"
    )
    fun searchCallHistory(query: String): Flow<List<CallLogEntity>>

    @Query("SELECT COUNT(*) FROM call_logs")
    fun getCallHistoryCount(): Flow<Int>

    @Query("SELECT MAX(syncedAt) FROM call_logs WHERE isSystemLog = 1")
    fun getLastSystemSyncTimestamp(): Flow<Long?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCallRecord(callLog: CallLogEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCallRecords(callLogs: List<CallLogEntity>)

    @Delete
    suspend fun deleteCallRecord(callLog: CallLogEntity)

    @Query("DELETE FROM call_logs WHERE id = :id")
    suspend fun deleteCallRecordById(id: String)

    @Query("DELETE FROM call_logs WHERE systemLogId = :systemLogId")
    suspend fun deleteBySystemLogId(systemLogId: Long)

    @Query("DELETE FROM call_logs")
    suspend fun clearAllCallHistory()

    @Query("DELETE FROM call_logs WHERE isSystemLog = 1")
    suspend fun clearSyncedSystemLogs()

    @Query("DELETE FROM call_logs WHERE isSystemLog = 1 AND id NOT IN (:activeIds)")
    suspend fun pruneStaleSystemLogs(activeIds: List<String>)

    @Query(
        "DELETE FROM call_logs WHERE isSystemLog = 0 " +
            "AND normalizedNumber = :normalizedNumber " +
            "AND ABS(timestamp - :timestamp) <= :windowMs"
    )
    suspend fun deleteMatchingLocalRecords(
        normalizedNumber: String,
        timestamp: Long,
        windowMs: Long = 30_000L
    )

    @Transaction
    suspend fun syncWithSystemCallLogs(
        systemLogs: List<CallLogEntity>,
        completeSnapshot: Boolean = true
    ) {
        if (systemLogs.isEmpty()) {
            if (completeSnapshot) {
                clearSyncedSystemLogs()
            }
            return
        }

        insertCallRecords(systemLogs)

        for (log in systemLogs) {
            if (log.normalizedNumber.isNotBlank()) {
                deleteMatchingLocalRecords(
                    normalizedNumber = log.normalizedNumber,
                    timestamp = log.timestamp
                )
            }
        }

        if (completeSnapshot) {
            val activeIds = systemLogs.map { it.id }
            if (activeIds.size <= 900) {
                pruneStaleSystemLogs(activeIds)
            }
        }
    }
}
