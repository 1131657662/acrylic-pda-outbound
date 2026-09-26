package com.acrylic.outbound.pda.evidence

import com.acrylic.outbound.pda.temporary.TemporaryFreeze
import com.acrylic.outbound.pda.temporary.TemporaryWorkFixture
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.SQLiteMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = android.app.Application::class)
@SQLiteMode(SQLiteMode.Mode.NATIVE)
class LocalE2aSnapshotTest {
    @get:Rule val folder = TemporaryFolder()

    @Test fun originalObjectsRemainByteIdenticalAfterRestart() = runBlocking<Unit>(Dispatchers.IO) {
        TemporaryWorkFixture(folder.root).use { fixture ->
            fixture.start(); fixture.scan()
            TemporaryFreeze(fixture.store).freeze(fixture.id, "短装待核对")
            val stop = LocalE2aStopRepository(fixture.store).record(fixture.id)
            val before = listOf("CAPTURE", "SCAN", "AUDIT").associateWith { kind ->
                fixture.db.localE2aObjects().page(fixture.id, kind, 0).map { it.objectId to it.rawBytes }
            }
            assertEquals(stop.captureHighWatermark, before.getValue("CAPTURE").size.toLong())
            assertEquals(stop.auditThroughSeq, before.getValue("AUDIT").size.toLong())
            assertEquals(stop.scanSnapshotCount, before.getValue("SCAN").size.toLong())
            fixture.reopen()
            assertEquals(stop, LocalE2aStopRepository(fixture.store).record(fixture.id))
            before.forEach { (kind, originals) ->
                val restored = fixture.db.localE2aObjects().page(fixture.id, kind, 0)
                originals.zip(restored).forEach { (original, row) ->
                    assertEquals(original.first, row.objectId)
                    assertArrayEquals(original.second, row.rawBytes)
                }
            }
        }
    }

    @Test fun missingOriginalAuditObjectFailsClosed() = runBlocking<Unit>(Dispatchers.IO) {
        TemporaryWorkFixture(folder.root).use { fixture ->
            fixture.start(); fixture.scan()
            TemporaryFreeze(fixture.store).freeze(fixture.id, "短装待核对")
            fixture.db.openHelper.writableDatabase.execSQL(
                "DELETE FROM local_e2a_object WHERE clientOrderId=? AND kind='AUDIT' AND deviceSeq=1",
                arrayOf(fixture.id))
            assertThrows(IllegalStateException::class.java) {
                LocalE2aStopRepository(fixture.store).record(fixture.id)
            }
        }
    }

    @Test fun rewrittenOriginalBytesFailClosed() = runBlocking<Unit>(Dispatchers.IO) {
        TemporaryWorkFixture(folder.root).use { fixture ->
            fixture.start(); fixture.scan()
            TemporaryFreeze(fixture.store).freeze(fixture.id, "短装待核对")
            fixture.db.openHelper.writableDatabase.execSQL(
                "UPDATE local_e2a_object SET rawBytes=? WHERE clientOrderId=? AND kind='CAPTURE'",
                arrayOf("{}".toByteArray(), fixture.id))
            assertThrows(IllegalStateException::class.java) {
                LocalE2aStopRepository(fixture.store).record(fixture.id)
            }
        }
    }
}
