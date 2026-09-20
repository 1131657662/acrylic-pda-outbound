package com.acrylic.outbound.pda.retention

import com.acrylic.outbound.pda.data.*
import com.acrylic.outbound.pda.submission.*
import com.acrylic.outbound.pda.work.WorkFixture
import org.json.*
import java.io.File

internal class AuditArchiveFixture(directory: File) : AutoCloseable {
    val work = WorkFixture(directory)
    val db get() = work.db
    val signer = AuditProofFactory()
    var auth = JSONObject(work.session.toString()).put("signingKey", signer.key)
    val device = auth.getString("deviceId")
    val warehouse = auth.getString("warehouseId")
    val calls = ArrayList<String>()
    var response: (Boolean, JSONObject, String) -> JSONObject = ::answer
    val repository get() = AuditArchiveRepository(db, { auth }) { restoring, body, epoch ->
        val after = (if (restoring) body.getJSONObject("archive") else body).getString("afterSeq").toLong()
        check(db.auditArchives().get(epoch, device, after)?.requestJson == SubmissionJson.canonical(body))
        calls += SubmissionJson.canonical(body)
        response(restoring, body, epoch)
    }
    init {
        db.sync().saveRecovery(LocalRecoveryControl(observedServerEpoch = "1", mode = "NORMAL"))
        acknowledge()
    }
    fun acknowledge() = db.audit().save(AuditCheckpoint(auth.getString("serverRecoveryEpoch"), device,
        db.work().deviceEvents(device).last().localSeq))
    fun payload(after: Long, through: Long): JSONObject = AuditArchiveVerifier.payload(device, after, through,
        JSONArray(db.work().deviceEvents(device).filter { it.localSeq > after && it.localSeq <= through }.map(SubmissionRecords::event)))
    fun answer(restoring: Boolean, body: JSONObject, epoch: String): JSONObject = if (restoring)
        signer.sign(JSONObject(body.getJSONObject("archive").toString()).put("recoveryEpoch", epoch))
    else signer.proof(payload(body.getString("afterSeq").toLong(), body.getString("throughSeq").toLong()), warehouse, epoch)
    fun saved(epoch: String = auth.getString("serverRecoveryEpoch")) = db.auditArchives().rows(epoch, device)
    fun epoch(value: String) {
        auth.put("serverRecoveryEpoch", value)
        db.sync().saveRecovery(LocalRecoveryControl(observedServerEpoch = value, mode = "RECONCILING"))
    }
    fun event(type: String = "TEST", detail: String = "") = db.runInTransaction {
        work.store.event(work.assignment, null, type, detail)
    }
    override fun close() = work.close()
}
