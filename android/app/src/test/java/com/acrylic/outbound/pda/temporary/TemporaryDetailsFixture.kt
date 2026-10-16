package com.acrylic.outbound.pda.temporary

import org.json.JSONObject
import org.junit.Assert.*
import java.io.File

/** Real TEMP activation/SCAN/VOID, explicitly synthetic signatures and identity from the contract fixture. */
internal class TemporaryDetailsFixture(root: File) : AutoCloseable {
    val work = TemporaryWorkFixture(root)
    val db get() = work.db
    val reader get() = TemporaryDetailsReader(work.store, work.id)
    fun populate() {
        val saved = work.drafts.save(work.id, work.plan("100").copy(vehicle = "合成长车号豫A测试1234567890挂",
            container = "合成长柜号用于小屏换行", reason = "合成分页测试，反复扫码及核实卸下保留原记录"))
        work.drafts.start(work.id, saved.revision)
        repeat(45) {
            val capture = work.scan()
            assertEquals("COMMITTED", db.temporaryScans().capture(capture.id)?.state)
            work.processor.corrections.voidSelected(work.id, capture.id, "合成实物已卸下，只保留原记录")
        }
        work.scan(work.barcodes[1])
        work.receive(work.barcodes[0], "MANUAL").also { work.processor.reception.validate(it.id) }
        work.receive("invalid-input").also { work.processor.reception.validate(it.id) }
        work.receive(work.barcodes[0])
        work.receive(work.barcodes[0]).also { work.processor.reception.validate(it.id) }
        assertEquals(50, db.temporaryScans().captures(work.id).size)
    }

    fun export() {
        val path = System.getenv("PDA_TEMPORARY_DETAILS_OUTPUT") ?: return
        val target = File(path).apply { check(!exists()); mkdirs() }
        db.openHelper.writableDatabase.query("PRAGMA wal_checkpoint(FULL)").use {
            assertTrue(it.moveToFirst()); assertEquals(0, it.getInt(0))
        }
        db.close()
        work.fixture.context.getDatabasePath("outbound-pda.db").copyTo(File(target, "synthetic-temporary.db"))
        android.database.sqlite.SQLiteDatabase.openDatabase(File(target, "synthetic-temporary.db").path, null,
            android.database.sqlite.SQLiteDatabase.OPEN_READONLY).use { copy ->
            copy.rawQuery("SELECT COUNT(*) FROM temporary_capture", null).use { assertTrue(it.moveToFirst()); assertEquals(50, it.getInt(0)) }
        }
        File(target, "synthetic-session.json").writeText(checkNotNull(work.fixture.auth).toString(2))
        File(target, "identity.json").writeText(JSONObject().put("syntheticOnly", true).put("clientOrderId", work.id)
            .put("userId", work.fixture.auth!!.getString("userId")).put("deviceId", work.fixture.auth!!.getString("deviceId"))
            .put("firstBarcode", work.barcodes[0]).put("lastSavedBarcode", work.barcodes[1]).toString(2))
    }
    override fun close() = work.close()
}
