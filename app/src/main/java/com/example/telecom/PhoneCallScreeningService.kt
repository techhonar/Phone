package com.example.telecom

import android.telecom.Call
import android.telecom.CallScreeningService
import com.example.data.AppDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Real Android CallScreeningService that automatically blocks incoming calls
 * matching numbers in the local Room database blacklist and records them in Room.
 */
class PhoneCallScreeningService : CallScreeningService() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onScreenCall(callDetails: Call.Details) {
        val incomingNumber = callDetails.handle?.schemeSpecificPart.orEmpty()
        val callerName = callDetails.callerDisplayName?.takeIf { it.isNotBlank() }

        serviceScope.launch {
            val shouldBlock = try {
                val db = AppDatabase.getDatabase(applicationContext)
                CallBlacklistEngine(db).evaluateIncomingCaller(
                    callerNumber = incomingNumber,
                    callerName = callerName,
                    recordBlockedCall = true
                ).isBlocked
            } catch (_: Exception) {
                false
            }

            val response = CallResponse.Builder()
                .setDisallowCall(shouldBlock)
                .setRejectCall(shouldBlock)
                .setSilenceCall(shouldBlock)
                .setSkipCallLog(false)
                .setSkipNotification(shouldBlock)
                .build()

            respondToCall(callDetails, response)
        }
    }
}
