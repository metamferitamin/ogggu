package com.example.ogumonitor

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

class MonitorWorker(
    private val appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val cookie = inputData.getString(KEY_COOKIE)?.takeIf { it.isNotBlank() }
            ?: prefs.getString(PREF_COOKIE, null)?.takeIf { it.isNotBlank() }
            ?: run {
                val aspValue = prefs.getString(CookieUtils.PREF_ASP, null)?.takeIf { it.isNotBlank() }
                val obsValue = prefs.getString(CookieUtils.PREF_OBS, null)?.takeIf { it.isNotBlank() }
                if (aspValue != null && obsValue != null) {
                    CookieUtils.formatCookieString(aspValue, obsValue)
                } else {
                    null
                }
            }

        if (cookie.isNullOrBlank()) {
            return@withContext Result.failure()
        }

        ensureNotificationChannel()

        val client = OkHttpClient.Builder()
            .callTimeout(20, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .build()

        val request = Request.Builder()
            .url(RESULTS_URL)
            .header("User-Agent", USER_AGENT)
            .header("Cookie", cookie)
            .get()
            .build()

        val response = try {
            client.newCall(request).execute()
        } catch (ex: Exception) {
            return@withContext Result.retry()
        }

        response.use { resp ->
            if (!resp.isSuccessful) {
                return@withContext Result.retry()
            }

            val body = resp.body?.string().orEmpty()
            if (body.isBlank()) {
                return@withContext Result.retry()
            }

            val lowerBody = body.lowercase(Locale.ROOT)
            if (LOGIN_KEYWORDS.any { lowerBody.contains(it) }) {
                sendNotification(
                    title = appContext.getString(R.string.app_name),
                    message = appContext.getString(
                        R.string.notification_session_invalid,
                        currentTimestamp()
                    )
                )
                return@withContext successWithReschedule(cookie)
            }

            val fragment = extractFragment(body)
            if (fragment.isEmpty()) {
                sendNotification(
                    title = appContext.getString(R.string.app_name),
                    message = appContext.getString(
                        R.string.notification_table_missing,
                        currentTimestamp()
                    )
                )
                return@withContext successWithReschedule(cookie)
            }

            val newHash = computeHash(fragment)
            val lastHash = prefs.getString(PREF_LAST_HASH, null)

            if (lastHash == null) {
                prefs.edit().putString(PREF_LAST_HASH, newHash).apply()
                sendNotification(
                    title = appContext.getString(R.string.app_name),
                    message = appContext.getString(
                        R.string.notification_initial_snapshot,
                        currentTimestamp()
                    )
                )
                return@withContext successWithReschedule(cookie)
            }

            if (newHash != lastHash) {
                prefs.edit().putString(PREF_LAST_HASH, newHash).apply()
                sendNotification(
                    title = appContext.getString(R.string.notification_results_changed_title),
                    message = appContext.getString(
                        R.string.notification_results_changed_body,
                        currentTimestamp()
                    )
                )
            } else {
                sendNotification(
                    title = appContext.getString(R.string.app_name),
                    message = appContext.getString(
                        R.string.notification_no_change,
                        currentTimestamp()
                    )
                )
            }

            successWithReschedule(cookie)
        }
    }

    private fun successWithReschedule(cookie: String): Result {
        scheduleNextRun(cookie)
        return Result.success()
    }

    private fun scheduleNextRun(cookie: String) {
        if (isStopped) return

        val workManager = WorkManager.getInstance(appContext)
        val nextRequest = OneTimeWorkRequestBuilder<MonitorWorker>()
            .setInitialDelay(REPEAT_INTERVAL_MINUTES.toLong(), TimeUnit.MINUTES)
            .setInputData(createInputData(cookie))
            .addTag(WORK_TAG)
            .build()

        workManager.enqueueUniqueWork(
            UNIQUE_PERIODIC_WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            nextRequest
        )
    }

    private fun extractFragment(html: String): String {
        val document = Jsoup.parse(html)
        val table = document.getElementById("gvSinavSonuc") ?: return ""
        val cells = table.select("td")
        if (cells.isEmpty()) return ""
        return cells.joinToString(separator = "||") { it.text().trim() }
    }

    private fun computeHash(text: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val bytes = digest.digest(text.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { byte -> "%02x".format(byte) }
    }

    private fun ensureNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = android.app.NotificationChannel(
                CHANNEL_ID,
                "OGU Monitor",
                android.app.NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Sınav sonucu bildirimleri"
            }
            val manager = appContext.getSystemService(android.app.NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    private fun sendNotification(title: String, message: String) {
        val builder = NotificationCompat.Builder(appContext, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)

        val manager = NotificationManagerCompat.from(appContext)
        val hasPermission = ContextCompat.checkSelfPermission(
            appContext,
            android.Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED

        if (hasPermission || Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            manager.notify(NOTIFICATION_ID, builder.build())
        }
    }

    private fun currentTimestamp(): String {
        val formatter = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
        return formatter.format(Date())
    }

    companion object {
        const val WORK_TAG = "ogu-monitor-work"
        const val UNIQUE_PERIODIC_WORK_NAME = "ogumonitor-periodic"
        private const val CHANNEL_ID = "ogu-monitor"
        private const val RESULTS_URL = "https://ogubs1.ogu.edu.tr/SinavSonuc.aspx"
        private const val USER_AGENT = "Mozilla/5.0 (Android)"
        private const val KEY_COOKIE = "key_cookie"
        private const val PREFS_NAME = "ogu_prefs"
        private const val PREF_COOKIE = "cookie"
        private const val PREF_LAST_HASH = "last_hash"
        private const val NOTIFICATION_ID = 101
        private val LOGIN_KEYWORDS = listOf("giriş", "oturum", "login")
        const val REPEAT_INTERVAL_MINUTES = 1

        fun createInputData(cookie: String): Data = Data.Builder()
            .putString(KEY_COOKIE, cookie)
            .build()
    }
}
