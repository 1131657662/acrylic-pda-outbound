package com.acrylic.outbound.pda.rescue

import android.content.Context
import android.net.Uri
import android.util.AtomicFile
import org.json.JSONObject
import java.io.File
import java.util.UUID

data class RescueGeneratedFile(val id: String, val file: File, val sha256: String, val summary: String)
data class RescuePrivatePackage(val id: String, val summary: String)

/** Durable private journal distinguishes generation, chosen target, incomplete copy and verified copy. */
class RescueExportFiles(context: Context) {
    private val directory = File(context.noBackupFilesDir, "rescue-exports").apply { mkdirs() }
    private val resolver = context.contentResolver
    fun available(grant: RescueReadGrant): List<RescuePrivatePackage> {
        grant.recheck()
        val journals = directory.listFiles { file -> file.extension == "json" }.orEmpty()
        check(journals.size <= 2_000) { "私有包目录超过读取上限，请保留原件" }
        val packages = journals.sortedByDescending { it.lastModified() }.mapNotNull { file ->
            val journal = runCatching { read(UUID.fromString(file.nameWithoutExtension).toString()) }.getOrNull()
                ?: return@mapNotNull null
            if (!journal.has("sha256") || runCatching { grant.check(journal) }.isFailure) return@mapNotNull null
            RescuePrivatePackage(journal.getString("id"), journal.getString("summary"))
        }.take(50)
        grant.recheck()
        return packages
    }
    fun generate(snapshot: RescueSnapshot, inputs: Pair<RescueReadGrant, RescueSigner>, password: CharArray): RescueGeneratedFile {
        inputs.first.check(snapshot.order)
        val id = UUID.randomUUID().toString()
        val file = File(directory, "$id.pdarescue")
        val journal = JSONObject().put("id", id).put("state", "GENERATING").put("source", snapshot.order.getString("source"))
            .put("localOrderId", snapshot.order.getString("localOrderId")).put("operatorId", snapshot.order.getString("operatorId"))
            .put("deviceId", snapshot.order.getString("deviceId")).put("warehouseId", snapshot.order.getString("warehouseId"))
        save(journal)
        try {
            val bytes = RescueEnvelope.encrypt(RescueBundle.build(snapshot, inputs.second), password)
            inputs.first.check(snapshot.order)
            file.outputStream().use { it.write(bytes); it.flush(); it.fd.sync() }
            val digest = RescueFileTransfer.digest { file.inputStream() }
            check(digest == RescueBundle.sha(bytes)) { "私有应急包回读不一致" }
            val facts = RescueFacts.summary(snapshot)
            val summary = "${snapshot.order.getString("source")} · ${snapshot.order.getString("localOrderId")}\n" +
                "有效 ${facts.getString("active")} · VOID ${facts.getString("void")} · 处置 ${facts.getString("disposed")} · 未决 ${facts.getString("pending")}\n" +
                "有效整数克 ${facts.getString("weightGrams")} · ${if ("submission.json" in snapshot.originals) "存在原冻结批次" else "未冻结，不补造批次"}"
            save(journal.put("state", "GENERATED").put("sha256", digest).put("summary", summary))
            return RescueGeneratedFile(id, file, digest, summary)
        } catch (error: Exception) { save(journal.put("state", "GENERATION_INCOMPLETE")); throw error }
        finally { password.fill('\u0000') }
    }
    fun copy(generated: RescueGeneratedFile, uri: Uri, grant: RescueReadGrant): String {
        val journal = read(generated.id)
        grant.check(journal)
        save(journal.put("state", "COPYING").put("targetUri", uri.toString()))
        try {
            check(RescueFileTransfer.digest { generated.file.inputStream() } == generated.sha256)
            val digest = RescueFileTransfer.copy(generated.file, { resolver.openOutputStream(uri, "wt") } to { resolver.openInputStream(uri) })
            grant.recheck()
            save(journal.put("state", "TARGET_VERIFIED").put("targetSha256", digest))
            return digest
        } catch (error: Exception) { save(journal.put("state", "COPY_INCOMPLETE")); throw error }
    }
    fun recover(id: String, grant: RescueReadGrant): RescueGeneratedFile {
        val normalized = UUID.fromString(id).toString()
        val journal = read(normalized)
        grant.check(journal)
        val file = File(directory, "$normalized.pdarescue")
        val digest = journal.getString("sha256")
        check(RescueFileTransfer.digest { file.inputStream() } == digest) { "原私有包损坏，请保留资料重新生成" }
        return RescueGeneratedFile(normalized, file, digest, journal.getString("summary"))
    }
    private fun read(id: String): JSONObject {
        val file = AtomicFile(File(directory, "$id.json"))
        file.openRead().use { input ->
            val bytes = ByteArray(32_769); var offset = 0
            while (offset < bytes.size) {
                val count = input.read(bytes, offset, bytes.size - offset)
                if (count <= 0) break
                offset += count
            }
            check(offset <= 32_768) { "私有包状态记录异常，请保留原件" }
            return JSONObject(String(bytes, 0, offset, Charsets.UTF_8))
        }
    }
    private fun save(journal: JSONObject) {
        val file = AtomicFile(File(directory, "${journal.getString("id")}.json")); val output = file.startWrite()
        try { output.write(journal.toString().toByteArray(Charsets.UTF_8)); file.finishWrite(output) }
        catch (error: Exception) { file.failWrite(output); throw error }
    }
}
