package com.example.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "contacts")
data class ContactEntity(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val phoneNumber: String,
    val email: String = "",
    val isFavorite: Boolean = false,
    val groupName: String = "Mobile",
    val avatarColorHex: String = "#27C064",
    val notes: String = "",
    val photoUri: String? = null,
    val phoneLabel: String = "Mobile",
    val isSystemContact: Boolean = false
)

@Entity(
    tableName = "call_logs",
    indices = [
        Index(value = ["timestamp"]),
        Index(value = ["systemLogId"]),
        Index(value = ["normalizedNumber"])
    ]
)
data class CallLogEntity(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val systemLogId: Long? = null,
    val phoneNumber: String,
    val normalizedNumber: String = "",
    val contactName: String? = null,
    val callType: String, // "OUTGOING", "INCOMING", "MISSED", "REJECTED", "BLOCKED", "VOICEMAIL"
    val timestamp: Long = System.currentTimeMillis(),
    val durationSeconds: Long = 0,
    val simSlot: Int = 1,
    val photoUri: String? = null,
    val geocodedLocation: String? = null,
    val isSystemLog: Boolean = false,
    val syncedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "speed_dials")
data class SpeedDialEntity(
    @PrimaryKey
    val keyDigit: Int, // 2 to 9
    val contactId: String,
    val contactName: String,
    val phoneNumber: String
)

@Entity(
    tableName = "blocked_numbers",
    indices = [Index(value = ["phoneNumber"])]
)
data class BlockedNumberEntity(
    @PrimaryKey
    val normalizedNumber: String,
    val phoneNumber: String,
    val label: String = "Spam / Blocked",
    val timestamp: Long = System.currentTimeMillis(),
    val blockedCount: Int = 0,
    val lastBlockedAt: Long? = null
)
