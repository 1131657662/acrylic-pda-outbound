package com.acrylic.outbound.pda.temporary

import com.acrylic.outbound.pda.data.PdaDatabase
import com.acrylic.outbound.pda.security.JwtVerifier
import com.acrylic.outbound.pda.submission.SubmissionJson
import org.json.JSONObject
import java.util.concurrent.Callable

/** Internal durable archive consumer; never removes facts or grants a business capability. */
class TemporaryArchiveRepository(private val db: PdaDatabase) {
    fun prepare(id: String, epoch: String, receiptKey: JSONObject): TemporaryArchiveRequest = db.runInTransaction(Callable {
        val request = TemporaryArchiveContext.request(db, id, epoch, receiptKey)
        val previous = db.temporaryArchives().request(id, epoch)
        if (previous == null) db.temporaryArchives().insert(request)
        else check(previous == request) { "原归档请求或来源发生变化" }
        request
    })

    fun retain(request: TemporaryArchiveRequest, original: ByteArray): String {
        check(original.size in 1..262144)
        val bytes = original.copyOf(); val hash = JwtVerifier.sha(bytes)
        db.runInTransaction {
            val dao = db.temporaryArchives(); check(dao.request(request.submissionId, request.recoveryEpoch) == request)
            val previous = dao.response(request.submissionId, request.recoveryEpoch, hash)
            if (previous == null) dao.insert(TemporaryArchiveResponse(request.submissionId, request.recoveryEpoch, hash, bytes))
            else check(previous.responseBytes.contentEquals(bytes))
        }
        return hash
    }

    fun verify(id: String, epoch: String, hash: String, receiptKey: JSONObject, archiveKey: JSONObject): JSONObject = try {
        db.runInTransaction(Callable {
            val request = prepare(id, epoch, receiptKey); val dao = db.temporaryArchives()
            val raw = checkNotNull(dao.response(id, epoch, hash))
            check(JwtVerifier.sha(raw.responseBytes) == hash) { "原归档响应摘要损坏" }
            val proof = TemporaryArchiveVerifier.verify(request, raw.responseBytes, archiveKey)
            val digest = TemporaryArchiveVerifier.digest(proof); val keyJson = SubmissionJson.canonical(archiveKey)
            val previous = dao.evidence(id, epoch)
            if (previous == null) dao.accept(TemporaryArchiveEvidence(id, epoch, hash, digest, keyJson))
            else check(previous.proofSha256 == digest && previous.signingKeyJson == keyJson) { "原归档凭据发生变化" }
            dao.result(id, epoch, hash, "VERIFIED", ""); proof
        })
    } catch (error: Exception) {
        db.temporaryArchives().result(id, epoch, hash, "REJECTED", "归档原件未通过核验，本机事实保留")
        throw error
    }

    fun read(id: String, epoch: String, receiptKey: JSONObject, archiveKey: JSONObject): JSONObject? {
        val accepted = db.temporaryArchives().evidence(id, epoch) ?: return null
        return verify(id, epoch, accepted.responseSha256, receiptKey, archiveKey)
    }
}
