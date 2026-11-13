package com.example.telecom

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.Person
import androidx.core.content.ContextCompat
import com.example.InCallActivity
import com.example.R

object CallNotificationManager {
    private const val CHANNEL_INCOMING_ID = "samsung_phone_incoming_call"
    private const val CHANNEL_ONGOING_ID = "samsung_phone_ongoing_call"
    private const val CALL_NOTIFICATION_ID = 4001

    fun ensureChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return

        val ringtoneUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

        val incomingChannel = NotificationChannel(
            CHANNEL_INCOMING_ID,
            context.getString(R.string.channel_incoming_call_name),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Alerts for incoming phone calls"
            setSound(ringtoneUri, audioAttributes)
            enableVibration(true)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }

        val ongoingChannel = NotificationChannel(
            CHANNEL_ONGOING_ID,
            context.getString(R.string.channel_ongoing_call_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Controls for active phone calls"
            setSound(null, null)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }

        manager.createNotificationChannel(incomingChannel)
        manager.createNotificationChannel(ongoingChannel)
    }

    fun showIncomingCallNotification(context: Context, callId: String, displayName: String, number: String) {
        if (!canPostNotifications(context)) return
        ensureChannels(context)

        val fullScreenIntent = Intent(context, InCallActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val fullScreenPendingIntent = PendingIntent.getActivity(
            context, 100, fullScreenIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val answerIntent = Intent(context, CallActionReceiver::class.java).apply {
            action = CallActionReceiver.ACTION_ANSWER
            putExtra(CallActionReceiver.EXTRA_CALL_ID, callId)
        }
        val answerPending = PendingIntent.getBroadcast(
            context, 101, answerIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val declineIntent = Intent(context, CallActionReceiver::class.java).apply {
            action = CallActionReceiver.ACTION_DECLINE
            putExtra(CallActionReceiver.EXTRA_CALL_ID, callId)
        }
        val declinePending = PendingIntent.getBroadcast(
            context, 102, declineIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val caller = Person.Builder()
            .setName(displayName.ifBlank { number })
            .setImportant(true)
            .build()

        val notification = NotificationCompat.Builder(context, CHANNEL_INCOMING_ID)
            .setSmallIcon(android.R.drawable.sym_action_call)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setAutoCancel(false)
            .setContentIntent(fullScreenPendingIntent)
            .setFullScreenIntent(fullScreenPendingIntent, true)
            .setStyle(NotificationCompat.CallStyle.forIncomingCall(caller, declinePending, answerPending))
            .build()

        val manager = context.getSystemService(NotificationManager::class.java)
        manager?.notify(CALL_NOTIFICATION_ID, notification)
    }

    fun showOngoingCallNotification(context: Context, callId: String, displayName: String, number: String, stateLabel: String) {
        if (!canPostNotifications(context)) return
        ensureChannels(context)

        val contentIntent = Intent(context, InCallActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingContent = PendingIntent.getActivity(
            context, 200, contentIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val hangUpIntent = Intent(context, CallActionReceiver::class.java).apply {
            action = CallActionReceiver.ACTION_HANGUP
            putExtra(CallActionReceiver.EXTRA_CALL_ID, callId)
        }
        val hangUpPending = PendingIntent.getBroadcast(
            context, 201, hangUpIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val caller = Person.Builder()
            .setName(displayName.ifBlank { number })
            .build()

        val notification = NotificationCompat.Builder(context, CHANNEL_ONGOING_ID)
            .setSmallIcon(android.R.drawable.sym_action_call)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setOngoing(true)
            .setContentText(stateLabel)
            .setUsesChronometer(stateLabel == "Active call")
            .setContentIntent(pendingContent)
            .setStyle(NotificationCompat.CallStyle.forOngoingCall(caller, hangUpPending))
            .build()

        val manager = context.getSystemService(NotificationManager::class.java)
        manager?.notify(CALL_NOTIFICATION_ID, notification)
    }

    fun cancelCallNotification(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager?.cancel(CALL_NOTIFICATION_ID)
    }

    private fun canPostNotifications(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }
}
