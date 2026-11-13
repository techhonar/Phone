package com.example.data

import com.example.rust.RustPhoneBridge
import com.example.rust.T9Match
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class PhoneRepository(
    private val dao: PhoneDao,
    private val callHistoryDao: CallHistoryDao,
    private val systemSource: SystemPhoneDataSource
) {
    private val refreshTick = MutableStateFlow(0)

    fun triggerRefresh() {
        refreshTick.value += 1
    }

    val allContacts: Flow<List<ContactEntity>> = combine(
        systemSource.observeSystemContacts(refreshTick),
        dao.getAllContacts()
    ) { systemContacts, localContacts ->
        if (systemSource.hasReadContactsPermission()) {
            val systemNumbers = systemContacts.map { RustPhoneBridge.normalizeForMatch(it.phoneNumber) }.toSet()
            val extraLocals = localContacts.filter {
                RustPhoneBridge.normalizeForMatch(it.phoneNumber) !in systemNumbers
            }
            (systemContacts + extraLocals).sortedBy { it.name.lowercase() }
        } else {
            localContacts
        }
    }

    val favoriteContacts: Flow<List<ContactEntity>> = allContacts.map { list ->
        list.filter { it.isFavorite }
    }

    // Room is the single source of truth for displaying call history
    val allCallLogs: Flow<List<CallLogEntity>> = callHistoryDao.getAllCallHistory()
    val missedCallLogs: Flow<List<CallLogEntity>> = callHistoryDao.getMissedCallHistory()
    val lastCallSyncTimestamp: Flow<Long?> = callHistoryDao.getLastSystemSyncTimestamp()

    fun getCallHistoryForNumber(phoneNumber: String): Flow<List<CallLogEntity>> {
        val normalized = RustPhoneBridge.normalizeForMatch(phoneNumber)
        return callHistoryDao.getCallHistoryForNumber(normalized)
    }

    /**
     * Continuously observes Android's CallLog provider and synchronizes changes into Room.
     */
    fun observeAndSyncSystemCallLogs(): Flow<Int> {
        return systemSource.observeSystemCallLogs(refreshTick).map { systemLogs ->
            if (systemSource.hasReadCallLogPermission()) {
                callHistoryDao.syncWithSystemCallLogs(
                    systemLogs = systemLogs,
                    completeSnapshot = systemLogs.size < SystemPhoneDataSource.DEFAULT_CALL_LOG_LIMIT
                )
                systemLogs.size
            } else {
                0
            }
        }
    }

    /**
     * Imperatively synchronizes the Room call_logs table with the system CallLog ContentProvider.
     */
    suspend fun syncCallHistoryNow(): Int = withContext(Dispatchers.IO) {
        if (!systemSource.hasReadCallLogPermission()) return@withContext 0
        val systemLogs = systemSource.querySystemCallLogs(SystemPhoneDataSource.DEFAULT_CALL_LOG_LIMIT)
        callHistoryDao.syncWithSystemCallLogs(
            systemLogs = systemLogs,
            completeSnapshot = systemLogs.size < SystemPhoneDataSource.DEFAULT_CALL_LOG_LIMIT
        )
        systemLogs.size
    }

    suspend fun recordCallInHistory(
        phoneNumber: String,
        contactName: String?,
        callType: String,
        timestamp: Long = System.currentTimeMillis(),
        durationSeconds: Long = 0,
        simSlot: Int = 1,
        photoUri: String? = null,
        writeToSystemIfPermitted: Boolean = false
    ) = withContext(Dispatchers.IO) {
        val formatted = if (phoneNumber.isBlank() || phoneNumber == "Private / Unknown") {
            "Private / Unknown"
        } else {
            RustPhoneBridge.formatPhoneNumber(phoneNumber)
        }
        val normalized = if (formatted == "Private / Unknown") {
            ""
        } else {
            RustPhoneBridge.normalizeForMatch(phoneNumber)
        }

        val systemId = if (writeToSystemIfPermitted && formatted != "Private / Unknown") {
            systemSource.insertSystemCallLog(
                phoneNumber = phoneNumber,
                callType = callType,
                timestamp = timestamp,
                durationSeconds = durationSeconds
            )
        } else null

        val entity = CallLogEntity(
            id = systemId?.let { "sys_$it" } ?: java.util.UUID.randomUUID().toString(),
            systemLogId = systemId,
            phoneNumber = formatted,
            normalizedNumber = normalized,
            contactName = contactName,
            callType = callType,
            timestamp = timestamp,
            durationSeconds = durationSeconds,
            simSlot = simSlot,
            photoUri = photoUri,
            isSystemLog = systemId != null,
            syncedAt = System.currentTimeMillis()
        )
        callHistoryDao.insertCallRecord(entity)
    }

    val allSpeedDials: Flow<List<SpeedDialEntity>> = dao.getAllSpeedDials()
    val allBlockedNumbers: Flow<List<BlockedNumberEntity>> = dao.getAllBlockedNumbers()

    suspend fun performT9Search(digits: String): List<T9Match> {
        if (digits.isBlank()) return emptyList()
        val contacts = allContacts.first()
        val tuples = contacts.map { Triple(it.id, it.name, it.phoneNumber) }
        val photoMap = contacts.associate { it.id to it.photoUri }
        return RustPhoneBridge.searchT9(digits, tuples, photoMap)
    }

    suspend fun saveContact(
        name: String,
        phoneNumber: String,
        email: String,
        phoneLabel: String,
        notes: String,
        avatarColorHex: String
    ) {
        if (systemSource.hasWriteContactsPermission()) {
            val savedToSystem = systemSource.insertSystemContact(
                name = name,
                phoneNumber = phoneNumber,
                email = email,
                phoneLabel = phoneLabel,
                notes = notes
            )
            if (savedToSystem) {
                triggerRefresh()
                return
            }
        }
        dao.insertContact(
            ContactEntity(
                name = name,
                phoneNumber = phoneNumber,
                email = email,
                groupName = phoneLabel,
                phoneLabel = phoneLabel,
                notes = notes,
                avatarColorHex = avatarColorHex,
                isSystemContact = false
            )
        )
    }

    suspend fun toggleFavorite(contact: ContactEntity) {
        val nextFav = !contact.isFavorite
        if (contact.isSystemContact && systemSource.hasWriteContactsPermission()) {
            if (systemSource.toggleSystemFavorite(contact.id, nextFav)) {
                triggerRefresh()
                return
            }
        }
        dao.toggleFavorite(contact.id, nextFav)
    }

    suspend fun deleteContact(contact: ContactEntity) {
        if (contact.isSystemContact && systemSource.hasWriteContactsPermission()) {
            if (systemSource.deleteSystemContact(contact.id)) {
                triggerRefresh()
                return
            }
        }
        dao.deleteContactById(contact.id)
    }

    suspend fun deleteCallLog(log: CallLogEntity) {
        if (log.isSystemLog && systemSource.hasWriteCallLogPermission()) {
            val targetId = log.systemLogId?.toString() ?: log.id.removePrefix("sys_")
            systemSource.deleteSystemCallLog(targetId)
        }
        callHistoryDao.deleteCallRecordById(log.id)
        triggerRefresh()
    }

    suspend fun clearAllCallLogs() {
        if (systemSource.hasWriteCallLogPermission()) {
            systemSource.clearAllSystemCallLogs()
        }
        callHistoryDao.clearAllCallHistory()
        triggerRefresh()
    }

    suspend fun setSpeedDial(digit: Int, contact: ContactEntity) {
        if (digit !in 2..9) return
        val entry = SpeedDialEntity(
            keyDigit = digit,
            contactId = contact.id,
            contactName = contact.name,
            phoneNumber = RustPhoneBridge.formatPhoneNumber(contact.phoneNumber)
        )
        dao.setSpeedDial(entry)
    }

    suspend fun setSpeedDialCustom(digit: Int, name: String, phoneNumber: String) {
        if (digit !in 2..9) return
        val trimmedPhone = phoneNumber.trim()
        if (trimmedPhone.isEmpty()) return
        val formattedPhone = RustPhoneBridge.formatPhoneNumber(trimmedPhone)
        val trimmedName = name.trim().ifBlank { formattedPhone }
        val newContact = ContactEntity(
            name = trimmedName,
            phoneNumber = formattedPhone,
            phoneLabel = "Speed Dial",
            isSystemContact = false
        )
        dao.insertContact(newContact)
        dao.setSpeedDial(
            SpeedDialEntity(
                keyDigit = digit,
                contactId = newContact.id,
                contactName = trimmedName,
                phoneNumber = formattedPhone
            )
        )
    }

    suspend fun removeSpeedDial(digit: Int) {
        dao.removeSpeedDial(digit)
    }

    suspend fun clearAllSpeedDials() {
        dao.clearAllSpeedDials()
    }

    suspend fun getSpeedDial(digit: Int): SpeedDialEntity? = dao.getSpeedDial(digit)

    suspend fun blockNumber(phone: String, label: String = "Blocked Number") {
        val trimmed = phone.trim()
        if (trimmed.isEmpty()) return
        val normalized = RustPhoneBridge.normalizeForMatch(trimmed).ifBlank { trimmed }
        val formatted = RustPhoneBridge.formatPhoneNumber(trimmed)
        dao.blockNumber(
            BlockedNumberEntity(
                normalizedNumber = normalized,
                phoneNumber = formatted,
                label = label
            )
        )
    }

    suspend fun unblockNumber(phone: String) {
        val trimmed = phone.trim()
        val normalized = RustPhoneBridge.normalizeForMatch(trimmed).ifBlank { trimmed }
        dao.unblockNumber(normalizedPhone = normalized, rawPhone = trimmed)
    }
}
