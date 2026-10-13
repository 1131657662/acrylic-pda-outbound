package com.acrylic.outbound.pda.independent

import com.acrylic.outbound.pda.ui.IndependentSkuDisplay
import kotlinx.coroutines.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.*

@RunWith(RobolectricTestRunner::class) @Config(sdk = [28], application = android.app.Application::class)
@SQLiteMode(SQLiteMode.Mode.NATIVE)
class IndependentSkuHistoryTest {
    @Test fun originalHistoryPagesWithoutRepeatingOrLosingIdenticalFiles() = runBlocking<Unit>(Dispatchers.IO) {
        IndependentSkuFixture().use { f ->
            val ids = (1..52).map { f.stage().imported.id }
            val page1 = f.repository.history(); val page2 = f.repository.history(page1.last().sequence)
            val page3 = f.repository.history(page2.last().sequence)
            assertEquals(listOf(25, 25, 2), listOf(page1.size, page2.size, page3.size))
            assertEquals(ids.reversed(), (page1 + page2 + page3).map { it.imported.id })
            assertEquals(1, (page1 + page2 + page3).map { it.imported.sha256 }.distinct().size)
            assertTrue(f.repository.history(page3.last().sequence).isEmpty())
        }
    }
    @Test fun issueFilterRetainsActualLineNumberAndVisibleControlCharacter() = runBlocking<Unit>(Dispatchers.IO) {
        IndependentSkuFixture().use { f ->
            val bytes = f.bytes() + byteArrayOf(10, 10, 0, 10) + f.bytes()
            val review = f.repository.stage(bytes, "sku.txt")
            val issues = IndependentSkuDisplay.filtered(review, true)
            assertEquals(listOf(3, 4), issues.map { it.lineNo })
            val text = IndependentSkuDisplay.rows(review, 0, true)
            assertTrue(text.contains("原第 3 行")); assertTrue(text.contains("\\u0000"))
            assertTrue(text.contains("原第 4 行")); assertFalse(text.contains("原第 1 行"))
        }
    }
}
