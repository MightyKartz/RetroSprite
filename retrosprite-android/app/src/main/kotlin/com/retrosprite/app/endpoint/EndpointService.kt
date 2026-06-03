package com.retrosprite.app.endpoint

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
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

    private val mainHandler = Handler(Looper.getMainLooper())

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        activeService = this
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
        if (activeService === this) {
            activeService = null
        }
        EndpointController.unbindFromService()
        super.onDestroy()
    }

    private fun startInForeground(port: Int) {
        val notification = buildNotification(this, port)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                EndpointForegroundServiceTypes.forPermissionState(
                    hasRecordAudioPermission = hasRecordAudioPermission(),
                ),
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun hasRecordAudioPermission(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED

    companion object {
        const val CHANNEL_ID = "retrosprite_endpoint"
        const val CHANNEL_NAME = "RetroSprite Endpoint"
        const val NOTIFICATION_ID = 0x52_53_45_31 // "RSE1"
        const val EXTRA_PORT = "extra_port"
        private const val TAG = "RetroSprite/Endpoint"

        @Volatile
        private var activeService: EndpointService? = null

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

        fun refreshForegroundMode(context: Context, port: Int = RetroArchEndpointServer.DEFAULT_PORT) {
            val service = activeService
            if (service == null) {
                start(context, port)
                return
            }
            val action = Runnable {
                runCatching { service.startInForeground(port) }
                    .onFailure {
                        Log.w(TAG, "Failed to refresh EndpointService foreground mode", it)
                    }
            }
            if (Looper.myLooper() == Looper.getMainLooper()) {
                action.run()
            } else {
                service.mainHandler.post(action)
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

internal object EndpointForegroundServiceTypes {
    fun forPermissionState(hasRecordAudioPermission: Boolean): Int {
        var type = ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        if (hasRecordAudioPermission) {
            type = type or ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
        }
        return type
    }
}
