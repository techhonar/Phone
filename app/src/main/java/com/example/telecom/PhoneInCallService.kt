package com.example.telecom

import android.media.AudioAttributes
import android.media.Ringtone
import android.media.RingtoneManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.telecom.Call
import android.telecom.CallAudioState
import android.telecom.InCallService

/**
 * Real Android Telecom InCallService.
 * When the user sets this app as the default Phone app, Android binds this service
 * for every incoming and outgoing cellular/SIM call.
 */
@Suppress("DEPRECATION")
class PhoneInCallService : InCallService() {

    private val callCallbacks = mutableMapOf<Call, Call.Callback>()
    private var ringtone: Ringtone? = null

    override fun onCreate() {
        super.onCreate()
        CallNotificationManager.ensureChannels(this)
        CallManager.attachService(this)
    }

    override fun onDestroy() {
        stopRinging()
        CallNotificationManager.cancelCallNotification(this)
        callCallbacks.forEach { (call, callback) -> call.unregisterCallback(callback) }
        callCallbacks.clear()
        CallManager.detachService(this)
        super.onDestroy()
    }

    override fun onCallAdded(call: Call) {
        super.onCallAdded(call)
        CallManager.onCallAdded(this, call)

        val callback = object : Call.Callback() {
            override fun onStateChanged(call: Call, state: Int) {
                super.onStateChanged(call, state)
                CallManager.onCallStateChanged(call)
                updateNotificationAndRingtone(call)
            }

            override fun onDetailsChanged(call: Call, details: Call.Details) {
                super.onDetailsChanged(call, details)
                CallManager.onCallStateChanged(call)
                updateNotificationAndRingtone(call)
            }
        }
        callCallbacks[call] = callback
        call.registerCallback(callback)

        updateNotificationAndRingtone(call)
        CallManager.launchInCallUi(this)
    }

    override fun onCallRemoved(call: Call) {
        super.onCallRemoved(call)
        callCallbacks.remove(call)?.let { call.unregisterCallback(it) }
        CallManager.onCallRemoved(call)
        if (calls.isEmpty()) {
            stopRinging()
            CallNotificationManager.cancelCallNotification(this)
        }
    }

    override fun onCallAudioStateChanged(audioState: CallAudioState?) {
        super.onCallAudioStateChanged(audioState)
        CallManager.syncAudioState(audioState)
    }

    private fun updateNotificationAndRingtone(call: Call) {
        val id = CallManager.callId(call)
        val model = CallManager.uiState.value.calls.firstOrNull { it.id == id }
        val displayName = model?.displayName ?: model?.number ?: CallManager.extractNumber(call)
        val number = model?.number ?: CallManager.extractNumber(call)

        when (call.state) {
            Call.STATE_RINGING -> {
                startRinging()
                CallNotificationManager.showIncomingCallNotification(this, id, displayName, number)
            }
            Call.STATE_ACTIVE -> {
                stopRinging()
                CallNotificationManager.showOngoingCallNotification(this, id, displayName, number, "Active call")
            }
            Call.STATE_DIALING, Call.STATE_CONNECTING -> {
                stopRinging()
                CallNotificationManager.showOngoingCallNotification(this, id, displayName, number, "Dialing...")
            }
            Call.STATE_HOLDING -> {
                stopRinging()
                CallNotificationManager.showOngoingCallNotification(this, id, displayName, number, "On hold")
            }
            Call.STATE_DISCONNECTED, Call.STATE_DISCONNECTING -> {
                stopRinging()
                if (calls.none { it.state != Call.STATE_DISCONNECTED }) {
                    CallNotificationManager.cancelCallNotification(this)
                }
            }
        }
    }

    private fun startRinging() {
        try {
            if (ringtone?.isPlaying != true) {
                val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
                ringtone = RingtoneManager.getRingtone(this, uri)?.apply {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        isLooping = true
                    }
                    audioAttributes = AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                    play()
                }
            }
            val pattern = longArrayOf(0, 600, 900)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = getSystemService(VibratorManager::class.java)
                vm?.defaultVibrator?.vibrate(VibrationEffect.createWaveform(pattern, 0))
            } else {
                @Suppress("DEPRECATION")
                val vibrator = getSystemService(VIBRATOR_SERVICE) as? Vibrator
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator?.vibrate(VibrationEffect.createWaveform(pattern, 0))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator?.vibrate(pattern, 0)
                }
            }
        } catch (_: Exception) {
        }
    }

    private fun stopRinging() {
        try {
            ringtone?.stop()
            ringtone = null
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = getSystemService(VibratorManager::class.java)
                vm?.defaultVibrator?.cancel()
            } else {
                @Suppress("DEPRECATION")
                val vibrator = getSystemService(VIBRATOR_SERVICE) as? Vibrator
                vibrator?.cancel()
            }
        } catch (_: Exception) {
        }
    }
}
