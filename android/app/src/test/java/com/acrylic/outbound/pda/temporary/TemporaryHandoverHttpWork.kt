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
        if (connection.largePayload) appendInvalidInputs(working)
        repeat(connection.scanCount) { scan(working) }
        val frozen = TemporaryFreeze(working).freeze(client, connection.shortShipmentReason)
        if (connection.largePayload) assertTrue(frozen.payload.toByteArray(Charsets.UTF_8).size > 1048576)
        File(fixture.output.parentFile, "android-handover-payload.json").writeText(frozen.payload)
        File(fixture.output.parentFile, "freeze-result.json").writeText(JSONObject().put("submissionId", frozen.id)
            .put("state", frozen.state).put("payloadSha256", frozen.sha256).toString())
        fixture.reopen("work-frozen")
    }

    private fun scan(working: TemporaryWorkStore) {
        val actual = working.products(client).associate { it.deliveryCode to it.count }
        val remaining = db.temporaryDrafts().lines(client).filter { (actual[it.deliveryCode] ?: 0) < it.plannedCount }.map { it.deliveryCode }.toSet()
        val existing = (db.temporaryScans().allScans(client).map { it.barcode } +
            db.temporaryHandoverGuards().forCase(fixture.caseId).map { it.barcode }).toSet()
        val bale = working.source(client).bales.values.first { it.status == "AVAILABLE" && it.code in remaining && it.barcode !in existing }
        val processor = TemporaryProcessor(working) {}
        val capture = processor.reception.receive(TemporaryInput(client, bale.barcode, "BROADCAST", working.sample()))
        processor.drain(client); assertEquals("COMMITTED", db.temporaryScans().capture(capture.id)!!.state)
    }

    private fun appendInvalidInputs(working: TemporaryWorkStore) {
        val processor = TemporaryProcessor(working) {}
        repeat(600) {
            val row = processor.reception.receive(TemporaryInput(client, "X".repeat(128), "BROADCAST", working.sample()))
            processor.drain(client)
            assertEquals("INVALID", db.temporaryScans().capture(row.id)!!.state)
            processor.corrections.acknowledge(row.id)
        }
    }

    fun upload() {
        val frozen = db.temporarySubmissions().forOrder(client)!!
        val result = uploadAfterLostResponse(frozen.id)
        if (connection.largePayload) verifyChunkTransport(frozen)
        assertEquals("REVIEW_REQUIRED", result.state)
        val evidence = JSONObject().put("submissionId", frozen.id).put("state", result.state).put("payloadSha256", result.sha256).toString()
        File(fixture.output.parentFile, "work-result.json").writeText(evidence)
        File(fixture.output.parentFile, "upload-result.json").writeText(evidence)
    }
    private fun verifyChunkTransport(frozen: TemporarySubmission) {
        val records = File(fixture.output, "transport.tsv").readLines().map { it.split('\t') }
        val base = "pda/temporary-orders/handover-submissions/${frozen.id}"
        assertTrue(records.any { it[0] == "$base/upload" && it[2] == "RECEIVING" })
        val size = frozen.payload.toByteArray(Charsets.UTF_8).size
        repeat((size + 262143) / 262144) { index ->
            assertTrue(records.any { it[0] == "$base/chunks/$index" && it[1].toInt() == minOf(262144, size - index * 262144) })
        }
        assertTrue(records.any { it[0] == "$base/finalize" && it[2] == "REVIEW_REQUIRED" })
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
