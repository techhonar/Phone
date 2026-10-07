package com.example.data

import androidx.room.Entity
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
    val groupName: String = "All Contacts",
    val avatarColorHex: String = "#27C064",
    val notes: String = ""
)

@Entity(tableName = "call_logs")
data class CallLogEntity(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val phoneNumber: String,
    val contactName: String? = null,
    val callType: String, // "OUTGOING", "INCOMING", "MISSED", "REJECTED"
    val timestamp: Long = System.currentTimeMillis(),
    val durationSeconds: Long = 0,
    val simSlot: Int = 1
)

@Entity(tableName = "speed_dials")
data class SpeedDialEntity(
    @PrimaryKey
    val keyDigit: Int, // 2 to 9
    val contactId: String,
    val contactName: String,
    val phoneNumber: String
)

@Entity(tableName = "blocked_numbers")
data class BlockedNumberEntity(
    @PrimaryKey
    val phoneNumber: String,
    val label: String = "Spam / Blocked",
    val timestamp: Long = System.currentTimeMillis()
)
