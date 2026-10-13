package com.acrylic.outbound.pda.independent

import java.math.BigDecimal
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction

/** Byte columns must decode independently: a multibyte character cannot cross a column boundary. */
internal object IndependentSkuFields {
    private val encoding = Charset.forName("GB18030")
    fun parse(bytes: ByteArray, lineNo: Int): IndependentSkuRow {
        val raw = String(bytes, 0, minOf(bytes.size, 1024), encoding)
        if (bytes.size != 57) return IndependentSkuRow(lineNo, raw, null, "ROW_BYTE_LENGTH", "每行必须为57字节，不含换行")
        return try {
            IndependentSkuRow(lineNo, raw, fields(bytes))
        } catch (error: CharacterCodingException) {
            IndependentSkuRow(lineNo, raw, null, "INVALID_ENCODING", "字段不是有效GB18030编码或字符跨越列边界")
        } catch (error: IllegalArgumentException) {
            IndependentSkuRow(lineNo, raw, null, "INVALID_FIELD", error.message)
        } catch (error: ArithmeticException) {
            IndependentSkuRow(lineNo, raw, null, "INVALID_FIELD", error.message)
        }
    }
    private fun fields(bytes: ByteArray): IndependentSkuBale {
        val model = field(bytes, 0, 20)
        val name = field(bytes, 20, 14)
        val barcode = field(bytes, 34, 13)
        val weight = field(bytes, 47, 10)
        require(model.isNotEmpty() && name.isNotEmpty()) { "型号和品名不能为空" }
        require(barcode.matches(Regex("[A-Z0-9]{11}"))) { "条码必须为11位大写字母或数字" }
        require(weight.matches(Regex("[0-9]+(?:\\.[0-9]{1,3})?"))) { "重量必须为最多3位小数的正数，不能舍入或使用科学计数法" }
        val grams = BigDecimal(weight).movePointRight(3).longValueExact()
        require(grams > 0) { "重量必须大于0" }
        return IndependentSkuBale(model, name, barcode, barcode.substring(0, 3), weight, grams.toString())
    }
    private fun field(bytes: ByteArray, start: Int, length: Int): String = encoding.newDecoder()
        .onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)
        .decode(ByteBuffer.wrap(bytes, start, length)).toString().trim { Character.isWhitespace(it) }
}
