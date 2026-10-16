package com.acrylic.outbound.pda.retention

import com.acrylic.outbound.pda.data.*
import com.acrylic.outbound.pda.preparation.*
import com.acrylic.outbound.pda.security.*
import com.acrylic.outbound.pda.submission.*
import com.acrylic.outbound.pda.work.*
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import java.io.File
import java.time.Instant

/** Product activation, durable capture/scan and freeze; no manufactured submission rows. */
internal object FormalRepositoryPreparation {
    fun freeze(attempt: File, input: JSONObject, db: PdaDatabase, session: com.acrylic.outbound.pda.network.PdaSession): LocalSubmission {
        val downloads = File(attempt, "formal-downloads"); check(downloads.mkdirs())
        val verified = ContractFixture.input(downloads, input)
        val prepared = LocalActivation(db).activate(verified)
        db.sync().saveRecovery(LocalRecoveryControl(observedServerEpoch = "1", mode = "NORMAL"))
        val ack = JSONObject().put("bundleId", prepared.bundleId).put("manifestSha256", prepared.manifestSha256)
            .put("assignmentEpoch", verified.assignment.getString("epoch"))
            .put("inventorySnapshotId", verified.assignment.getString("inventorySnapshotId")).put("activatedAt", verified.now.toString())
        session.call("pda/assignments/${prepared.assignmentId}/download-ack", ack, "1")
        val store = WorkStore(db, access(verified, input)); store.open(prepared.assignmentId)
        val processor = WorkProcessor(store) { _, _ -> }
        val inventory = JSONArray(verified.payloads.getValue("inventory").toString(Charsets.UTF_8))
        for (index in 0 until inventory.length()) {
            val capture = processor.reception.receive(WorkInput(prepared.assignmentId,
                inventory.getJSONObject(index).getString("barcode"), "BROADCAST", store.access.sample()))
            processor.drain(prepared.assignmentId)
            assertEquals("COMMITTED", db.captures().get(capture.id)!!.state)
        }
        assertEquals(2, db.work().guards(prepared.assignmentId).size)
        return SubmissionFreeze(store) { verified.now }.freeze(prepared.assignmentId, "")
    }

    private fun access(input: VerifiedPackage, fixture: JSONObject) = object : WorkAccess {
        private val auth = JSONObject(fixture.getJSONObject("session").toString()).put("signingKey", fixture.getJSONObject("signingKey"))
        override fun identity(order: PreparedOrder) {
            check(order.deviceId == auth.getString("deviceId") && order.operatorId == auth.getString("userId"))
        }
        override fun authorize(order: PreparedOrder): Instant {
            identity(order); JwtVerifier.authorization(JSONObject(order.assignmentJson), auth, fixture.getJSONObject("signingKey"), input.now)
            return input.now
        }
        override fun sample() = TimeSample(1000, input.now.toEpochMilli(), 1)
    }
}
