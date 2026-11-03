package com.example.datatracker

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.net.TrafficStats
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.graphics.drawable.IconCompat
import java.util.Locale

class SpeedMeterService : Service() {

    private val handler = Handler(Looper.getMainLooper())
    private var lastRxBytes: Long = 0
    private var lastTxBytes: Long = 0
    private var alertTriggeredToday = false

    private val CHANNEL_ID = "SpeedMeterChannel"
    private val ALERT_CHANNEL_ID = "DataAlertChannel"

    private val runnable = object : Runnable {
        override fun run() {
            updateSpeedAndCheckAlert()
            handler.postDelayed(this, 1000)
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
        lastRxBytes = TrafficStats.getTotalRxBytes()
        lastTxBytes = TrafficStats.getTotalTxBytes()
        startForeground(1, buildSpeedNotification("0", "KB/s", 0.0, 0.0))
        handler.post(runnable)
    }

    private fun updateSpeedAndCheckAlert() {
        val currentRxBytes = TrafficStats.getTotalRxBytes()
        val currentTxBytes = TrafficStats.getTotalTxBytes()

        val rxSpeed = currentRxBytes - lastRxBytes
        val txSpeed = currentTxBytes - lastTxBytes
        val totalSpeedBytes = rxSpeed + txSpeed

        lastRxBytes = currentRxBytes
        lastTxBytes = currentTxBytes

        val speedValue: String
        val speedUnit: String
        if (totalSpeedBytes >= 1024 * 1024) {
            speedValue = String.format(Locale.US, "%.1f", totalSpeedBytes / (1024.0 * 1024.0))
            speedUnit = "MB/s"
        } else {
            speedValue = (totalSpeedBytes / 1024).toString()
            speedUnit = "KB/s"
        }

        val usageHelper = DataUsageHelper(this)
        val todayMB = usageHelper.getTodayMobileDataMB()
        val monthlyMB = usageHelper.getMonthlyMobileDataMB()

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(1, buildSpeedNotification(speedValue, speedUnit, todayMB, monthlyMB))

        val prefs = getSharedPreferences("DataTrackerPrefs", Context.MODE_PRIVATE)
        val dailyLimitMB = prefs.getFloat("DAILY_LIMIT_MB", 1024f)

        if (todayMB >= dailyLimitMB && !alertTriggeredToday) {
            sendDataAlertNotification(todayMB, dailyLimitMB)
            alertTriggeredToday = true
        } else if (todayMB < dailyLimitMB) {
            alertTriggeredToday = false
        }
    }

    private fun buildSpeedNotification(speed: String, unit: String, todayMB: Double, monthlyMB: Double): Notification {
        val iconBitmap = createSpeedBitmap(speed, unit)
        val iconCompat = IconCompat.createWithBitmap(iconBitmap)

        val todayFormatted = String.format(Locale.US, "%.1f MB", todayMB)
        val monthlyFormatted = String.format(Locale.US, "%.2f GB", monthlyMB / 1024.0)

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(iconCompat)
            .setContentTitle("Speed: $speed $unit")
            .setContentText("Today: $todayFormatted | Month: $monthlyFormatted")
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun createSpeedBitmap(speed: String, unit: String): Bitmap {
        val bitmap = Bitmap.createBitmap(96, 96, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint().apply {
            color = Color.WHITE
            isAntiAlias = true
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }

        paint.textSize = 44f
        canvas.drawText(speed, 48f, 46f, paint)

        paint.textSize = 28f
        canvas.drawText(unit, 48f, 82f, paint)

        return bitmap
    }

    private fun sendDataAlertNotification(usedMB: Double, limitMB: Float) {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val alertNotification = NotificationCompat.Builder(this, ALERT_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_warning)
            .setContentTitle("Daily Data Limit Crossed!")
            .setContentText(String.format(Locale.US, "You have used %.1f MB data today (Limit: %.0f MB).", usedMB, limitMB))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(2, alertNotification)
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val speedChannel = NotificationChannel(
                CHANNEL_ID, "Live Speed Meter", NotificationManager.IMPORTANCE_LOW
            )
            val alertChannel = NotificationChannel(
                ALERT_CHANNEL_ID, "Data Usage Alert", NotificationManager.IMPORTANCE_HIGH
            )
            manager.createNotificationChannel(speedChannel)
            manager.createNotificationChannel(alertChannel)
        }
    }

    override fun onDestroy() {
        handler.removeCallbacks(runnable)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
