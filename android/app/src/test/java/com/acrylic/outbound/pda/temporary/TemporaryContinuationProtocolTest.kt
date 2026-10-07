package com.acrylic.outbound.pda.temporary

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.SQLiteMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import java.util.UUID

@RunWith(RobolectricTestRunner::class) @Config(sdk = [28], application = android.app.Application::class)
@SQLiteMode(SQLiteMode.Mode.NATIVE)
class TemporaryContinuationProtocolTest {
    @get:Rule val folder = TemporaryFolder()

    @Test fun signedActivationMatchesOriginalFrozenSource() = runBlocking<Unit>(Dispatchers.IO) {
        TemporaryContinuationFixture(folder.root).use { f ->
            val result = TemporaryContinuationProtocol.verify(f.raw(), f.input())
            assertEquals(2L, result.toRound); assertEquals(f.draft.nextScanSeq, result.nextOrderScanSeq)
            assertNotEquals(f.draft.authorizationId, result.authorization.getString("authorizationId"))
        }
    }

    @Test fun unsignedChangesAndWrongKeyAreRejected() = runBlocking<Unit>(Dispatchers.IO) {
        TemporaryContinuationFixture(folder.root).use { f ->
            val changed = JSONObject(String(f.raw())).put("status", "OTHER").toString().toByteArray()
            assertThrows(Exception::class.java) { TemporaryContinuationProtocol.verify(changed, f.input()) }
            val key = JSONObject(f.key.toString()).put("keyId", "wrong-key")
            assertThrows(Exception::class.java) { TemporaryContinuationProtocol.verify(f.raw(), f.input().copy(key = key)) }
        }
    }

    @Test fun oneShotPredecessorNeedsReceivedDigestButNoChunkDeclaration() = runBlocking<Unit>(Dispatchers.IO) {
        TemporaryContinuationFixture(folder.root).use { f ->
            val raw = f.raw { it.getJSONObject("originalContext").put("declaredUploadSha256", JSONObject.NULL) }
            assertEquals(2L, TemporaryContinuationProtocol.verify(raw, f.input()).toRound)
            for (field in listOf("receivedPayloadSha256", "declaredUploadSha256")) {
                val wrong = f.raw { it.getJSONObject("originalContext").put(field, "0".repeat(64)) }
                assertThrows(Exception::class.java) { TemporaryContinuationProtocol.verify(wrong, f.input()) }
            }
        }
    }

    @Test fun signedWrongSourceIdentityRoundAndSequenceAreRejected() = runBlocking<Unit>(Dispatchers.IO) {
        TemporaryContinuationFixture(folder.root).use { f ->
            val changes: List<(JSONObject) -> Unit> = listOf(
                { it.getJSONObject("originalContext").put("submissionId", UUID.randomUUID().toString()) },
                { it.getJSONObject("originalContext").put("authorizationId", UUID.randomUUID().toString()) },
                { it.getJSONObject("businessDecision").put("targetOperatorId", UUID.randomUUID().toString()) },
                { it.getJSONObject("businessDecision").put("nextOrderScanSeq", "3") },
                { it.getJSONObject("businessDecision").put("nextOrderScanSeq", 2) },
                { it.getJSONObject("businessDecision").put("toWorkRound", "3") },
                { it.getJSONObject("actor").put("deviceId", UUID.randomUUID().toString()) },
                { it.getJSONObject("capabilities").put("mayResumeScanning", true) })
            changes.forEachIndexed { index, change ->
                assertTrue("signed mismatch $index", runCatching { TemporaryContinuationProtocol.verify(f.raw(change), f.input()) }.isFailure)
            }
        }
    }

    @Test fun oldAuthorizationCannotBeReused() = runBlocking<Unit>(Dispatchers.IO) {
        TemporaryContinuationFixture(folder.root).use { f ->
            val raw = f.raw { it.getJSONObject("businessDecision").put("authorization", f.work.fixture.receipt)
                .put("newAuthorizationId", f.draft.authorizationId) }
            assertThrows(Exception::class.java) { TemporaryContinuationProtocol.verify(raw, f.input()) }
        }
    }

    @Test fun retainedManifestRehashCannotHideChangedOriginalScan() = runBlocking<Unit>(Dispatchers.IO) {
        TemporaryContinuationFixture(folder.root).use { f ->
            for ((field, value) in listOf("recordSha256" to "0".repeat(64), "expectedDispatchCycle" to "99",
                "baleId" to UUID.randomUUID().toString())) {
                val raw = f.raw { root -> root.getJSONObject("businessDecision").also {
                    it.getJSONArray("retained").getJSONObject(0).put(field, value); f.rehash(it)
                    root.getJSONObject("expected").put("retainedManifestSha256", it.get("retainedManifestSha256")) } }
                assertThrows(Exception::class.java) { TemporaryContinuationProtocol.verify(raw, f.input()) }
            }
        }
    }

    @Test fun duplicateAndNoncanonicalWireAreRejected() = runBlocking<Unit>(Dispatchers.IO) {
        TemporaryContinuationFixture(folder.root).use { f ->
            val raw = String(f.raw()); val invalid = listOf(raw + "{}", "{\"status\":\"RECORDED\"," + raw.drop(1),
                raw.replace("\"receiptVersion\":1", "\"receiptVersion\":1.0"), " " + raw)
            invalid.forEach { assertThrows(Exception::class.java) { TemporaryContinuationProtocol.verify(it.toByteArray(), f.input()) } }
        }
    }
}
