package com.acrylic.outbound.pda.ui

import org.junit.Assert.*
import org.junit.Test

class AdjustmentFilterTest {
    private val rows = (1L..83L).map { n ->
        AdjustmentRow("capture-$n", n, "003A%07d".format(n),
            if (n % 2L == 1L) "003 · M型号 · 长产品名" else "004 · Other · 第二品名", n, if (n == 1L) "VOID" else "ACTIVE")
    }
    private fun read(after: Long) = rows.filter { it.sequence > after }.take(20)

    @Test fun productSearchCrossesOriginalPagesAndRetainsOriginalSequence() {
        val filter = AdjustmentFilter.parse("", " m型号 ")
        val first = filter.page(0, ::read)
        val second = filter.page(first.last().sequence, ::read)
        assertEquals((1L..39L step 2).toList(), first.map { it.sequence })
        assertEquals((41L..79L step 2).toList(), second.map { it.sequence })
        assertEquals(listOf(81L, 83L), filter.page(second.last().sequence, ::read).map { it.sequence })
        assertEquals("VOID", first.first().status)
    }
    @Test fun exactOriginalSequenceFindsOldRowOutsideRecentWindow() {
        assertEquals(listOf(rows[60]), AdjustmentFilter.parse("61", "产品名").page(0, ::read))
        assertTrue(AdjustmentFilter.parse("61", "004").page(0, ::read).isEmpty())
        assertTrue(AdjustmentFilter.parse("999", "").page(0, ::read).isEmpty())
    }
    @Test fun specialCharactersAreLiteralAndNeverBroadenSearch() {
        listOf("%", "_", "' OR 1=1 --", "missing").forEach { query ->
            assertTrue(AdjustmentFilter.parse("", query).page(0, ::read).isEmpty())
        }
    }
    @Test fun invalidInputRejectsInsteadOfDroppingFilter() {
        listOf("0", "-1", "1.0", "9223372036854775808", "abc").forEach { number ->
            assertTrue(runCatching { AdjustmentFilter.parse(number, "") }.isFailure)
        }
        assertTrue(runCatching { AdjustmentFilter.parse("", "x".repeat(129)) }.isFailure)
        assertTrue(runCatching { AdjustmentFilter.parse("", "a\nb") }.isFailure)
    }
    @Test fun repeatedOrUnorderedSourceRowsRejectInsteadOfLooping() {
        assertTrue(runCatching { AdjustmentFilter().page(0) { listOf(rows[0], rows[0]) } }.isFailure)
        assertTrue(runCatching { AdjustmentFilter().page(1) { listOf(rows[0]) } }.isFailure)
    }
}
