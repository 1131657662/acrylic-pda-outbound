package com.acrylic.m71demo.export

import com.acrylic.m71demo.data.FrozenSubmission
import com.acrylic.m71demo.device.Telemetry
import com.acrylic.m71demo.workflow.*
import org.json.JSONObject
import javax.inject.Inject

open class OrderExporter @Inject constructor(private val store: DemoStore, private val telemetry: Telemetry,
    private val fault: com.acrylic.m71demo.device.FaultGate? = null) {
    fun export(orderId: String): String {
        check(store.db.captures().pendingCount(orderId) == 0) { "存在未决输入，先确认/恢复/放弃" }
        check(store.db.captures().failure(orderId) == null) { "先确认处理失败提示" }
        val target = java.io.File(telemetry.directory, "$orderId/${java.util.UUID.randomUUID()}").apply { check(mkdirs()) }
        // Persist the new directory entries before a later database row can reference their files.
        syncDirectory(telemetry.directory.parentFile!!)
        syncDirectory(telemetry.directory)
        syncDirectory(target.parentFile!!)
        writeCaptures(target, orderId)
        writeScans(target, orderId)
        writeAudit(target, orderId)
        writeManifest(target, orderId)
        return target.absolutePath
    }

    fun freeze(orderId: String): String {
        val order = requireNotNull(store.db.orders().get(orderId))
        check(order.actualCount > 0) { "整单0包不能冻结出库" }
        store.db.orders().submission(orderId)?.let {
            verifyRegisteredBody(it)
            return it.payloadPath
        }
        val path = export(orderId)
        val digest = sha256(java.io.File(path, "manifest.json"))
        fault?.hit("BEFORE_FREEZE_REGISTER", orderId)
        store.db.runInTransaction {
            store.db.orders().insertSubmission(FrozenSubmission(java.util.UUID.randomUUID().toString(), orderId,
                path, digest, store.clock.wallMs()))
            store.db.orders().update(order.copy(state = "FROZEN"))
            store.audit(orderId, null, AuditDetail("FROZEN_TEST_ONLY", "测试证据文件，不是云端出库回执"))
        }
        fault?.hit("AFTER_FREEZE_REGISTER", orderId)
        return path
    }

    private fun verifyRegisteredBody(submission: FrozenSubmission) {
        val manifest = java.io.File(submission.payloadPath, "manifest.json")
        check(sha256(manifest) == submission.payloadSha256) { "冻结清单缺失或摘要不符，停止并核对" }
        val digests = JSONObject(manifest.readText(Charsets.UTF_8)).getJSONObject("files")
        for (name in listOf("captures.ndjson", "scans.ndjson", "audit.ndjson")) {
            check(sha256(java.io.File(submission.payloadPath, name)) == digests.getString(name)) {
                "冻结明细缺失或摘要不符，保留原正文并停止核对：$name"
            }
        }
    }

    private fun writeCaptures(target: java.io.File, id: String) = write(target, "captures.ndjson") { writer ->
        var seq = 0L
        while (true) {
            val rows = store.db.captures().page(id, seq, 200)
            if (rows.isEmpty()) break
            rows.forEach { writer.write(JsonRows.capture(it).toString() + "\n") }
            seq = rows.last().context.orderScanSeq
        }
    }

    private fun writeScans(target: java.io.File, id: String) = write(target, "scans.ndjson") { writer ->
        var seq = 0L
        while (true) {
            val rows = store.db.scans().page(id, seq, 200)
            if (rows.isEmpty()) break
            rows.forEach { writer.write(JsonRows.scan(it).toString() + "\n") }
            seq = rows.last().context.orderScanSeq
        }
    }

    private fun writeAudit(target: java.io.File, id: String) = write(target, "audit.ndjson") { writer ->
        var seq = 0L
        var origin = ""
        while (true) {
            val rows = store.db.audit().exportPage(id, origin, seq, 200)
            if (rows.isEmpty()) break
            rows.forEach { writer.write(JsonRows.audit(it).toString() + "\n") }
            seq = rows.last().localSeq
            origin = rows.last().originDeviceId
        }
    }

    private fun writeManifest(target: java.io.File, id: String) {
        val order = requireNotNull(store.db.orders().get(id))
        val settings = com.acrylic.m71demo.data.DatabaseDiagnostics.writer(store.db)
        val json = JSONObject().put("schemaVersion", 1).put("purpose", "DEMO_EVIDENCE_NOT_CLOUD_SUBMISSION")
            .put("orderId", id).put("vehicle", order.vehicle).put("source", order.source)
            .put("stateAtExport", order.state).put("exportId", target.name).put("exportedAt", store.clock.wallMs())
            .put("savedCount", order.actualCount).put("weightGrams", order.weightGrams.toString())
            .put("captureCount", store.db.captures().count(id)).put("captureHighWatermark", order.nextOrderScanSeq - 1)
            .put("sqliteSynchronous", settings.synchronous).put("sqliteJournalMode", settings.journalMode)
            .put("sqliteConnection", "PRIMARY_WRITER_IN_TRANSACTION").put("sqliteForeignKeys", settings.foreignKeys)
            .put("sqliteWalAutoCheckpointPages", settings.walAutoCheckpointPages)
            .put("diagnosticDropped", telemetry.dropped.get()).put("physicalValidationPassed", false)
            .put("files", JSONObject(listOf("captures.ndjson", "scans.ndjson", "audit.ndjson")
                .associateWith { sha256(java.io.File(target, it)) }))
            .put("lines", org.json.JSONArray(store.db.orders().lines(id).map { line ->
                JSONObject().put("deliveryCode", line.deliveryCode).put("plannedCount", line.plannedCount)
                    .put("actualCount", line.actualCount).put("weightGrams", line.weightGrams.toString()) }))
        write(target, "manifest.json") { it.write(json.toString(2)) }
    }

    private fun sha256(file: java.io.File): String {
        val digest = java.security.MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(8192)
            while (true) { val size = input.read(buffer); if (size < 0) break; digest.update(buffer, 0, size) }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun write(target: java.io.File, name: String, content: (java.io.BufferedWriter) -> Unit) {
        val temp = java.io.File(target, "$name.tmp")
        java.io.FileOutputStream(temp).use { stream ->
            val writer = stream.bufferedWriter(Charsets.UTF_8)
            content(writer)
            writer.flush()
            stream.fd.sync()
        }
        check(temp.renameTo(java.io.File(target, name))) { "证据文件改名失败" }
        syncDirectory(target)
    }

    // Platform boundary: Windows Robolectric cannot open Android/Linux directory descriptors.
    protected open fun syncDirectory(directory: java.io.File) {
        check(directory.isDirectory) { "证据目录不存在" }
        val descriptor = android.system.Os.open(directory.absolutePath, android.system.OsConstants.O_RDONLY, 0)
        try { android.system.Os.fsync(descriptor) }
        finally { android.system.Os.close(descriptor) }
    }
}
