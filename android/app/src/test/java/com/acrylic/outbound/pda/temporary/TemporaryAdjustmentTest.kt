package com.acrylic.outbound.pda.temporary

import kotlinx.coroutines.*
import org.junit.*
import org.junit.Assert.*
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.*

@RunWith(RobolectricTestRunner::class) @Config(sdk = [28], application = android.app.Application::class)
@SQLiteMode(SQLiteMode.Mode.NATIVE)
class TemporaryAdjustmentTest {
    @get:Rule val folder = TemporaryFolder()
    private fun scenario(block: (TemporaryWorkFixture) -> Unit) = runBlocking<Unit>(Dispatchers.IO) {
        TemporaryWorkFixture(folder.root).use { it.start(); block(it) }
    }

    @Test fun olderPackageAdjustmentPreservesFactsAcrossReopenAndRescan() = scenario { f ->
        val first = f.scan(); val last = f.scan(f.barcodes[1])
        f.processor.corrections.voidSelected(f.id, first.id, "实物已卸下，调整较早的包"); f.reopen()
        assertEquals("COMMITTED", f.db.temporaryScans().capture(first.id)?.state)
        assertEquals("VOID", f.db.temporaryScans().scan(first.id)?.status)
        assertEquals("ACTIVE", f.db.temporaryScans().scan(last.id)?.status)
        assertEquals(listOf(1L, 2L), f.db.temporaryScans().page(f.id, 0).map { it.orderScanSeq })
        assertNull(f.db.temporaryScans().guard(first.barcode)); assertEquals(1L, f.store.total(f.id).count)
        val again = f.scan(); assertEquals(3L, again.orderScanSeq)
        assertEquals(again.id, f.db.temporaryScans().locate(f.id, first.barcode)?.captureId)
        assertEquals(2L, f.store.total(f.id).count)
    }

    @Test fun finalAuditFailurePreservesScanGuardAndOriginalTotal() = scenario { f ->
        val first = f.scan(); f.scan(f.barcodes[1]); val total = f.store.total(f.id)
        f.db.openHelper.writableDatabase.execSQL("CREATE TRIGGER fail_temp_adjustment BEFORE INSERT ON temporary_event WHEN NEW.type='VOID' BEGIN SELECT RAISE(ABORT,'adjustment audit failure'); END")
        assertThrows(Exception::class.java) { f.processor.corrections.voidSelected(f.id, first.id, "实物核对") }
        assertEquals(total, f.store.total(f.id)); assertEquals("ACTIVE", f.db.temporaryScans().scan(first.id)?.status)
        assertNotNull(f.db.temporaryScans().guard(first.barcode))
        assertFalse(f.db.temporaryScans().events(f.id).any { it.type == "VOID" })
    }

    @Test fun pendingInputAndFrozenStateRejectAdjustment() = scenario { f ->
        val first = f.scan(); val pending = f.receive(f.barcodes[1], "MANUAL")
        assertThrows(Exception::class.java) { f.processor.corrections.voidSelected(f.id, first.id, "实物核对") }
        f.processor.drain(f.id); f.processor.corrections.discard(pending.id, "取消未确认手输")
        TemporaryFreeze(f.store).freeze(f.id, "短装核对")
        assertThrows(Exception::class.java) { f.processor.corrections.voidSelected(f.id, first.id, "实物核对") }
        assertEquals("ACTIVE", f.db.temporaryScans().scan(first.id)?.status)
    }

    @Test fun repeatedConfirmationKeepsOneVoidAudit() = scenario { f ->
        val first = f.scan(); f.processor.corrections.voidSelected(f.id, first.id, "实物核对")
        assertThrows(Exception::class.java) { f.processor.corrections.voidSelected(f.id, first.id, "重复确认") }
        assertEquals(1, f.db.temporaryScans().events(f.id).count { it.type == "VOID" })
    }

    @Test fun expiredOrChangedIdentityCannotAdjustOriginalTemporaryFacts() = scenario { f ->
        val first = f.scan(); val now = f.fixture.time; val identity = f.fixture.auth!!.toString()
        f.fixture.time = now.plusSeconds(172801)
        assertThrows(Exception::class.java) { f.processor.corrections.voidSelected(f.id, first.id, "实物核对") }
        f.fixture.time = now; f.fixture.auth!!.put("userId", java.util.UUID.randomUUID().toString())
        assertThrows(Exception::class.java) { f.processor.corrections.voidSelected(f.id, first.id, "实物核对") }
        assertEquals("ACTIVE", f.db.temporaryScans().scan(first.id)?.status)
        assertNotNull(f.db.temporaryScans().guard(first.barcode)); f.fixture.auth = org.json.JSONObject(identity)
    }

    @Test fun pausedApplicationWriterReturnsOnlyAfterVoidAuditIsDurable() = scenario { f ->
        val first = f.scan()
        runBlocking {
            val engine = TemporaryEngine(f.newStore()) {}
            try {
                engine.resume(f.id); withTimeout(10000) { while (!engine.state.value.ready) delay(10) }
                assertTrue(runCatching { engine.adjust(f.id, first.id, "实物核对").await() }.isFailure)
                engine.pause(); engine.adjust(f.id, first.id, "实物核对").await()
                assertEquals("VOID", f.db.temporaryScans().scan(first.id)?.status)
                assertEquals(1, f.db.temporaryScans().events(f.id).count { it.type == "VOID" })
            } finally { engine.close() }
        }
    }
}
