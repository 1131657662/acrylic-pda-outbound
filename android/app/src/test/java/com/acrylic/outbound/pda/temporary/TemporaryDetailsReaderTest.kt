package com.acrylic.outbound.pda.temporary

import com.acrylic.outbound.pda.work.WorkDetailFilter
import kotlinx.coroutines.*
import org.junit.*
import org.junit.Assert.*
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.*
import java.util.UUID

@RunWith(RobolectricTestRunner::class) @Config(sdk = [28], application = android.app.Application::class)
@SQLiteMode(SQLiteMode.Mode.NATIVE)
class TemporaryDetailsReaderTest {
    @get:Rule val folder = TemporaryFolder()
    private fun scenario(block: (TemporaryDetailsFixture) -> Unit) = runBlocking<Unit>(Dispatchers.IO) {
        TemporaryDetailsFixture(folder.root).use {
            try { it.populate(); block(it) }
            catch (error: Throwable) { TemporaryDetailsFailure.preserve(it.db, error); throw error }
        }
    }
    @Test fun pagesKeepOriginalSequenceAndNeverWriteFacts() = scenario { f ->
        val events = f.db.temporaryScans().events(f.work.id)
        val first = f.reader.page(WorkDetailFilter.ALL)
        val second = f.reader.page(WorkDetailFilter.ALL, first.rows.last().sequence)
        val third = f.reader.page(WorkDetailFilter.ALL, second.rows.last().sequence)
        assertEquals(listOf(20,20,10), listOf(first,second,third).map { it.rows.size })
        assertEquals((1L..50L).toList(), (first.rows+second.rows+third.rows).map { it.sequence })
        assertFalse(first.previous); assertTrue(first.next); assertTrue(second.previous); assertTrue(second.next)
        assertTrue(third.previous); assertFalse(third.next)
        assertEquals(second.rows, f.reader.page(WorkDetailFilter.ALL, third.rows.first().sequence, true).rows)
        assertEquals(first.rows, f.reader.page(WorkDetailFilter.ALL, second.rows.first().sequence, true).rows)
        assertEquals(events,f.db.temporaryScans().events(f.work.id))
        assertEquals(1L,f.reader.page(WorkDetailFilter.ALL).total.count)
        f.export()
    }
    @Test fun filtersKeepSavedVoidAndPendingSeparate() = scenario { f ->
        assertEquals(listOf(46L),f.reader.page(WorkDetailFilter.SAVED).rows.map { it.sequence })
        val voids=f.reader.page(WorkDetailFilter.VOID)
        assertEquals((1L..20L).toList(),voids.rows.map { it.sequence }); assertTrue(voids.next)
        val pending=f.reader.page(WorkDetailFilter.UNRESOLVED)
        assertEquals((47L..50L).toList(),pending.rows.map { it.sequence })
        assertEquals(listOf("WAITING_CONFIRM","INVALID","RECEIVED","CONFIRM_REQUESTED"),pending.rows.map { it.status })
    }
    @Test fun locatorFindsOriginalSequenceAndLastBarcodeIncludingUnresolved() = scenario { f ->
        assertEquals("VOID", f.reader.locate("5",false)?.status)
        assertEquals(50L,f.reader.locate(f.work.barcodes[0].lowercase(),true)?.sequence)
        assertEquals(46L,f.reader.locate(f.work.barcodes[1],true)?.sequence)
        assertNull(f.reader.locate("999",false))
        assertThrows(Exception::class.java) { f.reader.locate("0",false) }
        assertThrows(Exception::class.java) { f.reader.locate("9223372036854775808",false) }
        assertThrows(Exception::class.java) { f.reader.locate("' OR 1=1 --",true) }
    }
    @Test fun changedIdentityCannotReadButExpiryDoesNotDestroyReadOnlyFacts() = scenario { f ->
        f.work.fixture.time=f.work.fixture.time.plusSeconds(172801)
        assertEquals(20,f.reader.page(WorkDetailFilter.ALL).rows.size)
        f.work.fixture.auth!!.put("userId",UUID.randomUUID().toString())
        assertThrows(Exception::class.java) { f.reader.page(WorkDetailFilter.ALL) }
        assertThrows(Exception::class.java) { f.reader.locate("5",false) }
    }
    @Test fun emptyDraftHasNoInventedRowsOrNavigation() = runBlocking<Unit>(Dispatchers.IO) {
        TemporaryDetailsFixture(folder.root).use { f ->
            WorkDetailFilter.entries.forEach {
                val page=f.reader.page(it)
                assertTrue(page.rows.isEmpty()); assertFalse(page.previous); assertFalse(page.next)
            }
        }
    }
    @Test fun verifiedBlockedContinuationKeepsPredecessorVisibleWithoutActivation() = runBlocking<Unit>(Dispatchers.IO) {
        TemporaryContinuationFixture(folder.root).use { f ->
            try {
            val predecessorBarcode = f.work.barcodes[0]
            val next=f.activate(); val before=f.frozen
            val reader=TemporaryDetailsReader(f.work.store,next.clientOrderId)
            val page=reader.page(WorkDetailFilter.SAVED)
            assertEquals(listOf(1L),page.rows.map { it.sequence }); assertEquals("RETAINED",page.rows.single().status)
            assertEquals(before,f.db.temporarySubmissions().get(before.id))
            assertEquals("CONTINUATION_BLOCKED",f.db.temporaryDrafts().get(next.clientOrderId)?.state)
            assertTrue(f.db.temporaryScans().captures(next.clientOrderId).isEmpty())
            assertEquals(page.rows.single(),reader.locate(predecessorBarcode,true))
            TemporaryDetailsPrefixExport.save(f, next.clientOrderId, predecessorBarcode)
            } catch (error: Throwable) { TemporaryDetailsFailure.preserve(f.db, error); throw error }
        }
    }
}
