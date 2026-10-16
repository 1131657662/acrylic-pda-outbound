package com.acrylic.outbound.pda.retention

import com.acrylic.outbound.pda.data.*
import com.acrylic.outbound.pda.network.PdaNetwork
import com.acrylic.outbound.pda.preparation.*
import com.acrylic.outbound.pda.security.*
import com.acrylic.outbound.pda.submission.*
import com.acrylic.outbound.pda.temporary.ArchiveRestoreRoom
import com.acrylic.outbound.pda.work.*
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import java.io.File
import java.time.Instant

/** Synthetic identity, real product activation/scan/freeze and explicit public HTTP test adapter. */
internal class FormalArchiveWork(private val attempt: File, private val input: JSONObject, val db: PdaDatabase) {
    private val auth = JSONObject(input.getJSONObject("session").toString()).put("signingKey", input.getJSONObject("signingKey"))
    private val key = input.getJSONObject("signingKey")
    private val network = PdaNetwork(input.getString("baseUrl"))
    private val headers = mapOf("Authorization" to "Bearer ${input.getString("accessToken")}", "X-Server-Recovery-Epoch" to "1")

    fun call(path: String, body: JSONObject? = null, label: String): JSONObject {
        val raw = network.original(path, body?.toString()?.toByteArray(Charsets.UTF_8), headers)
        val file = File(attempt, "$label.bin"); check(file.createNewFile()); file.writeBytes(raw)
        return JSONObject(raw.toString(Charsets.UTF_8)).getJSONObject("data")
    }

    fun source() {
        val downloads = File(attempt, "formal-downloads"); check(downloads.mkdirs())
        val verified = ContractFixture.input(downloads, input)
        val prepared = LocalActivation(db).activate(verified)
        db.sync().saveRecovery(LocalRecoveryControl(observedServerEpoch = "1", mode = "NORMAL"))
        val ack = JSONObject().put("bundleId", prepared.bundleId).put("manifestSha256", prepared.manifestSha256)
            .put("assignmentEpoch", verified.assignment.getString("epoch"))
            .put("inventorySnapshotId", verified.assignment.getString("inventorySnapshotId")).put("activatedAt", verified.now.toString())
        call("pda/assignments/${prepared.assignmentId}/download-ack", ack, "formal-ack")
        val store = WorkStore(db, access(verified)); store.open(prepared.assignmentId)
        val processor = WorkProcessor(store) { _, _ -> }
        val inventory = JSONArray(verified.payloads.getValue("inventory").toString(Charsets.UTF_8))
        for (index in 0 until inventory.length()) {
            val capture = processor.reception.receive(WorkInput(prepared.assignmentId,
                inventory.getJSONObject(index).getString("barcode"), "BROADCAST", store.access.sample()))
            processor.drain(prepared.assignmentId)
            assertEquals("COMMITTED", db.captures().get(capture.id)!!.state)
        }
        assertEquals(2, db.work().guards(prepared.assignmentId).size)
        val local = SubmissionFreeze(store) { verified.now }.freeze(prepared.assignmentId, "")
        val response = call("pda/orders/${prepared.orderId}/submissions", JSONObject(local.payload), "formal-source-success")
        assertEquals("SYNCED", ReceiptVerifier.verify(local, response, key))
        db.submissions().progress(local.id, "SYNCED", response.toString(), "", false)
        archive(local, "formal-source-archive")
        val original = JSONObject().put("id", local.id).put("assignmentId", prepared.assignmentId)
            .put("payload", local.payload).put("key", key)
        File(attempt, "formal-original.json").writeText(original.toString())
    }

    private fun access(input: VerifiedPackage) = object : WorkAccess {
        override fun identity(order: PreparedOrder) {
            check(order.deviceId == auth.getString("deviceId") && order.operatorId == auth.getString("userId"))
        }
        override fun authorize(order: PreparedOrder): Instant {
            identity(order); JwtVerifier.authorization(JSONObject(order.assignmentJson), auth, key, input.now)
            return input.now
        }
        override fun sample() = TimeSample(1000, input.now.toEpochMilli(), 1)
    }

    private fun archive(local: LocalSubmission, label: String) {
        val repository = SubmissionArchiveRepository(db, { auth }, { Instant.now() }) { id, body, epoch ->
            check(id == local.id && epoch == "1")
            call("pda/submissions/$id/archive-confirmation", body, label)
        }
        assertTrue(repository.refresh().contains("已核对"))
        val proof = checkNotNull(db.archives().get(local.id, "1"))
        assertFalse(JSONObject(proof.confirmationJson).getBoolean("localDeletionAllowed"))
    }

    fun restored() {
        val original = JSONObject(File(attempt, "formal-original.json").readText())
        val local = checkNotNull(db.submissions().get(original.getString("id")))
        assertEquals(original.getString("payload"), local.payload)
        assertEquals(2, db.work().guards(local.assignmentId).size)
        val before = ArchiveRestoreRoom.snapshot(db)
        val receipt = call("pda/submissions/${local.id}", label = "formal-restored-success")
        assertEquals("SYNCED", ReceiptVerifier.verify(local, receipt, key))
        assertEquals(SubmissionJson.canonical(JSONObject(local.receipt!!)), SubmissionJson.canonical(receipt))
        val request = SubmissionArchiveVerifier.request(local, key)
        val proof = call("pda/submissions/${local.id}/archive-confirmation", request, "formal-restored-archive")
        SubmissionArchiveVerifier.verify(local, auth, request, proof, key)
        assertEquals(SubmissionJson.canonical(JSONObject(db.archives().get(local.id, "1")!!.confirmationJson)),
            SubmissionJson.canonical(proof))
        assertEquals(SubmissionJson.canonical(before), SubmissionJson.canonical(ArchiveRestoreRoom.snapshot(db)))
    }
}
