package com.example.telecom

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.telecom.CallAudioState
import android.telecom.Connection
import android.telecom.ConnectionRequest
import android.telecom.ConnectionService
import android.telecom.DisconnectCause
import android.telecom.PhoneAccount
import android.telecom.PhoneAccountHandle
import android.telecom.TelecomManager
import android.telecom.VideoProfile
import android.util.Log
import com.example.data.AppDatabase
import com.example.rust.DtmfPlayer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking

/**
 * Real Android Telecom ConnectionService & Connection implementation.
 * Registers a Telecom PhoneAccount so the app can initiate outgoing connections and
 * receive incoming connections directly through Android's Telecom subsystem.
 */
@Suppress("DEPRECATION")
class PhoneConnectionService : ConnectionService() {

    override fun onCreateOutgoingConnection(
        connectionManagerPhoneAccount: PhoneAccountHandle?,
        request: ConnectionRequest?
    ): Connection {
        val address = request?.address ?: Uri.fromParts("tel", "", null)
        val number = address.schemeSpecificPart.orEmpty()
        val displayName = request?.extras?.getString(TelecomManager.EXTRA_CALL_SUBJECT)

        val connection = PhoneConnection(
            context = applicationContext,
            number = number,
            initialDisplayName = displayName,
            isIncoming = false
        ).apply {
            setAddress(address, TelecomManager.PRESENTATION_ALLOWED)
            if (!displayName.isNullOrBlank()) {
                setCallerDisplayName(displayName, TelecomManager.PRESENTATION_ALLOWED)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                setConnectionProperties(Connection.PROPERTY_SELF_MANAGED)
            }
            setAudioModeIsVoip(false)
            setConnectionCapabilities(
                Connection.CAPABILITY_HOLD or
                    Connection.CAPABILITY_SUPPORT_HOLD or
                    Connection.CAPABILITY_MUTE
            )
            setInitializing()
            setDialing()
        }

        CallManager.onConnectionAdded(applicationContext, connection)
        CallManager.launchInCallUi(applicationContext)

        // Transition outgoing ConnectionService call from DIALING to ACTIVE once Telecom audio path is ready
        Handler(Looper.getMainLooper()).postDelayed({
            if (connection.state == Connection.STATE_DIALING || connection.state == Connection.STATE_INITIALIZING) {
                connection.markDialingConnected()
            }
        }, 1200L)
        return connection
    }

    override fun onCreateOutgoingConnectionFailed(
        connectionManagerPhoneAccount: PhoneAccountHandle?,
        request: ConnectionRequest?
    ) {
        super.onCreateOutgoingConnectionFailed(connectionManagerPhoneAccount, request)
        Log.w(TAG, "Outgoing ConnectionService call rejected by Telecom")
    }

    override fun onCreateIncomingConnection(
        connectionManagerPhoneAccount: PhoneAccountHandle?,
        request: ConnectionRequest?
    ): Connection {
        return createIncomingConnectionWithBlacklist(
            request = request,
            database = AppDatabase.getDatabase(applicationContext)
        )
    }

    internal fun createIncomingConnectionWithBlacklist(
        request: ConnectionRequest?,
        database: AppDatabase
    ): Connection {
        val extras = request?.extras
        val address = request?.address
            ?: extras?.getParcelable(TelecomManager.EXTRA_INCOMING_CALL_ADDRESS)
            ?: Uri.fromParts("tel", "Unknown", null)
        val number = address.schemeSpecificPart.orEmpty()
        val displayName = extras?.getString(EXTRA_INCOMING_CALLER_NAME)

        val evaluation = runBlocking(Dispatchers.IO) {
            runCatching {
                CallBlacklistEngine(database).evaluateIncomingCaller(
                    callerNumber = number,
                    callerName = displayName,
                    recordBlockedCall = true
                )
            }.getOrDefault(BlacklistEvaluation(isBlocked = false))
        }

        if (evaluation.isBlocked) {
            Log.i(TAG, "Auto-rejected incoming ConnectionService call from blacklisted caller")
            CallNotificationManager.cancelCallNotification(applicationContext)
            return Connection.createFailedConnection(
                DisconnectCause(DisconnectCause.REJECTED, "Blocked caller")
            )
        }

        val connection = PhoneConnection(
            context = applicationContext,
            number = number,
            initialDisplayName = displayName,
            isIncoming = true
        ).apply {
            setAddress(address, TelecomManager.PRESENTATION_ALLOWED)
            if (!displayName.isNullOrBlank()) {
                setCallerDisplayName(displayName, TelecomManager.PRESENTATION_ALLOWED)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                setConnectionProperties(Connection.PROPERTY_SELF_MANAGED)
            }
            setAudioModeIsVoip(false)
            setConnectionCapabilities(
                Connection.CAPABILITY_HOLD or
                    Connection.CAPABILITY_SUPPORT_HOLD or
                    Connection.CAPABILITY_MUTE
            )
            setRinging()
        }

        CallManager.onConnectionAdded(applicationContext, connection)
        return connection
    }

    override fun onCreateIncomingConnectionFailed(
        connectionManagerPhoneAccount: PhoneAccountHandle?,
        request: ConnectionRequest?
    ) {
        super.onCreateIncomingConnectionFailed(connectionManagerPhoneAccount, request)
        Log.w(TAG, "Incoming ConnectionService call rejected by Telecom")
    }

    companion object {
        private const val TAG = "PhoneConnectionService"
        const val ACCOUNT_ID = "samsung_phone_telecom_connection_account"
        const val EXTRA_INCOMING_CALLER_NAME = "extra_incoming_caller_name"

        fun getPhoneAccountHandle(context: Context): PhoneAccountHandle {
            return PhoneAccountHandle(
                ComponentName(context.applicationContext, PhoneConnectionService::class.java),
                ACCOUNT_ID
            )
        }

        fun registerPhoneAccount(context: Context): Boolean {
            return try {
                val appContext = context.applicationContext
                val telecomManager = appContext.getSystemService(Context.TELECOM_SERVICE) as? TelecomManager
                    ?: return false
                val handle = getPhoneAccountHandle(appContext)
                val capabilities = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    PhoneAccount.CAPABILITY_SELF_MANAGED
                } else {
                    PhoneAccount.CAPABILITY_CALL_PROVIDER
                }
                val account = PhoneAccount.builder(handle, "Samsung Phone (Telecom)")
                    .setCapabilities(capabilities)
                    .addSupportedUriScheme(PhoneAccount.SCHEME_TEL)
                    .setShortDescription("Samsung Phone ConnectionService")
                    .build()
                telecomManager.registerPhoneAccount(account)
                true
            } catch (e: Exception) {
                Log.w(TAG, "Failed to register Telecom PhoneAccount", e)
                false
            }
        }

        fun isPhoneAccountRegistered(context: Context): Boolean {
            return try {
                val telecomManager = context.applicationContext.getSystemService(Context.TELECOM_SERVICE) as? TelecomManager
                    ?: return false
                val handle = getPhoneAccountHandle(context)
                telecomManager.getPhoneAccount(handle) != null
            } catch (_: Exception) {
                false
            }
        }
    }
}

/**
 * Live Telecom Connection managed by PhoneConnectionService.
 */
@Suppress("DEPRECATION")
class PhoneConnection(
    private val context: Context,
    val number: String,
    val initialDisplayName: String?,
    val isIncoming: Boolean
) : Connection() {

    val connectionId: String = Integer.toHexString(System.identityHashCode(this))
    var connectTimeMillis: Long = 0L
        private set
    private val dtmfPlayer = DtmfPlayer()

    override fun onShowIncomingCallUi() {
        super.onShowIncomingCallUi()
        val display = initialDisplayName?.takeIf { it.isNotBlank() } ?: number.ifBlank { "Incoming Call" }
        CallNotificationManager.showIncomingCallNotification(context, connectionId, display, number)
        CallManager.launchInCallUi(context)
    }

    override fun onAnswer() {
        onAnswer(VideoProfile.STATE_AUDIO_ONLY)
    }

    override fun onAnswer(videoState: Int) {
        connectTimeMillis = System.currentTimeMillis()
        setActive()
        CallNotificationManager.showOngoingCallNotification(
            context,
            connectionId,
            initialDisplayName ?: number,
            number,
            "Active call"
        )
        CallManager.onConnectionUpdated(this)
    }

    fun markDialingConnected() {
        if (state == STATE_DIALING || state == STATE_INITIALIZING) {
            connectTimeMillis = System.currentTimeMillis()
            setActive()
            CallNotificationManager.showOngoingCallNotification(
                context,
                connectionId,
                initialDisplayName ?: number,
                number,
                "Active call"
            )
            CallManager.onConnectionUpdated(this)
        }
    }

    override fun onReject() {
        onReject(null)
    }

    override fun onReject(replyMessage: String?) {
        dtmfPlayer.release()
        setDisconnected(DisconnectCause(DisconnectCause.REJECTED, replyMessage))
        CallNotificationManager.cancelCallNotification(context)
        CallManager.onConnectionRemoved(this, wasRejected = true)
        destroy()
    }

    override fun onDisconnect() {
        dtmfPlayer.release()
        setDisconnected(DisconnectCause(DisconnectCause.LOCAL))
        CallNotificationManager.cancelCallNotification(context)
        CallManager.onConnectionRemoved(this)
        destroy()
    }

    override fun onAbort() {
        dtmfPlayer.release()
        setDisconnected(DisconnectCause(DisconnectCause.CANCELED))
        CallNotificationManager.cancelCallNotification(context)
        CallManager.onConnectionRemoved(this)
        destroy()
    }

    override fun onHold() {
        setOnHold()
        CallNotificationManager.showOngoingCallNotification(
            context,
            connectionId,
            initialDisplayName ?: number,
            number,
            "On hold"
        )
        CallManager.onConnectionUpdated(this)
    }

    override fun onUnhold() {
        setActive()
        CallNotificationManager.showOngoingCallNotification(
            context,
            connectionId,
            initialDisplayName ?: number,
            number,
            "Active call"
        )
        CallManager.onConnectionUpdated(this)
    }

    override fun onPlayDtmfTone(c: Char) {
        super.onPlayDtmfTone(c)
        dtmfPlayer.playTone(c, 150)
    }

    override fun onStopDtmfTone() {
        super.onStopDtmfTone()
    }

    override fun onCallAudioStateChanged(state: CallAudioState?) {
        super.onCallAudioStateChanged(state)
        CallManager.syncAudioState(state)
    }

    override fun onSilence() {
        super.onSilence()
    }
}
