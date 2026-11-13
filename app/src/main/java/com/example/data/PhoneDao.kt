package com.example.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface PhoneDao {
    // --- Contacts ---
    @Query("SELECT * FROM contacts ORDER BY name ASC")
    fun getAllContacts(): Flow<List<ContactEntity>>

    @Query("SELECT * FROM contacts WHERE isFavorite = 1 ORDER BY name ASC")
    fun getFavoriteContacts(): Flow<List<ContactEntity>>

    @Query("SELECT * FROM contacts WHERE id = :id LIMIT 1")
    suspend fun getContactById(id: String): ContactEntity?

    @Query("SELECT * FROM contacts WHERE phoneNumber = :phone LIMIT 1")
    suspend fun getContactByPhone(phone: String): ContactEntity?

    @Query("SELECT * FROM contacts WHERE name LIKE '%' || :query || '%' OR phoneNumber LIKE '%' || :query || '%' ORDER BY name ASC")
    fun searchContacts(query: String): Flow<List<ContactEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertContact(contact: ContactEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertContacts(contacts: List<ContactEntity>)

    @Update
    suspend fun updateContact(contact: ContactEntity)

    @Delete
    suspend fun deleteContact(contact: ContactEntity)

    @Query("DELETE FROM contacts WHERE id = :id")
    suspend fun deleteContactById(id: String)

    @Query("UPDATE contacts SET isFavorite = :isFavorite WHERE id = :id")
    suspend fun toggleFavorite(id: String, isFavorite: Boolean)

    // --- Speed Dials ---
    @Query("SELECT * FROM speed_dials ORDER BY keyDigit ASC")
    fun getAllSpeedDials(): Flow<List<SpeedDialEntity>>

    @Query("SELECT * FROM speed_dials WHERE keyDigit = :digit LIMIT 1")
    suspend fun getSpeedDial(digit: Int): SpeedDialEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun setSpeedDial(speedDial: SpeedDialEntity)

    @Query("DELETE FROM speed_dials WHERE keyDigit = :digit")
    suspend fun removeSpeedDial(digit: Int)

    @Query("DELETE FROM speed_dials")
    suspend fun clearAllSpeedDials()

    // --- Blocked Numbers ---
    @Query("SELECT * FROM blocked_numbers ORDER BY timestamp DESC")
    fun getAllBlockedNumbers(): Flow<List<BlockedNumberEntity>>

    @Query(
        "SELECT * FROM blocked_numbers " +
            "WHERE normalizedNumber = :normalizedPhone OR phoneNumber = :rawPhone " +
            "LIMIT 1"
    )
    suspend fun findBlockedNumber(normalizedPhone: String, rawPhone: String): BlockedNumberEntity?

    @Query(
        "SELECT COUNT(*) > 0 FROM blocked_numbers " +
            "WHERE normalizedNumber = :normalizedPhone OR phoneNumber = :rawPhone"
    )
    suspend fun isNumberBlocked(normalizedPhone: String, rawPhone: String): Boolean

    @Query(
        "UPDATE blocked_numbers " +
            "SET blockedCount = blockedCount + 1, lastBlockedAt = :timestamp " +
            "WHERE normalizedNumber = :normalizedPhone"
    )
    suspend fun incrementBlockedCount(
        normalizedPhone: String,
        timestamp: Long = System.currentTimeMillis()
    )

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun blockNumber(blocked: BlockedNumberEntity)

    @Query("DELETE FROM blocked_numbers WHERE normalizedNumber = :normalizedPhone OR phoneNumber = :rawPhone")
    suspend fun unblockNumber(normalizedPhone: String, rawPhone: String)
}
