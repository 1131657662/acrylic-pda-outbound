package com.acrylic.outbound.pda.temporary

import com.acrylic.outbound.pda.submission.SubmissionJson
import org.json.JSONObject
import org.junit.Assert.*
import java.io.File

/** Registers the real frozen transport manifest, deliberately sends no chunks or business body. */
internal class TemporaryHandoverHttpLocal(private val fixture: TemporaryHandoverHttpTest) {
    fun register() {
        val db=fixture.db
        val client=db.temporaryHandoverActivations().get(fixture.caseId)!!.newClientOrderId
        val frozen=db.temporarySubmissions().forOrder(client)!!
        val body=JSONObject(frozen.payload);val manifest=JSONObject()
        for (field in listOf("schemaVersion","submissionId","source","assignmentId","continuationId","authorizationId",
            "freezeDeviceId","workRound","sourceRecoveryEpoch","temporaryHandover")) manifest.put(field,body.get(field))
        val size=frozen.payload.toByteArray(Charsets.UTF_8).size
        manifest.put("payloadSha256",frozen.sha256).put("byteCount",size.toString()).put("chunkCount",((size+262143)/262144).toString())
        val call=TemporaryConnection.Original("pda/temporary-orders/handover-submissions/${frozen.id}/upload",
            fixture.connection.current()!!,body=SubmissionJson.canonical(manifest).toByteArray(Charsets.UTF_8))
        val first=fixture.connection.original(call)
        assertEquals("RECEIVING",JSONObject(first.toString(Charsets.UTF_8)).getJSONObject("data").getString("status"))
        fixture.reopen("local-manifest-lost-state")
        val second=fixture.connection.original(call)
        assertEquals(JSONObject(first.toString(Charsets.UTF_8)).getJSONObject("data").toString(),
            JSONObject(second.toString(Charsets.UTF_8)).getJSONObject("data").toString())
        assertEquals(frozen,fixture.db.temporarySubmissions().get(frozen.id))
        File(fixture.output.parentFile,"localRegister-result.json").writeText(JSONObject().put("submissionId",frozen.id)
            .put("state","RECEIVING").put("payloadSha256",frozen.sha256).toString())
        File(fixture.output.parentFile,"local-upload-manifest.json").writeText(SubmissionJson.canonical(manifest))
    }
}
