package com.acrylic.outbound.pda.work

import com.acrylic.outbound.pda.data.PreparedBale
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
import java.io.File
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = android.app.Application::class)
@SQLiteMode(SQLiteMode.Mode.NATIVE)
class WorkDetailsReaderTest {
    @get:Rule val folder = TemporaryFolder()
    private fun scenario(block: (WorkFixture, WorkDetailsReader) -> Unit) = runBlocking<Unit>(Dispatchers.IO) {
        WorkFixture(folder.root).use { f ->
            populate(f)
            val reader = WorkDetailsReader(f.db, f.assignment) { f.access.identity(f.store.prepared(f.assignment)) }
            block(f, reader)
        }
    }

    @Test fun pagesRetainOriginalSequenceWithoutAccumulation() = scenario { f, reader ->
        val before = f.db.work().events(f.assignment)
        val first = reader.page(WorkDetailFilter.ALL)
        val second = reader.page(WorkDetailFilter.ALL, first.rows.last().sequence)
        val last = reader.page(WorkDetailFilter.ALL, second.rows.last().sequence)
        assertEquals((1L..50L).toList(), (first.rows + second.rows + last.rows).map { it.sequence })
        assertEquals(listOf(20, 20, 10), listOf(first, second, last).map { it.rows.size })
        assertFalse(first.previous); assertTrue(first.next); assertTrue(second.previous); assertTrue(second.next)
        assertTrue(last.previous); assertFalse(last.next)
        assertEquals(second.rows, reader.page(WorkDetailFilter.ALL, last.rows.first().sequence, true).rows)
        assertEquals(first.rows, reader.page(WorkDetailFilter.ALL, second.rows.first().sequence, true).rows)
        assertEquals(before, f.db.work().events(f.assignment)); f.store.verifyTotals(f.assignment)
        export(f)
    }

    @Test fun statusFiltersKeepVoidAndUnresolvedFactsSeparate() = scenario { _, reader ->
        val voids = reader.page(WorkDetailFilter.VOID)
        assertEquals((5L..45L step 5).toList(), voids.rows.map { it.sequence })
        val saved = reader.page(WorkDetailFilter.SAVED)
        assertEquals(20, saved.rows.size); assertTrue(saved.rows.all { it.status == "ACTIVE" })
        val pending = reader.page(WorkDetailFilter.UNRESOLVED)
        assertEquals((47L..50L).toList(), pending.rows.map { it.sequence })
        assertEquals(listOf("WAITING_CONFIRM", "INVALID", "RECEIVED", "CONFIRM_REQUESTED"), pending.rows.map { it.status })
    }

    @Test fun locatorReturnsExactSequenceOrLastOccurrenceOfBarcode() = scenario { _, reader ->
        assertEquals("VOID", reader.locate("5", false)?.status)
        assertEquals(46L, reader.locate(" 003z0000005 ", true)?.sequence)
        assertNull(reader.locate("999", false)); assertNull(reader.locate("009A0000999", true))
        assertThrows(Exception::class.java) { reader.locate("0", false) }
        assertThrows(Exception::class.java) { reader.locate("9223372036854775808", false) }
        assertThrows(Exception::class.java) { reader.locate("' OR 1=1 --", true) }
    }

    @Test fun changedIdentityCannotReadAnyPageOrLocate() = scenario { f, reader ->
        f.session.put("userId", UUID.randomUUID().toString())
        assertThrows(Exception::class.java) { reader.page(WorkDetailFilter.ALL) }
        assertThrows(Exception::class.java) { reader.locate("5", false) }
    }

    @Test fun emptyOrderHasNoNavigationOrInventedRecords() = runBlocking<Unit>(Dispatchers.IO) {
        WorkFixture(folder.root).use { f ->
            val reader = WorkDetailsReader(f.db, f.assignment) { f.access.identity(f.store.prepared(f.assignment)) }
            WorkDetailFilter.entries.forEach { filter ->
                val page = reader.page(filter)
                assertTrue(page.rows.isEmpty()); assertFalse(page.previous); assertFalse(page.next)
            }
        }
    }

    @Test fun supervisorRetainedPrefixRemainsVisibleWithoutBecomingAdjustable() = scenario { f, reader ->
        f.db.continuations().insert(com.acrylic.outbound.pda.continuation.LocalContinuation("synthetic-prefix", f.assignment,
            f.input.assignment.toString(), "{}", 3, 3, "synthetic-only"))
        val rows = reader.page(WorkDetailFilter.SAVED).rows
        assertEquals(listOf("RETAINED", "RETAINED", "ACTIVE"), rows.take(3).map { it.status })
        assertEquals((1L..3L).toList(), rows.take(3).map { it.sequence })
    }

    private fun populate(f: WorkFixture) {
        f.db.openHelper.writableDatabase.execSQL("UPDATE work_order SET vehicleNo='合成长车号豫A测试挂车1234567890' WHERE assignmentId=?", arrayOf(f.assignment))
        val captures = (1..45).map { index ->
            val barcode = "003Z" + index.toString().padStart(7, '0')
            f.db.preparations().insertBales(listOf(PreparedBale(f.assignment, barcode, UUID.randomUUID().toString(),
                "003", "合成长型号用于小屏多行核对", "合成测试品名用于分页及大字显示核对", "0.001", 1, 1, true)))
            f.scan(barcode).also { assertEquals("COMMITTED", f.db.captures().get(it.id)?.state) }
        }
        captures.filterIndexed { index, _ -> (index + 1) % 5 == 0 }.forEach {
            f.processor.corrections.voidSelected(f.assignment, it.id, "合成分页测试，原包已卸下")
        }
        f.scan(captures[4].context.barcode)
        f.receive(f.barcodes[0], "MANUAL").also { f.processor.reception.validate(it.id) }
        f.receive("invalid-input").also { f.processor.reception.validate(it.id) }
        f.receive(f.barcodes[1])
        f.receive(f.barcodes[1]).also { f.processor.reception.validate(it.id) }
    }

    private fun export(f: WorkFixture) {
        val path = System.getenv("PDA_WORK_DETAILS_OUTPUT") ?: return
        val target = File(path).apply { check(!exists()); mkdirs() }
        f.db.openHelper.writableDatabase.query("PRAGMA wal_checkpoint(FULL)").use {
            assertTrue(it.moveToFirst()); assertEquals(0, it.getInt(0))
        }
        f.db.close()
        f.context.getDatabasePath("outbound-pda.db").copyTo(File(target, "synthetic-formal.db"))
        android.database.sqlite.SQLiteDatabase.openDatabase(File(target, "synthetic-formal.db").path, null,
            android.database.sqlite.SQLiteDatabase.OPEN_READONLY).use { copy ->
            copy.rawQuery("SELECT COUNT(*) FROM work_capture", null).use { assertTrue(it.moveToFirst()); assertEquals(50, it.getInt(0)) }
        }
        File(target, "synthetic-session.json").writeText(f.session.toString(2))
        File(target, "identity.json").writeText(JSONObject().put("syntheticOnly", true).put("assignmentId", f.assignment)
            .put("userId", f.session.getString("userId")).put("deviceId", f.session.getString("deviceId")).toString(2))
    }
}
