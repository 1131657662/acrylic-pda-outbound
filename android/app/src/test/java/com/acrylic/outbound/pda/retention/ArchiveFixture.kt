package com.acrylic.outbound.pda.retention

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.acrylic.outbound.pda.data.*
import com.acrylic.outbound.pda.submission.*
import com.acrylic.outbound.pda.work.WorkOrder
import org.json.JSONObject
import java.io.File
import java.time.Instant

internal class ArchiveFixture : AutoCloseable {
    val context: Context = ApplicationProvider.getApplicationContext()
    var db: PdaDatabase
    val contract = JSONObject(File(checkNotNull(System.getProperty("pda.archiveFixture"))).readText())
    val body: JSONObject = contract.getJSONObject("body")
    val receipt: JSONObject = contract.getJSONObject("receipt")
    val proof: JSONObject = contract.getJSONObject("confirmation")
    val key: JSONObject = contract.getJSONObject("signingKey")
    val local = LocalSubmission(body.getString("submissionId"), body.getString("assignmentId"), SubmissionJson.canonical(body),
        proof.getString("payloadSha256"), "SYNCED", receipt.toString())
    var auth = JSONObject().put("userId", proof.getString("operatorId")).put("deviceId", proof.getString("freezeDeviceId"))
        .put("warehouseId", proof.getString("warehouseId")).put("serverRecoveryEpoch", proof.getString("recoveryEpoch")).put("signingKey", key)
    val now: Instant = Instant.parse(proof.getString("archivedAt")).plusSeconds(1)
    var calls = 0
    var respond: () -> JSONObject = { JSONObject(proof.toString()) }
    val repository get() = SubmissionArchiveRepository(db, { auth }, { now }) { id, request, epoch ->
        check(id == local.id && epoch == auth.getString("serverRecoveryEpoch"))
        check(request.getString("payloadSha256") == local.sha256 && request.getString("receiptSha256") == proof.getString("receiptSha256"))
        calls++; respond()
    }
    init {
        context.deleteDatabase("outbound-pda.db"); db = PdaDatabase.open(context)
        db.preparations().insertOrder(PreparedOrder(local.assignmentId, proof.getString("orderId"), proof.getString("operatorId"),
            proof.getString("freezeDeviceId"), "bundle", "manifest", "{}", "{}", "{}", key.toString(), "manifest-signature", now.toString(), 0))
        db.work().insertOrder(WorkOrder(local.assignmentId, proof.getString("orderId"), "TEST", 2, 3, state = "FROZEN"))
        db.submissions().insert(local)
        db.sync().saveRecovery(LocalRecoveryControl(observedServerEpoch = proof.getString("recoveryEpoch"), mode = "NORMAL"))
    }
    fun saved() = db.archives().get(local.id, proof.getString("recoveryEpoch"))
    fun reopen() { db.close(); db = PdaDatabase.open(context) }
    override fun close() { db.close(); context.deleteDatabase("outbound-pda.db") }
}
