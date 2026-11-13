package com.example.telecom

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.telephony.PhoneStateListener
import android.telephony.SubscriptionManager
import android.telephony.TelephonyCallback
import android.telephony.TelephonyDisplayInfo
import android.telephony.TelephonyManager
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class SubscriptionSlotInfo(
    val subscriptionId: Int,
    val slotIndex: Int,
    val displayName: String,
    val carrierName: String,
    val countryIso: String,
    val isEmbedded: Boolean
)

data class TelephonyStatus(
    val osCallState: Int = TelephonyManager.CALL_STATE_IDLE,
    val osCallStateLabel: String = "Idle",
    val simState: Int = TelephonyManager.SIM_STATE_UNKNOWN,
    val simStateLabel: String = "Unknown",
    val carrierName: String = "",
    val networkTypeLabel: String = "Unknown",
    val phoneTypeLabel: String = "GSM",
    val isVoiceCapable: Boolean = false,
    val subscriptions: List<SubscriptionSlotInfo> = emptyList(),
    val isMonitoring: Boolean = false
) {
    val isSimReady: Boolean
        get() = simState == TelephonyManager.SIM_STATE_READY
}

/**
 * Real Android TelephonyManager & SubscriptionManager monitor.
 * Uses TelephonyCallback (API 31+) and PhoneStateListener (API 24-30) to observe
 * hardware call states, carrier info, SIM slots, network generation, and USSD requests.
 */
@Suppress("DEPRECATION")
object TelephonyMonitor {
    private const val TAG = "TelephonyMonitor"

    private val _status = MutableStateFlow(TelephonyStatus())
    val status: StateFlow<TelephonyStatus> = _status.asStateFlow()

    private var telephonyCallback: Any? = null
    private var legacyListener: PhoneStateListener? = null
    private var isStarted = false

    fun refreshSnapshot(context: Context) {
        val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager ?: return
        val hasPhoneState = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_PHONE_STATE
        ) == PackageManager.PERMISSION_GRANTED

        val simState = try {
            tm.simState
        } catch (_: Exception) {
            TelephonyManager.SIM_STATE_UNKNOWN
        }

        val carrier = try {
            tm.networkOperatorName?.takeIf { it.isNotBlank() }
                ?: tm.simOperatorName?.takeIf { it.isNotBlank() }
                ?: ""
        } catch (_: Exception) {
            ""
        }

        val isVoice = try {
            tm.isVoiceCapable
        } catch (_: Exception) {
            false
        }

        val phoneType = when (runCatching { tm.phoneType }.getOrDefault(TelephonyManager.PHONE_TYPE_NONE)) {
            TelephonyManager.PHONE_TYPE_GSM -> "GSM"
            TelephonyManager.PHONE_TYPE_CDMA -> "CDMA"
            TelephonyManager.PHONE_TYPE_SIP -> "SIP"
            else -> "None"
        }

        val osCallState = if (hasPhoneState) {
            runCatching { tm.callState }.getOrDefault(TelephonyManager.CALL_STATE_IDLE)
        } else {
            TelephonyManager.CALL_STATE_IDLE
        }

        val networkLabel = if (hasPhoneState) {
            mapDataNetworkType(runCatching { tm.dataNetworkType }.getOrDefault(TelephonyManager.NETWORK_TYPE_UNKNOWN))
        } else {
            "Permission needed"
        }

        val subs = querySubscriptions(context, hasPhoneState)

        _status.value = _status.value.copy(
            osCallState = osCallState,
            osCallStateLabel = mapCallStateLabel(osCallState),
            simState = simState,
            simStateLabel = mapSimStateLabel(simState),
            carrierName = carrier.ifBlank { subs.firstOrNull()?.carrierName.orEmpty() },
            networkTypeLabel = if (_status.value.networkTypeLabel in listOf("5G", "5G+") && hasPhoneState) {
                _status.value.networkTypeLabel
            } else {
                networkLabel
            },
            phoneTypeLabel = phoneType,
            isVoiceCapable = isVoice,
            subscriptions = subs
        )
    }

    @SuppressLint("MissingPermission")
    fun startMonitoring(context: Context) {
        val appContext = context.applicationContext
        refreshSnapshot(appContext)

        val hasPhoneState = ContextCompat.checkSelfPermission(
            appContext,
            Manifest.permission.READ_PHONE_STATE
        ) == PackageManager.PERMISSION_GRANTED

        if (!hasPhoneState || isStarted) return

        val tm = appContext.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager ?: return

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val callback = createApi31Callback(appContext)
                tm.registerTelephonyCallback(ContextCompat.getMainExecutor(appContext), callback)
                telephonyCallback = callback
                isStarted = true
            } else {
                val listener = object : PhoneStateListener() {
                    @Deprecated("Deprecated in Java")
                    override fun onCallStateChanged(state: Int, phoneNumber: String?) {
                        _status.value = _status.value.copy(
                            osCallState = state,
                            osCallStateLabel = mapCallStateLabel(state)
                        )
                    }
                }
                tm.listen(listener, PhoneStateListener.LISTEN_CALL_STATE)
                legacyListener = listener
                isStarted = true
            }
            _status.value = _status.value.copy(isMonitoring = true)
        } catch (se: SecurityException) {
            Log.w(TAG, "Missing permission for TelephonyManager monitoring", se)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to start TelephonyManager monitoring", e)
        }
    }

    fun stopMonitoring(context: Context) {
        if (!isStarted) return
        val tm = context.applicationContext.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager ?: return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                (telephonyCallback as? TelephonyCallback)?.let { tm.unregisterTelephonyCallback(it) }
                telephonyCallback = null
            } else {
                legacyListener?.let { tm.listen(it, PhoneStateListener.LISTEN_NONE) }
                legacyListener = null
            }
        } catch (_: Exception) {
        }
        isStarted = false
        _status.value = _status.value.copy(isMonitoring = false)
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private fun createApi31Callback(context: Context): TelephonyCallback {
        return object : TelephonyCallback(), TelephonyCallback.CallStateListener, TelephonyCallback.DisplayInfoListener {
            override fun onCallStateChanged(state: Int) {
                _status.value = _status.value.copy(
                    osCallState = state,
                    osCallStateLabel = mapCallStateLabel(state)
                )
            }

            override fun onDisplayInfoChanged(telephonyDisplayInfo: TelephonyDisplayInfo) {
                val netLabel = when (telephonyDisplayInfo.overrideNetworkType) {
                    TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NR_ADVANCED -> "5G+"
                    TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NR_NSA -> "5G"
                    TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_LTE_CA,
                    TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_LTE_ADVANCED_PRO -> "4G+"
                    else -> mapDataNetworkType(telephonyDisplayInfo.networkType)
                }
                _status.value = _status.value.copy(networkTypeLabel = netLabel)
                refreshSnapshot(context)
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun querySubscriptions(context: Context, hasPhoneState: Boolean): List<SubscriptionSlotInfo> {
        if (!hasPhoneState) return emptyList()
        return try {
            val sm = context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as? SubscriptionManager
                ?: return emptyList()
            sm.activeSubscriptionInfoList.orEmpty().map { info ->
                SubscriptionSlotInfo(
                    subscriptionId = info.subscriptionId,
                    slotIndex = info.simSlotIndex,
                    displayName = info.displayName?.toString()?.takeIf { it.isNotBlank() }
                        ?: "SIM ${info.simSlotIndex + 1}",
                    carrierName = info.carrierName?.toString().orEmpty(),
                    countryIso = info.countryIso.orEmpty().uppercase(),
                    isEmbedded = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) info.isEmbedded else false
                )
            }.sortedBy { it.slotIndex }
        } catch (e: Exception) {
            Log.w(TAG, "Unable to read SubscriptionManager slots", e)
            emptyList()
        }
    }

    /**
     * Executes a carrier USSD request directly through Android TelephonyManager (API 26+).
     */
    @SuppressLint("MissingPermission")
    fun sendUssdCode(
        context: Context,
        ussdCode: String,
        onResponse: (String) -> Unit,
        onError: (String) -> Unit
    ) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            onError("Direct USSD API requires Android 8.0+; dialing normally instead")
            return
        }
        val hasCallPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CALL_PHONE
        ) == PackageManager.PERMISSION_GRANTED
        if (!hasCallPermission) {
            onError("Phone permission required to send USSD request")
            return
        }
        val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
        if (tm == null) {
            onError("TelephonyManager unavailable on this device")
            return
        }
        try {
            tm.sendUssdRequest(
                ussdCode,
                object : TelephonyManager.UssdResponseCallback() {
                    override fun onReceiveUssdResponse(
                        telephonyManager: TelephonyManager,
                        request: String,
                        response: CharSequence
                    ) {
                        onResponse(response.toString())
                    }

                    override fun onReceiveUssdResponseFailed(
                        telephonyManager: TelephonyManager,
                        request: String,
                        failureCode: Int
                    ) {
                        val reason = when (failureCode) {
                            TelephonyManager.USSD_RETURN_FAILURE -> "Carrier returned USSD failure"
                            TelephonyManager.USSD_ERROR_SERVICE_UNAVAIL -> "Carrier USSD service unavailable"
                            else -> "USSD request failed (code $failureCode)"
                        }
                        onError(reason)
                    }
                },
                Handler(Looper.getMainLooper())
            )
        } catch (e: Exception) {
            onError(e.localizedMessage ?: "Failed to execute USSD request")
        }
    }

    private fun mapCallStateLabel(state: Int): String = when (state) {
        TelephonyManager.CALL_STATE_RINGING -> "Ringing"
        TelephonyManager.CALL_STATE_OFFHOOK -> "In Call (Off-hook)"
        else -> "Idle"
    }

    private fun mapSimStateLabel(state: Int): String = when (state) {
        TelephonyManager.SIM_STATE_READY -> "Ready"
        TelephonyManager.SIM_STATE_ABSENT -> "No SIM"
        TelephonyManager.SIM_STATE_PIN_REQUIRED -> "PIN Locked"
        TelephonyManager.SIM_STATE_PUK_REQUIRED -> "PUK Locked"
        TelephonyManager.SIM_STATE_NETWORK_LOCKED -> "Carrier Locked"
        TelephonyManager.SIM_STATE_NOT_READY -> "Initializing"
        else -> "Unknown"
    }

    private fun mapDataNetworkType(type: Int): String = when (type) {
        TelephonyManager.NETWORK_TYPE_NR -> "5G"
        TelephonyManager.NETWORK_TYPE_LTE -> "LTE"
        TelephonyManager.NETWORK_TYPE_HSPAP,
        TelephonyManager.NETWORK_TYPE_HSPA,
        TelephonyManager.NETWORK_TYPE_HSDPA,
        TelephonyManager.NETWORK_TYPE_HSUPA,
        TelephonyManager.NETWORK_TYPE_UMTS,
        TelephonyManager.NETWORK_TYPE_EVDO_0,
        TelephonyManager.NETWORK_TYPE_EVDO_A,
        TelephonyManager.NETWORK_TYPE_EVDO_B -> "3G"
        TelephonyManager.NETWORK_TYPE_EDGE,
        TelephonyManager.NETWORK_TYPE_GPRS,
        TelephonyManager.NETWORK_TYPE_CDMA,
        TelephonyManager.NETWORK_TYPE_1xRTT -> "2G"
        TelephonyManager.NETWORK_TYPE_IWLAN -> "Wi-Fi Calling"
        else -> "Cellular"
    }
}
