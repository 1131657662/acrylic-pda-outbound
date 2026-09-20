package com.acrylic.outbound.pda.network

import org.json.JSONObject

/** Login generation is local metadata, never a credential or an authorization extension. */
class SessionChanged : IllegalStateException("登录身份或恢复代次已变化，请重新刷新")

object SessionIdentity {
    fun requireSame(expected: JSONObject, current: JSONObject?) {
        val fields = listOf("userId", "deviceId", "warehouseId", "serverRecoveryEpoch", "localSessionId")
        if (current == null || fields.any { expected.optString(it) != current.optString(it) }) throw SessionChanged()
    }
}

/** Only network/session mutations take this lock. Room readers must never acquire it. */
internal class SessionRequests(private val current: () -> JSONObject?) {
    private val lock = Any()
    fun <T> run(expected: JSONObject? = null, action: () -> T): T = synchronized(lock) {
        if (expected != null) SessionIdentity.requireSame(expected, current())
        action()
    }
}
