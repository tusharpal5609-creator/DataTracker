package com.example.datatracker

import android.app.usage.NetworkStats
import android.app.usage.NetworkStatsManager
import android.content.Context
import android.net.ConnectivityManager
import java.util.Calendar

class DataUsageHelper(private val context: Context) {

    private val networkStatsManager =
        context.getSystemService(Context.NETWORK_STATS_SERVICE) as NetworkStatsManager

    fun getTodayMobileDataMB(): Double {
        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return getNetworkUsage(ConnectivityManager.TYPE_MOBILE, calendar.timeInMillis, System.currentTimeMillis())
    }

    fun getMonthlyMobileDataMB(): Double {
        val calendar = Calendar.getInstance().apply {
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return getNetworkUsage(ConnectivityManager.TYPE_MOBILE, calendar.timeInMillis, System.currentTimeMillis())
    }

    private fun getNetworkUsage(networkType: Int, startTime: Long, endTime: Long): Double {
        return try {
            val bucket: NetworkStats.Bucket = networkStatsManager.querySummaryForDevice(
                networkType,
                null,
                startTime,
                endTime
            )
            val totalBytes = bucket.rxBytes + bucket.txBytes
            totalBytes / (1024.0 * 1024.0)
        } catch (e: Exception) {
            0.0
        }
    }
}
