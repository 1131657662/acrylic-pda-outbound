package com.acrylic.outbound.pda.rescue

import com.acrylic.outbound.pda.submission.SubmissionFreeze
import com.acrylic.outbound.pda.work.WorkCorrections
import com.acrylic.outbound.pda.work.WorkFixture
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.SQLiteMode
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

@RunWith(RobolectricTestRunner::class) @Config(sdk = [28], application = android.app.Application::class)
@SQLiteMode(SQLiteMode.Mode.NATIVE)
class RescueTransitionSnapshotTest {
    @get:Rule val folder = TemporaryFolder()
    private fun scenario(block: (WorkFixture) -> Unit) = runBlocking<Unit>(Dispatchers.IO) { WorkFixture(folder.root).use(block) }
    private fun read(f: WorkFixture): RescueSnapshot {
        val grant = RescueReadGrant(RescueReadIdentity(f.session.getString("deviceId"),
            f.session.getString("userId"), f.session.getString("warehouseId"))) {}
        return RescueRoomSnapshot(f.db).read(RescueTarget("FORMAL", f.assignment), grant)
    }
    @Test fun voidCannotMixOriginalActiveScanWithLaterVoidAudit() = scenario { f ->
        val capture = f.scan()
        val before = interleave(f) { WorkCorrections(f.store).voidSelected(f.assignment, capture.id, "合成未装车核对") }
        assertEquals("1", RescueFacts.summary(before).getString("active"))
        assertFalse(before.events.any { it.getString("type") == "VOID" })
        val after = read(f)
        assertEquals("0", RescueFacts.summary(after).getString("active"))
        assertEquals("1", RescueFacts.summary(after).getString("void"))
        assertEquals(capture.id, after.scans.single().getString("captureId"))
        assertEquals(1, after.events.count { it.getString("type") == "VOID" })
    }
    @Test fun freezeCannotMixScanningIdentityWithNewFrozenBody() = scenario { f ->
        f.scan()
        val before = interleave(f) { SubmissionFreeze(f.store) { f.now }.freeze(f.assignment, "合成短装核对") }
        assertEquals("SCANNING", before.order.getString("state")); assertFalse("submission.json" in before.originals)
        val after = read(f)
        assertEquals("FROZEN", after.order.getString("state"))
        assertArrayEquals(checkNotNull(f.db.submissions().forOrder(f.assignment)).payload.toByteArray(), after.originals["submission.json"])
        assertEquals(1, after.events.count { it.getString("type") == "FROZEN" })
    }
    @Test fun receiptReplacementCannotChangeBytesInsideAlreadyReadSnapshot() = scenario { f ->
        f.scan()
        val frozen = SubmissionFreeze(f.store) { f.now }.freeze(f.assignment, "合成短装核对")
        val old = "{ \"synthetic\" : \"unknown\" }\n"
        val next = "{ \"synthetic\" : \"terminal\" }\n"
        f.db.submissions().progress(frozen.id, "RESULT_UNKNOWN", old, "synthetic", false)
        val before = interleave(f) { f.db.submissions().progress(frozen.id, "SYNCED", next, "", false) }
        assertArrayEquals(old.toByteArray(), before.originals["receipt.json"])
        val after = read(f)
        assertArrayEquals(next.toByteArray(), after.originals["receipt.json"])
        assertArrayEquals(frozen.payload.toByteArray(), before.originals["submission.json"])
        assertArrayEquals(frozen.payload.toByteArray(), after.originals["submission.json"])
    }
    private fun interleave(f: WorkFixture, write: () -> Unit): RescueSnapshot {
        val ready = CountDownLatch(1); val finished = CountDownLatch(1)
        var error: Throwable? = null
        lateinit var writer: Thread; lateinit var original: RescueSnapshot
        f.db.runInTransaction {
            original = read(f)
            writer = thread {
                ready.countDown()
                try { write() } catch (failure: Throwable) { error = failure } finally { finished.countDown() }
            }
            check(ready.await(5, TimeUnit.SECONDS))
            val during = read(f)
            assertEquals(original.order.toString(), during.order.toString())
            assertEquals(original.scans.map { it.toString() }, during.scans.map { it.toString() })
            assertEquals(original.events.map { it.toString() }, during.events.map { it.toString() })
            assertEquals(original.originals.keys, during.originals.keys)
            original.originals.forEach { (name, bytes) -> assertArrayEquals(bytes, during.originals[name]) }
            assertFalse(finished.await(100, TimeUnit.MILLISECONDS))
        }
        writer.join(5000); assertFalse(writer.isAlive); assertNull(error)
        return original
    }
}
