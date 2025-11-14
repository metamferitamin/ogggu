package com.example.ogumonitor

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
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

        val toolbar: MaterialToolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)

        NotificationHelper.ensureChannels(this)

        val etAsp: TextInputEditText = findViewById(R.id.etAsp)
        val etObsKul: TextInputEditText = findViewById(R.id.etObsKul)
        val btnSaveStart: MaterialButton = findViewById(R.id.btnSaveStart)
        val btnStop: MaterialButton = findViewById(R.id.btnStop)
        val tvStatus: androidx.appcompat.widget.AppCompatTextView = findViewById(R.id.tvStatus)

        val prefs = getSharedPreferences(CookieUtils.PREFS_NAME, Context.MODE_PRIVATE)
        etAsp.setText(prefs.getString(CookieUtils.PREF_ASP, ""))
        etObsKul.setText(prefs.getString(CookieUtils.PREF_OBS, ""))

        val workManager = WorkManager.getInstance(this)

        workManager.getWorkInfosByTagLiveData(MonitorWorker.WORK_TAG)
            .observe(this) { workInfos ->
                tvStatus.text = resolveStatus(workInfos)
            }

        btnSaveStart.setOnClickListener {
            val aspValue = etAsp.text?.toString()?.trim().orEmpty()
            val obsValue = etObsKul.text?.toString()?.trim().orEmpty()

            if (aspValue.isEmpty() || obsValue.isEmpty()) {
                Toast.makeText(this, R.string.cookie_empty_message, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val cookieText = CookieUtils.formatCookieString(aspValue, obsValue)

            prefs.edit()
                .putString(CookieUtils.PREF_ASP, aspValue)
                .putString(CookieUtils.PREF_OBS, obsValue)
                .putString(CookieUtils.PREF_COMBINED_COOKIE, cookieText)
                .apply()
            requestNotificationPermissionIfNeeded()
            NotificationHelper.ensureChannels(this)

            val repeatingRequest = OneTimeWorkRequestBuilder<MonitorWorker>()
                .setInitialDelay(MonitorWorker.REPEAT_INTERVAL_MINUTES.toLong(), TimeUnit.MINUTES)
                .setInputData(MonitorWorker.createInputData(cookieText))
                .addTag(MonitorWorker.WORK_TAG)
                .build()

            val oneTimeRequest = OneTimeWorkRequestBuilder<MonitorWorker>()
                .setInitialDelay(5, TimeUnit.SECONDS)
                .setInputData(MonitorWorker.createInputData(cookieText))
                .addTag(MonitorWorker.WORK_TAG)
                .build()

            workManager.enqueueUniqueWork(
                MonitorWorker.UNIQUE_PERIODIC_WORK_NAME,
                ExistingWorkPolicy.REPLACE,
                repeatingRequest
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

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_settings -> {
                startActivity(Intent(this, SettingsActivity::class.java))
                true
            }
            else -> super.onOptionsItemSelected(item)
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
}
