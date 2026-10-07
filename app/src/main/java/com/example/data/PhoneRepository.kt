package com.example.data

import com.example.rust.RustPhoneBridge
import com.example.rust.T9Match
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

class PhoneRepository(private val dao: PhoneDao) {

    val allContacts: Flow<List<ContactEntity>> = dao.getAllContacts()
    val favoriteContacts: Flow<List<ContactEntity>> = dao.getFavoriteContacts()
    val allCallLogs: Flow<List<CallLogEntity>> = dao.getAllCallLogs()
    val missedCallLogs: Flow<List<CallLogEntity>> = dao.getMissedCallLogs()
    val allSpeedDials: Flow<List<SpeedDialEntity>> = dao.getAllSpeedDials()
    val allBlockedNumbers: Flow<List<BlockedNumberEntity>> = dao.getAllBlockedNumbers()

    suspend fun performT9Search(digits: String): List<T9Match> {
        if (digits.isBlank()) return emptyList()
        val contacts = dao.getAllContacts().first()
        val tuples = contacts.map { Triple(it.id, it.name, it.phoneNumber) }
        return RustPhoneBridge.searchT9(digits, tuples)
    }

    suspend fun getContactByPhone(phone: String): ContactEntity? {
        return dao.getContactByPhone(phone)
    }

    suspend fun insertContact(contact: ContactEntity) {
        dao.insertContact(contact)
    }

    suspend fun updateContact(contact: ContactEntity) {
        dao.updateContact(contact)
    }

    suspend fun deleteContact(id: String) {
        dao.deleteContactById(id)
    }

    suspend fun toggleFavorite(id: String, isFavorite: Boolean) {
        dao.toggleFavorite(id, isFavorite)
    }

    suspend fun addCallLog(
        phoneNumber: String,
        contactName: String?,
        callType: String,
        durationSeconds: Long,
        simSlot: Int = 1
    ) {
        val entry = CallLogEntity(
            phoneNumber = phoneNumber,
            contactName = contactName,
            callType = callType,
            timestamp = System.currentTimeMillis(),
            durationSeconds = durationSeconds,
            simSlot = simSlot
        )
        dao.insertCallLog(entry)
    }

    suspend fun deleteCallLog(id: String) {
        dao.deleteCallLogById(id)
    }

    suspend fun clearAllCallLogs() {
        dao.clearAllCallLogs()
    }

    suspend fun setSpeedDial(digit: Int, contact: ContactEntity) {
        val entry = SpeedDialEntity(
            keyDigit = digit,
            contactId = contact.id,
            contactName = contact.name,
            phoneNumber = contact.phoneNumber
        )
        dao.setSpeedDial(entry)
    }

    suspend fun removeSpeedDial(digit: Int) {
        dao.removeSpeedDial(digit)
    }

    suspend fun getSpeedDial(digit: Int): SpeedDialEntity? {
        return dao.getSpeedDial(digit)
    }

    suspend fun blockNumber(phone: String, label: String = "Blocked Number") {
        dao.blockNumber(BlockedNumberEntity(phoneNumber = phone, label = label))
    }

    suspend fun unblockNumber(phone: String) {
        dao.unblockNumber(phone)
    }

    suspend fun isBlocked(phone: String): Boolean {
        return dao.isNumberBlocked(phone) > 0
    }
}
