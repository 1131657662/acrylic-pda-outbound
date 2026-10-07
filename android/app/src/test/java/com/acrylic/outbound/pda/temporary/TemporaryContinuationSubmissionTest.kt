package com.acrylic.outbound.pda.temporary

import com.acrylic.outbound.pda.network.ApiFailure
import com.acrylic.outbound.pda.submission.SubmissionJson
import kotlinx.coroutines.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.SQLiteMode
import java.io.IOException
import java.util.UUID

@RunWith(RobolectricTestRunner::class) @Config(sdk = [28], application = android.app.Application::class)
@SQLiteMode(SQLiteMode.Mode.NATIVE)
class TemporaryContinuationSubmissionTest {
    @get:Rule val folder = TemporaryFolder()

    @Test fun lostIntakeResponseRecoversOriginalRoundAndConsumesSignedSuccess() = runBlocking<Unit>(Dispatchers.IO) {
        TemporaryContinuationFixture(folder.root).use { f ->
            val local = frozen(f); val paths = mutableListOf<String>(); val result = success(f, local)
            var submitted = false
            val connection = object : TemporaryConnection by f.work.fixture {
                override fun original(request: TemporaryConnection.Original): ByteArray {
                    paths.add(request.path)
                    if (request.body != null) {
                        assertEquals(local.payload, request.body.toString(Charsets.UTF_8)); submitted = true
                        throw IOException("lost intake response")
                    }
                    if (!submitted) throw ApiFailure("SUBMISSION_NOT_FOUND", "missing")
                    return result
                }
            }
            f.db.temporarySubmissions().queue(local.id)
            assertThrows(IOException::class.java) { TemporarySubmissionRepository(f.db, connection).upload(local.id) }
            f.work.reopen()
            val saved = TemporarySubmissionRepository(f.db, connection).upload(local.id)
            assertEquals("SYNCED", saved.state)
            assertEquals(listOf("pda/temporary-orders/continuation-submissions/${local.id}",
                "pda/temporary-orders/continuation-submissions", "pda/temporary-orders/continuation-submissions/${local.id}"), paths)
            assertEquals("2", JSONObject(saved.receipt!!).getString("workRound"))
            assertEquals(f.frozen, f.db.temporarySubmissions().get(f.frozen.id))
            assertArrayEquals(result, f.db.temporarySuccess().response(local.id,
                f.db.temporarySuccess().evidence(local.id)!!.responseSha256)!!.responseBytes)
        }
    }

    @Test fun signedWrongRoundIsRetainedButCannotBecomeSuccess() = runBlocking<Unit>(Dispatchers.IO) {
        TemporaryContinuationFixture(folder.root).use { f ->
            val local = frozen(f); val repository = TemporarySuccessRepository(f.db)
            val bytes = success(f, local, "1"); val hash = repository.retain(local.id, bytes)
            assertThrows(Exception::class.java) { repository.verify(local.id, hash, f.key) }
            assertNull(f.db.temporarySuccess().evidence(local.id))
            assertArrayEquals(bytes, f.db.temporarySuccess().response(local.id, hash)!!.responseBytes)
        }
    }

    @Test fun continuationCancelCannotPersistAnUnauthorizedIntent() = runBlocking<Unit>(Dispatchers.IO) {
        TemporaryContinuationFixture(folder.root).use { f ->
            val local = frozen(f)
            assertThrows(Exception::class.java) { TemporarySubmissionRepository(f.db, f.work.fixture).abort(local.id, "取消") }
            assertEquals(local, f.db.temporarySubmissions().get(local.id))
        }
    }

    @Test fun signedSuccessCannotOmitOrOpenContinuationDeletion() = runBlocking<Unit>(Dispatchers.IO) {
        TemporaryContinuationFixture(folder.root).use { f ->
            val local = frozen(f)
            for (deletion in listOf(true, JSONObject.NULL, "missing")) {
                val envelope = JSONObject(success(f, local).toString(Charsets.UTF_8))
                val receipt = envelope.getJSONObject("data").apply { remove("signedReceipt") }
                if (deletion == "missing") receipt.remove("localDeletionAllowed") else receipt.put("localDeletionAllowed", deletion)
                val token = f.sign(JSONObject().put("iss", "acrylic-outbound").put("aud", "acrylic-pda-receipt").put("receipt", receipt))
                receipt.put("signedReceipt", token)
                assertThrows(Exception::class.java) { TemporarySuccessVerifier.verify(local,
                    SubmissionJson.canonical(envelope).toByteArray(), f.key) }
            }
        }
    }

    private fun frozen(f: TemporaryContinuationFixture): TemporarySubmission {
        val next = f.activate(); val ticket = f.receipt.getJSONObject("businessDecision").getString("ticketId")
        f.work.fixture.ack = f.downloadAck(f.db.temporary().request(ticket)!!)
        TemporaryContinuationDownload(f.work.store).acknowledge(ticket)
        val store = TemporaryWorkStore(f.db, f.work.fixture, true, f.work.store.sample)
        TemporaryContinuationWork(store).start(next.clientOrderId)
        return TemporaryFreeze(store).freeze(next.clientOrderId, "保留包合成续作")
    }

    private fun success(f: TemporaryContinuationFixture, local: TemporarySubmission, round: String = "2"): ByteArray {
        val body = JSONObject(local.payload); val scan = body.getJSONArray("scans").getJSONObject(0)
        val result = JSONObject().put("submissionId", local.id).put("payloadSha256", local.sha256)
            .put("orderId", UUID.randomUUID().toString()).put("orderNo", "SYNTHETIC-CONTINUATION")
            .put("status", "SUCCEEDED").put("orderStatus", "COMPLETED").put("receiptVersion", 1).put("resultVersion", "1")
            .put("workRound", round).put("freezeDeviceId", body.get("freezeDeviceId"))
            .put("issuedRecoveryEpoch", body.get("sourceRecoveryEpoch")).put("source", body.get("source"))
            .put("captureHighWatermark", body.get("captureHighWatermark")).put("firstOrderScanSeq", "1").put("lastOrderScanSeq", "1")
            .put("actualCount", "1").put("actualWeightGrams", scan.get("weightGrams")).put("inventoryDeducted", body.get("autoDeductInventory"))
            .put("completedAt", f.work.fixture.time.toString()).put("mayResumeScanning", false).put("stockReleased", false)
            .put("localDeletionAllowed", false)
        val items = org.json.JSONArray(); val lines = body.getJSONObject("temporaryOrder").getJSONArray("lines")
        for (i in 0 until lines.length()) {
            val code = lines.getJSONObject(i).getString("deliveryCode"); val active = code == scan.getString("deliveryCode")
            items.put(JSONObject().put("deliveryCode", code).put("actualCount", if (active) "1" else "0")
                .put("actualWeightGrams", if (active) scan.getString("weightGrams") else "0"))
        }
        result.put("items", items)
        val token = f.sign(JSONObject().put("iss", "acrylic-outbound").put("aud", "acrylic-pda-receipt").put("receipt", result))
        result.put("signedReceipt", token)
        return SubmissionJson.canonical(JSONObject().put("code", "OK").put("data", result)
            .put("serviceMode", "NORMAL").put("serverRecoveryEpoch", "1")).toByteArray(Charsets.UTF_8)
    }
}
