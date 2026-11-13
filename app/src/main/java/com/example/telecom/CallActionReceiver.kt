package com.example.telecom

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class CallActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val callId = intent.getStringExtra(EXTRA_CALL_ID)
        when (intent.action) {
            ACTION_ANSWER -> {
                CallManager.answerCall(callId)
                CallManager.launchInCallUi(context)
            }
            ACTION_DECLINE -> {
                CallManager.rejectCall(callId)
                CallNotificationManager.cancelCallNotification(context)
            }
            ACTION_HANGUP -> {
                CallManager.disconnectCall(callId)
                CallNotificationManager.cancelCallNotification(context)
            }
        }
    }

    companion object {
        const val ACTION_ANSWER = "com.example.telecom.ACTION_ANSWER"
        const val ACTION_DECLINE = "com.example.telecom.ACTION_DECLINE"
        const val ACTION_HANGUP = "com.example.telecom.ACTION_HANGUP"
        const val EXTRA_CALL_ID = "extra_call_id"
    }
}
