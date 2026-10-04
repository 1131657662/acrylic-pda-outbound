package com.acrylic.outbound.pda.continuation

import com.acrylic.outbound.pda.data.PdaDatabase
import com.acrylic.outbound.pda.network.SessionIdentity
import com.acrylic.outbound.pda.preparation.PackageVerifier
import com.acrylic.outbound.pda.preparation.VerifiedPackage
import com.acrylic.outbound.pda.sync.RecoveryProtection
import com.acrylic.outbound.pda.work.WorkOrder
import org.json.JSONObject
import java.io.File
import java.util.UUID

/** Downloads target material without publishing an empty prepared work before the source facts. */
internal class HandoverPreparationDownload(private val db: PdaDatabase,
    private val connection: HandoverDownloadConnection) {
    fun import(caseId: String, cache: File, progress: (String) -> Unit): WorkOrder {
        val id = UUID.fromString(caseId).toString()
        val expected = JSONObject(checkNotNull(connection.current()) { "请先登录" }.toString())
        RecoveryProtection(db).requireNormal()
        val stage = checkNotNull(db.handoverStages().get(id)) { "请先暂存交接资料" }
        val assignment = connection.call(HandoverDownloadConnection.Call("pda/handovers/$id/assignment", expected))
        SessionIdentity.requireSame(expected, connection.current())
        HandoverTargetAssignmentVerifier.verify(stage, assignment, expected, connection.now())
        HandoverRecoveryFacts.verify(stage)
        HandoverRecoveryAudit.verify(stage)
        val target = UUID.fromString(assignment.getString("id")).toString()
        val receipt = connection.call(HandoverDownloadConnection.Call("pda/assignments/$target/preparation", expected, JSONObject()))
        SessionIdentity.requireSame(expected, connection.current())
        val key = expected.getJSONObject("signingKey")
        val manifest = PackageVerifier.manifest(receipt, assignment, key, connection.now())
        val directory = File(cache, "handover-${UUID.randomUUID()}")
        check(directory.mkdirs()) { "无法创建交接下载目录" }
        try {
            val payloads = download(Download(target, expected, manifest, directory), progress)
            progress("资料已校验，正在保存原交接记录")
            return persist(stage, VerifiedPackage(assignment, receipt, key, expected, payloads, connection.now()))
        } finally { directory.deleteRecursively() }
    }

    private fun download(input: Download, progress: (String) -> Unit): Map<String, ByteArray> {
        val result = HashMap<String, ByteArray>()
        val bundle = UUID.fromString(input.manifest.getString("bundleId")).toString()
        val parts = input.manifest.getJSONArray("parts")
        for (index in 0 until parts.length()) {
            val part = parts.getJSONObject(index)
            val name = part.getString("name")
            check(name in setOf("plan", "inventory", "availability"))
            progress("正在下载并校验 ${index + 1}/3")
            SessionIdentity.requireSame(input.expected, connection.current())
            val file = File(input.directory, "$name.gz")
            val path = "pda/assignments/${input.target}/content/$name?bundleId=$bundle"
            connection.download(HandoverDownloadConnection.Download(path, input.expected, file))
            SessionIdentity.requireSame(input.expected, connection.current())
            result[name] = PackageVerifier.unpack(file, part)
        }
        return result
    }

    private fun persist(stage: LocalHandoverStage, input: VerifiedPackage): WorkOrder {
        lateinit var result: WorkOrder
        db.runInTransaction {
            val current = connection.current()
            SessionIdentity.requireSame(input.session, current)
            val session = JSONObject(checkNotNull(current).toString())
            RecoveryProtection(db).requireNormal()
            val refreshed = input.copy(session = session, key = session.getJSONObject("signingKey"), now = connection.now())
            result = HandoverLocalImport(db).install(stage, refreshed)
            SessionIdentity.requireSame(session, connection.current())
        }
        return result
    }

    private data class Download(val target: String, val expected: JSONObject, val manifest: JSONObject, val directory: File)
}
