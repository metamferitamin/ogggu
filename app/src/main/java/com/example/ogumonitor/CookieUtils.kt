package com.example.ogumonitor

object CookieUtils {
    const val PREF_ASP = "asp_session_id"
    const val PREF_OBS = "obs_kul"

    fun formatCookieString(aspSessionId: String, obsKul: String): String {
        val aspPart = aspSessionId.trim()
        val obsPart = obsKul.trim()
        return "ASP.NET_SessionId=$aspPart; obsKul=$obsPart"
    }
}
