package com.blutrixx.plugins.push

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

/**
 * Receives what the server sends: a data message { title, body, action_url, notification_id }. Data messages reach this
 * service whether the app is open or closed, so the notification is built here. Tapping it opens the app on the page
 * action_url names (a path like /orders/42): the tap goes through OpenFromNotificationActivity, which keeps the link
 * for the web.
 */
class PushMessagingService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        Log.d(TAG, "a new token")
        getSharedPreferences(PushFunctions.PREFS, MODE_PRIVATE).edit().putString(PushFunctions.KEY_TOKEN, token).apply()
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val title = message.data["title"]?.takeIf { it.isNotBlank() } ?: message.notification?.title ?: applicationInfo.loadLabel(packageManager).toString()
        val body = message.data["body"]?.takeIf { it.isNotBlank() } ?: message.notification?.body ?: ""
        if (body.isEmpty() && message.data["title"].isNullOrBlank()) return
        val link = message.data["action_url"].orEmpty()
        // one notification per alert: the same alert sent twice replaces itself
        val id = (message.data["notification_id"] ?: message.messageId ?: System.currentTimeMillis().toString()).hashCode()
        show(title, body, link, id)
    }

    private fun show(title: String, body: String, link: String, id: Int) {
        createChannel()
        val open = Intent(this, OpenFromNotificationActivity::class.java).apply {
            putExtra(OpenFromNotificationActivity.EXTRA_LINK, link)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        val pending = PendingIntent.getActivity(this, id, open, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(applicationInfo.icon)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pending)
            .build()
        (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).notify(id, notification)
    }

    /** Creating a channel that exists only updates its name and description, so a name set later takes effect. */
    private fun createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val prefs = getSharedPreferences(PushFunctions.PREFS, MODE_PRIVATE)
        val name = prefs.getString(PushFunctions.KEY_CHANNEL_NAME, null) ?: "Notifications"
        val description = prefs.getString(PushFunctions.KEY_CHANNEL_DESCRIPTION, null) ?: "Alerts and updates"
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, name, NotificationManager.IMPORTANCE_HIGH).apply {
                this.description = description
                enableVibration(true)
            }
        )
    }

    companion object {
        private const val TAG = "Push"
        private const val CHANNEL_ID = "push_alerts"
    }
}
