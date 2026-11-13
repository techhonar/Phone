package com.example.data

import android.Manifest
import android.annotation.SuppressLint
import android.content.ContentProviderOperation
import android.content.ContentResolver
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.CallLog
import android.provider.ContactsContract
import android.util.Log
import androidx.core.content.ContextCompat
import com.example.rust.RustPhoneBridge
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Direct interface to real Android system ContactsContract and CallLog ContentProviders.
 */
class SystemPhoneDataSource(private val context: Context) {

    companion object {
        private const val TAG = "SystemPhoneDataSource"
        const val DEFAULT_CALL_LOG_LIMIT = 500
        private val AVATAR_PALETTE = listOf(
            "#27C064", "#2E88FF", "#8B5CF6", "#F59E0B",
            "#EC4899", "#06B6D4", "#10B981", "#EF4444"
        )
    }

    fun hasReadContactsPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED

    fun hasWriteContactsPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_CONTACTS) == PackageManager.PERMISSION_GRANTED

    fun hasReadCallLogPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALL_LOG) == PackageManager.PERMISSION_GRANTED

    fun hasWriteCallLogPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_CALL_LOG) == PackageManager.PERMISSION_GRANTED

    fun observeSystemContacts(refreshTrigger: Flow<Int>): Flow<List<ContactEntity>> = callbackFlow {
        val emitContacts = {
            trySend(if (hasReadContactsPermission()) querySystemContacts() else emptyList())
        }
        emitContacts()

        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                emitContacts()
            }
        }
        var observerRegistered = false
        val tryRegisterObserver = {
            if (!observerRegistered && hasReadContactsPermission()) {
                try {
                    context.contentResolver.registerContentObserver(
                        ContactsContract.Contacts.CONTENT_URI,
                        true,
                        observer
                    )
                    observerRegistered = true
                } catch (e: Exception) {
                    Log.w(TAG, "Unable to register contacts observer", e)
                }
            }
        }
        tryRegisterObserver()

        val job = launch {
            refreshTrigger.collect {
                tryRegisterObserver()
                emitContacts()
            }
        }

        awaitClose {
            job.cancel()
            try {
                if (observerRegistered) {
                    context.contentResolver.unregisterContentObserver(observer)
                }
            } catch (_: Exception) {
            }
        }
    }.flowOn(Dispatchers.IO)

    fun observeSystemCallLogs(refreshTrigger: Flow<Int>): Flow<List<CallLogEntity>> = callbackFlow {
        val emitLogs = {
            trySend(if (hasReadCallLogPermission()) querySystemCallLogs() else emptyList())
        }
        emitLogs()

        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                emitLogs()
            }
        }
        var observerRegistered = false
        val tryRegisterObserver = {
            if (!observerRegistered && hasReadCallLogPermission()) {
                try {
                    context.contentResolver.registerContentObserver(
                        CallLog.Calls.CONTENT_URI,
                        true,
                        observer
                    )
                    observerRegistered = true
                } catch (e: Exception) {
                    Log.w(TAG, "Unable to register call log observer", e)
                }
            }
        }
        tryRegisterObserver()

        val job = launch {
            refreshTrigger.collect {
                tryRegisterObserver()
                emitLogs()
            }
        }

        awaitClose {
            job.cancel()
            try {
                if (observerRegistered) {
                    context.contentResolver.unregisterContentObserver(observer)
                }
            } catch (_: Exception) {
            }
        }
    }.flowOn(Dispatchers.IO)

    @SuppressLint("Range")
    fun querySystemContacts(): List<ContactEntity> {
        if (!hasReadContactsPermission()) return emptyList()
        val results = linkedMapOf<String, ContactEntity>()
        val resolver: ContentResolver = context.contentResolver

        val emailByContactId = queryEmailsByContactId(resolver)
        val noteByContactId = queryNotesByContactId(resolver)

        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME_PRIMARY,
            ContactsContract.CommonDataKinds.Phone.NUMBER,
            ContactsContract.CommonDataKinds.Phone.TYPE,
            ContactsContract.CommonDataKinds.Phone.LABEL,
            ContactsContract.CommonDataKinds.Phone.PHOTO_URI,
            ContactsContract.CommonDataKinds.Phone.STARRED
        )

        try {
            resolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                projection,
                null,
                null,
                "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME_PRIMARY} ASC"
            )?.use { cursor ->
                val idxId = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.CONTACT_ID)
                val idxName = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME_PRIMARY)
                val idxNumber = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                val idxType = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.TYPE)
                val idxLabel = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.LABEL)
                val idxPhoto = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.PHOTO_URI)
                val idxStarred = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.STARRED)

                while (cursor.moveToNext()) {
                    val contactId = cursor.getString(idxId) ?: continue
                    val rawNumber = cursor.getString(idxNumber)?.trim().orEmpty()
                    if (rawNumber.isEmpty()) continue
                    val name = cursor.getString(idxName)?.takeIf { it.isNotBlank() } ?: rawNumber
                    val typeInt = if (idxType >= 0) cursor.getInt(idxType) else ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE
                    val customLabel = if (idxLabel >= 0) cursor.getString(idxLabel) else null
                    val phoneLabel = ContactsContract.CommonDataKinds.Phone.getTypeLabel(
                        context.resources,
                        typeInt,
                        customLabel
                    ).toString()
                    val photoUri = if (idxPhoto >= 0) cursor.getString(idxPhoto) else null
                    val isStarred = if (idxStarred >= 0) cursor.getInt(idxStarred) == 1 else false

                    val normNum = RustPhoneBridge.normalizeForMatch(rawNumber)
                    val uniqueKey = "${contactId}_$normNum"
                    if (!results.containsKey(uniqueKey)) {
                        val colorIdx = kotlin.math.abs(contactId.hashCode()) % AVATAR_PALETTE.size
                        results[uniqueKey] = ContactEntity(
                            id = contactId,
                            name = name,
                            phoneNumber = RustPhoneBridge.formatPhoneNumber(rawNumber),
                            email = emailByContactId[contactId].orEmpty(),
                            isFavorite = isStarred,
                            groupName = phoneLabel,
                            avatarColorHex = AVATAR_PALETTE[colorIdx],
                            notes = noteByContactId[contactId].orEmpty(),
                            photoUri = photoUri,
                            phoneLabel = phoneLabel,
                            isSystemContact = true
                        )
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error querying system contacts", e)
        }

        return results.values.distinctBy { it.id }
    }

    private fun queryEmailsByContactId(resolver: ContentResolver): Map<String, String> {
        val map = mutableMapOf<String, String>()
        try {
            resolver.query(
                ContactsContract.CommonDataKinds.Email.CONTENT_URI,
                arrayOf(
                    ContactsContract.CommonDataKinds.Email.CONTACT_ID,
                    ContactsContract.CommonDataKinds.Email.ADDRESS
                ),
                null,
                null,
                null
            )?.use { cursor ->
                while (cursor.moveToNext()) {
                    val id = cursor.getString(0) ?: continue
                    val email = cursor.getString(1) ?: continue
                    if (!map.containsKey(id)) map[id] = email
                }
            }
        } catch (_: Exception) {
        }
        return map
    }

    private fun queryNotesByContactId(resolver: ContentResolver): Map<String, String> {
        val map = mutableMapOf<String, String>()
        try {
            resolver.query(
                ContactsContract.Data.CONTENT_URI,
                arrayOf(
                    ContactsContract.Data.CONTACT_ID,
                    ContactsContract.CommonDataKinds.Note.NOTE
                ),
                "${ContactsContract.Data.MIMETYPE} = ?",
                arrayOf(ContactsContract.CommonDataKinds.Note.CONTENT_ITEM_TYPE),
                null
            )?.use { cursor ->
                while (cursor.moveToNext()) {
                    val id = cursor.getString(0) ?: continue
                    val note = cursor.getString(1) ?: continue
                    if (!map.containsKey(id) && note.isNotBlank()) map[id] = note
                }
            }
        } catch (_: Exception) {
        }
        return map
    }

    @SuppressLint("Range", "MissingPermission")
    fun querySystemCallLogs(limit: Int = DEFAULT_CALL_LOG_LIMIT): List<CallLogEntity> {
        if (!hasReadCallLogPermission()) return emptyList()
        val logs = mutableListOf<CallLogEntity>()
        val syncTime = System.currentTimeMillis()

        val projection = arrayOf(
            CallLog.Calls._ID,
            CallLog.Calls.NUMBER,
            CallLog.Calls.CACHED_NAME,
            CallLog.Calls.CACHED_PHOTO_URI,
            CallLog.Calls.TYPE,
            CallLog.Calls.DATE,
            CallLog.Calls.DURATION,
            CallLog.Calls.PHONE_ACCOUNT_ID,
            CallLog.Calls.GEOCODED_LOCATION
        )

        try {
            context.contentResolver.query(
                CallLog.Calls.CONTENT_URI,
                projection,
                null,
                null,
                "${CallLog.Calls.DATE} DESC"
            )?.use { cursor ->
                val idxId = cursor.getColumnIndex(CallLog.Calls._ID)
                val idxNumber = cursor.getColumnIndex(CallLog.Calls.NUMBER)
                val idxName = cursor.getColumnIndex(CallLog.Calls.CACHED_NAME)
                val idxPhoto = cursor.getColumnIndex(CallLog.Calls.CACHED_PHOTO_URI)
                val idxType = cursor.getColumnIndex(CallLog.Calls.TYPE)
                val idxDate = cursor.getColumnIndex(CallLog.Calls.DATE)
                val idxDuration = cursor.getColumnIndex(CallLog.Calls.DURATION)
                val idxAccount = cursor.getColumnIndex(CallLog.Calls.PHONE_ACCOUNT_ID)
                val idxLocation = cursor.getColumnIndex(CallLog.Calls.GEOCODED_LOCATION)

                var count = 0
                while (cursor.moveToNext() && count < limit) {
                    val rawId = cursor.getString(idxId) ?: continue
                    val rawNumber = cursor.getString(idxNumber)?.takeIf { it.isNotBlank() } ?: "Private / Unknown"
                    val cachedName = cursor.getString(idxName)?.takeIf { it.isNotBlank() }
                    val photoUri = if (idxPhoto >= 0) cursor.getString(idxPhoto) else null
                    val typeInt = cursor.getInt(idxType)
                    val date = cursor.getLong(idxDate)
                    val duration = cursor.getLong(idxDuration)
                    val accountId = if (idxAccount >= 0) cursor.getString(idxAccount) else null
                    val geocodedLocation = if (idxLocation >= 0) {
                        cursor.getString(idxLocation)?.takeIf { it.isNotBlank() }
                    } else null

                    val callTypeStr = when (typeInt) {
                        CallLog.Calls.INCOMING_TYPE -> "INCOMING"
                        CallLog.Calls.OUTGOING_TYPE -> "OUTGOING"
                        CallLog.Calls.MISSED_TYPE -> "MISSED"
                        CallLog.Calls.REJECTED_TYPE -> "REJECTED"
                        CallLog.Calls.BLOCKED_TYPE -> "BLOCKED"
                        CallLog.Calls.VOICEMAIL_TYPE -> "VOICEMAIL"
                        else -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && typeInt == CallLog.Calls.ANSWERED_EXTERNALLY_TYPE) {
                            "INCOMING"
                        } else {
                            "INCOMING"
                        }
                    }

                    val simSlot = if (accountId?.contains("2") == true || accountId?.endsWith("_1") == true) 2 else 1
                    val formattedPhone = if (rawNumber == "Private / Unknown") {
                        rawNumber
                    } else {
                        RustPhoneBridge.formatPhoneNumber(rawNumber)
                    }
                    val normalizedPhone = if (rawNumber == "Private / Unknown") {
                        ""
                    } else {
                        RustPhoneBridge.normalizeForMatch(rawNumber)
                    }

                    logs.add(
                        CallLogEntity(
                            id = "sys_$rawId",
                            systemLogId = rawId.toLongOrNull(),
                            phoneNumber = formattedPhone,
                            normalizedNumber = normalizedPhone,
                            contactName = cachedName,
                            callType = callTypeStr,
                            timestamp = date,
                            durationSeconds = duration,
                            simSlot = simSlot,
                            photoUri = photoUri,
                            geocodedLocation = geocodedLocation,
                            isSystemLog = true,
                            syncedAt = syncTime
                        )
                    )
                    count++
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error querying system call logs", e)
        }
        return logs
    }

    suspend fun insertSystemContact(
        name: String,
        phoneNumber: String,
        email: String,
        phoneLabel: String,
        notes: String
    ): Boolean = withContext(Dispatchers.IO) {
        if (!hasWriteContactsPermission()) return@withContext false
        try {
            val ops = ArrayList<ContentProviderOperation>()
            ops.add(
                ContentProviderOperation.newInsert(ContactsContract.RawContacts.CONTENT_URI)
                    .withValue(ContactsContract.RawContacts.ACCOUNT_TYPE, null as String?)
                    .withValue(ContactsContract.RawContacts.ACCOUNT_NAME, null as String?)
                    .build()
            )
            ops.add(
                ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                    .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
                    .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE)
                    .withValue(ContactsContract.CommonDataKinds.StructuredName.DISPLAY_NAME, name)
                    .build()
            )
            val phoneTypeInt = when (phoneLabel.lowercase()) {
                "home" -> ContactsContract.CommonDataKinds.Phone.TYPE_HOME
                "work" -> ContactsContract.CommonDataKinds.Phone.TYPE_WORK
                else -> ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE
            }
            ops.add(
                ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                    .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
                    .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE)
                    .withValue(ContactsContract.CommonDataKinds.Phone.NUMBER, phoneNumber)
                    .withValue(ContactsContract.CommonDataKinds.Phone.TYPE, phoneTypeInt)
                    .build()
            )
            if (email.isNotBlank()) {
                ops.add(
                    ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                        .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
                        .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.Email.CONTENT_ITEM_TYPE)
                        .withValue(ContactsContract.CommonDataKinds.Email.ADDRESS, email)
                        .withValue(ContactsContract.CommonDataKinds.Email.TYPE, ContactsContract.CommonDataKinds.Email.TYPE_MOBILE)
                        .build()
                )
            }
            if (notes.isNotBlank()) {
                ops.add(
                    ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                        .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
                        .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.Note.CONTENT_ITEM_TYPE)
                        .withValue(ContactsContract.CommonDataKinds.Note.NOTE, notes)
                        .build()
                )
            }
            context.contentResolver.applyBatch(ContactsContract.AUTHORITY, ops)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to insert system contact", e)
            false
        }
    }

    suspend fun toggleSystemFavorite(contactId: String, isFavorite: Boolean): Boolean = withContext(Dispatchers.IO) {
        if (!hasWriteContactsPermission()) return@withContext false
        try {
            val idNum = contactId.toLongOrNull() ?: return@withContext false
            val uri: Uri = ContentUris.withAppendedId(ContactsContract.Contacts.CONTENT_URI, idNum)
            val values = ContentValues().apply {
                put(ContactsContract.Contacts.STARRED, if (isFavorite) 1 else 0)
            }
            context.contentResolver.update(uri, values, null, null) > 0
        } catch (e: Exception) {
            Log.e(TAG, "Failed to toggle favorite", e)
            false
        }
    }

    suspend fun deleteSystemContact(contactId: String): Boolean = withContext(Dispatchers.IO) {
        if (!hasWriteContactsPermission()) return@withContext false
        try {
            val idNum = contactId.toLongOrNull() ?: return@withContext false
            val uri = ContentUris.withAppendedId(ContactsContract.Contacts.CONTENT_URI, idNum)
            context.contentResolver.delete(uri, null, null) > 0
        } catch (e: Exception) {
            Log.e(TAG, "Failed to delete contact", e)
            false
        }
    }

    @SuppressLint("MissingPermission")
    suspend fun insertSystemCallLog(
        phoneNumber: String,
        callType: String,
        timestamp: Long,
        durationSeconds: Long
    ): Long? = withContext(Dispatchers.IO) {
        if (!hasWriteCallLogPermission()) return@withContext null
        try {
            val typeInt = when (callType) {
                "OUTGOING" -> CallLog.Calls.OUTGOING_TYPE
                "INCOMING" -> CallLog.Calls.INCOMING_TYPE
                "MISSED" -> CallLog.Calls.MISSED_TYPE
                "REJECTED" -> CallLog.Calls.REJECTED_TYPE
                "BLOCKED" -> CallLog.Calls.BLOCKED_TYPE
                "VOICEMAIL" -> CallLog.Calls.VOICEMAIL_TYPE
                else -> CallLog.Calls.OUTGOING_TYPE
            }
            val values = ContentValues().apply {
                put(CallLog.Calls.NUMBER, phoneNumber)
                put(CallLog.Calls.TYPE, typeInt)
                put(CallLog.Calls.DATE, timestamp)
                put(CallLog.Calls.DURATION, durationSeconds)
                put(CallLog.Calls.NEW, if (callType == "MISSED") 1 else 0)
            }
            val insertedUri = context.contentResolver.insert(CallLog.Calls.CONTENT_URI, values)
            insertedUri?.lastPathSegment?.toLongOrNull()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to insert system call log", e)
            null
        }
    }

    @SuppressLint("MissingPermission")
    suspend fun deleteSystemCallLog(id: String): Boolean = withContext(Dispatchers.IO) {
        if (!hasWriteCallLogPermission()) return@withContext false
        val systemId = id.removePrefix("sys_")
        try {
            val deleted = context.contentResolver.delete(
                CallLog.Calls.CONTENT_URI,
                "${CallLog.Calls._ID} = ?",
                arrayOf(systemId)
            )
            deleted > 0
        } catch (e: Exception) {
            Log.e(TAG, "Failed to delete call log", e)
            false
        }
    }

    @SuppressLint("MissingPermission")
    suspend fun clearAllSystemCallLogs(): Boolean = withContext(Dispatchers.IO) {
        if (!hasWriteCallLogPermission()) return@withContext false
        try {
            context.contentResolver.delete(CallLog.Calls.CONTENT_URI, null, null)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to clear call logs", e)
            false
        }
    }
}
