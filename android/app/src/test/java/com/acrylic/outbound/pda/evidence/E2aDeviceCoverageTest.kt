package com.acrylic.outbound.pda.evidence

import com.acrylic.outbound.pda.temporary.TemporaryWorkFixture
import com.acrylic.outbound.pda.work.WorkFixture
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
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
class E2aDeviceCoverageTest {
    @get:Rule val folder = TemporaryFolder()

    @Test fun originalDevicePrefixSurvivesRestart() = runBlocking<Unit>(Dispatchers.IO) {
        TemporaryWorkFixture(folder.root).use { fixture ->
            fixture.start(); fixture.scan(); fixture.reopen()
            val device = fixture.catalog.request.deviceId
            fixture.db.runInTransaction {
                val expected = checkNotNull(fixture.db.work().device(device))
                assertEquals(E2aDeviceCoverage.Boundary(expected.nextCaptureSeq - 1,
                    expected.nextEventSeq - 1), E2aDeviceCoverage(fixture.db).verify(device))
            }
        }
    }

    @Test fun removedAuditRecordFailsBeforeAStopCanBeSigned() = runBlocking<Unit>(Dispatchers.IO) {
        TemporaryWorkFixture(folder.root).use { fixture ->
            fixture.start(); fixture.scan()
            fixture.db.openHelper.writableDatabase.execSQL("DELETE FROM temporary_event WHERE localSeq=2")
            assertThrows(IllegalStateException::class.java) {
                fixture.db.runInTransaction { E2aDeviceCoverage(fixture.db).verify(fixture.catalog.request.deviceId) }
            }
        }
    }

    @Test fun missingCaptureOrStaleCounterFailsClosed() = runBlocking<Unit>(Dispatchers.IO) {
        TemporaryWorkFixture(folder.root).use { fixture ->
            fixture.start(); fixture.scan()
            val device = fixture.catalog.request.deviceId
            fixture.db.openHelper.writableDatabase.execSQL("UPDATE work_device SET nextCaptureSeq=nextCaptureSeq+1 WHERE deviceId=?", arrayOf(device))
            assertThrows(IllegalStateException::class.java) {
                fixture.db.runInTransaction { E2aDeviceCoverage(fixture.db).verify(device) }
            }
        }
    }

    @Test fun duplicateSequenceAcrossFormalAndTemporaryFailsClosed() = runBlocking<Unit>(Dispatchers.IO) {
        WorkFixture(folder.root).use { formal ->
            TemporaryWorkFixture(folder.newFolder(), formal.db).use { temporary ->
                val device = temporary.catalog.request.deviceId
                formal.db.openHelper.writableDatabase.execSQL("UPDATE work_event SET originDeviceId=?", arrayOf(device))
                assertThrows(IllegalStateException::class.java) {
                    formal.db.runInTransaction { E2aDeviceCoverage(formal.db).verify(device) }
                }
            }
        }
    }

    @Test fun staleAuditCounterOrMissingTransactionFailsClosed() = runBlocking<Unit>(Dispatchers.IO) {
        TemporaryWorkFixture(folder.root).use { fixture ->
            fixture.start(); fixture.scan()
            val device = fixture.catalog.request.deviceId
            assertThrows(IllegalStateException::class.java) { E2aDeviceCoverage(fixture.db).verify(device) }
            fixture.db.openHelper.writableDatabase.execSQL("UPDATE work_device SET nextEventSeq=nextEventSeq-1 WHERE deviceId=?", arrayOf(device))
            assertThrows(IllegalStateException::class.java) {
                fixture.db.runInTransaction { E2aDeviceCoverage(fixture.db).verify(device) }
            }
        }
    }
}
