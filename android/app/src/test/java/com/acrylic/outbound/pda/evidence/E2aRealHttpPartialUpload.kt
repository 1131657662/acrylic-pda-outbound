package com.acrylic.outbound.pda.evidence

import com.acrylic.outbound.pda.temporary.TemporaryConnection
import com.acrylic.outbound.pda.temporary.TemporarySubmission
import com.acrylic.outbound.pda.temporary.TemporaryWorkFixture
import org.json.JSONObject
import org.junit.Assert.*

/** Keeps a real first chunk on the server without finalizing the frozen business payload. */
internal object E2aRealHttpPartialUpload {
    fun grow(work: TemporaryWorkFixture) {
        repeat(256) {
            val invalid = work.receive("INVALID-" + "x".repeat(110))
            work.processor.drain(work.id)
            work.processor.corrections.acknowledge(invalid.id)
        }
    }

    fun start(local: TemporarySubmission, connection: E2aRealHttpConnection) {
        val body = JSONObject(local.payload)
        val bytes = local.payload.toByteArray(Charsets.UTF_8)
        assertTrue("Requires an actually incomplete multi-chunk body", bytes.size > 262144)
        val control = JSONObject()
        for (key in listOf("source", "assignmentId", "continuationId", "authorizationId",
            "freezeDeviceId", "workRound", "sourceRecoveryEpoch")) control.put(key, body.get(key))
        val manifest = control.put("payloadSha256", local.sha256).put("byteCount", bytes.size.toString())
            .put("chunkCount", ((bytes.size + 262143) / 262144).toString())
        val session = checkNotNull(connection.current())
        val base = "pda/submissions/${local.id}"
        assertEquals("RECEIVING", connection.call(TemporaryConnection.Call(
            "$base/upload", session, manifest, epoch = "1")).getString("status"))
        connection.call(TemporaryConnection.Call("$base/chunks/0", session,
            raw = bytes.copyOfRange(0, 262144), epoch = "1"))
    }
}
