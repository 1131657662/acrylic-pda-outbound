package com.acrylic.m71demo

import android.app.Application
import com.acrylic.m71demo.domain.DemoSigner
import com.acrylic.m71demo.fixtures.HistoryFixture
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class HistoryFixtureTest {
    @Test fun historyBatchPreservesRealFactsAndCreatesCompleteSyntheticOrder() = TestRig().use { rig ->
        val original = rig.save("003A0000001")
        val signer = object : DemoSigner { override fun sign(claim: String) = "fixture-only" }
        HistoryFixture(rig.store, signer).seed(400) { }
        val history = rig.db.orders().recent().single { it.source == "HISTORY_FIXTURE" }
        assertEquals(400, history.actualCount)
        assertEquals(120200000L, history.weightGrams)
        val captures = rig.db.captures().page(history.id, 0, 500)
        val scans = rig.db.scans().page(history.id, 0, 500)
        assertEquals((1L..400L).toList(), captures.map { it.context.orderScanSeq })
        assertEquals(captures.map { it.id }, scans.map { it.id })
        assertEquals(1200, rig.db.audit().exportPage(history.id, "", 0, 2000).size)
        assertTrue(scans.all { rig.db.scans().guard(it.context.barcode)?.scanId == it.id })
        assertEquals(original, rig.db.captures().get(original.id))
        assertEquals("ok", rig.db.openHelper.writableDatabase.query("PRAGMA integrity_check").use { it.moveToFirst(); it.getString(0) })
    }
}
