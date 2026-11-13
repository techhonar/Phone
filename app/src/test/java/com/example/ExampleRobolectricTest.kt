package com.example

import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.telecom.Connection
import android.telecom.ConnectionRequest
import android.telecom.DisconnectCause
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.AppDatabase
import com.example.data.BlockedNumberEntity
import com.example.data.CallHistoryDao
import com.example.data.CallLogEntity
import com.example.data.CallStatsEngine
import com.example.data.CallStatsRange
import com.example.data.ContactEntity
import com.example.data.PhoneRepository
import com.example.data.SpeedDialEntity
import com.example.data.SystemPhoneDataSource
import com.example.data.TopContactSortMode
import com.example.telecom.AudioRoute
import com.example.telecom.CallBlacklistEngine
import com.example.telecom.CallManager
import com.example.telecom.PhoneConnection
import com.example.telecom.PhoneConnectionService
import com.example.ui.components.SamsungDialButton
import com.example.ui.screens.CallStatsContent
import com.example.ui.screens.InCallScreen
import com.example.ui.screens.SpeedDialContent
import com.example.ui.theme.SamsungPhoneTheme
import java.util.Locale
import java.util.TimeZone
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private lateinit var context: Context
    private lateinit var database: AppDatabase
    private lateinit var callHistoryDao: CallHistoryDao

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        callHistoryDao = database.callHistoryDao()
    }

    @After
    fun teardown() {
        while (CallManager.uiState.value.calls.isNotEmpty()) {
            CallManager.disconnectCall()
        }
        database.close()
    }

    @Test
    fun testInsertQueryAndFilterCallHistory() = runTest {
        val t1 = 1_700_000_000_000L
        val t2 = 1_700_000_060_000L

        callHistoryDao.insertCallRecord(
            CallLogEntity(
                id = "local_1",
                phoneNumber = "(555) 234-5678",
                normalizedNumber = "5552345678",
                contactName = "Alex Johnson",
                callType = "OUTGOING",
                timestamp = t1,
                durationSeconds = 42,
                isSystemLog = false
            )
        )
        callHistoryDao.insertCallRecord(
            CallLogEntity(
                id = "local_2",
                phoneNumber = "(555) 876-5432",
                normalizedNumber = "5558765432",
                contactName = "Sarah Miller",
                callType = "MISSED",
                timestamp = t2,
                durationSeconds = 0,
                isSystemLog = false
            )
        )

        val all = callHistoryDao.getAllCallHistory().first()
        assertEquals(2, all.size)
        assertEquals("local_2", all.first().id)

        val missed = callHistoryDao.getMissedCallHistory().first()
        assertEquals(1, missed.size)
        assertEquals("Sarah Miller", missed.first().contactName)

        val searchResult = callHistoryDao.searchCallHistory("Alex").first()
        assertEquals(1, searchResult.size)
        assertEquals("local_1", searchResult.first().id)
    }

    @Test
    fun testSyncWithSystemCallLogsDeduplicatesAndPrunes() = runTest {
        val callTime = 1_700_001_000_000L

        callHistoryDao.insertCallRecord(
            CallLogEntity(
                id = "local_pending",
                phoneNumber = "(555) 123-4567",
                normalizedNumber = "5551234567",
                contactName = "Mom",
                callType = "OUTGOING",
                timestamp = callTime,
                durationSeconds = 0,
                isSystemLog = false
            )
        )

        callHistoryDao.insertCallRecord(
            CallLogEntity(
                id = "sys_99",
                systemLogId = 99L,
                phoneNumber = "(555) 000-0000",
                normalizedNumber = "5550000000",
                callType = "INCOMING",
                timestamp = callTime - 100_000L,
                isSystemLog = true
            )
        )

        val systemSnapshot = listOf(
            CallLogEntity(
                id = "sys_101",
                systemLogId = 101L,
                phoneNumber = "(555) 123-4567",
                normalizedNumber = "5551234567",
                contactName = "Mom",
                callType = "OUTGOING",
                timestamp = callTime + 2_000L,
                durationSeconds = 115,
                geocodedLocation = "Seattle, WA",
                isSystemLog = true
            )
        )

        callHistoryDao.syncWithSystemCallLogs(systemSnapshot, completeSnapshot = true)

        val synced = callHistoryDao.getAllCallHistory().first()
        assertEquals(1, synced.size)
        assertEquals("sys_101", synced.first().id)
        assertEquals(115L, synced.first().durationSeconds)
        assertEquals("Seattle, WA", synced.first().geocodedLocation)
        assertTrue(synced.first().isSystemLog)
        assertFalse(synced.any { it.id == "local_pending" || it.id == "sys_99" })
    }

    @Test
    fun testInCallScreenWithConnectionServiceControls() {
        val connection = PhoneConnection(
            context = context,
            number = "+15552345678",
            initialDisplayName = "Alex Johnson",
            isIncoming = false
        ).apply {
            setDialing()
        }
        CallManager.onConnectionAdded(context, connection)
        connection.markDialingConnected()

        composeTestRule.setContent {
            SamsungPhoneTheme {
                val state by CallManager.uiState.collectAsState()
                if (state.calls.isNotEmpty()) {
                    InCallScreen(
                        telecomState = state,
                        onAddCall = {}
                    )
                }
            }
        }

        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("in_call_screen").assertIsDisplayed()
        composeTestRule.onNodeWithTag("in_call_contact_name").assertTextContains("Alex Johnson")
        composeTestRule.onNodeWithTag("in_call_status_duration").assertIsDisplayed()

        // Toggle Mute
        composeTestRule.onNodeWithTag("in_call_mute_button").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        assertTrue(CallManager.uiState.value.audioState.isMuted)
        composeTestRule.onNodeWithText("Unmute").assertIsDisplayed()

        // Toggle Speaker
        composeTestRule.onNodeWithTag("in_call_speaker_button").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        assertEquals(AudioRoute.SPEAKER, CallManager.uiState.value.audioState.currentRoute)

        // End Call
        composeTestRule.onNodeWithTag("end_call_button").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        assertTrue(CallManager.uiState.value.calls.isEmpty())
    }

    @Test
    fun testCallBlacklistEngineMatchesFormattedNumberAndRecordsBlockedCall() = runTest {
        database.phoneDao().blockNumber(
            BlockedNumberEntity(
                normalizedNumber = "5559998888",
                phoneNumber = "(555) 999-8888",
                label = "Telemarketer"
            )
        )

        val engine = CallBlacklistEngine(database)
        val evaluation = engine.evaluateIncomingCaller(
            callerNumber = "+15559998888",
            callerName = null,
            recordBlockedCall = true
        )

        assertTrue(evaluation.isBlocked)
        assertEquals("5559998888", evaluation.normalizedNumber)
        assertEquals("Telemarketer", evaluation.matchedRule?.label)

        val blockedList = database.phoneDao().getAllBlockedNumbers().first()
        assertEquals(1, blockedList.size)
        assertEquals(1, blockedList.first().blockedCount)
        assertNotNull(blockedList.first().lastBlockedAt)

        val logs = callHistoryDao.getAllCallHistory().first()
        assertEquals(1, logs.size)
        assertEquals("BLOCKED", logs.first().callType)
        assertEquals("5559998888", logs.first().normalizedNumber)
        assertEquals("Telemarketer", logs.first().contactName)
    }

    @Test
    fun testConnectionServiceAutoRejectsBlacklistedIncomingCall() = runTest {
        database.phoneDao().blockNumber(
            BlockedNumberEntity(
                normalizedNumber = "5559998888",
                phoneNumber = "(555) 999-8888",
                label = "Spam Caller"
            )
        )

        val service = Robolectric.setupService(PhoneConnectionService::class.java)
        val request = ConnectionRequest(
            PhoneConnectionService.getPhoneAccountHandle(context),
            Uri.fromParts("tel", "+15559998888", null),
            Bundle()
        )

        val connection = service.createIncomingConnectionWithBlacklist(request, database)

        assertEquals(Connection.STATE_DISCONNECTED, connection.state)
        assertEquals(DisconnectCause.REJECTED, connection.disconnectCause?.code)
        assertTrue(CallManager.uiState.value.calls.isEmpty())

        val blockedEntry = database.phoneDao().findBlockedNumber("5559998888", "+15559998888")
        assertNotNull(blockedEntry)
        assertEquals(1, blockedEntry?.blockedCount)

        val blockedLogs = callHistoryDao.getAllCallHistory().first().filter { it.callType == "BLOCKED" }
        assertEquals(1, blockedLogs.size)
    }

    @Test
    fun testConnectionServiceAllowsNonBlacklistedIncomingCall() = runTest {
        database.phoneDao().blockNumber(
            BlockedNumberEntity(
                normalizedNumber = "5559998888",
                phoneNumber = "(555) 999-8888",
                label = "Spam Caller"
            )
        )

        val service = Robolectric.setupService(PhoneConnectionService::class.java)
        val request = ConnectionRequest(
            PhoneConnectionService.getPhoneAccountHandle(context),
            Uri.fromParts("tel", "+15551112222", null),
            Bundle()
        )

        val connection = service.createIncomingConnectionWithBlacklist(request, database)

        assertEquals(Connection.STATE_RINGING, connection.state)
        assertEquals(1, CallManager.uiState.value.calls.size)
        (connection as PhoneConnection).onDisconnect()
        assertTrue(CallManager.uiState.value.calls.isEmpty())
    }

    @Test
    fun testSpeedDialRoomPersistenceCrud() = runTest {
        val repository = PhoneRepository(
            dao = database.phoneDao(),
            callHistoryDao = callHistoryDao,
            systemSource = SystemPhoneDataSource(context)
        )

        val mom = ContactEntity(
            id = "c_mom",
            name = "Mom",
            phoneNumber = "5551234567"
        )
        val alex = ContactEntity(
            id = "c_alex",
            name = "Alex Johnson",
            phoneNumber = "5559876543"
        )

        repository.setSpeedDial(2, mom)
        repository.setSpeedDial(9, alex)

        val all = repository.allSpeedDials.first()
        assertEquals(2, all.size)
        assertEquals(2, all[0].keyDigit)
        assertEquals("(555) 123-4567", all[0].phoneNumber)
        assertEquals("Alex Johnson", repository.getSpeedDial(9)?.contactName)

        // Custom speed dial also persists contact + mapping
        repository.setSpeedDialCustom(digit = 5, name = "Office Desk", phoneNumber = "5554443333")
        val slot5 = repository.getSpeedDial(5)
        assertNotNull(slot5)
        assertEquals("Office Desk", slot5?.contactName)
        assertEquals("(555) 444-3333", slot5?.phoneNumber)

        // Remove single key & clear all
        repository.removeSpeedDial(2)
        assertEquals(null, repository.getSpeedDial(2))

        repository.clearAllSpeedDials()
        assertTrue(repository.allSpeedDials.first().isEmpty())
    }

    @Test
    fun testSpeedDialScreenAssignCallAndRemoveFlow() {
        val alex = ContactEntity(id = "c1", name = "Alex Johnson", phoneNumber = "(555) 234-5678")
        val sarah = ContactEntity(id = "c2", name = "Sarah Miller", phoneNumber = "(555) 876-5432")
        var calledNumber: String? = null

        composeTestRule.setContent {
            SamsungPhoneTheme {
                var speedDials by remember { mutableStateOf<List<SpeedDialEntity>>(emptyList()) }
                SpeedDialContent(
                    speedDials = speedDials,
                    allContacts = listOf(alex, sarah),
                    onAssignContact = { digit, contact ->
                        speedDials = speedDials.filterNot { it.keyDigit == digit } +
                            SpeedDialEntity(digit, contact.id, contact.name, contact.phoneNumber)
                    },
                    onAssignCustomNumber = { _, _, _ -> },
                    onRemoveSpeedDial = { digit ->
                        speedDials = speedDials.filterNot { it.keyDigit == digit }
                    },
                    onClearAllSpeedDials = { speedDials = emptyList() },
                    onCallNumber = { phone -> calledNumber = phone },
                    onBack = {}
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("speed_dial_screen").assertIsDisplayed()

        // Assign key 3 by opening picker and filtering contacts
        composeTestRule.onNodeWithTag("speed_dial_slot_3").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("speed_dial_picker_dialog").assertIsDisplayed()
        composeTestRule.onNodeWithTag("speed_dial_contact_search").performTextInput("Alex")
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("speed_dial_picker_contact_c1").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("Alex Johnson").assertIsDisplayed()

        // Quick-call assigned key 3
        composeTestRule.onNodeWithTag("speed_dial_call_3").performClick()
        composeTestRule.waitForIdle()
        assertEquals("(555) 234-5678", calledNumber)

        // Remove key 3
        composeTestRule.onNodeWithTag("speed_dial_remove_3").performClick()
        composeTestRule.waitForIdle()
        assertTrue(
            composeTestRule.onAllNodes(
                androidx.compose.ui.test.hasText("Alex Johnson")
            ).fetchSemanticsNodes().isEmpty()
        )
    }

    @Test
    fun testDialKeyLongPressTriggersSpeedDialQuickCall() {
        var tapped = false
        var longPressed = false

        composeTestRule.setContent {
            SamsungPhoneTheme {
                SamsungDialButton(
                    digit = '2',
                    subLetters = "ABC",
                    speedDialName = "Alex Johnson",
                    onClick = { tapped = true },
                    onLongClick = { longPressed = true }
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("dial_key_speed_badge_2").assertIsDisplayed()
        composeTestRule.onNodeWithText("Alex").assertIsDisplayed()

        composeTestRule.onNodeWithTag("dial_key_2").performTouchInput { longClick() }
        composeTestRule.waitForIdle()

        assertTrue(longPressed)
        assertFalse(tapped)
    }

    @Test
    fun testCallStatsEngineComputesDailyDurationAndTopContacts() {
        val utc = TimeZone.getTimeZone("UTC")
        val dayMs = 24L * 60L * 60L * 1000L
        val now = 1_700_000_000_000L + 12L * 3600L * 1000L

        val alex = ContactEntity(id = "c1", name = "Alex Johnson", phoneNumber = "(555) 234-5678")
        val mom = ContactEntity(id = "c2", name = "Mom", phoneNumber = "(555) 111-2222")

        val logs = listOf(
            // Today: Alex 300s outgoing, Mom 60s incoming
            CallLogEntity(
                id = "l1",
                phoneNumber = "5552345678",
                normalizedNumber = "5552345678",
                contactName = "Alex Johnson",
                callType = "OUTGOING",
                timestamp = now - 3600_000L,
                durationSeconds = 300
            ),
            CallLogEntity(
                id = "l2",
                phoneNumber = "5551112222",
                normalizedNumber = "5551112222",
                contactName = "Mom",
                callType = "INCOMING",
                timestamp = now - 7200_000L,
                durationSeconds = 60
            ),
            // 2 days ago: Mom twice (120s + 0s missed)
            CallLogEntity(
                id = "l3",
                phoneNumber = "5551112222",
                normalizedNumber = "5551112222",
                contactName = "Mom",
                callType = "OUTGOING",
                timestamp = now - 2 * dayMs,
                durationSeconds = 120
            ),
            CallLogEntity(
                id = "l4",
                phoneNumber = "5551112222",
                normalizedNumber = "5551112222",
                contactName = "Mom",
                callType = "MISSED",
                timestamp = now - 2 * dayMs + 1000L,
                durationSeconds = 0
            ),
            // 15 days ago (only inside 30D / ALL_TIME, outside 7D)
            CallLogEntity(
                id = "l5",
                phoneNumber = "5552345678",
                normalizedNumber = "5552345678",
                contactName = "Alex Johnson",
                callType = "INCOMING",
                timestamp = now - 15 * dayMs,
                durationSeconds = 600
            )
        )

        val stats7d = CallStatsEngine.compute(
            logs = logs,
            contacts = listOf(alex, mom),
            range = CallStatsRange.DAYS_7,
            nowMillis = now,
            timeZone = utc,
            locale = Locale.US
        )

        assertEquals(4, stats7d.totalCalls)
        assertEquals(480L, stats7d.totalDurationSeconds)
        assertEquals(160L, stats7d.averageDurationSeconds)
        assertEquals(75, stats7d.answeredRatePercent)
        assertEquals(7, stats7d.dailyStats.size)
        assertEquals(360L, stats7d.dailyStats.last().totalDurationSeconds)
        assertEquals(120L, stats7d.dailyStats[4].totalDurationSeconds)

        // By duration in 7D: Alex (300s) > Mom (180s)
        assertEquals("Alex Johnson", stats7d.topContactsByDuration.first().displayName)
        // By call count in 7D: Mom (3 calls) > Alex (1 call)
        assertEquals("Mom", stats7d.topContactsByCount.first().displayName)

        val stats30d = CallStatsEngine.compute(
            logs = logs,
            contacts = listOf(alex, mom),
            range = CallStatsRange.DAYS_30,
            nowMillis = now,
            timeZone = utc,
            locale = Locale.US
        )
        assertEquals(5, stats30d.totalCalls)
        assertEquals(1080L, stats30d.totalDurationSeconds)
        assertEquals(30, stats30d.dailyStats.size)
    }

    @Test
    fun testCallStatsDashboardUiDisplaysChartsAndInteractiveSort() {
        val utc = TimeZone.getTimeZone("UTC")
        val now = 1_700_000_000_000L + 12L * 3600L * 1000L
        val dayMs = 24L * 60L * 60L * 1000L

        val alex = ContactEntity(id = "c1", name = "Alex Johnson", phoneNumber = "(555) 234-5678")
        val mom = ContactEntity(id = "c2", name = "Mom", phoneNumber = "(555) 111-2222")
        val logs = listOf(
            CallLogEntity(
                id = "l1",
                phoneNumber = "5552345678",
                normalizedNumber = "5552345678",
                contactName = "Alex Johnson",
                callType = "OUTGOING",
                timestamp = now,
                durationSeconds = 420
            ),
            CallLogEntity(
                id = "l2",
                phoneNumber = "5551112222",
                normalizedNumber = "5551112222",
                contactName = "Mom",
                callType = "INCOMING",
                timestamp = now - dayMs,
                durationSeconds = 90
            ),
            CallLogEntity(
                id = "l3",
                phoneNumber = "5551112222",
                normalizedNumber = "5551112222",
                contactName = "Mom",
                callType = "OUTGOING",
                timestamp = now - 2 * dayMs,
                durationSeconds = 60
            )
        )

        var calledNumber: String? = null

        composeTestRule.setContent {
            SamsungPhoneTheme {
                var range by remember { mutableStateOf(CallStatsRange.DAYS_7) }
                var sortMode by remember { mutableStateOf(TopContactSortMode.DURATION) }
                val snapshot = remember(range) {
                    CallStatsEngine.compute(logs, listOf(alex, mom), range, now, utc, Locale.US)
                }
                CallStatsContent(
                    snapshot = snapshot,
                    selectedRange = range,
                    sortMode = sortMode,
                    onRangeChange = { range = it },
                    onSortModeChange = { sortMode = it },
                    onCallNumber = { calledNumber = it },
                    onBack = {}
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("call_stats_screen").assertIsDisplayed()
        composeTestRule.onNodeWithTag("stats_total_duration").assertTextContains("9m 30s")
        composeTestRule.onNodeWithTag("stats_total_calls").assertTextContains("3")
        composeTestRule.onNodeWithTag("stats_lazy_list")
            .performScrollToNode(hasTestTag("daily_duration_chart_card"))
        composeTestRule.onNodeWithTag("daily_duration_chart_card").assertIsDisplayed()

        // Tap yesterday's bar (index 5 of 0..6) and confirm selected day pill updates
        composeTestRule.onNodeWithTag("daily_bar_5").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("selected_day_detail").assertTextContains("1m 30s", substring = true)

        // Scroll to Top Contacts, verify Alex #1 by talk time, switch to Call Count -> Mom #1
        composeTestRule.onNodeWithTag("stats_lazy_list")
            .performScrollToNode(hasTestTag("top_contacts_card"))
        composeTestRule.onNodeWithTag("top_contacts_card").assertIsDisplayed()
        composeTestRule.onNodeWithTag("top_contact_name_0").assertTextContains("Alex Johnson")

        composeTestRule.onNodeWithTag("top_contacts_sort_call_count").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("top_contact_name_0").assertTextContains("Mom")

        // Quick-call top contact
        composeTestRule.onNodeWithTag("top_contact_call_0").performClick()
        composeTestRule.waitForIdle()
        assertEquals("(555) 111-2222", calledNumber)
    }
}
