package com.example.telecom

import android.Manifest
import android.annotation.SuppressLint
import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.ContactsContract
import android.telecom.Call
import android.telecom.CallAudioState
import android.telecom.Connection
import android.telecom.InCallService
import android.telecom.PhoneAccountHandle
import android.telecom.TelecomManager
import android.telecom.VideoProfile
import android.util.Log
import androidx.core.content.ContextCompat
import com.example.InCallActivity
import com.example.data.AppDatabase
import com.example.data.CallLogEntity
import com.example.data.SystemPhoneDataSource
import com.example.rust.RustPhoneBridge
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

enum class CallLifecycleState {
    IDLE,
    CONNECTING,
    DIALING,
    RINGING,
    ACTIVE,
    HOLDING,
    DISCONNECTING,
    DISCONNECTED
}

enum class AudioRoute {
    EARPIECE,
    SPEAKER,
    BLUETOOTH,
    WIRED_HEADSET
}

data class SimAccountInfo(
    val handle: PhoneAccountHandle,
    val label: String,
    val slotNumber: Int,
    val isConnectionServiceAccount: Boolean = false
)

data class CallUiModel(
    val id: String,
    val number: String,
    val displayName: String?,
    val photoUri: String? = null,
    val state: CallLifecycleState,
    val connectTimeMillis: Long = 0L,
    val isIncoming: Boolean = false,
    val disconnectReason: String? = null,
    val canHold: Boolean = false,
    val canMerge: Boolean = false,
    val canSwap: Boolean = false,
    val simLabel: String? = null
)

data class AudioRouteState(
    val isMuted: Boolean = false,
    val currentRoute: AudioRoute = AudioRoute.EARPIECE,
    val hasBluetooth: Boolean = false,
    val hasWiredHeadset: Boolean = false
)

data class TelecomUiState(
    val calls: List<CallUiModel> = emptyList(),
    val primaryCall: CallUiModel? = null,
    val secondaryCall: CallUiModel? = null,
    val audioState: AudioRouteState = AudioRouteState(),
    val isInCallServiceConnected: Boolean = false,
    val activeConnectionCount: Int = 0
)

sealed class CallLaunchResult {
    object PlacedViaTelecom : CallLaunchResult()
    object PlacedViaConnectionService : CallLaunchResult()
    object PlacedViaActionCall : CallLaunchResult()
    object OpenedSystemDialer : CallLaunchResult()
    data class Error(val message: String) : CallLaunchResult()
}

/**
 * Central manager connecting Android's real Telecom `InCallService` AND
 * `PhoneConnectionService` (`ConnectionService`) + `TelephonyMonitor` with Compose UI.
 */
@Suppress("DEPRECATION")
object CallManager {
    private const val TAG = "CallManager"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private var appContext: Context? = null
    private var inCallService: InCallService? = null
    private val activeCalls = linkedMapOf<String, Call>()
    private val activeConnections = linkedMapOf<String, PhoneConnection>()
    private val connectionStartTimes = mutableMapOf<String, Long>()
    private val resolvedCallerCache = mutableMapOf<String, Pair<String?, String?>>() // number -> (name, photoUri)

    private val _uiState = MutableStateFlow(TelecomUiState())
    val uiState: StateFlow<TelecomUiState> = _uiState.asStateFlow()

    fun attachService(service: InCallService) {
        appContext = service.applicationContext
        inCallService = service
        syncAudioState(service.callAudioState)
        emitState()
    }

    fun detachService(service: InCallService) {
        if (inCallService === service) {
            inCallService = null
            activeCalls.clear()
            emitState()
        }
    }

    // --- InCallService Call Callbacks ---

    fun onCallAdded(context: Context, call: Call) {
        appContext = context.applicationContext
        val id = callId(call)
        activeCalls[id] = call

        val number = extractNumber(call)
        resolveCallerAsync(context, number)
        emitState()
    }

    fun onCallStateChanged(call: Call) {
        emitState()
        if (call.state == Call.STATE_DISCONNECTED) {
            val id = callId(call)
            scope.launch {
                delay(1400)
                activeCalls.remove(id)
                emitState()
            }
        }
    }

    fun onCallRemoved(call: Call) {
        val ctx = appContext
        if (ctx != null) {
            persistInCallRemovalFallback(ctx, call)
        }
        val id = callId(call)
        if (call.state != Call.STATE_DISCONNECTED) {
            activeCalls.remove(id)
            emitState()
        } else {
            scope.launch {
                delay(1200)
                activeCalls.remove(id)
                emitState()
            }
        }
    }

    // --- ConnectionService Connection Callbacks ---

    fun onConnectionAdded(context: Context, connection: PhoneConnection) {
        appContext = context.applicationContext
        activeConnections[connection.connectionId] = connection
        connectionStartTimes[connection.connectionId] = System.currentTimeMillis()
        resolveCallerAsync(context, connection.number)
        emitState()
    }

    fun onConnectionUpdated(connection: PhoneConnection) {
        if (activeConnections.containsKey(connection.connectionId)) {
            emitState()
        }
    }

    fun onConnectionRemoved(connection: PhoneConnection, wasRejected: Boolean = false) {
        activeConnections.remove(connection.connectionId)
        val createdAt = connectionStartTimes.remove(connection.connectionId) ?: System.currentTimeMillis()
        appContext?.let { ctx ->
            persistConnectionRecord(ctx, connection, createdAt, wasRejected)
        }
        emitState()
    }

    private fun persistConnectionRecord(
        context: Context,
        connection: PhoneConnection,
        createdAt: Long,
        wasRejected: Boolean
    ) {
        val rawNumber = connection.number.trim()
        if (rawNumber.isBlank()) return
        val connectTime = connection.connectTimeMillis
        val durationSec = if (connectTime > 0L) {
            ((System.currentTimeMillis() - connectTime) / 1000L).coerceAtLeast(0L)
        } else 0L
        val callType = when {
            wasRejected -> "REJECTED"
            connection.isIncoming && connectTime == 0L -> "MISSED"
            connection.isIncoming -> "INCOMING"
            else -> "OUTGOING"
        }
        val timestamp = if (connectTime > 0L) connectTime else createdAt
        val cached = resolvedCallerCache[rawNumber]
        val displayName = cached?.first ?: connection.initialDisplayName?.takeIf { it.isNotBlank() }
        val photoUri = cached?.second

        scope.launch(Dispatchers.IO) {
            try {
                val systemSource = SystemPhoneDataSource(context)
                val systemId = systemSource.insertSystemCallLog(
                    phoneNumber = rawNumber,
                    callType = callType,
                    timestamp = timestamp,
                    durationSeconds = durationSec
                )
                val formatted = RustPhoneBridge.formatPhoneNumber(rawNumber)
                val normalized = RustPhoneBridge.normalizeForMatch(rawNumber)
                AppDatabase.getDatabase(context).callHistoryDao().insertCallRecord(
                    CallLogEntity(
                        id = systemId?.let { "sys_$it" } ?: java.util.UUID.randomUUID().toString(),
                        systemLogId = systemId,
                        phoneNumber = formatted,
                        normalizedNumber = normalized,
                        contactName = displayName,
                        callType = callType,
                        timestamp = timestamp,
                        durationSeconds = durationSec,
                        simSlot = 1,
                        photoUri = photoUri,
                        isSystemLog = systemId != null
                    )
                )
            } catch (e: Exception) {
                Log.w(TAG, "Failed to persist connection call record", e)
            }
        }
    }

    private fun persistInCallRemovalFallback(context: Context, call: Call) {
        val rawNumber = extractNumber(call).trim()
        if (rawNumber.isBlank()) return
        scope.launch(Dispatchers.IO) {
            try {
                val systemSource = SystemPhoneDataSource(context)
                if (systemSource.hasReadCallLogPermission()) {
                    // System CallLog provider logs cellular InCallService calls natively; sync immediately
                    delay(450)
                    val systemLogs = systemSource.querySystemCallLogs(SystemPhoneDataSource.DEFAULT_CALL_LOG_LIMIT)
                    AppDatabase.getDatabase(context).callHistoryDao().syncWithSystemCallLogs(
                        systemLogs = systemLogs,
                        completeSnapshot = systemLogs.size < SystemPhoneDataSource.DEFAULT_CALL_LOG_LIMIT
                    )
                } else {
                    val details = call.details
                    val connectTime = details?.connectTimeMillis ?: 0L
                    val creationTime = details?.creationTimeMillis?.takeIf { it > 0L } ?: System.currentTimeMillis()
                    val durationSec = if (connectTime > 0L) {
                        ((System.currentTimeMillis() - connectTime) / 1000L).coerceAtLeast(0L)
                    } else 0L
                    val isIncoming = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        details?.callDirection == Call.Details.DIRECTION_INCOMING
                    } else {
                        false
                    }
                    val callType = when {
                        isIncoming && connectTime == 0L -> "MISSED"
                        isIncoming -> "INCOMING"
                        else -> "OUTGOING"
                    }
                    val cached = resolvedCallerCache[rawNumber]
                    val displayName = cached?.first
                        ?: details?.callerDisplayName?.takeIf { it.isNotBlank() }
                        ?: details?.contactDisplayName?.takeIf { it.isNotBlank() }
                    AppDatabase.getDatabase(context).callHistoryDao().insertCallRecord(
                        CallLogEntity(
                            phoneNumber = RustPhoneBridge.formatPhoneNumber(rawNumber),
                            normalizedNumber = RustPhoneBridge.normalizeForMatch(rawNumber),
                            contactName = displayName,
                            callType = callType,
                            timestamp = if (connectTime > 0L) connectTime else creationTime,
                            durationSeconds = durationSec,
                            photoUri = cached?.second ?: details?.contactPhotoUri?.toString(),
                            isSystemLog = false
                        )
                    )
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to sync/persist InCallService record", e)
            }
        }
    }

    private fun resolveCallerAsync(context: Context, number: String) {
        if (number.isNotBlank() && !resolvedCallerCache.containsKey(number)) {
            scope.launch(Dispatchers.IO) {
                val info = lookupCallerInfo(context, number)
                if (info.first != null || info.second != null) {
                    resolvedCallerCache[number] = info
                    scope.launch(Dispatchers.Main) { emitState() }
                }
            }
        }
    }

    fun syncAudioState(audioState: CallAudioState?) {
        if (audioState == null) return
        val route = when (audioState.route) {
            CallAudioState.ROUTE_SPEAKER -> AudioRoute.SPEAKER
            CallAudioState.ROUTE_BLUETOOTH -> AudioRoute.BLUETOOTH
            CallAudioState.ROUTE_WIRED_HEADSET -> AudioRoute.WIRED_HEADSET
            else -> AudioRoute.EARPIECE
        }
        val mask = audioState.supportedRouteMask
        val newAudio = AudioRouteState(
            isMuted = audioState.isMuted,
            currentRoute = route,
            hasBluetooth = (mask and CallAudioState.ROUTE_BLUETOOTH) != 0,
            hasWiredHeadset = (mask and CallAudioState.ROUTE_WIRED_HEADSET) != 0
        )
        _uiState.value = _uiState.value.copy(audioState = newAudio)
    }

    private fun emitState() {
        val callModels = activeCalls.entries.map { (id, call) -> toUiModel(id, call) }
        val connModels = activeConnections.entries.map { (id, conn) -> connectionToUiModel(id, conn) }
        val models = callModels + connModels

        val primary = models.firstOrNull { it.state == CallLifecycleState.RINGING }
            ?: models.firstOrNull { it.state == CallLifecycleState.ACTIVE }
            ?: models.firstOrNull { it.state == CallLifecycleState.DIALING || it.state == CallLifecycleState.CONNECTING }
            ?: models.firstOrNull()
        val secondary = models.firstOrNull { it.id != primary?.id }

        _uiState.value = _uiState.value.copy(
            calls = models,
            primaryCall = primary,
            secondaryCall = secondary,
            isInCallServiceConnected = inCallService != null,
            activeConnectionCount = activeConnections.size
        )

        if (models.isEmpty()) {
            resetAudioHardwareState()
        }
    }

    private fun resetAudioHardwareState() {
        val ctx = appContext ?: return
        try {
            val am = ctx.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
            am.isMicrophoneMute = false
            am.isSpeakerphoneOn = false
            am.mode = AudioManager.MODE_NORMAL
        } catch (_: Exception) {
        }
        _uiState.value = _uiState.value.copy(audioState = AudioRouteState())
    }

    private fun toUiModel(id: String, call: Call): CallUiModel {
        val details = call.details
        val rawNumber = extractNumber(call)
        val formattedNumber = if (rawNumber.isBlank()) "Private / Unknown" else RustPhoneBridge.formatPhoneNumber(rawNumber)
        val cached = resolvedCallerCache[rawNumber]
        val callerDisplayName = cached?.first
            ?: details?.callerDisplayName?.takeIf { it.isNotBlank() }
            ?: details?.contactDisplayName?.takeIf { it.isNotBlank() }
        val photoUri = cached?.second ?: details?.contactPhotoUri?.toString()

        val lifecycle = when (call.state) {
            Call.STATE_RINGING -> CallLifecycleState.RINGING
            Call.STATE_DIALING -> CallLifecycleState.DIALING
            Call.STATE_CONNECTING, Call.STATE_SELECT_PHONE_ACCOUNT -> CallLifecycleState.CONNECTING
            Call.STATE_ACTIVE -> CallLifecycleState.ACTIVE
            Call.STATE_HOLDING -> CallLifecycleState.HOLDING
            Call.STATE_DISCONNECTING -> CallLifecycleState.DISCONNECTING
            Call.STATE_DISCONNECTED -> CallLifecycleState.DISCONNECTED
            else -> CallLifecycleState.IDLE
        }

        val caps = details?.callCapabilities ?: 0
        val canHold = (caps and Call.Details.CAPABILITY_HOLD) != 0 || (caps and Call.Details.CAPABILITY_SUPPORT_HOLD) != 0
        val canMerge = (caps and Call.Details.CAPABILITY_MERGE_CONFERENCE) != 0
        val canSwap = (caps and Call.Details.CAPABILITY_SWAP_CONFERENCE) != 0 || activeCalls.size > 1

        val isIncoming = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            details?.callDirection == Call.Details.DIRECTION_INCOMING
        } else {
            call.state == Call.STATE_RINGING
        }

        val disconnectCause = details?.disconnectCause?.label?.toString()
            ?: details?.disconnectCause?.reason?.takeIf { it.isNotBlank() }

        val carrierSim = TelephonyMonitor.status.value.carrierName.takeIf { it.isNotBlank() }
            ?: details?.accountHandle?.id?.takeIf { it.length <= 12 }

        return CallUiModel(
            id = id,
            number = formattedNumber,
            displayName = callerDisplayName,
            photoUri = photoUri,
            state = lifecycle,
            connectTimeMillis = details?.connectTimeMillis ?: 0L,
            isIncoming = isIncoming,
            disconnectReason = disconnectCause,
            canHold = canHold,
            canMerge = canMerge,
            canSwap = canSwap,
            simLabel = carrierSim
        )
    }

    private fun connectionToUiModel(id: String, conn: PhoneConnection): CallUiModel {
        val formattedNumber = if (conn.number.isBlank()) "Private / Unknown" else RustPhoneBridge.formatPhoneNumber(conn.number)
        val cached = resolvedCallerCache[conn.number]
        val displayName = cached?.first ?: conn.initialDisplayName?.takeIf { it.isNotBlank() }
        val photoUri = cached?.second

        val lifecycle = when (conn.state) {
            Connection.STATE_RINGING -> CallLifecycleState.RINGING
            Connection.STATE_DIALING -> CallLifecycleState.DIALING
            Connection.STATE_INITIALIZING, Connection.STATE_NEW -> CallLifecycleState.CONNECTING
            Connection.STATE_ACTIVE -> CallLifecycleState.ACTIVE
            Connection.STATE_HOLDING -> CallLifecycleState.HOLDING
            Connection.STATE_DISCONNECTED -> CallLifecycleState.DISCONNECTED
            else -> CallLifecycleState.IDLE
        }

        return CallUiModel(
            id = id,
            number = formattedNumber,
            displayName = displayName,
            photoUri = photoUri,
            state = lifecycle,
            connectTimeMillis = conn.connectTimeMillis,
            isIncoming = conn.isIncoming,
            canHold = true,
            canMerge = false,
            canSwap = activeConnections.size > 1,
            simLabel = "ConnectionService"
        )
    }

    // --- Unified Call Control Actions (InCallService Call OR ConnectionService PhoneConnection) ---

    fun answerCall(callId: String? = null) {
        val id = callId ?: _uiState.value.primaryCall?.id
        activeCalls[id]?.let {
            it.answer(VideoProfile.STATE_AUDIO_ONLY)
            return
        }
        activeConnections[id]?.onAnswer(VideoProfile.STATE_AUDIO_ONLY)
    }

    fun rejectCall(callId: String? = null, message: String? = null) {
        val id = callId ?: _uiState.value.primaryCall?.id
        activeCalls[id]?.let { target ->
            if (target.state == Call.STATE_RINGING) {
                target.reject(!message.isNullOrBlank(), message)
            } else {
                target.disconnect()
            }
            return
        }
        activeConnections[id]?.let { conn ->
            if (conn.state == Connection.STATE_RINGING) {
                conn.onReject(message)
            } else {
                conn.onDisconnect()
            }
        }
    }

    fun disconnectCall(callId: String? = null) {
        val id = callId ?: _uiState.value.primaryCall?.id
        activeCalls[id]?.let { target ->
            if (target.state == Call.STATE_RINGING) {
                target.reject(false, null)
            } else {
                target.disconnect()
            }
            return
        }
        activeConnections[id]?.let { conn ->
            if (conn.state == Connection.STATE_RINGING) {
                conn.onReject(null)
            } else {
                conn.onDisconnect()
            }
        }
    }

    fun toggleHold(callId: String? = null) {
        val id = callId ?: _uiState.value.primaryCall?.id
        activeCalls[id]?.let { target ->
            if (target.state == Call.STATE_HOLDING) target.unhold()
            else if (target.state == Call.STATE_ACTIVE) target.hold()
            return
        }
        activeConnections[id]?.let { conn ->
            if (conn.state == Connection.STATE_HOLDING) conn.onUnhold()
            else if (conn.state == Connection.STATE_ACTIVE) conn.onHold()
        }
    }

    fun swapCalls() {
        val calls = activeCalls.values.toList()
        if (calls.size >= 2) {
            val held = calls.firstOrNull { it.state == Call.STATE_HOLDING }
            val active = calls.firstOrNull { it.state == Call.STATE_ACTIVE }
            active?.hold()
            held?.unhold()
            return
        }
        val conns = activeConnections.values.toList()
        if (conns.size >= 2) {
            val held = conns.firstOrNull { it.state == Connection.STATE_HOLDING }
            val active = conns.firstOrNull { it.state == Connection.STATE_ACTIVE }
            active?.onHold()
            held?.onUnhold()
        }
    }

    fun mergeCalls() {
        val calls = activeCalls.values.toList()
        if (calls.size < 2) return
        calls[0].conference(calls[1])
    }

    fun toggleMute() {
        val nextMuted = !_uiState.value.audioState.isMuted
        inCallService?.setMuted(nextMuted)
        if (activeConnections.isNotEmpty() || inCallService == null) {
            try {
                val am = appContext?.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
                am?.isMicrophoneMute = nextMuted
            } catch (_: Exception) {
            }
        }
        _uiState.value = _uiState.value.copy(
            audioState = _uiState.value.audioState.copy(isMuted = nextMuted)
        )
    }

    fun setAudioRoute(route: AudioRoute) {
        val telecomRoute = when (route) {
            AudioRoute.SPEAKER -> CallAudioState.ROUTE_SPEAKER
            AudioRoute.BLUETOOTH -> CallAudioState.ROUTE_BLUETOOTH
            AudioRoute.WIRED_HEADSET -> CallAudioState.ROUTE_WIRED_HEADSET
            AudioRoute.EARPIECE -> CallAudioState.ROUTE_EARPIECE
        }
        inCallService?.setAudioRoute(telecomRoute)
        val primaryId = _uiState.value.primaryCall?.id
        activeConnections[primaryId]?.let { conn ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                conn.setAudioRoute(telecomRoute)
            }
        }
        if (activeConnections.isNotEmpty() || inCallService == null) {
            try {
                val am = appContext?.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
                if (am != null) {
                    am.mode = AudioManager.MODE_IN_COMMUNICATION
                    am.isSpeakerphoneOn = (route == AudioRoute.SPEAKER)
                }
            } catch (_: Exception) {
            }
        }
        _uiState.value = _uiState.value.copy(
            audioState = _uiState.value.audioState.copy(currentRoute = route)
        )
    }

    fun toggleSpeaker() {
        val current = _uiState.value.audioState.currentRoute
        if (current == AudioRoute.SPEAKER) {
            val fallback = when {
                _uiState.value.audioState.hasBluetooth -> AudioRoute.BLUETOOTH
                _uiState.value.audioState.hasWiredHeadset -> AudioRoute.WIRED_HEADSET
                else -> AudioRoute.EARPIECE
            }
            setAudioRoute(fallback)
        } else {
            setAudioRoute(AudioRoute.SPEAKER)
        }
    }

    fun playInCallDtmf(digit: Char) {
        val id = _uiState.value.primaryCall?.id
        activeCalls[id]?.let { target ->
            target.playDtmfTone(digit)
            scope.launch {
                delay(180)
                target.stopDtmfTone()
            }
            return
        }
        activeConnections[id]?.let { conn ->
            conn.onPlayDtmfTone(digit)
            scope.launch {
                delay(180)
                conn.onStopDtmfTone()
            }
        }
    }

    fun launchInCallUi(context: Context) {
        val intent = Intent(context, InCallActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_SINGLE_TOP or
                Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
        }
        context.startActivity(intent)
    }

    fun callId(call: Call): String = Integer.toHexString(System.identityHashCode(call))

    fun extractNumber(call: Call): String = call.details?.handle?.schemeSpecificPart ?: ""

    @SuppressLint("Range")
    private suspend fun lookupCallerInfo(context: Context, number: String): Pair<String?, String?> {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED) {
            try {
                val uri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(number))
                val projection = arrayOf(
                    ContactsContract.PhoneLookup.DISPLAY_NAME,
                    ContactsContract.PhoneLookup.PHOTO_URI
                )
                context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val name = cursor.getString(0)
                        val photo = cursor.getString(1)
                        if (!name.isNullOrBlank() || !photo.isNullOrBlank()) {
                            return name to photo
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "PhoneLookup failed", e)
            }
        }
        // Fallback: lookup saved contacts in Room database
        return try {
            val targetNormalized = RustPhoneBridge.normalizeForMatch(number)
            if (targetNormalized.isEmpty()) return null to null
            val localMatch = AppDatabase.getDatabase(context).phoneDao().getAllContacts().first()
                .firstOrNull { RustPhoneBridge.normalizeForMatch(it.phoneNumber) == targetNormalized }
            localMatch?.let { it.name to it.photoUri } ?: (null to null)
        } catch (_: Exception) {
            null to null
        }
    }

    // --- Real Outgoing & Incoming Call Placement via TelecomManager / ConnectionService / TelephonyManager ---

    @SuppressLint("MissingPermission")
    fun placeRealCall(
        context: Context,
        phoneNumber: String,
        simAccount: SimAccountInfo? = null,
        contactName: String? = null
    ): CallLaunchResult {
        val cleaned = phoneNumber.trim()
        if (cleaned.isEmpty()) return CallLaunchResult.Error("Enter a valid phone number")

        if (!contactName.isNullOrBlank()) {
            resolvedCallerCache[cleaned] = contactName to resolvedCallerCache[cleaned]?.second
        }

        val hasCallPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CALL_PHONE
        ) == PackageManager.PERMISSION_GRANTED

        val uri = Uri.fromParts("tel", cleaned, null)
        val telecomManager = context.getSystemService(Context.TELECOM_SERVICE) as? TelecomManager

        // If user explicitly picked the app's ConnectionService PhoneAccount, route via ConnectionService
        if (simAccount?.isConnectionServiceAccount == true && telecomManager != null) {
            return try {
                PhoneConnectionService.registerPhoneAccount(context)
                val extras = Bundle().apply {
                    putParcelable(TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE, simAccount.handle)
                    if (!contactName.isNullOrBlank()) {
                        putString(TelecomManager.EXTRA_CALL_SUBJECT, contactName)
                    }
                }
                telecomManager.placeCall(uri, extras)
                CallLaunchResult.PlacedViaConnectionService
            } catch (e: Exception) {
                Log.w(TAG, "ConnectionService placeCall failed", e)
                CallLaunchResult.Error(e.localizedMessage ?: "ConnectionService call failed")
            }
        }

        if (hasCallPermission && telecomManager != null) {
            try {
                val extras = Bundle().apply {
                    if (simAccount != null) {
                        putParcelable(TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE, simAccount.handle)
                    }
                }
                telecomManager.placeCall(uri, extras)
                return CallLaunchResult.PlacedViaTelecom
            } catch (se: SecurityException) {
                Log.w(TAG, "TelecomManager.placeCall denied, trying ACTION_CALL", se)
            } catch (e: Exception) {
                Log.w(TAG, "TelecomManager.placeCall failed, trying ACTION_CALL", e)
            }
        }

        if (hasCallPermission) {
            try {
                val callIntent = Intent(Intent.ACTION_CALL, uri).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    if (simAccount != null) {
                        putExtra(TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE, simAccount.handle)
                    }
                }
                context.startActivity(callIntent)
                return CallLaunchResult.PlacedViaActionCall
            } catch (e: Exception) {
                Log.w(TAG, "ACTION_CALL failed", e)
            }
        }

        // Fallback if CALL_PHONE permission was not granted: open system dialer safely
        return try {
            val dialIntent = Intent(Intent.ACTION_DIAL, uri).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(dialIntent)
            CallLaunchResult.OpenedSystemDialer
        } catch (e: Exception) {
            CallLaunchResult.Error(e.localizedMessage ?: "No calling application available on this device")
        }
    }

    /**
     * Reports a real incoming Telecom call through TelecomManager.addNewIncomingCall
     * handled by PhoneConnectionService.onCreateIncomingConnection.
     */
    fun notifyIncomingConnectionCall(
        context: Context,
        phoneNumber: String,
        callerName: String? = null
    ): Boolean {
        return try {
            PhoneConnectionService.registerPhoneAccount(context)
            val telecomManager = context.getSystemService(Context.TELECOM_SERVICE) as? TelecomManager
                ?: return false
            val handle = PhoneConnectionService.getPhoneAccountHandle(context)
            val extras = Bundle().apply {
                putParcelable(
                    TelecomManager.EXTRA_INCOMING_CALL_ADDRESS,
                    Uri.fromParts("tel", phoneNumber, null)
                )
                if (!callerName.isNullOrBlank()) {
                    putString(PhoneConnectionService.EXTRA_INCOMING_CALLER_NAME, callerName)
                }
            }
            telecomManager.addNewIncomingCall(handle, extras)
            true
        } catch (e: Exception) {
            Log.w(TAG, "addNewIncomingCall failed", e)
            false
        }
    }

    @SuppressLint("MissingPermission")
    fun getAvailableSimAccounts(context: Context): List<SimAccountInfo> {
        val accounts = mutableListOf<SimAccountInfo>()
        val hasPhoneState = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_PHONE_STATE
        ) == PackageManager.PERMISSION_GRANTED

        if (hasPhoneState) {
            try {
                val telecomManager = context.getSystemService(Context.TELECOM_SERVICE) as? TelecomManager
                val handles = telecomManager?.callCapablePhoneAccounts.orEmpty()
                val subs = TelephonyMonitor.status.value.subscriptions
                val connHandle = PhoneConnectionService.getPhoneAccountHandle(context)

                handles.filter { it != connHandle }.forEachIndexed { index, handle ->
                    val account = telecomManager?.getPhoneAccount(handle)
                    val subMatch = subs.getOrNull(index)
                    val label = subMatch?.carrierName?.takeIf { it.isNotBlank() }
                        ?: account?.label?.toString()?.takeIf { it.isNotBlank() }
                        ?: "SIM ${index + 1}"
                    accounts.add(
                        SimAccountInfo(
                            handle = handle,
                            label = label,
                            slotNumber = (subMatch?.slotIndex ?: index) + 1,
                            isConnectionServiceAccount = false
                        )
                    )
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to query SIM accounts", e)
            }
        }

        // Also expose registered ConnectionService PhoneAccount so user can route via ConnectionService if desired
        if (PhoneConnectionService.isPhoneAccountRegistered(context)) {
            accounts.add(
                SimAccountInfo(
                    handle = PhoneConnectionService.getPhoneAccountHandle(context),
                    label = "Telecom (ConnectionService)",
                    slotNumber = accounts.size + 1,
                    isConnectionServiceAccount = true
                )
            )
        }
        return accounts
    }

    @SuppressLint("MissingPermission")
    fun getVoicemailNumber(context: Context, simHandle: PhoneAccountHandle? = null): String? {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) {
            return null
        }
        return try {
            val telecomManager = context.getSystemService(Context.TELECOM_SERVICE) as? TelecomManager
            val targetHandle = simHandle ?: telecomManager?.callCapablePhoneAccounts?.firstOrNull()
            val telecomVm = targetHandle?.let { telecomManager?.getVoiceMailNumber(it) }?.takeIf { it.isNotBlank() }
            if (telecomVm != null) return telecomVm

            val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as? android.telephony.TelephonyManager
            tm?.voiceMailNumber?.takeIf { it.isNotBlank() }
        } catch (_: Exception) {
            null
        }
    }

    fun isDefaultDialer(context: Context): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val roleManager = context.getSystemService(Context.ROLE_SERVICE) as? RoleManager
                roleManager?.isRoleHeld(RoleManager.ROLE_DIALER) == true
            } else {
                val telecomManager = context.getSystemService(Context.TELECOM_SERVICE) as? TelecomManager
                telecomManager?.defaultDialerPackage == context.packageName
            }
        } catch (_: Exception) {
            false
        }
    }

    fun createDefaultDialerIntent(context: Context): Intent? {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val roleManager = context.getSystemService(Context.ROLE_SERVICE) as? RoleManager
                if (roleManager != null && roleManager.isRoleAvailable(RoleManager.ROLE_DIALER)) {
                    roleManager.createRequestRoleIntent(RoleManager.ROLE_DIALER)
                } else {
                    null
                }
            } else {
                Intent(TelecomManager.ACTION_CHANGE_DEFAULT_DIALER).apply {
                    putExtra(TelecomManager.EXTRA_CHANGE_DEFAULT_DIALER_PACKAGE_NAME, context.packageName)
                }
            }
        } catch (_: Exception) {
            null
        }
    }
}
