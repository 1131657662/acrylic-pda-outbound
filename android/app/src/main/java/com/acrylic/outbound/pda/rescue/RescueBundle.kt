package com.acrylic.outbound.pda.rescue

import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Format core only. Callers must acquire a scoped read grant and a consistent transaction snapshot. */
object RescueBundle {
    const val PURPOSE = "PDA_READONLY_RESCUE_V1"
    private val optional = setOf("submission.json", "receipt.json", "submission-info.json", "source-evidence.ndjson")
    internal val independentFiles = setOf("independent-sku.bin", "independent-source.json", "independent-voids.ndjson", "independent-state.json")

    fun build(snapshot: RescueSnapshot, signer: RescueSigner): ByteArray {
        val summary = RescueFacts.summary(snapshot)
        val independent = snapshot.order.getString("source") == "INDEPENDENT"
        require(if (independent) snapshot.originals.keys == independentFiles else snapshot.originals.keys.all { it in optional })
        if (independent) {
            RescueIndependentValidation.verify(snapshot)
            val source = JSONObject(snapshot.originals.getValue("independent-source.json").toString(Charsets.UTF_8))
            check(source.getJSONObject("credential").getString("deviceKeySha256") == sha(signer.publicKey)) {
                "原独立采集设备钥与导出签名钥不一致，请保留原件"
            }
        }
        originals(snapshot.originals)
        val files = linkedMapOf("order.json" to snapshot.order.toString().toByteArray(Charsets.UTF_8),
            "captures.ndjson" to RescueText.jsonLines(snapshot.captures), "scans.ndjson" to RescueText.jsonLines(snapshot.scans),
            "events.ndjson" to RescueText.jsonLines(snapshot.events), "lines.ndjson" to RescueText.jsonLines(snapshot.lines),
            "dispositions.ndjson" to RescueText.jsonLines(snapshot.dispositions),
            "scan-details.tsv" to RescueFacts.scanTable(snapshot), "product-summary.tsv" to RescueFacts.productTable(snapshot),
            "readme.txt" to ("本机只读事实副本，不等于出库确认或放行。未决输入不计有效数。电脑表格不能替代原件。\n" +
                if (independent) "独立来源始终待主管对账；服务端原采集授权尚待核对，不自动关联或记账。\n" else "").toByteArray(Charsets.UTF_8))
        files.putAll(snapshot.originals)
        require(files.values.sumOf { it.size.toLong() } <= RescueEnvelope.MAX_BYTES)
        val manifest = JSONObject().put("purpose", PURPOSE).put("exportId", UUID.randomUUID().toString())
            .put("deviceKeySha256", sha(signer.publicKey)).put("summary", summary)
            .put("files", JSONObject().apply { files.forEach { (name, bytes) ->
                put(name, JSONObject().put("bytes", bytes.size.toString()).put("sha256", sha(bytes)))
            } })
        val text = manifest.toString()
        files["manifest.json"] = text.toByteArray(Charsets.UTF_8)
        files["manifest.sig"] = RescueSeal.sign("$PURPOSE\n$text", signer)
        files["device-public-key.der"] = signer.publicKey.copyOf()
        return zip(files)
    }

    fun sha(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    private fun originals(files: Map<String, ByteArray>) {
        val payload = files["submission.json"]
        if (payload == null) {
            require("submission-info.json" !in files && "receipt.json" !in files) { "没有原冻结正文，不能补造回执归属" }
            return
        }
        val info = JSONObject(checkNotNull(files["submission-info.json"]).toString(Charsets.UTF_8))
        require(sha(payload) == info.getString("sha256")) { "原冻结正文摘要不一致" }
        require(JSONObject(payload.toString(Charsets.UTF_8)).getString("submissionId") == info.getString("id"))
    }

    private fun zip(files: Map<String, ByteArray>): ByteArray {
        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { zip -> files.forEach { (name, bytes) ->
            zip.putNextEntry(ZipEntry(name).apply { time = 0 }); zip.write(bytes); zip.closeEntry()
        } }
        return output.toByteArray().also { require(it.size <= RescueEnvelope.MAX_BYTES) }
    }
}
