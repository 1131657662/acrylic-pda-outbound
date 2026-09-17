package com.acrylic.m71demo

import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = android.app.Application::class)
class MigrationTest {
    @Test fun schemaOnePreservesSavedAndUnconfirmedFacts() = migrate(1)
    @Test fun schemaTwoPreservesSavedAndUnconfirmedFacts() = migrate(2)

    private fun migrate(version: Int) = TestRig().use { rig ->
        val capture = rig.save("003A0000001")
        val pending = rig.receive("003A0000002")
        rig.db.captures().update(pending.copy(context = pending.context.copy(inputSource = "BROADCAST")))
        LegacyDatabaseFixture.rebuild(rig, version)
        rig.reopen()
        assertEquals(capture.id, rig.db.scans().get(capture.id)?.id)
        assertEquals(1, rig.db.scans().count("order"))
        assertEquals("MANUAL", rig.db.scans().get(capture.id)!!.context.confirmationPolicy)
        assertEquals("MANUAL", rig.db.captures().get(pending.id)!!.context.confirmationPolicy)
        assertEquals("WAITING_CONFIRM", rig.db.captures().get(pending.id)!!.state)
        assertEquals("INVALID", rig.receive("003A0000001").state)
        rig.db.openHelper.writableDatabase.query("PRAGMA user_version").use { it.moveToFirst(); assertEquals(3, it.getInt(0)) }
        rig.db.openHelper.writableDatabase.query("PRAGMA foreign_key_check").use { assertEquals(0, it.count) }
    }
}
