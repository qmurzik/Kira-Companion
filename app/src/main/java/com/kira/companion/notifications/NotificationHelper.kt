package com.kira.companion.notifications

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.kira.companion.MainActivity
import com.kira.companion.R

/** Builds the persistent overlay notification and (optional) chat-reply notifications. */
object NotificationHelper {

    const val OVERLAY_CHANNEL_ID = "kira_overlay_channel"
    const val CHAT_CHANNEL_ID = "kira_chat_channel"
    const val OVERLAY_NOTIFICATION_ID = 1001
    private const val CHAT_NOTIFICATION_ID = 2001

    fun ensureChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannel(
            NotificationChannel(
                OVERLAY_CHANNEL_ID,
                "Kira overlay",
                NotificationManager.IMPORTANCE_MIN,
            ).apply { setShowBadge(false) },
        )
        manager.createNotificationChannel(
            NotificationChannel(
                CHAT_CHANNEL_ID,
                "Kira messages",
                NotificationManager.IMPORTANCE_DEFAULT,
            ),
        )
    }

    fun buildOverlayNotification(context: Context): Notification {
        ensureChannels(context)
        val openAppIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            openAppIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(context, OVERLAY_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.overlay_notification_title))
            .setContentText(context.getString(R.string.overlay_notification_text))
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .build()
    }

    /** Shows a notification for Kira's reply while the app isn't in the foreground. */
    fun showChatReplyNotification(context: Context, replyText: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) return
        }
        ensureChannels(context)

        val openChatIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(MainActivity.EXTRA_OPEN_CHAT, true)
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            1,
            openChatIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        val notification = NotificationCompat.Builder(context, CHAT_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.app_name))
            .setContentText(replyText)
            .setStyle(NotificationCompat.BigTextStyle().bigText(replyText))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        NotificationManagerCompat.from(context).notify(CHAT_NOTIFICATION_ID, notification)
    }
}
