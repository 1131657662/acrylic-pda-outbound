package com.acrylic.outbound.pda.temporary

import com.acrylic.outbound.pda.work.WorkFixture
import kotlinx.coroutines.*
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.*
import java.util.UUID

@RunWith(RobolectricTestRunner::class) @Config(sdk = [28], application = android.app.Application::class)
@SQLiteMode(SQLiteMode.Mode.NATIVE)
class TemporaryInteroperabilityTest {
    @get:Rule val folder = TemporaryFolder()
    @Test fun formalGuardBlocksTemporaryEvenWithAvailableCloudProjection() = runBlocking<Unit>(Dispatchers.IO) {
        WorkFixture(folder.root).use { formal ->
            TemporaryWorkFixture(folder.newFolder(), formal.db).use { temp ->
                val code = overlap(formal, temp); formal.scan(code)
                temp.start(); temp.cloud(code); val row = temp.scan(code)
                assertNull(temp.db.temporaryScans().scan(row.id))
                assertTrue(temp.db.temporaryScans().capture(row.id)!!.error.contains("本机"))
                assertNotNull(formal.db.scans().guard(row.barcode))
            }
        }
    }
    @Test fun temporaryGuardBlocksFormalWithoutCreatingFormalFact() = runBlocking<Unit>(Dispatchers.IO) {
        WorkFixture(folder.root).use { formal ->
            TemporaryWorkFixture(folder.newFolder(), formal.db).use { temp ->
                val code = overlap(formal, temp); temp.start(); val row = temp.scan(code)
                assertNotNull(temp.db.temporaryScans().scan(row.id))
                val blocked = formal.scan(code)
                assertNull(formal.db.scans().get(blocked.id)); assertTrue(formal.db.captures().get(blocked.id)!!.error.contains("本机"))
                assertNotNull(temp.db.temporaryScans().guard(row.barcode))
            }
        }
    }
    @Test fun temporaryDraftsShareWholeDeviceGuard() = runBlocking<Unit>(Dispatchers.IO) {
        TemporaryWorkFixture(folder.root).use { f ->
            f.start(); val row = f.scan(); val other = f.drafts.create(UUID.randomUUID().toString())
            val saved = f.drafts.save(other.clientOrderId, TemporaryPlanInput(other.revision, "另一合成车", "", "第二张单",
                f.store.source(other.clientOrderId).products.keys.map { it to "1" }))
            f.drafts.start(other.clientOrderId, saved.revision)
            val capture = f.processor.reception.receive(TemporaryInput(other.clientOrderId, row.barcode, "BROADCAST", f.store.sample()))
            f.processor.drain(other.clientOrderId)
            assertNull(f.db.temporaryScans().scan(capture.id)); assertNotNull(f.db.temporaryScans().guard(row.barcode))
        }
    }
    @Test fun pendingTemporaryInputStopsFormalReceptionWithoutAllocatingSequence() = runBlocking<Unit>(Dispatchers.IO) {
        WorkFixture(folder.root).use { formal ->
            TemporaryWorkFixture(folder.newFolder(), formal.db).use { temp ->
                temp.start(); temp.receive(source = "MANUAL")
                val before = formal.db.work().order(formal.assignment)
                assertThrows(Exception::class.java) { formal.receive() }
                assertEquals(before, formal.db.work().order(formal.assignment))
                assertThrows(Exception::class.java) { formal.store.open(formal.assignment) }
            }
        }
    }
    @Test fun pendingFormalInputStopsTemporaryDraftCreation() = runBlocking<Unit>(Dispatchers.IO) {
        WorkFixture(folder.root).use { formal ->
            TemporaryWorkFixture(folder.newFolder(), formal.db).use { temp ->
                formal.receive(source = "MANUAL")
                assertThrows(Exception::class.java) { temp.drafts.create(UUID.randomUUID().toString()) }
                assertThrows(Exception::class.java) { temp.drafts.save(temp.id, temp.plan()) }
            }
        }
    }
    private fun overlap(formal: WorkFixture, temp: TemporaryWorkFixture): String {
        val code = temp.barcodes.first { it.startsWith(formal.barcodes[0].take(3)) }
        // Two independently signed fixtures have different stock. Synthesize a local shared barcode
        // solely to exercise cross-source guard checks; this is not server authorization evidence.
        formal.db.openHelper.writableDatabase.execSQL("UPDATE prepared_bale SET barcode=? WHERE assignmentId=? AND barcode=?",
            arrayOf(code, formal.assignment, formal.barcodes[0]))
        return code
    }
}
