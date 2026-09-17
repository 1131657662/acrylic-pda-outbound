package com.acrylic.m71demo.domain

import java.math.BigDecimal
import java.math.RoundingMode

object Weight {
    fun grams(raw: String): Long {
        require(raw.matches(Regex("[0-9]+(?:\\.[0-9]{1,3})?"))) { "重量格式或精度不兼容" }
        val grams = BigDecimal(raw).movePointRight(3).setScale(0, RoundingMode.UNNECESSARY).longValueExact()
        require(grams > 0) { "重量必须是有效正数" }
        return grams
    }

    fun kg(grams: Long): String = BigDecimal.valueOf(grams, 3).stripTrailingZeros().toPlainString()
}
