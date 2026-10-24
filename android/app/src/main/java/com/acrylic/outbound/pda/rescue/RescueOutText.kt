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
        RescueFacts.summary(snapshot)
        val names = operators(snapshot)
        val excluded = RescueDispositions.excluded(snapshot)
        val rows = snapshot.scans
            .filter { it.getString("status") == "ACTIVE" && it.getString("captureId") !in excluded }
            .sortedWith(compareBy { it.getString("orderScanSeq").toBigInteger() })
            .map { scan ->
                listOf(scan.getString("model"), scan.getString("barcode"), scan.getString("rawWeight"),
                    names.getValue(scan.getString("captureId")), formatTime(scan.getString("savedAt")))
            }
        rows.flatten().forEach(::requireCell)
        val text = if (rows.isEmpty()) "" else rows.joinToString("\r\n") { it.joinToString("\t") } + "\r\n"
        return text.toByteArray(gbk)
    }

    private fun operators(snapshot: RescueSnapshot): Map<String, String> {
        val order = snapshot.order
        if (order.getString("source") == "FORMAL") {
            val name = order.optString("operatorName").trim()
            require(name.isNotEmpty()) { "原订单缺少可信原操作员姓名，不能生成 out.txt" }
            return snapshot.captures.associate { it.getString("id") to name }
        }
        val names = order.optJSONObject("textOperatorNames")
        require(names != null && names.length() > 0) { "原记录缺少可信原操作员姓名证明，不能生成 out.txt" }
        val credential = if (order.getString("source") == "INDEPENDENT") names.keys().asSequence().single() else null
        return snapshot.captures.associate { capture ->
            if (order.getString("source") == "TEMP") require(capture.getString("operatorId") == order.getString("operatorId") &&
                capture.getString("originDeviceId") == order.getString("deviceId")) { "原采集身份与姓名证明不一致，不能生成 out.txt" }
            val id = credential ?: capture.getString("authorizationId")
            val name = names.optJSONObject(id)?.optString("displayName").orEmpty()
            require(name.isNotBlank()) { "原采集缺少对应的原操作员姓名证明，不能生成 out.txt" }
            capture.getString("id") to name
        }
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
