package com.acrylic.outbound.pda.temporary

import com.acrylic.outbound.pda.work.WorkDetailRow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TemporaryDetailsStatusFilterTest {
    private fun row(status: String) = WorkDetailRow("capture", 7, "003A0000001", status,
        "003", "M", "Product", 1000, "", "")

    @Test fun approvedChoicesMatchOnlyTheirOriginalStatus() {
        assertEquals(listOf("全部", "有效", "已撤销", "主管保留"),
            TemporaryDetailsStatusFilter.entries.map { it.label })
        assertTrue(TemporaryDetailsStatusFilter.ALL.matches(row("ACTIVE")))
        assertTrue(TemporaryDetailsStatusFilter.ALL.matches(row("VOID")))
        assertTrue(TemporaryDetailsStatusFilter.ALL.matches(row("RETAINED")))
        assertTrue(TemporaryDetailsStatusFilter.ACTIVE.matches(row("ACTIVE")))
        assertTrue(TemporaryDetailsStatusFilter.VOID.matches(row("VOID")))
        assertTrue(TemporaryDetailsStatusFilter.RETAINED.matches(row("RETAINED")))
        assertFalse(TemporaryDetailsStatusFilter.ACTIVE.matches(row("RETAINED")))
        assertFalse(TemporaryDetailsStatusFilter.VOID.matches(row("ACTIVE")))
        assertFalse(TemporaryDetailsStatusFilter.RETAINED.matches(row("HISTORY")))
    }
}
