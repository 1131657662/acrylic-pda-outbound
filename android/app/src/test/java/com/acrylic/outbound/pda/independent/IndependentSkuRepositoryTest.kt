package com.acrylic.outbound.pda.independent

import kotlinx.coroutines.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.*
import java.io.ByteArrayInputStream
import java.util.concurrent.*

@RunWith(RobolectricTestRunner::class) @Config(sdk = [28], application = android.app.Application::class)
@SQLiteMode(SQLiteMode.Mode.NATIVE)
class IndependentSkuRepositoryTest {
    @Test fun stageNeverReplacesActiveOriginal() = runBlocking<Unit>(Dispatchers.IO) {
        IndependentSkuFixture().use { f ->
            val active = f.activate(); val later = f.stage("004A0000001")
            assertEquals(active.imported.id, f.repository.active()!!.imported.id)
            assertNull(later.decision); assertTrue(later.canActivate)
            assertArrayEquals(f.bytes(), f.repository.original(active.imported.id))
        }
    }
    @Test fun duplicateConfirmationIsRequiredWithoutDiscardingOriginalLines() = runBlocking<Unit>(Dispatchers.IO) {
        IndependentSkuFixture().use { f ->
            val bytes = f.bytes() + byteArrayOf(10) + f.bytes()
            val staged = f.repository.stage(bytes, "duplicate.txt")
            assertEquals(1, staged.preview!!.duplicateRows)
            assertThrows(IllegalStateException::class.java) { f.repository.activate(staged.imported.id, false, null) }
            assertNull(f.repository.active())
            val active = f.repository.activate(staged.imported.id, true, null)
            assertEquals(2, active.preview!!.rows.size); assertEquals(1, active.preview.validRows)
            f.reopen(); assertArrayEquals(bytes, f.repository.original(staged.imported.id))
            assertTrue(f.repository.review(staged.imported.id).decision!!.duplicatesConfirmed)
        }
    }
    @Test fun badRowsRemainReadableAfterCancelAndReopen() = runBlocking<Unit>(Dispatchers.IO) {
        IndependentSkuFixture().use { f ->
            val old = f.activate()
            val bytes = f.bytes() + byteArrayOf(10) + "bad".toByteArray()
            val bad = f.repository.stage(bytes, "bad.txt")
            assertEquals(2, bad.preview!!.rows.last().lineNo)
            assertFalse(bad.canActivate)
            assertThrows(IllegalStateException::class.java) { f.repository.activate(bad.imported.id, true, old.imported.id) }
            f.repository.cancel(bad.imported.id); f.reopen()
            assertArrayEquals(bytes, f.repository.original(bad.imported.id))
            assertEquals("CANCELLED", f.repository.review(bad.imported.id).decision!!.outcome)
            assertEquals(old.imported.id, f.repository.active()!!.imported.id)
        }
    }
    @Test fun wholeFileErrorIsSavedBeforeParsing() = runBlocking<Unit>(Dispatchers.IO) {
        IndependentSkuFixture().use { f ->
            val bad = f.repository.stage(byteArrayOf(), "empty.txt")
            assertTrue(bad.error!!.startsWith("EMPTY_FILE")); assertFalse(bad.canActivate)
            f.reopen(); assertArrayEquals(byteArrayOf(), f.repository.original(bad.imported.id))
        }
    }
    @Test fun activationTailFailureRollsBackPointerAndDecision() = runBlocking<Unit>(Dispatchers.IO) {
        IndependentSkuFixture().use { f ->
            val old = f.activate(); val next = f.stage("004A0000001")
            f.db.openHelper.writableDatabase.execSQL("""CREATE TRIGGER fail_activation BEFORE UPDATE ON independent_sku_active
                BEGIN SELECT RAISE(ABORT,'isolated tail failure'); END""")
            assertThrows(Exception::class.java) { f.repository.activate(next.imported.id, false, old.imported.id) }
            assertNull(f.repository.review(next.imported.id).decision)
            assertEquals(old.imported.id, f.repository.active()!!.imported.id)
            f.db.openHelper.writableDatabase.execSQL("DROP TRIGGER fail_activation")
            f.repository.activate(next.imported.id, false, old.imported.id); f.reopen()
            assertEquals(next.imported.id, f.repository.active()!!.imported.id)
            assertArrayEquals(f.bytes(), f.repository.original(old.imported.id))
        }
    }
    @Test fun originalWriteFailureDoesNotLeavePartialImport() = runBlocking<Unit>(Dispatchers.IO) {
        IndependentSkuFixture().use { f ->
            val old = f.activate()
            f.db.openHelper.writableDatabase.execSQL("""CREATE TRIGGER fail_import BEFORE INSERT ON independent_sku_import
                BEGIN SELECT RAISE(ABORT,'isolated import failure'); END""")
            val bytes = f.bytes("004A0000001")
            assertThrows(Exception::class.java) { f.repository.stage(bytes, "failed.txt") }
            assertNull(f.db.independentSku().original(IndependentSkuBytes.hash(bytes)))
            assertEquals(old.imported.id, f.repository.latest()!!.imported.id)
        }
    }
    @Test fun staleReviewCannotOverwriteNewlyActivatedFile() = runBlocking<Unit>(Dispatchers.IO) {
        IndependentSkuFixture().use { f ->
            val old = f.activate(); val first = f.stage("004A0000001"); val second = f.stage("005A0000001")
            f.repository.activate(second.imported.id, false, old.imported.id)
            assertThrows(IllegalStateException::class.java) { f.repository.activate(first.imported.id, false, old.imported.id) }
            assertNull(f.repository.review(first.imported.id).decision)
            assertEquals(second.imported.id, f.repository.active()!!.imported.id)
        }
    }
    @Test fun retryOfOldActivationNeverRewindsPointer() = runBlocking<Unit>(Dispatchers.IO) {
        IndependentSkuFixture().use { f ->
            val first = f.activate(); val second = f.activate("004A0000001")
            f.repository.activate(first.imported.id, false, null)
            assertEquals(second.imported.id, f.repository.active()!!.imported.id)
            assertThrows(IllegalStateException::class.java) { f.repository.cancel(first.imported.id) }
            assertArrayEquals(f.bytes(), f.repository.original(first.imported.id))
        }
    }
    @Test fun simultaneousStaleActivationsHaveOnlyOneWinner() = runBlocking<Unit>(Dispatchers.IO) {
        IndependentSkuFixture().use { f ->
            val a = f.stage(); val b = f.stage("004A0000001")
            val gate = CountDownLatch(1); val pool = Executors.newFixedThreadPool(2)
            try {
                val outcomes = listOf(a, b).map { r -> pool.submit<Boolean> { gate.await(); runCatching {
                    f.repository.activate(r.imported.id, false, null)
                }.isSuccess } }
                gate.countDown()
                assertEquals(1, outcomes.count { it.get(30, TimeUnit.SECONDS) })
                assertNotNull(f.repository.active())
            } finally { pool.shutdownNow() }
        }
    }
    @Test fun changedOriginalBlocksReadAndActivation() = runBlocking<Unit>(Dispatchers.IO) {
        IndependentSkuFixture().use { f ->
            val staged = f.stage()
            f.db.openHelper.writableDatabase.execSQL("UPDATE independent_sku_chunk SET bytes=? WHERE sha256=?",
                arrayOf(f.bytes("004A0000001"), staged.imported.sha256))
            assertThrows(IllegalStateException::class.java) { f.repository.original(staged.imported.id) }
            assertThrows(IllegalStateException::class.java) { f.repository.activate(staged.imported.id, false, null) }
            assertNull(f.db.independentSku().active()); assertNull(f.db.independentSku().decision(staged.imported.id))
        }
    }
    @Test fun maximumFileIsChunkedAndReopensWithoutCursorWindowOverflow() = runBlocking<Unit>(Dispatchers.IO) {
        IndependentSkuFixture().use { f ->
            val bytes = ByteArray(IndependentSkuParser.MAX_BYTES) { 32 }
            val staged = f.repository.stage(bytes, "maximum.txt")
            assertFalse(staged.canActivate); f.reopen()
            assertArrayEquals(bytes, f.repository.original(staged.imported.id))
            assertEquals(16, f.db.independentSku().chunks(staged.imported.sha256).size)
        }
    }
    @Test fun oversizedStreamFailsWithoutTouchingPriorCatalog() = runBlocking<Unit>(Dispatchers.IO) {
        IndependentSkuFixture().use { f ->
            val old = f.activate()
            assertThrows(IllegalStateException::class.java) {
                IndependentSkuBytes.read(ByteArrayInputStream(ByteArray(IndependentSkuParser.MAX_BYTES + 1)))
            }
            assertEquals(old.imported.id, f.repository.active()!!.imported.id)
        }
    }
}
