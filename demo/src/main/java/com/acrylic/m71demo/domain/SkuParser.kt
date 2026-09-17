package com.acrylic.m71demo.domain

import com.acrylic.m71demo.data.InventoryBale
import java.nio.ByteBuffer
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction

object SkuParser {
    fun parse(bytes: ByteArray, snapshotId: String): InventoryBale {
        require(bytes.size == 57) { "sku行必须是57字节，实际${bytes.size}" }
        val barcode = field(bytes, 34 until 47)
        require(barcode.matches(Regex("[0-9A-Z]{11}"))) { "条码格式错误" }
        val weight = field(bytes, 47 until 57)
        return InventoryBale(snapshotId, barcode, barcode.take(3), field(bytes, 0 until 20),
            field(bytes, 20 until 34), Weight.grams(weight), weight)
    }

    private fun field(bytes: ByteArray, range: IntRange): String = Charset.forName("GBK")
        .newDecoder().onMalformedInput(CodingErrorAction.REPORT)
        .onUnmappableCharacter(CodingErrorAction.REPORT)
        .decode(ByteBuffer.wrap(bytes.copyOfRange(range.first, range.last + 1))).toString().trim()
}
