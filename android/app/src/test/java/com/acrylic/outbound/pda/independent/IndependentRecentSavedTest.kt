package com.acrylic.outbound.pda.independent

import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.*

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = android.app.Application::class)
@SQLiteMode(SQLiteMode.Mode.NATIVE)
class IndependentRecentSavedTest {
    @Test fun unconfirmedAndInvalidInputsDoNotReplaceLastCommittedScan() = runBlocking<Unit>(Dispatchers.IO) {
        IndependentScanFixture().use { f ->
            f.start()
            val pending = f.reception.receive(f.input(source = "MANUAL"))
            f.reception.validate(pending.capture.id)
            assertNull(f.store.dao.latestSaved(f.command.id))
            f.saving.confirm(pending.capture.id); f.saving.save(pending.capture.id)
            val saved = checkNotNull(f.store.dao.latestSaved(f.command.id))

            val duplicate = f.reception.receive(f.input()); f.reception.validate(duplicate.capture.id)

            assertEquals("INVALID", f.store.dao.capture(duplicate.capture.id)?.processing?.state)
            assertEquals(saved, f.store.dao.latestSaved(f.command.id))
            assertEquals(pending.capture.id, saved.scan.captureId)
            assertEquals("ACTIVE", saved.status)
        }
    }

    @Test fun tailRollbackAndReopenKeepVoidedIdentityUntilRetryActuallyCommits() = runBlocking<Unit>(Dispatchers.IO) {
        IndependentScanFixture().use { f ->
            f.start(); val first = f.save()
            f.corrections.voidSelected(f.command.id, first.capture.id, "已核实未装车")
            val original = checkNotNull(f.store.dao.latestSaved(f.command.id))
            assertEquals("VOID", original.status)
            val next = f.reception.receive(f.input()); f.reception.validate(next.capture.id)
            f.task.sku.db.openHelper.writableDatabase.execSQL("""CREATE TRIGGER recent_tail BEFORE INSERT ON independent_task_event
                WHEN NEW.type='INDEPENDENT_SAVED' BEGIN SELECT RAISE(ABORT,'recent save tail'); END""")

            assertThrows(Exception::class.java) { f.saving.save(next.capture.id) }
            f.reopen()

            assertEquals(original, f.store.dao.latestSaved(f.command.id))
            assertEquals(0L, f.task.repository.read(f.command.id).state.actualCount)
            f.task.sku.db.openHelper.writableDatabase.execSQL("DROP TRIGGER recent_tail")
            assertEquals("SAVED", f.saving.save(next.capture.id))
            assertEquals(next.capture.id, f.store.dao.latestSaved(f.command.id)?.scan?.captureId)
            assertEquals("ACTIVE", f.store.dao.latestSaved(f.command.id)?.status)
        }
    }

    @Test fun latestIsTaskScopedBeyondFirstFiftyAndDoesNotDependOnPageCursor() = runBlocking<Unit>(Dispatchers.IO) {
        IndependentScanFixture().use { f ->
            val other = f.task.command()
            f.task.repository.create(other, f.grant)
            seed(f, f.command.id, 51); seed(f, other.id, 1)
            val dao = f.store.dao

            assertEquals(50L, dao.page(f.command.id, 0).last().scan.sequence)
            assertEquals(51L, dao.page(f.command.id, 50).single().scan.sequence)
            assertEquals(51L, dao.latestSaved(f.command.id)?.scan?.sequence)
            assertEquals(other.id, dao.latestSaved(other.id)?.scan?.taskId)
            assertEquals(1L, dao.latestSaved(other.id)?.scan?.sequence)
            assertNull(dao.latestSaved("not-a-task"))
            printPlan(f)
        }
    }

    private fun seed(f: IndependentScanFixture, taskId: String, count: Int) {
        // Synthetic durable rows for query cardinality; save/rollback behavior is tested above.
        f.task.sku.db.runInTransaction {
            repeat(count) { index ->
                val seq = index + 1L; val id = "$taskId-$seq"
                f.store.dao.insertCapture(IndependentCapture(id, taskId, seq, "synthetic-$taskId", seq,
                    "003A0000001", "003A0000001", "BROADCAST", 1, 1, 1, "synthetic"))
                f.store.dao.insertScan(IndependentScan(id, taskId, seq, "003A0000001", "003", "MODEL", "NAME",
                    "1.000", 1000, 1, "synthetic", "synthetic"))
            }
        }
    }

    private fun printPlan(f: IndependentScanFixture) {
        f.task.sku.db.openHelper.readableDatabase.query("""EXPLAIN QUERY PLAN SELECT s.*,
            CASE WHEN v.captureId IS NULL THEN 'ACTIVE' ELSE 'VOID' END AS status
            FROM independent_capture c INNER JOIN independent_scan s ON s.captureId=c.id
            LEFT JOIN independent_void v ON v.captureId=s.captureId
            WHERE c.taskId=? ORDER BY c.sequence DESC LIMIT 1""", arrayOf(f.command.id)).use { cursor ->
            while (cursor.moveToNext()) println("latestSaved plan: ${cursor.getString(3)}")
        }
    }
}
