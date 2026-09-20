package com.acrylic.outbound.pda.sync

import android.content.Context
import com.acrylic.outbound.pda.security.JwtVerifier
import org.json.JSONObject

/** No tokens, credentials, audit bodies or authorization are stored in scheduler metadata. */
class SyncScheduleState(context: Context) {
    private val prefs = context.getSharedPreferences("background-sync", Context.MODE_PRIVATE)
    fun scope(auth: JSONObject): String = JwtVerifier.sha(listOf("userId", "deviceId", "warehouseId",
        "serverRecoveryEpoch", "localSessionId").joinToString("\n") { auth.optString(it) }.toByteArray())

    fun ready(scope: String, now: Long): Boolean = prefs.getString("scope", "") != scope ||
        (!prefs.getBoolean("stopped", false) && now >= prefs.getLong("after", 0))

    fun stopped(scope: String): Boolean = prefs.getString("scope", "") == scope && prefs.getBoolean("stopped", false)
    fun attempt(scope: String): Int = if (prefs.getString("scope", "") == scope) prefs.getInt("attempt", 0) else 0
    fun remaining(scope: String, now: Long): Long = if (prefs.getString("scope", "") != scope) 0
        else ((prefs.getLong("after", 0) - now).coerceAtLeast(0) / 1000 + 1).coerceAtMost(86400)

    fun save(scope: String, decision: SyncDecision, now: Long, attempt: Int = 0) {
        val seconds = decision.delaySeconds ?: 0
        val after = if (seconds > (Long.MAX_VALUE - now) / 1000) Long.MAX_VALUE else now + seconds * 1000
        check(prefs.edit().putString("scope", scope).putString("message", decision.message)
            .putBoolean("stopped", decision.stopped).putLong("after", after).putLong("checkedAt", now)
            .putInt("attempt", attempt.coerceIn(0, 6)).commit())
    }

    fun reset() { check(prefs.edit().clear().commit()) }
    fun message(): String {
        val message = prefs.getString("message", null) ?: "连接 Wi-Fi 且空闲时自动同步；原记录保留"
        val checkedAt = prefs.getLong("checkedAt", 0)
        val time = if (checkedAt == 0L) "" else "\n最近核对：" + java.text.DateFormat.getDateTimeInstance().format(java.util.Date(checkedAt))
        return "自动同步：$message$time"
    }
}
