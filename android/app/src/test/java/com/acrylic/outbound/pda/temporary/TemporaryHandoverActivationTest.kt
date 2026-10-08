package com.acrylic.outbound.pda.temporary

import com.acrylic.outbound.pda.work.WorkDevice
import kotlinx.coroutines.*
import org.junit.*
import org.junit.Assert.*
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.*

@RunWith(RobolectricTestRunner::class) @Config(sdk = [28], application = android.app.Application::class)
@SQLiteMode(SQLiteMode.Mode.NATIVE)
class TemporaryHandoverActivationTest {
    @get:Rule val folder = TemporaryFolder()

    @Test fun activationInstallsIndependentCredentialAndReadonlyPrefixWithOriginalLocalCounters() = runBlocking<Unit>(Dispatchers.IO) {
        TemporaryHandoverActivationFixture(folder.root).use { f ->
            val originalStage = f.stage.repository.read(f.id)
            f.db.work().insertDevice(WorkDevice(originalStage.targetDeviceId, 9, 7))
            val draft = f.repository.install(f.id, f.original)
            assertEquals("HANDOVER_BLOCKED", draft.state); assertEquals(2, draft.workRound)
            assertFalse(draft.canScan(true)); assertEquals(2, draft.nextScanSeq)
            assertNotEquals(originalStage.targetAuthorizationId, draft.authorizationId)
            assertEquals(9, f.db.work().device(draft.creatorDeviceId)!!.nextCaptureSeq)
            assertEquals(8, f.db.work().device(draft.creatorDeviceId)!!.nextEventSeq)
            assertEquals(1, f.db.temporaryHandoverGuards().forCase(f.id).size)
            assertArrayEquals(originalStage.payloadBytes, f.stage.repository.read(f.id).payloadBytes)
            assertNull(f.db.temporaryContinuations().forTarget(draft.clientOrderId))
            assertEquals(originalStage.targetAuthorizationId, f.db.temporary().current(draft.operatorId, draft.creatorDeviceId, draft.warehouseId)!!.let { f.db.temporary().prepared(it.key)!!.authorizationId })
            f.stage.local.reopen()
            assertEquals(draft, f.repository.install(f.id, f.original))
            assertArrayEquals(f.original, f.db.temporaryHandoverActivations().get(f.id)!!.receiptBytes)
            assertEquals(8, f.db.work().device(draft.creatorDeviceId)!!.nextEventSeq)
        }
    }

    @Test fun finalAuditFailureRollsBackCredentialDraftGuardAndCounterTogether() = runBlocking<Unit>(Dispatchers.IO) {
        TemporaryHandoverActivationFixture(folder.root).use { f ->
            f.db.openHelper.writableDatabase.execSQL("CREATE TRIGGER handover_last_write AFTER INSERT ON temporary_event WHEN NEW.type='TEMP_HANDOVER_INSTALLED' BEGIN SELECT RAISE(ABORT,'D06_ACTIVATION_LAST_WRITE'); END")
            assertThrows(Exception::class.java) { f.repository.install(f.id, f.original) }
            assertNull(f.db.temporaryHandoverActivations().get(f.id)); assertNull(f.db.temporaryDrafts().get(f.client))
            assertNull(f.db.temporary().request(f.ticket)); assertTrue(f.db.temporaryHandoverGuards().forCase(f.id).isEmpty())
            assertNull(f.db.work().device(f.stage.session.getString("deviceId")))
            assertEquals("CONFIRMED", f.confirmation.repository.read(f.id)!!.state)
            f.db.openHelper.writableDatabase.execSQL("DROP TRIGGER handover_last_write"); f.stage.local.reopen()
            assertEquals("HANDOVER_BLOCKED", f.repository.install(f.id, f.original).state)
        }
    }

    @Test fun wrongSessionPrefixAndBoundAuthorizationRejectWithoutInstalling() = runBlocking<Unit>(Dispatchers.IO) {
        TemporaryHandoverActivationFixture(folder.root).use { f ->
            val invalid = listOf<(org.json.JSONObject) -> Unit>(
                { it.getJSONObject("businessDecision").put("targetSessionId", java.util.UUID.randomUUID().toString()) },
                { it.getJSONObject("businessDecision").put("sourcePrefixSha256", "a".repeat(64)) },
                { it.getJSONObject("businessDecision").put("newAuthorizationId", f.stage.prepared.authorizationId) },
                { it.getJSONObject("capabilities").put("unknownPermission", false) },
                { it.getJSONObject("capabilities").put("mayResumeScanning", "false") })
            for (change in invalid) {
                assertThrows(Exception::class.java) { f.repository.install(f.id, f.activation(change)) }
                assertNull(f.db.temporary().request(f.ticket)); assertNull(f.db.temporaryHandoverActivations().get(f.id))
            }
            assertEquals("HANDOVER_BLOCKED", f.repository.install(f.id, f.original).state)
        }
    }

    @Test fun shortenedCredentialRequiresSignedHandoverAndClosedDevelopmentRemainsDisabled() = runBlocking<Unit>(Dispatchers.IO) {
        TemporaryHandoverActivationFixture(folder.root).use { f ->
            assertThrows(Exception::class.java) { TemporaryVerifier.manifest(f.authorization, f.stage.signer.key) }
            assertThrows(Exception::class.java) { TemporaryHandoverActivationRepository(f.store, false).install(f.id, f.original) }
            assertNull(f.db.temporaryHandoverActivations().get(f.id))
            f.repository.install(f.id, f.original)
            val request = f.db.temporary().request(f.ticket)!!
            assertEquals(f.authorization.getString("manifestSha256"), f.db.temporary().prepared(f.ticket)!!.manifestSha256)
            assertNotNull(TemporaryCatalog.load(f.db, request))
        }
    }
}
