package com.acrylic.m71demo

import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = android.app.Application::class)
class BoundaryTest {
    @Test fun durableBusinessRejectionCannotBecomeRetryableStorageFailure() = TestRig(1).use { rig ->
        val first = rig.receive("003A0000001")
        val second = rig.receive("003A0000002")
        rig.confirmation.request(first.id, rig.clock.elapsedNs())
        rig.confirmation.request(second.id, rig.clock.elapsedNs())
        rig.confirmation.save(first.id)
        assertEquals("BUSINESS_REJECTED", rig.confirmation.save(second.id).status)
        rig.confirmation.markStorageError(second.id)
        assertEquals("BUSINESS_REJECTED", rig.db.captures().get(second.id)!!.state)
    }

    @Test fun changedOriginCannotBorrowLocalAuthorization() = TestRig().use { rig ->
        val capture = rig.receive("003A0000001")
        rig.db.captures().update(capture.copy(context = capture.context.copy(originDeviceId = "another-device")))
        rig.confirmation.request(capture.id, rig.clock.elapsedNs())
        assertEquals("AUTH_DEVICE_MISMATCH", rig.confirmation.save(capture.id).error)
        assertEquals(0, rig.db.scans().count("order"))
    }

    @Test fun writerConnectionUsesWalFullBeforeAndAfterReopen() = TestRig().use { rig ->
        repeat(2) {
            rig.db.runInTransaction {
                val db = rig.db.openHelper.writableDatabase
                db.query("PRAGMA synchronous").use { it.moveToFirst(); assertEquals(2, it.getInt(0)) }
                db.query("PRAGMA journal_mode").use { it.moveToFirst(); assertEquals("wal", it.getString(0)) }
                db.query("PRAGMA foreign_keys").use { it.moveToFirst(); assertEquals(1, it.getInt(0)) }
            }
            rig.reopen()
        }
    }
}
