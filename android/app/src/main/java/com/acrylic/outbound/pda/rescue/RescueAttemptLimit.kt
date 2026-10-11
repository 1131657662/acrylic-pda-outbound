package com.acrylic.outbound.pda.rescue

import com.acrylic.outbound.pda.security.TimeSample
import org.json.JSONObject

/** A reboot starts a fresh cooldown, never removes the persisted failed-attempt penalty. */
internal object RescueAttemptLimit {
    private const val COOLDOWN = 15 * 60 * 1000L
    fun permitted(state: JSONObject, sample: TimeSample): Boolean {
        if (state.optInt("failures") < 5) return true
        if (sample.boot < 0 || sample.boot != state.optInt("lockBoot", -1) || sample.elapsed < state.optLong("lockStarted")) {
            state.put("lockBoot", sample.boot).put("lockStarted", sample.elapsed)
            return false
        }
        return sample.elapsed - state.getLong("lockStarted") >= COOLDOWN
    }
    fun failed(state: JSONObject, sample: TimeSample) {
        val failures = minOf(1000, state.optInt("failures") + 1)
        state.put("failures", failures)
        if (failures >= 5) state.put("lockBoot", sample.boot).put("lockStarted", sample.elapsed)
    }
    fun succeeded(state: JSONObject) { state.put("failures", 0).remove("lockStarted"); state.remove("lockBoot") }
}
