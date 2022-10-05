package com.intersvyaz.detection.service

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.SystemClock
import android.util.Log
import androidx.core.app.NotificationCompat
import com.intersvyaz.detection.ConnectionLevel
import com.intersvyaz.detection.NetworkManager
import com.intersvyaz.detection.NetworkStatus
import com.intersvyaz.detection.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class NetworkService : Service() {

    private val scope = CoroutineScope(IO)
    private var networkManager: NetworkManager? = null
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onCreate() {
        super.onCreate()
        networkManager = NetworkManager(this)
        startForeground(1, serviceWorkingNotification())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        intent?.let {
            when (intent.action) {
                Actions.START.name -> startService()
                Actions.STOP.name -> stopService()
                else -> Log.e(TAG, "No action in the received intent")
            }
        }
        return START_STICKY
    }

    private fun startService() {
        wakeLock = (getSystemService(Context.POWER_SERVICE) as PowerManager).run {
            newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, TAG).apply {
                acquire(10 * 60 * 1000L)
            }
        }
        scope.launch {
            networkManager?.networkStatus?.collectLatest {
                when (it) {
                    is NetworkStatus.Available ->
                        Log.e(TAG, "Available")
                    is NetworkStatus.Lost ->
                        Log.e(TAG, "Lost")
                    is NetworkStatus.Unavailable ->
                        Log.e(TAG, "Unavailable")
                    is NetworkStatus.CapabilitiesChanging -> {
                        Log.e(TAG, "CapabilitiesChanging")

                        val wifiExist = it.connectionLevel() != ConnectionLevel.WIFI_LEVEL_WEAK

                        if (wifiExist && it.internetConnectionExist().not())
                            noConnectionMessage()
                    }
                }
            }
        }
    }

    private fun buildNotificationChannel() {
        val notificationManager =
            getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                notificationChannelId,
                notificationChannelName,
                NotificationManager.IMPORTANCE_HIGH,
            )
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun noConnectionMessage() {
        val notificationManager =
            getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        buildNotificationChannel()
        makeNotificationBuilder("Внимание! Отсутствует интернет соединение!")
            .also {
                notificationManager.notify(2, it)
            }
    }

    private fun serviceWorkingNotification(): Notification {
        buildNotificationChannel()
        return makeNotificationBuilder("Детекция проблем Wi-fi сети")
    }

    private fun makeNotificationBuilder(
        text: String,
    ) = NotificationCompat.Builder(this, notificationChannelId)
        .apply {
            setContentTitle("Уведомление")
            setContentText(text)
            setSmallIcon(R.drawable.wifi_icon)
            priority = NotificationCompat.PRIORITY_HIGH
        }.build()

    private fun stopService() {
        try {
            wakeLock?.let { if (it.isHeld) it.release() }
            stopForeground(true)
            stopSelf()
        } catch (e: Exception) {
            Log.e(TAG, "Service stopped before being started")
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onTaskRemoved(rootIntent: Intent?) {
        val intent = Intent(applicationContext, NetworkService::class.java)
        val pendingIntent = PendingIntent.getService(
            this,
            1,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_ONE_SHOT
        )
        val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager

        alarmManager.set(
            AlarmManager.RTC_WAKEUP,
            SystemClock.elapsedRealtime() + ELAPSED_REALTIME_COEFFICIENT,
            pendingIntent
        )
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.e(TAG, "The service has been destroyed")
    }

    companion object {
        private val TAG = NetworkService::class.java.simpleName

        private const val ELAPSED_REALTIME_COEFFICIENT = 5000
        private const val notificationChannelId = "Network Channel"
        private const val notificationChannelName = "Network Service"
    }
}