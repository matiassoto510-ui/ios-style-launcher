package com.matiassoto.ioslauncher.lost

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.location.Location
import android.os.BatteryManager
import android.os.Build
import android.os.IBinder
import android.telephony.SmsManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.matiassoto.ioslauncher.MainActivity
import com.matiassoto.ioslauncher.R
import com.matiassoto.ioslauncher.data.SecurityStore
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Foreground service that keeps the always-visible "Lost phone mode is on" notification. */
class LostModeService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        ensureChannels(this)
        val store = SecurityStore(this)
        goForeground()
        if (!store.lostPhoneEnabled) {
            AlarmPlayer.stop(this)
            stopForegroundCompat(); stopSelf(); return START_NOT_STICKY
        }
        when (intent?.action) {
            ACTION_RING -> startRing()
            ACTION_STOP_RING -> AlarmPlayer.stop(this)
            ACTION_LOCATE -> {
                val sender = intent.getStringExtra(EXTRA_SENDER)
                if (!sender.isNullOrBlank()) {
                    LocationHelper.getLocation(this) { loc -> sendLocationSms(this, sender, loc) }
                }
            }
        }
        return START_STICKY
    }

    private fun goForeground() {
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val n = NotificationCompat.Builder(this, CH_STATUS)
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setContentTitle("Lost phone mode is on")
            .setContentText("This phone answers its owner's code-word texts with its location or a loud alarm.")
            .setStyle(NotificationCompat.BigTextStyle().bigText(
                "This phone answers its owner's code-word texts (LOCATE / RING) with its location or a loud alarm. Turn off in launcher Settings."))
            .setOngoing(true)
            .setContentIntent(open)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
        val type = if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0
        runCatching { ServiceCompat.startForeground(this, NOTIF_STATUS, n, type) }
    }

    private fun stopForegroundCompat() {
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
    }

    private fun startRing() {
        val nm = getSystemService(NotificationManager::class.java)
        AlarmPlayer.start(this) { nm.cancel(NOTIF_RING) }
        val full = PendingIntent.getActivity(
            this, 1, Intent(this, RingActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val stop = PendingIntent.getService(
            this, 2, Intent(this, LostModeService::class.java).setAction(ACTION_STOP_RING),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val n = NotificationCompat.Builder(this, CH_RING)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("Find my phone alarm")
            .setContentText("Ringing because the owner sent the RING code word.")
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setFullScreenIntent(full, true)
            .setContentIntent(full)
            .addAction(android.R.drawable.ic_media_pause, "Stop alarm", stop)
            .setOngoing(true)
            .setTimeoutAfter(AlarmPlayer.DURATION_MS)
            .build()
        runCatching { nm.notify(NOTIF_RING, n) }
        runCatching { startActivity(Intent(this, RingActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }

    override fun onDestroy() {
        AlarmPlayer.stop(this)
        super.onDestroy()
    }

    companion object {
        const val CMD_LOCATE = "LOCATE"
        const val CMD_RING = "RING"
        const val ACTION_START = "com.matiassoto.ioslauncher.lost.START"
        const val ACTION_RING = "com.matiassoto.ioslauncher.lost.RING"
        const val ACTION_STOP_RING = "com.matiassoto.ioslauncher.lost.STOP_RING"
        const val ACTION_LOCATE = "com.matiassoto.ioslauncher.lost.LOCATE"
        const val EXTRA_SENDER = "sender"
        const val CH_STATUS = "lost_status"
        const val CH_RING = "lost_ring"
        const val NOTIF_STATUS = 41
        const val NOTIF_RING = 42

        fun start(ctx: Context) {
            runCatching {
                ContextCompat.startForegroundService(ctx, Intent(ctx, LostModeService::class.java).setAction(ACTION_START))
            }
        }

        fun stop(ctx: Context) {
            AlarmPlayer.stop(ctx)
            ctx.stopService(Intent(ctx, LostModeService::class.java))
        }

        fun ensureChannels(ctx: Context) {
            val nm = ctx.getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(NotificationChannel(CH_STATUS, "Lost phone mode", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Shown the whole time Lost phone mode is enabled"
            })
            nm.createNotificationChannel(NotificationChannel(CH_RING, "Find my phone alarm", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Alarm triggered by the RING code word"
                setSound(null, null)
                setBypassDnd(true)
            })
        }

        fun batteryPercent(ctx: Context): Int =
            ctx.getSystemService(BatteryManager::class.java).getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)

        fun sendLocationSms(ctx: Context, to: String, loc: Location?) {
            val battery = batteryPercent(ctx)
            val text = if (loc != null) {
                val t = SimpleDateFormat("MMM d HH:mm", Locale.US).format(Date(loc.time))
                "Phone location: https://maps.google.com/?q=%.6f,%.6f\nAccuracy ~%dm (%s, %s)\nBattery: %d%%".format(
                    Locale.US, loc.latitude, loc.longitude, loc.accuracy.toInt(), loc.provider ?: "?", t, battery
                )
            } else {
                "Phone location unavailable (location off or no permission). Battery: $battery%"
            }
            runCatching {
                val sms: SmsManager = if (Build.VERSION.SDK_INT >= 31) ctx.getSystemService(SmsManager::class.java)
                else @Suppress("DEPRECATION") SmsManager.getDefault()
                val parts = sms.divideMessage(text)
                sms.sendMultipartTextMessage(to, null, parts, null, null)
            }
        }
    }
}
