package com.a4sizepaper.typing.texttopdf

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

internal const val APP_UPDATES_CHANNEL = "app_updates"

internal fun registerForPushUpdates(context: Context) {
    createAppUpdatesChannel(context)
    val messaging = com.google.firebase.messaging.FirebaseMessaging.getInstance()
    messaging.isAutoInitEnabled = true
    messaging.subscribeToTopic(APP_UPDATES_CHANNEL)
}

internal fun createAppUpdatesChannel(context: Context) {
    context.getSystemService(NotificationManager::class.java).createNotificationChannel(
        NotificationChannel(APP_UPDATES_CHANNEL, context.getString(R.string.push_channel_name), NotificationManager.IMPORTANCE_DEFAULT)
    )
}

class AppMessagingService : FirebaseMessagingService() {
    override fun onNewToken(token: String) {
        super.onNewToken(token)
        // Subscribe after every token refresh. The topic is reserved for essential app updates.
        com.google.firebase.messaging.FirebaseMessaging.getInstance().subscribeToTopic("app_updates")
        Log.i("A4Push", "FCM registration token refreshed")
    }

    override fun onMessageReceived(message: RemoteMessage) {
        if (!NotificationManagerCompat.from(this).areNotificationsEnabled()) return
        createAppUpdatesChannel(this)
        val payload = message.notification
        val title = payload?.title ?: message.data["title"] ?: getString(R.string.app_name)
        val body = payload?.body ?: message.data["body"] ?: return
        val openApp = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(this, APP_UPDATES_CHANNEL)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(openApp)
            .setAutoCancel(true)
            .build()
        try {
            getSystemService(NotificationManager::class.java)
                .notify(message.messageId?.hashCode() ?: System.nanoTime().toInt(), notification)
        } catch (_: SecurityException) {
            // The user may have revoked notification permission during delivery.
        }
    }
}
