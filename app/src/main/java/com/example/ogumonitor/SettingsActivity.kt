package com.example.ogumonitor

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.OpenableColumns
import android.view.MenuItem
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.edit
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText

class SettingsActivity : AppCompatActivity() {

    private enum class SoundTarget { STATUS, CHANGE }

    private var pendingSoundTarget: SoundTarget? = null

    private val soundPicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { handleSoundPicked(it) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        val toolbar: MaterialToolbar = findViewById(R.id.settingsToolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        val prefs = getSharedPreferences(CookieUtils.PREFS_NAME, Context.MODE_PRIVATE)

        val etStatusMessage: TextInputEditText = findViewById(R.id.etStatusMessage)
        val etChangeMessage: TextInputEditText = findViewById(R.id.etChangeMessage)
        val btnStatusSound: MaterialButton = findViewById(R.id.btnStatusSound)
        val btnChangeSound: MaterialButton = findViewById(R.id.btnChangeSound)
        val tvStatusSound: TextView = findViewById(R.id.tvStatusSound)
        val tvChangeSound: TextView = findViewById(R.id.tvChangeSound)
        val btnSave: MaterialButton = findViewById(R.id.btnSaveSettings)
        val btnClearStatusSound: MaterialButton = findViewById(R.id.btnClearStatusSound)
        val btnClearChangeSound: MaterialButton = findViewById(R.id.btnClearChangeSound)

        etStatusMessage.setText(
            prefs.getString(CookieUtils.PREF_STATUS_MESSAGE, null)
                ?.takeIf { it.isNotBlank() }
                ?: getString(R.string.default_no_change_message)
        )
        etChangeMessage.setText(
            prefs.getString(CookieUtils.PREF_CHANGE_MESSAGE, null)
                ?.takeIf { it.isNotBlank() }
                ?: getString(R.string.default_change_message)
        )

        tvStatusSound.text = soundLabel(prefs.getString(CookieUtils.PREF_STATUS_SOUND_URI, null))
        tvChangeSound.text = soundLabel(prefs.getString(CookieUtils.PREF_CHANGE_SOUND_URI, null))

        btnStatusSound.setOnClickListener {
            pendingSoundTarget = SoundTarget.STATUS
            launchPicker()
        }

        btnChangeSound.setOnClickListener {
            pendingSoundTarget = SoundTarget.CHANGE
            launchPicker()
        }

        btnClearStatusSound.setOnClickListener {
            prefs.edit { remove(CookieUtils.PREF_STATUS_SOUND_URI) }
            tvStatusSound.text = getString(R.string.sound_default_label)
            NotificationHelper.ensureChannels(this)
        }

        btnClearChangeSound.setOnClickListener {
            prefs.edit { remove(CookieUtils.PREF_CHANGE_SOUND_URI) }
            tvChangeSound.text = getString(R.string.sound_default_label)
            NotificationHelper.ensureChannels(this)
        }

        btnSave.setOnClickListener {
            val statusMessage = etStatusMessage.text?.toString()?.trim().orEmpty()
            val changeMessage = etChangeMessage.text?.toString()?.trim().orEmpty()

            prefs.edit {
                putString(CookieUtils.PREF_STATUS_MESSAGE, statusMessage)
                putString(CookieUtils.PREF_CHANGE_MESSAGE, changeMessage)
            }
            NotificationHelper.ensureChannels(this)
            finish()
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                finish()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun launchPicker() {
        soundPicker.launch(arrayOf("audio/mpeg", "audio/*"))
    }

    private fun handleSoundPicked(uri: Uri) {
        val target = pendingSoundTarget ?: return
        pendingSoundTarget = null

        val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
        try {
            contentResolver.takePersistableUriPermission(uri, flags)
        } catch (_: SecurityException) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                try {
                    contentResolver.takePersistableUriPermission(uri, flags)
                } catch (_: SecurityException) {
                    // ignore if not persistable
                }
            }
        }

        val prefs = getSharedPreferences(CookieUtils.PREFS_NAME, Context.MODE_PRIVATE)
        val label = soundLabel(uri.toString())

        when (target) {
            SoundTarget.STATUS -> {
                prefs.edit { putString(CookieUtils.PREF_STATUS_SOUND_URI, uri.toString()) }
                findViewById<TextView>(R.id.tvStatusSound).text = label
            }
            SoundTarget.CHANGE -> {
                prefs.edit { putString(CookieUtils.PREF_CHANGE_SOUND_URI, uri.toString()) }
                findViewById<TextView>(R.id.tvChangeSound).text = label
            }
        }

        NotificationHelper.ensureChannels(this)
    }

    private fun soundLabel(uriString: String?): String {
        if (uriString.isNullOrBlank()) {
            return getString(R.string.sound_default_label)
        }

        return runCatching {
            val uri = Uri.parse(uriString)
            contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { cursor ->
                    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (cursor.moveToFirst() && nameIndex >= 0) {
                        cursor.getString(nameIndex)
                    } else {
                        null
                    }
                }
        }.getOrNull()
            ?: uriString.substringAfterLast('/')
            ?: uriString
    }
}
