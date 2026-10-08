package com.acrylic.outbound.pda.temporary

import org.json.JSONObject
import org.junit.Assert.*
import java.io.File
import java.io.IOException

/** Actual HTTP work consumer, with a durable freeze boundary for independent intake adversaries. */
internal class TemporaryHandoverHttpWork(private val fixture: TemporaryHandoverHttpTest) {
    private val db get() = fixture.db
    private val connection get() = fixture.connection
    private val client get() = db.temporaryHandoverActivations().get(fixture.caseId)!!.newClientOrderId

    fun freeze() {
        assertThrows(IOException::class.java) { TemporaryHandoverDownload(fixture.store(), true).acknowledge(client) }
        fixture.reopen("work-lost-ack"); TemporaryHandoverDownload(fixture.store(), true).acknowledge(client)
        TemporaryHandoverWork(fixture.store(), true).start(client)
        val working = fixture.store(); working.load(client)
        val actual = working.products(client).associate { it.deliveryCode to it.count }
        val remaining = db.temporaryDrafts().lines(client).filter { (actual[it.deliveryCode] ?: 0) < it.plannedCount }.map { it.deliveryCode }.toSet()
        val bale = working.source(client).bales.values.first { it.status == "AVAILABLE" && it.code in remaining }
        val processor = TemporaryProcessor(working) {}
        val capture = processor.reception.receive(TemporaryInput(client, bale.barcode, "BROADCAST", working.sample()))
        processor.drain(client); assertEquals("COMMITTED", db.temporaryScans().capture(capture.id)!!.state)
        val frozen = TemporaryFreeze(working).freeze(client, "")
        File(fixture.output.parentFile, "android-handover-payload.json").writeText(frozen.payload)
        File(fixture.output.parentFile, "freeze-result.json").writeText(JSONObject().put("submissionId", frozen.id)
            .put("state", frozen.state).put("payloadSha256", frozen.sha256).toString())
        fixture.reopen("work-frozen")
    }

    fun upload() {
        val frozen = db.temporarySubmissions().forOrder(client)!!
        val result = uploadAfterLostResponse(frozen.id)
        assertEquals("REVIEW_REQUIRED", result.state)
        val evidence = JSONObject().put("submissionId", frozen.id).put("state", result.state).put("payloadSha256", result.sha256).toString()
        File(fixture.output.parentFile, "work-result.json").writeText(evidence)
        File(fixture.output.parentFile, "upload-result.json").writeText(evidence)
    }

    private fun uploadAfterLostResponse(id: String): TemporarySubmission {
        assertThrows(IOException::class.java) { TemporarySubmissionRepository(db, connection).upload(id) }
        assertNull(db.temporarySubmissions().get(id)!!.receipt); fixture.reopen("work-lost-upload")
        db.openHelper.writableDatabase.execSQL("CREATE TRIGGER temp_upload_last_write AFTER UPDATE OF state ON temporary_submission WHEN NEW.state='REVIEW_REQUIRED' BEGIN SELECT RAISE(ABORT,'D06_HTTP_UPLOAD_LAST_WRITE'); END")
        assertThrows(Exception::class.java) { TemporarySubmissionRepository(db, connection).upload(id) }
        assertNull(db.temporarySubmissions().get(id)!!.receipt)
        db.openHelper.writableDatabase.execSQL("DROP TRIGGER temp_upload_last_write"); fixture.reopen("work-last-write-rollback")
        return TemporarySubmissionRepository(db, connection).upload(id)
    }

    fun success() {
        val frozen = db.temporarySubmissions().forOrder(client)!!
        assertThrows(IOException::class.java) { TemporarySubmissionRepository(db, connection).upload(frozen.id) }
        assertNull(db.temporarySuccess().evidence(frozen.id)); fixture.reopen("success-lost-response")
        val result = TemporarySubmissionRepository(db, connection).upload(frozen.id)
        assertEquals("SYNCED", result.state); assertNotNull(db.temporarySuccess().evidence(frozen.id))
        fixture.reopen("success-saved")
        assertEquals("SYNCED", TemporarySubmissionRepository(db, connection).checked(frozen.id).state)
        File(fixture.output.parentFile, "success-result.json").writeText(JSONObject().put("submissionId", frozen.id)
            .put("state", result.state).put("payloadSha256", result.sha256).toString())
    }
}
