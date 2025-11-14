package com.example.ogumonitor

object CookieUtils {
    const val PREFS_NAME = "ogu_prefs"
    const val PREF_ASP = "asp_session_id"
    const val PREF_OBS = "obs_kul"
    const val PREF_COMBINED_COOKIE = "cookie"
    const val PREF_LAST_HASH = "last_hash"
    const val PREF_STATUS_MESSAGE = "pref_status_message"
    const val PREF_CHANGE_MESSAGE = "pref_change_message"
    const val PREF_STATUS_SOUND_URI = "pref_status_sound_uri"
    const val PREF_CHANGE_SOUND_URI = "pref_change_sound_uri"

    fun formatCookieString(aspSessionId: String, obsKul: String): String {
        val aspPart = aspSessionId.trim()
        val obsPart = obsKul.trim()
        return "ASP.NET_SessionId=$aspPart; obsKul=$obsPart"
    }
}
