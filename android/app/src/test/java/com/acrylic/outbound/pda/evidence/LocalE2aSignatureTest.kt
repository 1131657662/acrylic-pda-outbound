package com.acrylic.outbound.pda.evidence

import com.acrylic.outbound.pda.temporary.TemporaryFreeze
import com.acrylic.outbound.pda.temporary.TemporaryWorkFixture
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
class LocalE2aSignatureTest {
    @get:Rule val folder = TemporaryFolder()

    @Test fun unavailableOriginalKeyRollsBackFreezeAndAllRequests() = runBlocking<Unit>(Dispatchers.IO) {
        TemporaryWorkFixture(folder.root).use { f ->
            f.start(); f.scan(); f.fixture.stopSigner.unavailable = true
            val before = checkNotNull(f.db.work().device(f.catalog.request.deviceId))
            assertThrows(IllegalStateException::class.java) { TemporaryFreeze(f.store).freeze(f.id, "短装") }
            assertEquals("SCANNING", f.db.temporaryDrafts().get(f.id)?.state)
            assertNull(f.db.temporarySubmissions().forOrder(f.id))
            assertNull(f.db.localE2aStops().get(f.id))
            assertEquals(0L, f.db.localE2aTransfers().requestCount(f.id))
            assertEquals(before, f.db.work().device(f.catalog.request.deviceId))
        }
    }

    @Test fun restartUsesOriginalSignatureWithoutCallingPrivateKeyAgain() = runBlocking<Unit>(Dispatchers.IO) {
        TemporaryWorkFixture(folder.root).use { f ->
            f.start(); f.scan(); TemporaryFreeze(f.store).freeze(f.id, "短装")
            val saved = checkNotNull(f.db.localE2aTransfers().request(f.id, "STOP", 0))
            f.fixture.stopSigner.unavailable = true
            f.reopen()
            val plan = LocalE2aTransferRepository(f.store).record(f.id)
            assertEquals(saved, f.db.localE2aTransfers().request(f.id, "STOP", 0))
            assertEquals(plan.stopOriginalKey, saved.originalKey)
            assertEquals(1, f.fixture.stopSigner.calls)
        }
    }

    @Test fun changedAuthorityDuringSigningRollsBackFreeze() = runBlocking<Unit>(Dispatchers.IO) {
        TemporaryWorkFixture(folder.root).use { f ->
            f.start(); f.scan()
            f.fixture.stopSigner.beforeSign = { checkNotNull(f.fixture.auth).put("userAuthVersion", "99") }
            assertThrows(IllegalStateException::class.java) { TemporaryFreeze(f.store).freeze(f.id, "短装") }
            assertEquals("SCANNING", f.db.temporaryDrafts().get(f.id)?.state)
            assertNull(f.db.temporarySubmissions().forOrder(f.id))
            assertNull(f.db.localE2aStops().get(f.id))
            assertEquals(0L, f.db.localE2aTransfers().requestCount(f.id))
        }
    }

    @Test fun substitutedPublicKeyCannotVerifyOriginalStop() = runBlocking<Unit>(Dispatchers.IO) {
        TemporaryWorkFixture(folder.root).use { f ->
            f.start(); f.scan(); TemporaryFreeze(f.store).freeze(f.id, "短装")
            val stop = checkNotNull(f.db.localE2aStops().get(f.id))
            val plan = checkNotNull(f.db.localE2aTransfers().get(f.id))
            val other = E2aTestDeviceSigner().sign(E2aTransferRequests.facts(stop,
                E2aWire.Manifest(plan.manifestJson, plan.manifestBytesHash, plan.setHash)))
            f.db.openHelper.writableDatabase.execSQL("UPDATE local_e2a_transfer SET devicePublicKey=?", arrayOf(other.publicKey))
            assertThrows(IllegalStateException::class.java) { LocalE2aTransferRepository(f.store).record(f.id) }
        }
    }

    @Test fun changedStopCoreCannotBeTrustedEvenWithRecomputedRequestDigest() = runBlocking<Unit>(Dispatchers.IO) {
        TemporaryWorkFixture(folder.root).use { f ->
            f.start(); f.scan(); TemporaryFreeze(f.store).freeze(f.id, "短装")
            val row = checkNotNull(f.db.localE2aTransfers().request(f.id, "STOP", 0))
            val body = JSONObject(row.requestJson)
            body.getJSONObject("stopProof").getJSONObject("core").put("captureHighWatermark", 999)
            val changed = E2aTransferRequests.saved(f.id, "STOP", 0, row.originalKey, body)
            f.db.openHelper.writableDatabase.execSQL(
                "UPDATE local_e2a_request SET requestJson=?,requestSha256=? WHERE operation='STOP'",
                arrayOf(changed.requestJson, changed.requestSha256))
            assertThrows(IllegalStateException::class.java) { LocalE2aTransferRepository(f.store).record(f.id) }
        }
    }
}
