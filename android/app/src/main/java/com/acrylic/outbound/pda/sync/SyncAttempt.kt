package com.acrylic.outbound.pda.sync

import com.acrylic.outbound.pda.network.SessionIdentity
import com.acrylic.outbound.pda.update.UpdateGate
import org.json.JSONObject
import java.util.concurrent.CancellationException

/** One bounded pass; all WorkManager entry points share the same admission and durable delay. */
internal class SyncAttempt(private val state: SyncScheduleState, private val current: () -> JSONObject?,
    private val synchronize: (SyncRun) -> List<SyncOutcome>) {
    fun execute(clock: () -> Long = System::currentTimeMillis, active: () -> Unit): Long? {
        active()
        val auth = current()?.let { JSONObject(it.toString()) } ?: return null
        if (auth.has("blockedReason")) return null
        val scope = state.scope(auth)
        if (state.stopped(scope)) return null
        if (!state.ready(scope, clock())) return state.remaining(scope, clock())
        val outcomes = try { synchronize(SyncRun(1, background = true, expected = auth, active = active)) }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (error: Exception) { listOf(SyncOutcome(error = error)) }
        active()
        try { SessionIdentity.requireSame(auth, current()) }
        catch (_: Exception) { return 30 }
        val attempt = state.attempt(scope)
        val decision = SyncRetry.decide(outcomes, attempt)
        val failed = outcomes.any { it.error != null && it.error !is UpdateGate.Busy }
        state.save(scope, decision, clock(), if (failed) attempt + 1 else 0)
        return decision.delaySeconds
    }
}
