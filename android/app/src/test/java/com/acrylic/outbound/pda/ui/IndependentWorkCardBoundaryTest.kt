package com.acrylic.outbound.pda.ui

import com.acrylic.outbound.pda.data.LocalRecoveryControl
import com.acrylic.outbound.pda.security.TrustedTime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.SQLiteMode
import org.robolectric.shadows.ShadowSystemClock
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = RescueSignedFileFixture.TestApp::class)
@SQLiteMode(SQLiteMode.Mode.NATIVE)
class IndependentWorkCardBoundaryTest {
    @Test fun stoppedPageDefersCardConsumptionUntilForegroundResumes() {
        IndependentWorkCardUiFixture().use { f ->
            val picker = f.begin(); f.background(); f.deliverWhileStopped(picker)
            assertFalse(f.engine.state.value.ready); assertNoCapture(f)
            runBlocking(Dispatchers.IO) {
                assertEquals("PLANNED", f.base.app.database.independentTasks().state(f.original.id)?.state)
            }
            f.foreground(); f.await { f.engine.state.value.ready }
            assertEquals(f.original, f.engine.state.value.task?.task)
        }
    }

    @Test fun cancellationLeavesTaskAndScannerUnauthorized() {
        IndependentWorkCardUiFixture().use { f ->
            f.receive(f.begin(), null)
            assertFalse(f.engine.state.value.ready); assertFalse(f.engine.scanning)
            assertTrue(f.status.contains("取消"))
            assertNoCapture(f)
        }
    }

    @Test fun everyCapturedIdentityFieldChangeRejectsPreviouslySelectedCard() {
        IndependentWorkCardUiFixture().use { f ->
            val original = checkNotNull(f.base.vault.read()).toString()
            for (field in listOf("userId", "deviceId", "warehouseId", "serverRecoveryEpoch",
                "localSessionId", "userAuthVersion", "deviceVersion")) {
                f.base.vault.save(JSONObject(original)); val picker = f.begin()
                f.base.vault.save(JSONObject(original).put(field, "synthetic-changed"))
                f.receive(picker)
                assertFalse(f.engine.state.value.ready); assertTrue(field + ": " + f.status, f.status.contains("变化"))
                assertNoCapture(f)
            }
        }
    }

    @Test fun recoveryFenceRejectsCardAfterChooserReturns() {
        IndependentWorkCardUiFixture().use { f ->
            val picker = f.begin()
            runBlocking(Dispatchers.IO) {
                f.base.app.database.sync().saveRecovery(LocalRecoveryControl(observedServerEpoch = "1", mode = "RECONCILING"))
            }
            f.receive(picker)
            assertFalse(f.engine.state.value.ready); assertTrue(f.status.contains("未通过"))
            assertNoCapture(f)
        }
    }

    @Test fun signedCredentialExpiryRejectsCardWithoutSavingScan() {
        IndependentWorkCardUiFixture(60).use { f ->
            val picker = f.begin()
            ShadowSystemClock.advanceBy(Duration.ofSeconds(61))
            val session = checkNotNull(f.base.vault.read())
            assertTrue(TrustedTime.now(session.getJSONObject("timeAnchor"), TrustedTime.sample(f.base.app)) >=
                f.base.authority.issued.plusSeconds(60))
            f.receive(picker)
            assertFalse(f.engine.state.value.ready); assertTrue(f.status.contains("未通过"))
            assertNoCapture(f)
        }
    }

    @Test fun recreatedPageCannotReuseInFlightSelection() {
        IndependentWorkCardUiFixture().use { f ->
            val picker = f.begin(); f.recreate(); f.receive(picker)
            assertFalse(f.engine.state.value.ready)
            assertNoCapture(f)
        }
    }

    @Test fun cancelledNewSelectionCannotReusePreviouslyAuthorizedProcessor() {
        IndependentWorkCardUiFixture().use { f ->
            f.receive(f.begin()); f.await { f.engine.state.value.ready }
            f.receive(f.begin(), null)
            f.engine.resume(f.original.id); f.await { f.engine.state.value.queued == 0 }
            assertFalse(f.engine.state.value.ready)
            assertFalse(f.engine.receive("003A0000001", "BROADCAST"))
            assertNoCapture(f)
        }
    }

    @Test fun revokedCardCannotAuthorizeOriginalTask() {
        IndependentWorkCardUiFixture().use { f ->
            val picker = f.begin(); f.base.credentials.revoke(); f.receive(picker)
            assertFalse(f.engine.state.value.ready); assertTrue(f.status.contains("未通过"))
            assertTrue(f.base.credentialFile.exists()); assertNoCapture(f)
        }
    }

    @Test fun forgedCardCannotBypassPersistentAttemptLimit() {
        IndependentWorkCardUiFixture().use { f ->
            val card = f.task.card.card; val original = card.readText()
            card.writeText(JSONObject(original).put("credential", "x".repeat(32)).toString())
            repeat(5) { f.receive(f.begin()); assertNoCapture(f) }
            card.writeText(original); f.receive(f.begin())
            assertFalse(f.engine.state.value.ready); assertTrue(f.status.contains("未通过"))
            assertEquals(1, f.base.server.requestCount); assertNoCapture(f)
        }
    }

    private fun assertNoCapture(f: IndependentWorkCardUiFixture) = runBlocking(Dispatchers.IO) {
        assertTrue(f.base.app.database.independentScans().captures(f.original.id).isEmpty())
        assertEquals(0L, checkNotNull(f.base.app.database.independentTasks().state(f.original.id)).actualCount)
    }
}
