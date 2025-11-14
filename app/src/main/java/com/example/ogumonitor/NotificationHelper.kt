package com.example.ogumonitor

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.media.AudioAttributes
import android.net.Uri
import android.os.Build

object NotificationHelper {
    const val STATUS_CHANNEL_ID = "ogu-monitor-status"
    const val CHANGE_CHANNEL_ID = "ogu-monitor-change"
    const val STATUS_NOTIFICATION_ID = 101
    const val CHANGE_NOTIFICATION_ID = 201

    fun ensureChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.deleteNotificationChannel(STATUS_CHANNEL_ID)
        manager.deleteNotificationChannel(CHANGE_CHANNEL_ID)

        val prefs = context.getSharedPreferences(CookieUtils.PREFS_NAME, Context.MODE_PRIVATE)
        val statusSoundUri = prefs.getString(CookieUtils.PREF_STATUS_SOUND_URI, null)?.toUriOrNull()
        val changeSoundUri = prefs.getString(CookieUtils.PREF_CHANGE_SOUND_URI, null)?.toUriOrNull()

        val audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_NOTIFICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

        val statusChannel = NotificationChannel(
            STATUS_CHANNEL_ID,
            context.getString(R.string.notification_channel_status_name),
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = context.getString(R.string.notification_channel_status_description)
            enableVibration(true)
            enableLights(true)
            if (statusSoundUri != null) {
                setSound(statusSoundUri, audioAttributes)
            }
        }

        val changeChannel = NotificationChannel(
            CHANGE_CHANNEL_ID,
            context.getString(R.string.notification_channel_change_name),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = context.getString(R.string.notification_channel_change_description)
            enableVibration(true)
            enableLights(true)
            if (changeSoundUri != null) {
                setSound(changeSoundUri, audioAttributes)
            }
        }

        manager.createNotificationChannel(statusChannel)
        manager.createNotificationChannel(changeChannel)
    }

    private fun String.toUriOrNull(): Uri? = try {
        Uri.parse(this)
    } catch (_: Exception) {
        null
    }
}
