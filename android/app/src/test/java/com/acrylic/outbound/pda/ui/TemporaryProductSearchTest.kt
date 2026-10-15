package com.acrylic.outbound.pda.ui

import com.acrylic.outbound.pda.temporary.TemporaryProduct
import org.junit.Test
import org.junit.Assert.*

class TemporaryProductSearchTest {
    private val products = listOf(TemporaryProduct("003", "p1", "ABC-100", "合成亚克力板"), TemporaryProduct("005", "p2", "DEF", "合成透明板"))
    @Test fun searchUsesCodeModelAndNameWithoutChangingOriginalCatalog() {
        assertEquals(listOf(products[0]), TemporaryProductSearch.filter(products, " 003 "))
        assertEquals(listOf(products[0]), TemporaryProductSearch.filter(products, "abc"))
        assertEquals(listOf(products[1]), TemporaryProductSearch.filter(products, "透明"))
        assertTrue(TemporaryProductSearch.filter(products, "不存在").isEmpty())
        assertEquals(products, TemporaryProductSearch.filter(products, ""))
        assertEquals(2, products.size)
    }
}
