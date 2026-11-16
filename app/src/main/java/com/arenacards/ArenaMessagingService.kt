package com.arenacards

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.arenacards.data.FirebaseNotificationDeviceId
import com.arenacards.data.FirebaseSocialRepository
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class ArenaMessagingService : FirebaseMessagingService() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onNewToken(token: String) {
        if (FirebaseAuth.getInstance().currentUser == null || !notificationsAllowed()) return
        serviceScope.launch {
            try {
                FirebaseSocialRepository().registerNotificationToken(
                    token,
                    FirebaseNotificationDeviceId.get(applicationContext)
                )
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                Log.e(TAG, "FCM token registration failed.", error)
            }
        }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        if (!notificationsAllowed()) return
        val type = message.data["type"].orEmpty()
        val title = when (type) {
            "FRIEND_REQUEST" -> "New friend request"
            "FRIEND_REQUEST_ACCEPTED" -> "Friend request accepted"
            "ROOM_INVITATION" -> "Private room invitation"
            "PRIVATE_MESSAGE", "ROOM_MESSAGE" -> "New ArenaCards message"
            else -> "ArenaCards update"
        }
        ensureNotificationChannel()
        val openNotifications = Intent(this, MainActivity::class.java).apply {
            putExtra(MainActivity.EXTRA_OPEN_NOTIFICATIONS, true)
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            openNotifications,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText("Open ArenaCards to view your latest activity.")
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        try {
            NotificationManagerCompat.from(this).notify(
                message.data["notificationId"]?.hashCode() ?: System.currentTimeMillis().toInt(),
                notification
            )
        } catch (error: SecurityException) {
            Log.w(TAG, "Notification permission changed while posting an alert.", error)
        }
    }

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun notificationsAllowed(): Boolean =
        NotificationManagerCompat.from(this).areNotificationsEnabled()
            && (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU
                || ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED)

    private fun ensureNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "ArenaCards activity",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Friend requests, room invitations, and social activity."
            }
        )
    }

    private companion object {
        const val TAG = "ArenaMessagingService"
        const val CHANNEL_ID = "arena_cards_activity"
    }
}
