package com.example.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Database(
    entities = [
        ContactEntity::class,
        CallLogEntity::class,
        SpeedDialEntity::class,
        BlockedNumberEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun phoneDao(): PhoneDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context, scope: CoroutineScope): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "samsung_phone_database"
                )
                    .addCallback(DatabaseCallback(scope))
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }

        private class DatabaseCallback(
            private val scope: CoroutineScope
        ) : RoomDatabase.Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {
                super.onCreate(db)
                INSTANCE?.let { database ->
                    scope.launch(Dispatchers.IO) {
                        seedInitialData(database.phoneDao())
                    }
                }
            }
        }

        suspend fun seedInitialData(dao: PhoneDao) {
            val sampleContacts = listOf(
                ContactEntity(
                    id = "contact_mom",
                    name = "Mom ❤️",
                    phoneNumber = "+1 (555) 019-2831",
                    email = "mom@family.org",
                    isFavorite = true,
                    groupName = "Family",
                    avatarColorHex = "#FF5E7E",
                    notes = "Birthday: Nov 12. Loves morning calls!"
                ),
                ContactEntity(
                    id = "contact_alex",
                    name = "Alex Johnson",
                    phoneNumber = "+1 (555) 234-5678",
                    email = "alex.j@techcorp.io",
                    isFavorite = true,
                    groupName = "Work",
                    avatarColorHex = "#2E88FF",
                    notes = "Lead Engineer at TechCorp"
                ),
                ContactEntity(
                    id = "contact_sarah",
                    name = "Sarah Miller",
                    phoneNumber = "+1 (555) 876-5432",
                    email = "sarah.m@designstudio.com",
                    isFavorite = true,
                    groupName = "Friends",
                    avatarColorHex = "#9C27B0",
                    notes = "Design collaborator"
                ),
                ContactEntity(
                    id = "contact_david",
                    name = "David Kim",
                    phoneNumber = "+1 (555) 345-6789",
                    email = "david.kim@sample.net",
                    isFavorite = false,
                    groupName = "Friends",
                    avatarColorHex = "#00B0FF",
                    notes = "Met at Android Summit"
                ),
                ContactEntity(
                    id = "contact_emma",
                    name = "Emma Watson",
                    phoneNumber = "+1 (555) 456-7890",
                    email = "emma.w@creative.org",
                    isFavorite = true,
                    groupName = "Friends",
                    avatarColorHex = "#E91E63",
                    notes = ""
                ),
                ContactEntity(
                    id = "contact_michael",
                    name = "Michael Brown",
                    phoneNumber = "+1 (555) 567-8901",
                    email = "mbrown@ventures.com",
                    isFavorite = false,
                    groupName = "Work",
                    avatarColorHex = "#4CAF50",
                    notes = "Product Manager"
                ),
                ContactEntity(
                    id = "contact_olivia",
                    name = "Olivia Garcia",
                    phoneNumber = "+1 (555) 678-9012",
                    email = "olivia@architects.io",
                    isFavorite = false,
                    groupName = "Work",
                    avatarColorHex = "#FF9800",
                    notes = ""
                ),
                ContactEntity(
                    id = "contact_pizza",
                    name = "Tony's Pizzeria",
                    phoneNumber = "+1 (555) 789-0123",
                    email = "orders@tonyspizza.com",
                    isFavorite = false,
                    groupName = "Places",
                    avatarColorHex = "#F44336",
                    notes = "Favorite: Pepperoni & Truffle Mushroom"
                ),
                ContactEntity(
                    id = "contact_dr_smith",
                    name = "Dr. James Smith (Clinic)",
                    phoneNumber = "+1 (555) 890-1234",
                    email = "reception@smithmedical.com",
                    isFavorite = false,
                    groupName = "Medical",
                    avatarColorHex = "#009688",
                    notes = "Appointments 9am - 4pm"
                )
            )
            dao.insertContacts(sampleContacts)

            // Seed initial Speed Dials (2 -> Mom, 3 -> Alex, 4 -> Sarah)
            dao.setSpeedDial(SpeedDialEntity(2, "contact_mom", "Mom ❤️", "+1 (555) 019-2831"))
            dao.setSpeedDial(SpeedDialEntity(3, "contact_alex", "Alex Johnson", "+1 (555) 234-5678"))
            dao.setSpeedDial(SpeedDialEntity(4, "contact_sarah", "Sarah Miller", "+1 (555) 876-5432"))

            // Seed initial realistic Call History
            val now = System.currentTimeMillis()
            val sampleLogs = listOf(
                CallLogEntity(
                    phoneNumber = "+1 (555) 019-2831",
                    contactName = "Mom ❤️",
                    callType = "INCOMING",
                    timestamp = now - (18 * 60 * 1000), // 18 mins ago
                    durationSeconds = 245,
                    simSlot = 1
                ),
                CallLogEntity(
                    phoneNumber = "+1 (555) 234-5678",
                    contactName = "Alex Johnson",
                    callType = "OUTGOING",
                    timestamp = now - (2 * 3600 * 1000), // 2 hours ago
                    durationSeconds = 85,
                    simSlot = 1
                ),
                CallLogEntity(
                    phoneNumber = "+1 (555) 999-1122",
                    contactName = null,
                    callType = "MISSED",
                    timestamp = now - (5 * 3600 * 1000), // 5 hours ago
                    durationSeconds = 0,
                    simSlot = 2
                ),
                CallLogEntity(
                    phoneNumber = "+1 (555) 876-5432",
                    contactName = "Sarah Miller",
                    callType = "OUTGOING",
                    timestamp = now - (24 * 3600 * 1000), // Yesterday
                    durationSeconds = 412,
                    simSlot = 1
                ),
                CallLogEntity(
                    phoneNumber = "+1 (555) 789-0123",
                    contactName = "Tony's Pizzeria",
                    callType = "OUTGOING",
                    timestamp = now - (26 * 3600 * 1000),
                    durationSeconds = 124,
                    simSlot = 1
                ),
                CallLogEntity(
                    phoneNumber = "+1 (555) 444-0000",
                    contactName = null,
                    callType = "REJECTED",
                    timestamp = now - (48 * 3600 * 1000),
                    durationSeconds = 0,
                    simSlot = 1
                )
            )
            dao.insertCallLogs(sampleLogs)
        }
    }
}
