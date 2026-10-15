package com.acrylic.outbound.pda.ui

import com.acrylic.outbound.pda.temporary.TemporaryProduct
import java.util.Locale

object TemporaryProductSearch {
    fun filter(products: List<TemporaryProduct>, query: String): List<TemporaryProduct> {
        val needle = query.trim().lowercase(Locale.ROOT)
        return if (needle.isEmpty()) products else products.filter {
            listOf(it.code, it.model, it.name).any { value -> value.lowercase(Locale.ROOT).contains(needle) }
        }
    }
}
