package com.acrylic.outbound.pda.temporary

import com.acrylic.outbound.pda.data.LocalRecoveryControl
import org.json.JSONObject
import java.io.File
import java.util.UUID

/** Imported real server vector with synthetic local identity/preparation binding for repository tests. */
class TemporaryProductSuccessFixture(root: File) : AutoCloseable {
    val f = TemporarySuccessFixture(root)
    val db get() = f.db
    val id = f.local.id
    val guardBarcode = f.work.barcodes[0]
    val requests = mutableListOf<TemporaryConnection.Original>()
    var reply: (TemporaryConnection.Original) -> ByteArray = { f.bytes }
    val connection = object : TemporaryConnection by f.work.fixture {
        override fun original(request: TemporaryConnection.Original): ByteArray {
            check(!db.inTransaction()); requests += request; return reply(request)
        }
        override fun call(request: TemporaryConnection.Call): JSONObject = error("Unexpected parsed call ${request.path}")
    }
    val repository get() = TemporarySubmissionRepository(db, connection)

    init {
        val draft = checkNotNull(db.temporaryDrafts().get(f.local.clientOrderId))
        val old = checkNotNull(db.temporary().byAuthorization(draft.authorizationId))
        val request = checkNotNull(db.temporary().request(old.requestKey))
        val key = UUID.randomUUID().toString()
        db.temporary().insertRequest(request.copy(key = key, signingKeyJson = f.key.toString()))
        db.temporary().insertPreparation(old.copy(authorizationId = f.body.getString("authorizationId"), requestKey = key))
        db.temporaryDrafts().update(draft.copy(authorizationId = f.body.getString("authorizationId")))
        f.work.fixture.auth!!.put("deviceId", draft.creatorDeviceId).put("userId", draft.operatorId)
            .put("warehouseId", draft.warehouseId).put("serverRecoveryEpoch", f.body.getString("sourceRecoveryEpoch"))
        db.sync().saveRecovery(LocalRecoveryControl(observedServerEpoch = f.body.getString("sourceRecoveryEpoch"), mode = "NORMAL"))
        db.temporarySubmissions().queue(id)
    }
    fun noAbort() = db.openHelper.writableDatabase.execSQL("UPDATE temporary_submission SET abortReason=NULL WHERE id=?", arrayOf(id))
    fun reopen() = f.work.fixture.reopen() // Reopen Room without activating the unrelated preparation fixture.
    fun pending() = JSONObject(f.bytes.toString(Charsets.UTF_8)).put("data", f.vector.getJSONObject("pendingReceipt")).toString().toByteArray()
    override fun close() = f.close()
}
