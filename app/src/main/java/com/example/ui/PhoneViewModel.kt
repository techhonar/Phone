package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AppDatabase
import com.example.data.CallLogEntity
import com.example.data.ContactEntity
import com.example.data.PhoneRepository
import com.example.data.SpeedDialEntity
import com.example.rust.DtmfPlayer
import com.example.rust.RustPhoneBridge
import com.example.rust.T9Match
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

enum class PhoneTab {
    KEYPAD, RECENTS, CONTACTS, PLACES
}

enum class RecentsFilter {
    ALL, MISSED, REJECTED, OUTGOING, INCOMING, VOICEMAIL
}

sealed class ActiveCallState {
    object Idle : ActiveCallState()
    data class Calling(val number: String, val name: String?, val simSlot: Int = 1) : ActiveCallState()
    data class Connected(
        val number: String,
        val name: String?,
        val seconds: Long = 0,
        val isMuted: Boolean = false,
        val isSpeaker: Boolean = false,
        val isHeld: Boolean = false,
        val isRecording: Boolean = false,
        val simSlot: Int = 1
    ) : ActiveCallState()
    data class Ended(
        val number: String,
        val name: String?,
        val totalSeconds: Long,
        val reason: String
    ) : ActiveCallState()
}

class PhoneViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: PhoneRepository
    private val dtmfPlayer = DtmfPlayer()

    init {
        val database = AppDatabase.getDatabase(application, viewModelScope)
        repository = PhoneRepository(database.phoneDao())
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

    private val _simSlot = MutableStateFlow(1) // 1 or 2
    val simSlot: StateFlow<Int> = _simSlot.asStateFlow()

    fun toggleSimSlot() {
        _simSlot.value = if (_simSlot.value == 1) 2 else 1
    }

    // Settings
    val keypadTonesEnabled = MutableStateFlow(true)
    val keypadHapticsEnabled = MutableStateFlow(true)

    // --- Active Call State ---
    private val _callState = MutableStateFlow<ActiveCallState>(ActiveCallState.Idle)
    val callState: StateFlow<ActiveCallState> = _callState.asStateFlow()
    private var callTimerJob: Job? = null

    // --- Recents State ---
    private val _recentsFilter = MutableStateFlow(RecentsFilter.ALL)
    val recentsFilter: StateFlow<RecentsFilter> = _recentsFilter.asStateFlow()

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

    private val _expandedLogId = MutableStateFlow<String?>(null)
    val expandedLogId: StateFlow<String?> = _expandedLogId.asStateFlow()

    fun toggleExpandLog(id: String) {
        _expandedLogId.value = if (_expandedLogId.value == id) null else id
    }

    fun setRecentsFilter(filter: RecentsFilter) {
        _recentsFilter.value = filter
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
            list.filter {
                it.name.lowercase().contains(q) ||
                it.phoneNumber.contains(q) ||
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

    fun closeContactDetail() {
        _selectedContactDetail.value = null
    }

    private val _showCreateContactDialog = MutableStateFlow(false)
    val showCreateContactDialog: StateFlow<Boolean> = _showCreateContactDialog.asStateFlow()

    fun openCreateContact(prefilledNumber: String = "") {
        _prefilledNumberForContact.value = prefilledNumber
        _showCreateContactDialog.value = true
    }

    fun closeCreateContact() {
        _showCreateContactDialog.value = false
    }

    private val _prefilledNumberForContact = MutableStateFlow("")
    val prefilledNumberForContact: StateFlow<String> = _prefilledNumberForContact.asStateFlow()

    private val _showSpeedDialDialog = MutableStateFlow(false)
    val showSpeedDialDialog: StateFlow<Boolean> = _showSpeedDialDialog.asStateFlow()

    fun openSpeedDialDialog() {
        _showSpeedDialDialog.value = true
    }

    fun closeSpeedDialDialog() {
        _showSpeedDialDialog.value = false
    }

    private val _showSettingsDialog = MutableStateFlow(false)
    val showSettingsDialog: StateFlow<Boolean> = _showSettingsDialog.asStateFlow()

    fun openSettingsDialog() {
        _showSettingsDialog.value = true
    }

    fun closeSettingsDialog() {
        _showSettingsDialog.value = false
    }

    // --- Keypad Actions ---

    fun onDigitPress(char: Char) {
        if (keypadTonesEnabled.value) {
            dtmfPlayer.playTone(char, 110)
        }
        val current = _dialedDigits.value + char
        updateDialedNumber(current)
    }

    fun onDigitLongPress(char: Char) {
        when (char) {
            '1' -> {
                // Call Voicemail
                startCall("1", "Voicemail")
            }
            '0' -> {
                // Append '+'
                val current = _dialedDigits.value + '+'
                updateDialedNumber(current)
            }
            in '2'..'9' -> {
                val digit = char.digitToInt()
                viewModelScope.launch {
                    val assigned = repository.getSpeedDial(digit)
                    if (assigned != null) {
                        startCall(assigned.phoneNumber, assigned.contactName)
                    } else {
                        // Open speed dial config dialog
                        _showSpeedDialDialog.value = true
                    }
                }
            }
        }
    }

    fun onBackspace() {
        val current = _dialedDigits.value
        if (current.isNotEmpty()) {
            val updated = current.substring(0, current.length - 1)
            updateDialedNumber(updated)
        }
    }

    fun onClearAllDigits() {
        updateDialedNumber("")
    }

    fun setDialedNumber(number: String) {
        updateDialedNumber(number)
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

    // --- Call Management ---

    fun startCall(number: String = _dialedDigits.value, contactName: String? = null) {
        val cleanNumber = number.ifBlank { _dialedDigits.value }
        if (cleanNumber.isBlank()) return

        val targetName = contactName ?: findContactName(cleanNumber)
        _callState.value = ActiveCallState.Calling(cleanNumber, targetName, _simSlot.value)

        // Connect simulated call after 2 seconds
        callTimerJob?.cancel()
        callTimerJob = viewModelScope.launch {
            delay(1800)
            if (_callState.value is ActiveCallState.Calling) {
                _callState.value = ActiveCallState.Connected(
                    number = cleanNumber,
                    name = targetName,
                    seconds = 0,
                    simSlot = _simSlot.value
                )
                startCallTicker()
            }
        }
    }

    private fun startCallTicker() {
        viewModelScope.launch {
            while (isActive) {
                delay(1000)
                val current = _callState.value
                if (current is ActiveCallState.Connected) {
                    if (!current.isHeld) {
                        _callState.value = current.copy(seconds = current.seconds + 1)
                    }
                } else {
                    break
                }
            }
        }
    }

    private fun findContactName(phone: String): String? {
        val contacts = allContacts.value
        val norm = RustPhoneBridge.normalizePhoneNumber(phone)
        val match = contacts.firstOrNull { RustPhoneBridge.normalizePhoneNumber(it.phoneNumber) == norm }
        return match?.name
    }

    fun toggleMute() {
        val current = _callState.value
        if (current is ActiveCallState.Connected) {
            _callState.value = current.copy(isMuted = !current.isMuted)
        }
    }

    fun toggleSpeaker() {
        val current = _callState.value
        if (current is ActiveCallState.Connected) {
            _callState.value = current.copy(isSpeaker = !current.isSpeaker)
        }
    }

    fun toggleHold() {
        val current = _callState.value
        if (current is ActiveCallState.Connected) {
            _callState.value = current.copy(isHeld = !current.isHeld)
        }
    }

    fun toggleRecord() {
        val current = _callState.value
        if (current is ActiveCallState.Connected) {
            _callState.value = current.copy(isRecording = !current.isRecording)
        }
    }

    fun sendInCallDtmf(key: Char) {
        if (keypadTonesEnabled.value) {
            dtmfPlayer.playTone(key, 120)
        }
    }

    fun endCall() {
        callTimerJob?.cancel()
        val current = _callState.value
        val (number, name, duration, reason) = when (current) {
            is ActiveCallState.Connected -> {
                Quadruple(current.number, current.name, current.seconds, "Call Ended")
            }
            is ActiveCallState.Calling -> {
                Quadruple(current.number, current.name, 0L, "Cancelled")
            }
            else -> return
        }

        _callState.value = ActiveCallState.Ended(number, name, duration, reason)

        viewModelScope.launch {
            // Log to Room Database
            repository.addCallLog(
                phoneNumber = number,
                contactName = name,
                callType = if (duration > 0) "OUTGOING" else "CANCELLED",
                durationSeconds = duration,
                simSlot = _simSlot.value
            )
            delay(1200)
            _callState.value = ActiveCallState.Idle
        }
    }

    // --- Contact CRUD ---

    fun saveContact(
        name: String,
        phoneNumber: String,
        email: String,
        group: String,
        notes: String
    ) {
        viewModelScope.launch {
            val newContact = ContactEntity(
                name = name,
                phoneNumber = phoneNumber,
                email = email,
                groupName = group.ifBlank { "Personal" },
                notes = notes,
                avatarColorHex = getRandomAvatarColor()
            )
            repository.insertContact(newContact)
            closeCreateContact()
        }
    }

    fun toggleFavoriteContact(contact: ContactEntity) {
        viewModelScope.launch {
            repository.toggleFavorite(contact.id, !contact.isFavorite)
        }
    }

    fun deleteContact(id: String) {
        viewModelScope.launch {
            repository.deleteContact(id)
            closeContactDetail()
        }
    }

    fun deleteCallLog(id: String) {
        viewModelScope.launch {
            repository.deleteCallLog(id)
        }
    }

    fun deleteCallLogs(ids: Collection<String>) {
        viewModelScope.launch {
            ids.forEach { repository.deleteCallLog(it) }
        }
        _expandedLogId.value = null
    }

    fun deleteContacts(ids: Collection<String>) {
        viewModelScope.launch {
            ids.forEach { repository.deleteContact(it) }
        }
        closeContactDetail()
    }

    fun clearAllCallHistory() {
        viewModelScope.launch {
            repository.clearAllCallLogs()
        }
    }

    fun assignSpeedDial(digit: Int, contact: ContactEntity) {
        viewModelScope.launch {
            repository.setSpeedDial(digit, contact)
        }
    }

    fun removeSpeedDial(digit: Int) {
        viewModelScope.launch {
            repository.removeSpeedDial(digit)
        }
    }

    fun blockNumber(phone: String, label: String) {
        viewModelScope.launch {
            repository.blockNumber(phone, label)
        }
    }

    fun unblockNumber(phone: String) {
        viewModelScope.launch {
            repository.unblockNumber(phone)
        }
    }

    private fun getRandomAvatarColor(): String {
        val colors = listOf("#27C064", "#2E88FF", "#9C27B0", "#FF9800", "#E91E63", "#00BCD4", "#4CAF50")
        return colors.random()
    }

    override fun onCleared() {
        super.onCleared()
        dtmfPlayer.release()
    }
}

data class Quadruple<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)
