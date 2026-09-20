package com.acrylic.outbound.pda.sync

import com.acrylic.outbound.pda.network.SessionIdentity
import com.acrylic.outbound.pda.update.UpdateGate
import org.json.JSONObject

data class SyncResult(val message: String, val more: Boolean = false, val supported: Boolean = true)

/** Foreground keeps its existing bounds; background yields between every network request and commit. */
class SyncRun(val batches: Int = 20, private val background: Boolean = false, private val expected: JSONObject? = null,
    private val active: () -> Unit = {}) {
    init { require(batches in 1..20) }
    fun check() {
        active()
        if (background) UpdateGate.requireIdle()
    }
    fun identity(expected: JSONObject, current: () -> JSONObject) {
        check()
        this.expected?.let { SessionIdentity.requireSame(it, expected) }
        val latest = current()
        SessionIdentity.requireSame(expected, latest)
        check(!latest.has("blockedReason")) { latest.optString("blockedReason") }
    }
    fun <T> commit(action: () -> T): T {
        check()
        return if (background) UpdateGate.commitWhenIdle { check(); action() } else action()
    }
}
