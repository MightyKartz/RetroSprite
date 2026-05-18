package com.retrosprite.app.endpoint

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.retrosprite.app.MainActivity
import com.retrosprite.app.R

/**
 * Foreground service that owns the lifetime of [RetroArchEndpointServer].
 *
 * Android requires a visible notification for any long-running network listener; this is
 * why the service exists at all. The actual server lives inside [EndpointController] so
 * the UI can observe its [EndpointStatus] without needing a service binding.
 */
class EndpointService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        ensureNotificationChannel(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val port = intent?.getIntExtra(EXTRA_PORT, RetroArchEndpointServer.DEFAULT_PORT)
            ?: RetroArchEndpointServer.DEFAULT_PORT

        startInForeground(port)

        try {
            EndpointController.bindToService(applicationContext, port)
        } catch (t: Throwable) {
            Log.e(TAG, "EndpointController.bindToService failed", t)
            stopSelf(startId)
            return START_NOT_STICKY
        }
        return START_STICKY
    }

    override fun onDestroy() {
        EndpointController.unbindFromService()
        super.onDestroy()
    }

    private fun startInForeground(port: Int) {
        val notification = buildNotification(this, port)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    companion object {
        const val CHANNEL_ID = "retrosprite_endpoint"
        const val CHANNEL_NAME = "RetroSprite Endpoint"
        const val NOTIFICATION_ID = 0x52_53_45_31 // "RSE1"
        const val EXTRA_PORT = "extra_port"
        private const val TAG = "RetroSprite/Endpoint"

        fun start(context: Context, port: Int = RetroArchEndpointServer.DEFAULT_PORT) {
            val intent = Intent(context, EndpointService::class.java).apply {
                putExtra(EXTRA_PORT, port)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, EndpointService::class.java))
        }

        fun ensureNotificationChannel(context: Context) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
            val mgr = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (mgr.getNotificationChannel(CHANNEL_ID) != null) return
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = "Local HTTP endpoint that receives RetroArch AI Service requests."
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                setShowBadge(false)
            }
            mgr.createNotificationChannel(channel)
        }

        fun buildNotification(context: Context, port: Int): Notification {
            ensureNotificationChannel(context)
            val openAppIntent = android.app.PendingIntent.getActivity(
                context,
                0,
                Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
                },
                android.app.PendingIntent.FLAG_IMMUTABLE or
                    android.app.PendingIntent.FLAG_UPDATE_CURRENT,
            )
            return NotificationCompat.Builder(context, CHANNEL_ID)
                .setContentTitle(CHANNEL_NAME)
                .setContentText("RetroSprite endpoint running on port $port")
                .setSmallIcon(R.mipmap.ic_launcher)
                .setOngoing(true)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setContentIntent(openAppIntent)
                .build()
        }
    }
}
