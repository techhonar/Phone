package com.example.ui

import android.Manifest
import android.app.Application
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AppDatabase
import com.example.data.CallLogEntity
import com.example.data.CallStatisticsSnapshot
import com.example.data.CallStatsEngine
import com.example.data.CallStatsRange
import com.example.data.ContactEntity
import com.example.data.PhoneRepository
import com.example.data.SpeedDialEntity
import com.example.data.SystemPhoneDataSource
import com.example.data.TopContactSortMode
import com.example.rust.DtmfPlayer
import com.example.rust.RustPhoneBridge
import com.example.rust.T9Match
import com.example.telecom.CallLaunchResult
import com.example.telecom.CallManager
import com.example.telecom.PhoneConnectionService
import com.example.telecom.SimAccountInfo
import com.example.telecom.TelecomUiState
import com.example.telecom.TelephonyMonitor
import com.example.telecom.TelephonyStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class PhoneTab {
    KEYPAD, RECENTS, CONTACTS, PLACES
}

enum class RecentsFilter(val label: String) {
    ALL("All"),
    MISSED("Missed"),
    INCOMING("Incoming"),
    OUTGOING("Outgoing"),
    BLOCKED("Blocked")
}

data class PermissionStatus(
    val hasCallPhone: Boolean = false,
    val hasReadPhoneState: Boolean = false,
    val hasReadContacts: Boolean = false,
    val hasWriteContacts: Boolean = false,
    val hasReadCallLog: Boolean = false,
    val hasWriteCallLog: Boolean = false,
    val hasPostNotifications: Boolean = true,
    val isDefaultDialer: Boolean = false
) {
    val hasCoreTelephony: Boolean
        get() = hasCallPhone && hasReadPhoneState && hasReadContacts && hasReadCallLog
}

class PhoneViewModel(application: Application) : AndroidViewModel(application) {

    private val systemSource = SystemPhoneDataSource(application)
    private val repository: PhoneRepository
    private val dtmfPlayer = DtmfPlayer()

    init {
        val database = AppDatabase.getDatabase(application)
        repository = PhoneRepository(database.phoneDao(), database.callHistoryDao(), systemSource)
    }

    // --- Real Telecom & TelephonyManager State ---
    val telecomState: StateFlow<TelecomUiState> = CallManager.uiState
    val telephonyStatus: StateFlow<TelephonyStatus> = TelephonyMonitor.status

    private val _isConnectionAccountRegistered = MutableStateFlow(false)
    val isConnectionAccountRegistered: StateFlow<Boolean> = _isConnectionAccountRegistered.asStateFlow()

    private val _permissionStatus = MutableStateFlow(computePermissionStatus())
    val permissionStatus: StateFlow<PermissionStatus> = _permissionStatus.asStateFlow()

    private val _simAccounts = MutableStateFlow<List<SimAccountInfo>>(emptyList())
    val simAccounts: StateFlow<List<SimAccountInfo>> = _simAccounts.asStateFlow()

    private val _selectedSimIndex = MutableStateFlow(0)
    val selectedSimIndex: StateFlow<Int> = _selectedSimIndex.asStateFlow()

    private val _ussdDialogMessage = MutableStateFlow<Pair<String, String>?>(null)
    val ussdDialogMessage: StateFlow<Pair<String, String>?> = _ussdDialogMessage.asStateFlow()

    private val _isSyncingCallLogs = MutableStateFlow(false)
    val isSyncingCallLogs: StateFlow<Boolean> = _isSyncingCallLogs.asStateFlow()

    val lastCallSyncTimestamp: StateFlow<Long?> = repository.lastCallSyncTimestamp.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        null
    )

    private val _snackbarMessage = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val snackbarMessage: SharedFlow<String> = _snackbarMessage.asSharedFlow()

    init {
        viewModelScope.launch(Dispatchers.IO) {
            repository.observeAndSyncSystemCallLogs()
                .catch { /* Ignore transient provider errors */ }
                .collect {}
        }
        refreshSystemState()
    }

    fun refreshSystemState() {
        val ctx = getApplication<Application>()
        _permissionStatus.value = computePermissionStatus()
        PhoneConnectionService.registerPhoneAccount(ctx)
        _isConnectionAccountRegistered.value = PhoneConnectionService.isPhoneAccountRegistered(ctx)
        TelephonyMonitor.startMonitoring(ctx)
        TelephonyMonitor.refreshSnapshot(ctx)

        val sims = CallManager.getAvailableSimAccounts(ctx)
        _simAccounts.value = sims
        if (_selectedSimIndex.value >= sims.size) {
            _selectedSimIndex.value = 0
        }
        repository.triggerRefresh()
        syncCallHistoryNow(showFeedback = false)
    }

    fun syncCallHistoryNow(showFeedback: Boolean = true) {
        if (!permissionStatus.value.hasReadCallLog) {
            if (showFeedback) {
                _snackbarMessage.tryEmit("Grant Call Log permission to sync with system CallLog")
            }
            return
        }
        viewModelScope.launch {
            _isSyncingCallLogs.value = true
            try {
                val count = repository.syncCallHistoryNow()
                if (showFeedback) {
                    _snackbarMessage.tryEmit("Synced $count calls to Room database")
                }
            } finally {
                _isSyncingCallLogs.value = false
            }
        }
    }

    private fun computePermissionStatus(): PermissionStatus {
        val ctx = getApplication<Application>()
        val notificationsGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
        return PermissionStatus(
            hasCallPhone = ContextCompat.checkSelfPermission(ctx, Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED,
            hasReadPhoneState = ContextCompat.checkSelfPermission(ctx, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED,
            hasReadContacts = systemSource.hasReadContactsPermission(),
            hasWriteContacts = systemSource.hasWriteContactsPermission(),
            hasReadCallLog = systemSource.hasReadCallLogPermission(),
            hasWriteCallLog = systemSource.hasWriteCallLogPermission(),
            hasPostNotifications = notificationsGranted,
            isDefaultDialer = CallManager.isDefaultDialer(ctx)
        )
    }

    fun toggleSimSlot() {
        val sims = _simAccounts.value
        if (sims.size > 1) {
            _selectedSimIndex.value = (_selectedSimIndex.value + 1) % sims.size
        } else {
            _selectedSimIndex.value = if (_selectedSimIndex.value == 0) 1 else 0
        }
    }

    fun dismissUssdDialog() {
        _ussdDialogMessage.value = null
    }

    // --- Tab Selection ---
    private val _selectedTab = MutableStateFlow(PhoneTab.KEYPAD)
    val selectedTab: StateFlow<PhoneTab> = _selectedTab.asStateFlow()

    fun selectTab(tab: PhoneTab) {
        _selectedTab.value = tab
    }

    // --- Keypad State ---
    private val _dialedDigits = MutableStateFlow("")
    val dialedDigits: StateFlow<String> = _dialedDigits.asStateFlow()

    private val _formattedDigits = MutableStateFlow("")
    val formattedDigits: StateFlow<String> = _formattedDigits.asStateFlow()

    private val _t9Results = MutableStateFlow<List<T9Match>>(emptyList())
    val t9Results: StateFlow<List<T9Match>> = _t9Results.asStateFlow()

    private var lastDialedNumber: String = ""

    // Settings
    val keypadTonesEnabled = MutableStateFlow(true)
    val keypadHapticsEnabled = MutableStateFlow(true)

    // --- Recents State ---
    private val _recentsFilter = MutableStateFlow(RecentsFilter.ALL)
    val recentsFilter: StateFlow<RecentsFilter> = _recentsFilter.asStateFlow()

    private val _recentsSearchQuery = MutableStateFlow("")
    val recentsSearchQuery: StateFlow<String> = _recentsSearchQuery.asStateFlow()

    val allCallLogs: StateFlow<List<CallLogEntity>> = repository.allCallLogs.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        emptyList()
    )

    val missedCallLogs: StateFlow<List<CallLogEntity>> = repository.missedCallLogs.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        emptyList()
    )

    val filteredCallLogs: StateFlow<List<CallLogEntity>> = combine(
        allCallLogs,
        _recentsFilter,
        _recentsSearchQuery
    ) { logs, filter, query ->
        val byType = when (filter) {
            RecentsFilter.ALL -> logs
            RecentsFilter.MISSED -> logs.filter { it.callType == "MISSED" }
            RecentsFilter.INCOMING -> logs.filter { it.callType == "INCOMING" }
            RecentsFilter.OUTGOING -> logs.filter { it.callType == "OUTGOING" }
            RecentsFilter.BLOCKED -> logs.filter { it.callType == "BLOCKED" || it.callType == "REJECTED" }
        }
        if (query.isBlank()) {
            byType
        } else {
            val q = query.trim().lowercase()
            val qDigits = RustPhoneBridge.normalizePhoneNumber(query)
            byType.filter { log ->
                (log.contactName?.lowercase()?.contains(q) == true) ||
                    log.phoneNumber.lowercase().contains(q) ||
                    (qDigits.isNotEmpty() && RustPhoneBridge.normalizePhoneNumber(log.phoneNumber).contains(qDigits))
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _expandedLogId = MutableStateFlow<String?>(null)
    val expandedLogId: StateFlow<String?> = _expandedLogId.asStateFlow()

    fun toggleExpandLog(id: String) {
        _expandedLogId.value = if (_expandedLogId.value == id) null else id
    }

    fun setRecentsFilter(filter: RecentsFilter) {
        _recentsFilter.value = filter
    }

    fun setRecentsSearchQuery(query: String) {
        _recentsSearchQuery.value = query
    }

    // --- Contacts State ---
    val allContacts: StateFlow<List<ContactEntity>> = repository.allContacts.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        emptyList()
    )

    val favoriteContacts: StateFlow<List<ContactEntity>> = repository.favoriteContacts.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        emptyList()
    )

    val speedDials: StateFlow<List<SpeedDialEntity>> = repository.allSpeedDials.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        emptyList()
    )

    val blockedNumbers = repository.allBlockedNumbers.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        emptyList()
    )

    private val _contactSearchQuery = MutableStateFlow("")
    val contactSearchQuery: StateFlow<String> = _contactSearchQuery.asStateFlow()

    fun setContactSearchQuery(query: String) {
        _contactSearchQuery.value = query
    }

    val filteredContacts: StateFlow<List<ContactEntity>> = combine(
        allContacts,
        _contactSearchQuery
    ) { list, query ->
        if (query.isBlank()) {
            list
        } else {
            val q = query.trim().lowercase()
            val qDigits = RustPhoneBridge.normalizePhoneNumber(query)
            list.filter {
                it.name.lowercase().contains(q) ||
                    it.phoneNumber.lowercase().contains(q) ||
                    (qDigits.isNotEmpty() && RustPhoneBridge.normalizePhoneNumber(it.phoneNumber).contains(qDigits)) ||
                    it.email.lowercase().contains(q)
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // --- Modals and Dialogs ---
    private val _selectedContactDetail = MutableStateFlow<ContactEntity?>(null)
    val selectedContactDetail: StateFlow<ContactEntity?> = _selectedContactDetail.asStateFlow()

    fun openContactDetail(contact: ContactEntity) {
        _selectedContactDetail.value = contact
    }

    fun openContactDetailById(contactId: String) {
        val match = allContacts.value.firstOrNull { it.id == contactId }
        if (match != null) {
            _selectedContactDetail.value = match
        }
    }

    fun closeContactDetail() {
        _selectedContactDetail.value = null
    }

    private val _showCreateContactDialog = MutableStateFlow(false)
    val showCreateContactDialog: StateFlow<Boolean> = _showCreateContactDialog.asStateFlow()

    private val _prefilledNumberForContact = MutableStateFlow("")
    val prefilledNumberForContact: StateFlow<String> = _prefilledNumberForContact.asStateFlow()

    fun openCreateContact(prefilledNumber: String = "") {
        _prefilledNumberForContact.value = prefilledNumber
        _showCreateContactDialog.value = true
    }

    fun closeCreateContact() {
        _showCreateContactDialog.value = false
    }

    private val _showSpeedDialScreen = MutableStateFlow(false)
    val showSpeedDialScreen: StateFlow<Boolean> = _showSpeedDialScreen.asStateFlow()

    private val _speedDialTargetDigit = MutableStateFlow<Int?>(null)
    val speedDialTargetDigit: StateFlow<Int?> = _speedDialTargetDigit.asStateFlow()

    fun openSpeedDialDialog(targetDigit: Int? = null) {
        _speedDialTargetDigit.value = targetDigit?.takeIf { it in 2..9 }
        _showSpeedDialScreen.value = true
    }

    fun closeSpeedDialDialog() {
        _showSpeedDialScreen.value = false
        _speedDialTargetDigit.value = null
    }

    fun clearSpeedDialTargetDigit() {
        _speedDialTargetDigit.value = null
    }

    private val _showSettingsDialog = MutableStateFlow(false)
    val showSettingsDialog: StateFlow<Boolean> = _showSettingsDialog.asStateFlow()

    fun openSettingsDialog() {
        refreshSystemState()
        _showSettingsDialog.value = true
    }

    fun closeSettingsDialog() {
        _showSettingsDialog.value = false
    }

    // --- Call Statistics Dashboard State ---
    private val _showCallStatsScreen = MutableStateFlow(false)
    val showCallStatsScreen: StateFlow<Boolean> = _showCallStatsScreen.asStateFlow()

    private val _callStatsRange = MutableStateFlow(CallStatsRange.DAYS_7)
    val callStatsRange: StateFlow<CallStatsRange> = _callStatsRange.asStateFlow()

    private val _topContactSortMode = MutableStateFlow(TopContactSortMode.DURATION)
    val topContactSortMode: StateFlow<TopContactSortMode> = _topContactSortMode.asStateFlow()

    val callStatistics: StateFlow<CallStatisticsSnapshot> = combine(
        allCallLogs,
        allContacts,
        _callStatsRange
    ) { logs, contacts, range ->
        CallStatsEngine.compute(
            logs = logs,
            contacts = contacts,
            range = range
        )
    }
        .flowOn(Dispatchers.Default)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = CallStatsEngine.compute(emptyList(), emptyList(), CallStatsRange.DAYS_7)
        )

    fun openCallStatsScreen() {
        _showCallStatsScreen.value = true
    }

    fun closeCallStatsScreen() {
        _showCallStatsScreen.value = false
    }

    fun setCallStatsRange(range: CallStatsRange) {
        _callStatsRange.value = range
    }

    fun setTopContactSortMode(mode: TopContactSortMode) {
        _topContactSortMode.value = mode
    }

    // --- Keypad Actions ---

    fun onDigitPress(char: Char) {
        if (keypadTonesEnabled.value) {
            dtmfPlayer.playTone(char, 105)
        }
        updateDialedNumber(_dialedDigits.value + char)
    }

    fun onDigitLongPress(char: Char) {
        when (char) {
            '1' -> callVoicemail()
            '0' -> updateDialedNumber(_dialedDigits.value + '+')
            in '2'..'9' -> {
                val digit = char.digitToInt()
                viewModelScope.launch {
                    val assigned = speedDials.value.firstOrNull { it.keyDigit == digit }
                        ?: repository.getSpeedDial(digit)
                    if (assigned != null) {
                        _snackbarMessage.tryEmit("Speed dialing ${assigned.contactName} (Key $digit)")
                        startCall(assigned.phoneNumber)
                    } else {
                        _snackbarMessage.tryEmit("Assign a contact to speed dial key $digit")
                        openSpeedDialDialog(targetDigit = digit)
                    }
                }
            }
        }
    }

    fun onBackspace() {
        val current = _dialedDigits.value
        if (current.isNotEmpty()) {
            updateDialedNumber(current.substring(0, current.length - 1))
        }
    }

    fun onClearAllDigits() {
        updateDialedNumber("")
    }

    fun setDialedNumber(number: String) {
        updateDialedNumber(number)
    }

    fun handleCallButtonPress() {
        val current = _dialedDigits.value
        if (current.isNotBlank()) {
            startCall(current)
        } else {
            val redial = lastDialedNumber.ifBlank {
                allCallLogs.value.firstOrNull {
                    it.phoneNumber != "Private / Unknown" && it.phoneNumber.isNotBlank()
                }?.phoneNumber.orEmpty()
            }
            if (redial.isNotBlank()) {
                updateDialedNumber(redial)
            } else {
                _snackbarMessage.tryEmit("Enter a phone number to call")
            }
        }
    }

    private fun updateDialedNumber(number: String) {
        _dialedDigits.value = number
        _formattedDigits.value = RustPhoneBridge.formatPhoneNumber(number)
        triggerT9Search(number)
    }

    private fun triggerT9Search(digits: String) {
        viewModelScope.launch {
            if (digits.isBlank()) {
                _t9Results.value = emptyList()
            } else {
                _t9Results.value = repository.performT9Search(digits)
            }
        }
    }

    // --- Real Call Placement via TelecomManager / ConnectionService / TelephonyManager ---

    fun startCall(number: String = _dialedDigits.value) {
        val cleanNumber = number.trim().ifBlank { _dialedDigits.value.trim() }
        if (cleanNumber.isBlank()) return

        val selectedAccount = _simAccounts.value.getOrNull(_selectedSimIndex.value)

        // Direct TelephonyManager USSD request for carrier USSD codes when SIM is ready
        if (cleanNumber.startsWith("*") &&
            cleanNumber.endsWith("#") &&
            cleanNumber.length >= 3 &&
            cleanNumber != "*#06#" &&
            selectedAccount?.isConnectionServiceAccount != true &&
            telephonyStatus.value.isSimReady &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            permissionStatus.value.hasCallPhone
        ) {
            TelephonyMonitor.sendUssdCode(
                context = getApplication(),
                ussdCode = cleanNumber,
                onResponse = { response ->
                    _ussdDialogMessage.value = cleanNumber to response
                },
                onError = { errorMsg ->
                    _snackbarMessage.tryEmit(errorMsg)
                }
            )
            if (cleanNumber == _dialedDigits.value.trim()) {
                updateDialedNumber("")
            }
            return
        }

        lastDialedNumber = cleanNumber
        val matchedContact = allContacts.value.firstOrNull {
            RustPhoneBridge.normalizeForMatch(it.phoneNumber) == RustPhoneBridge.normalizeForMatch(cleanNumber)
        }
        when (val result = CallManager.placeRealCall(
            context = getApplication(),
            phoneNumber = cleanNumber,
            simAccount = selectedAccount,
            contactName = matchedContact?.name
        )) {
            is CallLaunchResult.PlacedViaTelecom,
            is CallLaunchResult.PlacedViaConnectionService,
            is CallLaunchResult.PlacedViaActionCall -> {
                if (!permissionStatus.value.hasReadCallLog && result !is CallLaunchResult.PlacedViaConnectionService) {
                    viewModelScope.launch {
                        repository.recordCallInHistory(
                            phoneNumber = cleanNumber,
                            contactName = matchedContact?.name,
                            callType = "OUTGOING",
                            simSlot = selectedAccount?.slotNumber ?: 1,
                            photoUri = matchedContact?.photoUri
                        )
                    }
                }
                if (cleanNumber == _dialedDigits.value.trim()) {
                    updateDialedNumber("")
                }
            }
            is CallLaunchResult.OpenedSystemDialer -> {
                _snackbarMessage.tryEmit("Grant Phone permission to place calls directly")
            }
            is CallLaunchResult.Error -> {
                _snackbarMessage.tryEmit(result.message)
            }
        }
    }

    fun callVoicemail() {
        val selectedHandle = _simAccounts.value.getOrNull(_selectedSimIndex.value)?.handle
        val vmNumber = CallManager.getVoicemailNumber(getApplication(), selectedHandle)
        if (!vmNumber.isNullOrBlank()) {
            startCall(vmNumber)
        } else {
            startCall("*86")
        }
    }

    fun sendMessage(phoneNumber: String) {
        val cleaned = phoneNumber.trim()
        if (cleaned.isBlank() || cleaned == "Private / Unknown") {
            _snackbarMessage.tryEmit("No valid phone number to message")
            return
        }
        try {
            val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:${Uri.encode(cleaned)}")).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            getApplication<Application>().startActivity(intent)
        } catch (_: Exception) {
            _snackbarMessage.tryEmit("No SMS messaging app found on this device")
        }
    }

    fun sendEmail(email: String) {
        val cleaned = email.trim()
        if (cleaned.isBlank()) return
        try {
            val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:${Uri.encode(cleaned)}")).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            getApplication<Application>().startActivity(intent)
        } catch (_: Exception) {
            _snackbarMessage.tryEmit("No email app found on this device")
        }
    }

    // --- Contact & Call Log CRUD ---

    fun saveContact(
        name: String,
        phoneNumber: String,
        email: String,
        phoneLabel: String,
        notes: String
    ) {
        viewModelScope.launch {
            repository.saveContact(
                name = name.trim(),
                phoneNumber = phoneNumber.trim(),
                email = email.trim(),
                phoneLabel = phoneLabel.ifBlank { "Mobile" },
                notes = notes.trim(),
                avatarColorHex = getRandomAvatarColor()
            )
            closeCreateContact()
            _snackbarMessage.tryEmit("Saved $name")
        }
    }

    fun toggleFavoriteContact(contact: ContactEntity) {
        viewModelScope.launch {
            repository.toggleFavorite(contact)
            if (_selectedContactDetail.value?.id == contact.id) {
                _selectedContactDetail.value = contact.copy(isFavorite = !contact.isFavorite)
            }
        }
    }

    fun deleteContact(contact: ContactEntity) {
        viewModelScope.launch {
            repository.deleteContact(contact)
            closeContactDetail()
            _snackbarMessage.tryEmit("Deleted ${contact.name}")
        }
    }

    fun deleteCallLog(log: CallLogEntity) {
        viewModelScope.launch {
            repository.deleteCallLog(log)
        }
    }

    fun clearAllCallHistory() {
        viewModelScope.launch {
            repository.clearAllCallLogs()
            _snackbarMessage.tryEmit("Call history cleared")
        }
    }

    fun assignSpeedDial(digit: Int, contact: ContactEntity) {
        viewModelScope.launch {
            repository.setSpeedDial(digit, contact)
            _speedDialTargetDigit.value = null
            _snackbarMessage.tryEmit("Assigned ${contact.name} to key $digit")
        }
    }

    fun assignCustomSpeedDial(digit: Int, name: String, phoneNumber: String) {
        viewModelScope.launch {
            repository.setSpeedDialCustom(digit, name, phoneNumber)
            _speedDialTargetDigit.value = null
            val display = name.trim().ifBlank { phoneNumber.trim() }
            _snackbarMessage.tryEmit("Assigned $display to key $digit")
        }
    }

    fun removeSpeedDial(digit: Int) {
        viewModelScope.launch {
            repository.removeSpeedDial(digit)
            _snackbarMessage.tryEmit("Cleared speed dial key $digit")
        }
    }

    fun clearAllSpeedDials() {
        viewModelScope.launch {
            repository.clearAllSpeedDials()
            _snackbarMessage.tryEmit("Cleared all speed dial keys")
        }
    }

    fun blockNumber(phone: String, label: String = "Blocked Number") {
        viewModelScope.launch {
            repository.blockNumber(phone, label)
            _snackbarMessage.tryEmit("Blocked $phone")
        }
    }

    fun unblockNumber(phone: String) {
        viewModelScope.launch {
            repository.unblockNumber(phone)
            _snackbarMessage.tryEmit("Unblocked $phone")
        }
    }

    private fun getRandomAvatarColor(): String {
        val colors = listOf("#27C064", "#2E88FF", "#8B5CF6", "#F59E0B", "#EC4899", "#06B6D4")
        return colors.random()
    }

    override fun onCleared() {
        super.onCleared()
        TelephonyMonitor.stopMonitoring(getApplication())
        dtmfPlayer.release()
    }
}
