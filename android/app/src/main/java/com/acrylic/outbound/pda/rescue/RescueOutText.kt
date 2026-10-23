package com.acrylic.outbound.pda.rescue

import java.nio.charset.Charset
import java.time.DateTimeException
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.math.BigInteger
import java.util.Locale

/** Legacy five-column export for systems that cannot consume the encrypted rescue package. */
object RescueOutText {
    private val gbk: Charset = Charset.forName("GBK")
    private val timeFormatter = DateTimeFormatter.ofPattern("yyyy/M/d H:mm", Locale.ROOT)
        .withZone(ZoneId.of("Asia/Shanghai"))

    fun encode(snapshot: RescueSnapshot): ByteArray {
        require(snapshot.order.getString("source") == "FORMAL") { "旧式 out.txt 仅支持正式订单" }
        val operator = snapshot.order.optString("operatorName").trim()
        require(operator.isNotEmpty()) { "原订单缺少可信原操作员姓名，不能生成 out.txt" }
        RescueFacts.summary(snapshot)
        val excluded = RescueDispositions.excluded(snapshot)
        val rows = snapshot.scans
            .filter { it.getString("status") == "ACTIVE" && it.getString("captureId") !in excluded }
            .sortedWith(compareBy { it.getString("orderScanSeq").toBigInteger() })
            .map { scan ->
                listOf(scan.getString("model"), scan.getString("barcode"), scan.getString("rawWeight"),
                    operator, formatTime(scan.getString("savedAt")))
            }
        rows.flatten().forEach(::requireCell)
        val text = if (rows.isEmpty()) "" else rows.joinToString("\r\n") { it.joinToString("\t") } + "\r\n"
        return text.toByteArray(gbk)
    }

    private fun formatTime(value: String): String {
        val instant = try {
            if (value.endsWith("Z")) Instant.parse(value) else OffsetDateTime.parse(value).toInstant()
        } catch (_: DateTimeException) {
            throw IllegalArgumentException("扫码时间无效，不能生成 out.txt")
        }
        return timeFormatter.format(instant)
    }

    private fun requireCell(value: String) {
        require(value.isNotEmpty()) { "out.txt 字段不能为空" }
        require('\t' !in value && '\r' !in value && '\n' !in value) { "out.txt 字段包含制表符或换行" }
        require(gbk.newEncoder().canEncode(value)) { "out.txt 字段无法用 GBK 编码" }
    }
}
