package com.acrylic.outbound.pda.sync

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.acrylic.outbound.pda.network.ApiFailure
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.IOException
import java.util.concurrent.CancellationException

@RunWith(RobolectricTestRunner::class) @Config(sdk = [28], application = android.app.Application::class)
class SyncAttemptTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private var auth: JSONObject? = null
    private var now = 100_000L
    private var calls = 0
    private val state get() = SyncScheduleState(context)
    private var response: () -> List<SyncOutcome> = { listOf(SyncOutcome(SyncResult("done"))) }
    private fun run() = SyncAttempt(state, { auth }) { calls++; response() }.execute({ now }) {}
    @Before fun setup() {
        state.reset(); auth = JSONObject().put("userId", "u").put("deviceId", "d")
            .put("warehouseId", "w").put("serverRecoveryEpoch", "1").put("localSessionId", "login1")
    }

    @Test fun retryAfterSurvivesReconstructionAndEarlyWakeDoesNotSendRequest() {
        response = { now += 90_000; listOf(SyncOutcome(error = ApiFailure("RATE", "retry", 900, 429))) }
        assertEquals(900L, run()); assertEquals(1, calls)
        now += 60_000
        assertEquals(841L, run()); assertEquals(1, calls)
        now += 840_000; response = { listOf(SyncOutcome(SyncResult("done"))) }
        assertNull(run()); assertEquals(2, calls)
    }

    @Test fun persistentFailuresIncreaseBackoffAcrossWorkerInstances() {
        response = { throw IOException("offline") }
        assertEquals(30L, run()); now += 30_000
        assertEquals(60L, run()); now += 60_000
        assertEquals(120L, run())
    }

    @Test fun permanentFailureWaitsForExplicitResume() {
        response = { throw ApiFailure("PERMISSION_DENIED", "denied", httpStatus = 403) }
        assertNull(run()); now += 86_400_000; assertNull(run()); assertEquals(1, calls)
        state.reset(); response = { listOf(SyncOutcome(SyncResult("done"))) }
        assertNull(run()); assertEquals(2, calls)
    }

    @Test fun changedLoginCannotPersistOldSuccessOrSuspendNewLogin() {
        response = { auth!!.put("localSessionId", "login2"); throw IllegalStateException("bad old page") }
        assertEquals(30L, run()); assertTrue(state.ready(state.scope(auth!!), now))
        response = { listOf(SyncOutcome(SyncResult("done"))) }; assertNull(run()); assertEquals(2, calls)
    }

    @Test fun cancellationDoesNotWriteFailureOrDelay() {
        response = { throw CancellationException("stopped") }
        assertThrows(CancellationException::class.java) { run() }
        assertEquals(0, state.attempt(state.scope(auth!!))); assertTrue(state.ready(state.scope(auth!!), now))
    }

    @Test fun loggedOutOrBlockedSessionNeverInvokesRepositories() {
        auth!!.put("blockedReason", "expired"); assertNull(run())
        auth = null; assertNull(run()); assertEquals(0, calls)
    }

    @Test fun unsupportedStreamDoesNotHoldUpIncompleteSupportedBaseline() {
        response = { listOf(SyncOutcome(SyncResult("old", supported = false)), SyncOutcome(SyncResult("partial", more = true))) }
        assertEquals(30L, run()); now += 30_000
        response = { listOf(SyncOutcome(SyncResult("old", supported = false)), SyncOutcome(SyncResult("done"))) }
        assertEquals(21600L, run())
    }
}
