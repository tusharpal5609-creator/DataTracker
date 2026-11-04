package com.example.datatracker

import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.provider.Settings
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var tvTodayUsage: TextView
    private lateinit var tvMonthlyUsage: TextView
    private lateinit var etDataLimit: EditText

    private val uiHandler = Handler(Looper.getMainLooper())
    private val uiUpdater = object : Runnable {
        override fun run() {
            if (hasUsageStatsPermission()) {
                updateUI()
            }
            uiHandler.postDelayed(this, 1000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tvTodayUsage = findViewById(R.id.tvTodayUsage)
        tvMonthlyUsage = findViewById(R.id.tvMonthlyUsage)
        etDataLimit = findViewById(R.id.etDataLimit)
        val btnSaveLimit: Button = findViewById(R.id.btnSaveLimit)
        val btnPermission: Button = findViewById(R.id.btnPermission)
        val btnStartService: Button = findViewById(R.id.btnStartService)

        val prefs = getSharedPreferences("DataTrackerPrefs", Context.MODE_PRIVATE)
        etDataLimit.setText(prefs.getFloat("DAILY_LIMIT_MB", 1000f).toString())

        btnSaveLimit.setOnClickListener {
            val limitText = etDataLimit.text.toString()
            if (limitText.isNotEmpty()) {
                prefs.edit().putFloat("DAILY_LIMIT_MB", limitText.toFloat()).apply()
                Toast.makeText(this, "Data Alert Limit Saved!", Toast.LENGTH_SHORT).show()
            }
        }

        btnPermission.setOnClickListener {
            if (!hasUsageStatsPermission()) {
                startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
            } else {
                Toast.makeText(this, "Permission Already Granted!", Toast.LENGTH_SHORT).show()
                updateUI()
            }
        }

        btnStartService.setOnClickListener {
            val serviceIntent = Intent(this, SpeedMeterService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent)
            } else {
                startService(serviceIntent)
            }
            Toast.makeText(this, "Speed Meter Started!", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onResume() {
        super.onResume()
        uiHandler.post(uiUpdater)
    }

    override fun onPause() {
        super.onPause()
        uiHandler.removeCallbacks(uiUpdater)
    }

    private fun updateUI() {
        val helper = DataUsageHelper(this)
        val todayMB = helper.getTodayMobileDataMB()
        val monthlyGB = helper.getMonthlyMobileDataMB() / 1000.0

        tvTodayUsage.text = String.format(Locale.US, "Today's Usage: %.1f MB", todayMB)
        tvMonthlyUsage.text = String.format(Locale.US, "Monthly Usage: %.2f GB", monthlyGB)
    }

    private fun hasUsageStatsPermission(): Boolean {
        val appOps = getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = appOps.checkOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            packageName
        )
        return mode == AppOpsManager.MODE_ALLOWED
    }
}
