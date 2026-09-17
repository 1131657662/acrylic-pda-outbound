package com.acrylic.m71demo

import com.acrylic.m71demo.domain.*
import com.acrylic.m71demo.fixtures.OrderCatalog
import org.junit.Assert.*
import org.junit.Test

class WeightAndParserTest {
    @Test fun decimalWeightsKeepAllSignificantDigits() {
        assertEquals(300500L, Weight.grams("300.50"))
        assertEquals("300.5", Weight.kg(300500))
        assertEquals("601.001", Weight.kg(Weight.grams("300.5") + Weight.grams("300.501")))
    }

    @Test fun excessivePrecisionIsRejectedWithoutRounding() {
        assertThrows(IllegalArgumentException::class.java) { Weight.grams("300.5001") }
    }

    @Test fun smallTailBaleIsAccepted() { assertEquals(15500L, Weight.grams("15.5")) }

    @Test fun parserUsesByteWidthsForChineseAndKeepsLeadingZeros() {
        val line = "H815B 1.7 RE 38 300 ".toByteArray(Charsets.US_ASCII) +
            "有光短纤      ".toByteArray(charset("GBK")) + "003B0051601  305       ".toByteArray()
        val parsed = SkuParser.parse(line, "sample")
        assertEquals("有光短纤", parsed.name)
        assertEquals("003B0051601", parsed.barcode)
        assertEquals(305000L, parsed.weightGrams)
    }

    @Test fun truncatedLineCannotBecomeValidInventory() {
        assertThrows(IllegalArgumentException::class.java) { SkuParser.parse(ByteArray(56), "sample") }
    }

    @Test fun multipleProductPlansCanExceed116() {
        assertEquals(360, OrderCatalog.parsePlans("003:120,571:240").sumOf { it.second })
    }

    @Test fun zeroOrDuplicatePlansAreRejected() {
        assertThrows(IllegalArgumentException::class.java) { OrderCatalog.parsePlans("003:0") }
        assertThrows(IllegalArgumentException::class.java) { OrderCatalog.parsePlans("003:1,003:2") }
    }
}
