package com.example.ogumonitor

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

class MainActivity : AppCompatActivity() {

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (!isGranted) {
                Toast.makeText(
                    this,
                    getString(R.string.notification_permission_denied_message),
                    Toast.LENGTH_LONG
                ).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val etCookie: EditText = findViewById(R.id.etCookie)
        val btnSaveStart: Button = findViewById(R.id.btnSaveStart)
        val btnStop: Button = findViewById(R.id.btnStop)
        val tvStatus: TextView = findViewById(R.id.tvStatus)

        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        etCookie.setText(prefs.getString(PREF_COOKIE, ""))

        val workManager = WorkManager.getInstance(this)

        workManager.getWorkInfosByTagLiveData(MonitorWorker.WORK_TAG)
            .observe(this) { workInfos ->
                tvStatus.text = resolveStatus(workInfos)
            }

        btnSaveStart.setOnClickListener {
            val cookieText = etCookie.text.toString().trim()
            if (cookieText.isEmpty()) {
                Toast.makeText(this, R.string.cookie_empty_message, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            prefs.edit().putString(PREF_COOKIE, cookieText).apply()
            requestNotificationPermissionIfNeeded()

            val periodicRequest = PeriodicWorkRequestBuilder<MonitorWorker>(15, TimeUnit.MINUTES)
                .setInputData(MonitorWorker.createInputData(cookieText))
                .addTag(MonitorWorker.WORK_TAG)
                .build()

            val oneTimeRequest = OneTimeWorkRequestBuilder<MonitorWorker>()
                .setInitialDelay(5, TimeUnit.SECONDS)
                .setInputData(MonitorWorker.createInputData(cookieText))
                .addTag(MonitorWorker.WORK_TAG)
                .build()

            workManager.enqueueUniquePeriodicWork(
                MonitorWorker.UNIQUE_PERIODIC_WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                periodicRequest
            )
            workManager.enqueue(oneTimeRequest)

            tvStatus.text = getString(R.string.status_started)
            Toast.makeText(this, R.string.monitoring_started_message, Toast.LENGTH_SHORT).show()
        }

        btnStop.setOnClickListener {
            workManager.cancelUniqueWork(MonitorWorker.UNIQUE_PERIODIC_WORK_NAME)
            workManager.cancelAllWorkByTag(MonitorWorker.WORK_TAG)
            tvStatus.text = getString(R.string.status_stopped)
            Toast.makeText(this, R.string.monitoring_stopped_message, Toast.LENGTH_SHORT).show()
        }
    }

    private fun resolveStatus(workInfos: List<WorkInfo>?): String {
        if (workInfos.isNullOrEmpty()) {
            return getString(R.string.status_idle)
        }

        val hasRunning = workInfos.any { it.state == WorkInfo.State.RUNNING }
        val hasEnqueued = workInfos.any { it.state == WorkInfo.State.ENQUEUED }
        val hasFailed = workInfos.any { it.state == WorkInfo.State.FAILED }

        return when {
            hasRunning -> getString(R.string.status_running)
            hasEnqueued -> getString(R.string.status_waiting)
            hasFailed -> getString(R.string.status_failed)
            workInfos.all { it.state == WorkInfo.State.CANCELLED } -> getString(R.string.status_stopped)
            else -> getString(R.string.status_idle)
        }
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val permissionState = ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            )
            if (permissionState != PackageManager.PERMISSION_GRANTED) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    companion object {
        private const val PREFS_NAME = "ogu_prefs"
        private const val PREF_COOKIE = "cookie"
    }
}
