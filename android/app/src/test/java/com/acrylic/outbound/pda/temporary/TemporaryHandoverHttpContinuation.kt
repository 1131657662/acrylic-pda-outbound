package com.acrylic.outbound.pda.temporary

import com.acrylic.outbound.pda.submission.SubmissionJson
import com.acrylic.outbound.pda.security.TimeSample
import org.json.JSONObject
import org.junit.Assert.*
import java.io.File
import java.io.IOException

/** Actual R06 receipts installed over the previous TEMP handover Room database. */
internal class TemporaryHandoverHttpContinuation(private val fixture: TemporaryHandoverHttpTest) {
    private val db get() = fixture.db
    private val root get() = fixture.output.parentFile!!
    private val source get() = db.temporaryHandoverActivations().get(fixture.caseId)!!.newClientOrderId
    private val connection = object : TemporaryConnection by fixture.connection {
        var lostUpload = false
        var lostSuccess = false
        override fun original(request: TemporaryConnection.Original): ByteArray {
            val response = fixture.connection.original(request)
            if (!request.path.contains("continuation-submissions")) return response
            val status = JSONObject(response.toString(Charsets.UTF_8)).getJSONObject("data").optString("status")
            if (status == "REVIEW_REQUIRED" && !lostUpload) {
                lostUpload = true; throw IOException("Injected lost actual mixed continuation intake")
            }
            if (status == "SUCCEEDED" && !lostSuccess) {
                lostSuccess = true; throw IOException("Injected lost actual mixed continuation success")
            }
            return response
        }
    }
    private fun store() = TemporaryWorkStore(db, connection, true, true) { TimeSample(1000, 500, 1) }

    fun activateAndSubmit(scanCount: Int = 2) {
        val original = db.temporarySubmissions().forOrder(source)!!
        val raw = File(root,"server-continuation-activation.json").readBytes()
        rollback(raw)
        val next = TemporaryContinuationActivation(store()).activate(source,raw)
        assertEquals("CONTINUATION_BLOCKED",next.state)
        assertEquals(3L,next.workRound)
        fixture.reopen("continuation-installed")
        assertEquals(next,TemporaryContinuationActivation(store()).activate(source,raw))
        val link=db.temporaryContinuations().forSource(source)!!
        val coordinator=TemporaryContinuationCoordinator(store());coordinator.queue(link.ticketId)
        assertThrows(IOException::class.java) { coordinator.resume(link.ticketId) }
        assertNull(db.temporary().prepared(link.ticketId)!!.ackJson)
        fixture.reopen("continuation-lost-ack")
        assertEquals("READY",TemporaryContinuationCoordinator(store()).resume(link.ticketId).state)
        val working=store();working.load(next.clientOrderId)
        repeat(scanCount) { scan(working,next.clientOrderId) }
        val reason = if (scanCount == 1) "隔离反向混合链，剩余实物在停止后交接" else ""
        val frozen=TemporaryFreeze(working).freeze(next.clientOrderId,reason)
        prefix(original,frozen,scanCount)
        File(root,"android-continuation-payload.json").writeText(frozen.payload)
        assertThrows(IOException::class.java) { TemporarySubmissionRepository(db,connection).upload(frozen.id) }
        fixture.reopen("continuation-lost-upload")
        assertEquals("REVIEW_REQUIRED",TemporarySubmissionRepository(db,connection).upload(frozen.id).state)
        assertEquals(original,db.temporarySubmissions().get(original.id))
        val phase = if (scanCount == 1) "continuationShort" else "continuation"
        File(root,"$phase-result.json").writeText(JSONObject().put("submissionId",frozen.id)
            .put("state","REVIEW_REQUIRED").put("payloadSha256",frozen.sha256).toString())
    }

    private fun rollback(raw: ByteArray) {
        db.openHelper.writableDatabase.execSQL("CREATE TRIGGER mixed_activation_tail AFTER INSERT ON temporary_continuation BEGIN SELECT RAISE(ABORT,'D06_MIXED_ROOM_TAIL'); END")
        try {
            assertThrows(Exception::class.java) { TemporaryContinuationActivation(store()).activate(source,raw) }
            assertNull(db.temporaryContinuations().forSource(source))
        } finally { db.openHelper.writableDatabase.execSQL("DROP TRIGGER mixed_activation_tail") }
        fixture.reopen("continuation-room-rollback")
    }

    private fun scan(working: TemporaryWorkStore,id: String) {
        val totals=working.products(id).associate { it.deliveryCode to it.count }
        val remaining=db.temporaryDrafts().lines(id).filter { (totals[it.deliveryCode]?:0)<it.plannedCount }.map { it.deliveryCode }.toSet()
        val old=working.page(id,0).map { it.barcode }.toSet()
        val bale=working.source(id).bales.values.first { it.status=="AVAILABLE" && it.code in remaining && it.barcode !in old }
        val processor=TemporaryProcessor(working) {}
        val row=processor.reception.receive(TemporaryInput(id,bale.barcode,"BROADCAST",working.sample()))
        processor.drain(id);assertEquals("COMMITTED",db.temporaryScans().capture(row.id)!!.state)
    }

    private fun prefix(original: TemporarySubmission,next: TemporarySubmission,scanCount: Int) {
        val old=JSONObject(original.payload);val body=JSONObject(next.payload)
        assertEquals("3",body.getString("workRound"))
        assertEquals((old.getString("expectedCount").toLong() + scanCount).toString(),body.getString("expectedCount"))
        assertFalse(body.has("temporaryHandover"));assertTrue(body.has("temporaryContinuation"))
        for(field in listOf("captures","scans","events")) {
            val previous=old.getJSONArray(field);val actual=body.getJSONArray(field)
            for(i in 0 until previous.length())assertEquals(SubmissionJson.canonical(previous.getJSONObject(i)),SubmissionJson.canonical(actual.getJSONObject(i)))
        }
    }

    fun success() {
        val next=db.temporaryContinuations().forSource(source)!!.newClientOrderId
        val frozen=db.temporarySubmissions().forOrder(next)!!
        assertThrows(IOException::class.java) { TemporarySubmissionRepository(db,connection).upload(frozen.id) }
        assertNull(db.temporarySuccess().evidence(frozen.id));fixture.reopen("continuation-success-lost")
        val result=TemporarySubmissionRepository(db,connection).upload(frozen.id)
        assertEquals("SYNCED",result.state);assertNotNull(db.temporarySuccess().evidence(frozen.id))
        fixture.reopen("continuation-success-saved")
        assertEquals("SYNCED",TemporarySubmissionRepository(db,connection).checked(frozen.id).state)
        File(root,"continuationSuccess-result.json").writeText(JSONObject().put("submissionId",frozen.id)
            .put("state","SYNCED").put("payloadSha256",frozen.sha256).toString())
    }
}
