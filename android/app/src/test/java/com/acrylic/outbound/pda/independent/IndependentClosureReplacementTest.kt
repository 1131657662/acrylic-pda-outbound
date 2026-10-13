package com.acrylic.outbound.pda.independent

import kotlinx.coroutines.*
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.*

@RunWith(RobolectricTestRunner::class) @Config(sdk = [28], application = android.app.Application::class)
@SQLiteMode(SQLiteMode.Mode.NATIVE)
class IndependentClosureReplacementTest {
    @Test fun foreignTaskStateCannotReplaceASealedTaskState() = runBlocking<Unit>(Dispatchers.IO) {
        IndependentRescueFixture().use { f ->
            f.db.openHelper.writableDatabase.execSQL("PRAGMA recursive_triggers=OFF")
            val other = f.scan.task.repository.create(f.scan.task.command(), f.scan.task.grant())
            IndependentTaskClosing(f.scan.store) { f.signer }.close(f.target.localOrderId, "短装", true)
            val before = f.digest()
            assertThrows(Exception::class.java) {
                f.db.openHelper.writableDatabase.execSQL(
                    "UPDATE OR REPLACE independent_task_state SET taskId=? WHERE taskId=?",
                    arrayOf(f.target.localOrderId, other.task.id))
            }
            assertEquals(before, f.digest())
        }
    }
    @Test fun foreignOwnerCannotReplaceASealedEffectiveGuard() = runBlocking<Unit>(Dispatchers.IO) {
        IndependentRescueFixture().use { f ->
            f.db.openHelper.writableDatabase.execSQL("PRAGMA recursive_triggers=OFF")
            f.scan.start(); val saved = f.scan.save()
            val other = f.scan.task.repository.create(f.scan.task.command(), f.scan.task.grant())
            IndependentTaskClosing(f.scan.store) { f.signer }.close(f.target.localOrderId, "短装", true)
            val before = f.digest()
            assertThrows(Exception::class.java) {
                f.db.openHelper.writableDatabase.execSQL(
                    "INSERT OR REPLACE INTO independent_guard SELECT barcode,captureId,? FROM independent_guard WHERE barcode=?",
                    arrayOf(other.task.id, saved.capture.barcode))
            }
            assertEquals(before, f.digest())
        }
    }
    @Test fun foreignGuardCannotMoveOntoASealedBarcode() = runBlocking<Unit>(Dispatchers.IO) {
        IndependentRescueFixture().use { f ->
            f.db.openHelper.writableDatabase.execSQL("PRAGMA recursive_triggers=OFF")
            f.scan.start(); val saved = f.scan.save()
            val other = f.scan.task.repository.create(f.scan.task.command(), f.scan.task.grant())
            val dao = f.scan.store.dao
            val foreign = saved.capture.copy(id = saved.capture.id + "-foreign", taskId = other.task.id, deviceSequence = 999)
            dao.insertCapture(foreign); dao.insertState(IndependentCaptureState(foreign.id, state = "COMMITTED"))
            dao.insertScan(checkNotNull(dao.scan(saved.capture.id)).copy(captureId = foreign.id, taskId = other.task.id))
            dao.insertGuard(IndependentGuard("FOREIGN-BARCODE", foreign.id, other.task.id))
            IndependentTaskClosing(f.scan.store) { f.signer }.close(f.target.localOrderId, "短装", true)
            val before = f.digest()
            assertThrows(Exception::class.java) {
                f.db.openHelper.writableDatabase.execSQL(
                    "UPDATE OR REPLACE independent_guard SET barcode=? WHERE barcode='FOREIGN-BARCODE'",
                    arrayOf(saved.capture.barcode))
            }
            assertEquals(before, f.digest())
        }
    }
    @Test fun foreignTaskCannotReplaceSealedOriginalFacts() = runBlocking<Unit>(Dispatchers.IO) {
        IndependentRescueFixture().use { f ->
            f.db.openHelper.writableDatabase.execSQL("PRAGMA recursive_triggers=OFF")
            f.scan.start(); val saved = f.scan.save()
            f.scan.corrections.voidSelected(f.target.localOrderId, saved.capture.id, "实物已卸下")
            val other = f.scan.task.repository.create(f.scan.task.command(), f.scan.task.grant())
            IndependentTaskClosing(f.scan.store) { f.signer }.close(f.target.localOrderId, "短装", true)
            val before = f.digest(); val sqlite = f.db.openHelper.writableDatabase
            for (table in listOf("independent_capture", "independent_scan", "independent_void", "independent_task_event")) {
                val columns = sqlite.query("PRAGMA table_info($table)").use { cursor ->
                    buildList { while (cursor.moveToNext()) add(cursor.getString(1)) }
                }
                val values = columns.joinToString(",") { if (it == "taskId") "?" else "`$it`" }
                assertThrows(Exception::class.java) {
                    sqlite.execSQL("INSERT OR REPLACE INTO $table SELECT $values FROM $table WHERE taskId=?",
                        arrayOf(other.task.id, f.target.localOrderId))
                }
            }
            assertEquals(before, f.digest())
        }
    }
}
