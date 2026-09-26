package com.acrylic.outbound.pda.evidence

import com.acrylic.outbound.pda.submission.SubmissionJson
import com.acrylic.outbound.pda.temporary.TemporaryConnection
import com.acrylic.outbound.pda.temporary.TemporaryWorkFixture
import com.acrylic.outbound.pda.temporary.TemporaryWorkStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.SQLiteMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = android.app.Application::class)
@SQLiteMode(SQLiteMode.Mode.NATIVE)
class LocalDraftStopSignatureTest {
    @get:Rule val folder = TemporaryFolder()

    @Test fun restartAndRetryPreserveOriginalSignatureWithoutSigningAgain() = runBlocking<Unit>(Dispatchers.IO) {
        TemporaryWorkFixture(folder.root).use { f ->
            f.start(); f.receive(source = "MANUAL")
            val stop = LocalDraftStopRepository(f.store).stop(f.id)
            val signed = checkNotNull(f.db.localDraftStopSignatures().get(f.id))
            f.fixture.stopSigner.unavailable = true; f.reopen()
            assertEquals(stop, LocalDraftStopRepository(f.store).read(f.id))
            assertEquals(stop, LocalDraftStopRepository(f.store).stop(f.id))
            assertEquals(signed, f.db.localDraftStopSignatures().get(f.id))
            assertEquals(1, f.fixture.stopSigner.calls)
            LocalDraftStopProof.verify(stop, JSONObject(signed.proofJson), signed.publicKey)
            System.getenv("LOCAL_DRAFT_STOP_VECTOR")?.let { path ->
                val vector = JSONObject().put("metadata", JSONObject(stop.metadataJson))
                    .put("proof", JSONObject(signed.proofJson)).put("publicKey", signed.publicKey)
                java.io.File(path).writeText(SubmissionJson.canonical(vector), Charsets.UTF_8)
            }
        }
    }

    @Test fun unavailablePrivateKeyRollsBackEntireStop() = rollback { f -> f.fixture.stopSigner.unavailable = true }

    @Test fun sessionChangeDuringSigningRollsBackEntireStop() = rollback { f ->
        f.fixture.stopSigner.beforeSign = { checkNotNull(f.fixture.auth).put("userAuthVersion", "99") }
    }

    @Test fun signatureStorageFailureRollsBackEntireStop() = rollback { f ->
        f.db.openHelper.writableDatabase.execSQL("""CREATE TRIGGER fail_stop_signature BEFORE INSERT ON
            local_draft_stop_signature BEGIN SELECT RAISE(ABORT,'synthetic storage failure'); END""")
    }

    @Test fun keyChangeDuringSigningRollsBackEntireStop() = rollback { f ->
        val other = E2aTestDeviceSigner(); var changed = false
        val connection = object : TemporaryConnection by f.fixture {
            override fun localDraftStopKey() = if (changed) other.publicKey() else f.fixture.localDraftStopKey()
            override fun signLocalDraftStop(stop: LocalDraftStop): LocalDraftStopProof.Signed {
                val result = f.fixture.signLocalDraftStop(stop); changed = true; return result
            }
        }
        f.store = TemporaryWorkStore(f.db, connection, f.store.sample)
    }

    @Test fun missingOriginalSignatureNeverGetsReplaced() = runBlocking<Unit>(Dispatchers.IO) {
        TemporaryWorkFixture(folder.root).use { f ->
            val stop = LocalDraftStopRepository(f.store).stop(f.id)
            f.db.openHelper.writableDatabase.execSQL("DELETE FROM local_draft_stop_signature")
            f.reopen()
            assertThrows(IllegalStateException::class.java) { LocalDraftStopRepository(f.store).read(f.id) }
            assertThrows(IllegalStateException::class.java) { LocalDraftStopRepository(f.store).stop(f.id) }
            assertEquals(stop, f.db.localDraftStops().get(f.id)); assertEquals(1, f.fixture.stopSigner.calls)
            assertNull(f.db.localDraftStopSignatures().get(f.id))
        }
    }

    @Test fun alteredSignedClaimsCannotBeTrusted() = runBlocking<Unit>(Dispatchers.IO) {
        TemporaryWorkFixture(folder.root).use { f ->
            val stop = LocalDraftStopRepository(f.store).stop(f.id)
            val saved = checkNotNull(f.db.localDraftStopSignatures().get(f.id))
            for (field in listOf("rootSha256", "metadataSha256", "creatorDeviceId", "clientOrderId", "captureHighWatermark")) {
                val proof = JSONObject(saved.proofJson)
                proof.getJSONObject("core").put(field, if (field.endsWith("Sha256")) "0".repeat(64) else "changed")
                f.db.openHelper.writableDatabase.execSQL("UPDATE local_draft_stop_signature SET proofJson=?",
                    arrayOf(SubmissionJson.canonical(proof)))
                assertThrows(IllegalStateException::class.java) { LocalDraftStopRepository(f.store).read(f.id) }
            }
            assertEquals(stop, f.db.localDraftStops().get(f.id)); assertEquals(1, f.fixture.stopSigner.calls)
        }
    }

    @Test fun otherKeyCannotReplaceSignedIdentity() = runBlocking<Unit>(Dispatchers.IO) {
        TemporaryWorkFixture(folder.root).use { f ->
            val stop = LocalDraftStopRepository(f.store).stop(f.id)
            val other = E2aTestDeviceSigner().sign(stop)
            f.db.openHelper.writableDatabase.execSQL("UPDATE local_draft_stop_signature SET publicKey=?,proofJson=?",
                arrayOf(other.publicKey, SubmissionJson.canonical(other.envelope)))
            assertThrows(IllegalStateException::class.java) { LocalDraftStopRepository(f.store).read(f.id) }
            assertEquals(stop, f.db.localDraftStops().get(f.id))
        }
    }

    @Test fun corruptSignatureCannotBeRead() = runBlocking<Unit>(Dispatchers.IO) {
        TemporaryWorkFixture(folder.root).use { f ->
            LocalDraftStopRepository(f.store).stop(f.id)
            val saved = checkNotNull(f.db.localDraftStopSignatures().get(f.id))
            val proof = JSONObject(saved.proofJson).put("signature", "AA==")
            f.db.openHelper.writableDatabase.execSQL("UPDATE local_draft_stop_signature SET proofJson=?",
                arrayOf(SubmissionJson.canonical(proof)))
            assertThrows(Exception::class.java) { LocalDraftStopRepository(f.store).read(f.id) }
            assertEquals(1, f.fixture.stopSigner.calls)
        }
    }

    private fun rollback(inject: (TemporaryWorkFixture) -> Unit) = runBlocking<Unit>(Dispatchers.IO) {
        TemporaryWorkFixture(folder.root).use { f ->
            f.start(); val input = f.receive(source = "MANUAL")
            val draft = f.db.temporaryDrafts().get(f.id)
            val device = f.db.work().device(f.catalog.request.deviceId)
            val events = f.db.temporaryScans().events(f.id)
            inject(f)
            assertThrows(Exception::class.java) { LocalDraftStopRepository(f.store).stop(f.id) }
            assertEquals(draft, f.db.temporaryDrafts().get(f.id))
            assertEquals(device, f.db.work().device(draft!!.creatorDeviceId))
            assertEquals(events, f.db.temporaryScans().events(f.id)); assertEquals(input, f.db.temporaryScans().capture(input.id))
            assertNull(f.db.localDraftStops().get(f.id)); assertEquals(0L, f.db.localDraftStops().count(f.id))
            assertNull(f.db.localDraftStopSignatures().get(f.id)); assertNull(f.db.temporarySubmissions().forOrder(f.id))
        }
    }
}
