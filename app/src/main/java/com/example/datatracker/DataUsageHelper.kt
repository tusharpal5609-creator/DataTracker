package com.example.datatracker

import android.app.usage.NetworkStats
import android.app.usage.NetworkStatsManager
import android.content.Context
import android.net.ConnectivityManager
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class DataUsageHelper(private val context: Context) {

    private val networkStatsManager =
        context.getSystemService(Context.NETWORK_STATS_SERVICE) as NetworkStatsManager
    private val prefs = context.getSharedPreferences("DataTrackerPrefs", Context.MODE_PRIVATE)

    fun recordLiveMobileBytes(deltaBytes: Long) {
        checkAndResetDailyCounter()
        if (deltaBytes > 0 && isMobileDataActive()) {
            val currentLive = prefs.getLong("LIVE_TODAY_BYTES", 0L)
            prefs.edit().putLong("LIVE_TODAY_BYTES", currentLive + deltaBytes).apply()
        }
    }

    private fun checkAndResetDailyCounter() {
        val todayStr = SimpleDateFormat("yyyyMMdd", Locale.US).format(Date())
        val savedDate = prefs.getString("LAST_DATE", "")
        val sysBytes = querySystemBytes(getStartOfDayMillis(), System.currentTimeMillis())

        if (savedDate != todayStr) {
            prefs.edit()
                .putString("LAST_DATE", todayStr)
                .putLong("LIVE_TODAY_BYTES", sysBytes)
                .apply()
        } else {
            val currentLive = prefs.getLong("LIVE_TODAY_BYTES", 0L)
            if (sysBytes > currentLive) {
                prefs.edit().putLong("LIVE_TODAY_BYTES", sysBytes).apply()
            }
        }
    }

    fun getTodayMobileDataMB(): Double {
        checkAndResetDailyCounter()
        val sysBytes = querySystemBytes(getStartOfDayMillis(), System.currentTimeMillis())
        val liveBytes = prefs.getLong("LIVE_TODAY_BYTES", 0L)
        val accurateBytes = maxOf(sysBytes, liveBytes)
        return bytesToMB(accurateBytes)
    }

    fun getMonthlyMobileDataMB(): Double {
        val calendar = Calendar.getInstance().apply {
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val monthSysBytes = querySystemBytes(calendar.timeInMillis, System.currentTimeMillis())
        val todaySysBytes = querySystemBytes(getStartOfDayMillis(), System.currentTimeMillis())
        val todayLiveBytes = prefs.getLong("LIVE_TODAY_BYTES", 0L)

        val unrecordedBuffer = maxOf(0L, todayLiveBytes - todaySysBytes)
        return bytesToMB(monthSysBytes + unrecordedBuffer)
    }

    private fun getStartOfDayMillis(): Long {
        return Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }

    private fun querySystemBytes(startTime: Long, endTime: Long): Long {
        var deviceTotal = 0L
        try {
            val summaryBucket = networkStatsManager.querySummaryForDevice(
                ConnectivityManager.TYPE_MOBILE,
                null,
                startTime,
                endTime
            )
            deviceTotal = summaryBucket.rxBytes + summaryBucket.txBytes
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return deviceTotal
    }

    private fun isMobileDataActive(): Boolean {
        return try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val activeNetwork = cm.activeNetworkInfo
            activeNetwork != null && activeNetwork.isConnected && activeNetwork.type == ConnectivityManager.TYPE_MOBILE
        } catch (e: Exception) {
            false
        }
    }

    // Android Settings ebong Telecom Operator-der moto 1000-based (Decimal) MB hiseb
    private fun bytesToMB(bytes: Long): Double {
        return bytes / (1000.0 * 1000.0)
    }
}
