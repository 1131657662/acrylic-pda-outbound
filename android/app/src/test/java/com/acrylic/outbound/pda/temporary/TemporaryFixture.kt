package com.acrylic.outbound.pda.temporary

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.acrylic.outbound.pda.data.PdaDatabase
import com.acrylic.outbound.pda.preparation.ContractFixture
import org.json.JSONObject
import java.io.File
import java.time.Instant
import java.util.*

class TemporaryFixture(val root: File, borrowed: PdaDatabase? = null) : TemporaryConnection, AutoCloseable {
    val context: Context = ApplicationProvider.getApplicationContext()
    private val ownsDatabase = borrowed == null
    var db: PdaDatabase = borrowed ?: run { context.deleteDatabase("outbound-pda.db"); PdaDatabase.open(context) }
    val fixture = JSONObject(File(checkNotNull(System.getProperty("pda.temporaryFixture"))).readText())
    val receipt = fixture.getJSONObject("receipt")
    val key = fixture.getJSONObject("signingKey")
    val manifest = JSONObject(receipt.getString("manifest"))
    val payloads = ContractFixture.unpack(root, fixture)
    var auth: JSONObject? = JSONObject(fixture.getJSONObject("session").toString()).put("signingKey", key).put("localSessionId", UUID.randomUUID().toString())
    var time: Instant = Instant.parse(fixture.getJSONObject("activationCommand").getString("activatedAt"))
    val calls = mutableListOf<TemporaryConnection.Call>()
    val downloads = mutableListOf<String>()
    var beforeCall: (TemporaryConnection.Call) -> Unit = {}
    var beforeDownload: (String) -> Unit = {}
    var ack: JSONObject = fixture.getJSONObject("activationReceipt")
    val repository get() = TemporaryRepository(db, this, File(root, "downloads"))
    override fun current() = auth
    override fun now() = time
    override fun call(request: TemporaryConnection.Call): JSONObject {
        calls.add(request); beforeCall(request)
        return when {
            request.path == "pda/devices/me" -> JSONObject()
            request.path.endsWith("baseline") -> JSONObject().put("warehouseId", manifest.getJSONObject("identity").getString("warehouseId"))
                .put("inventorySnapshotId", manifest.getString("inventorySnapshotId")).put("mappingRevision", manifest.getString("mappingRevision"))
                .put("policy", JSONObject().put("version", manifest.getString("policyVersion")))
            request.path.endsWith("download-ack") -> ack
            request.path == "pda/temporary-preparations" -> receipt
            else -> error("Unexpected request: ${request.path}")
        }
    }
    override fun download(request: TemporaryConnection.Download) {
        val name = request.path.substringAfterLast('/').substringBefore('?')
        downloads.add(name); beforeDownload(name)
        request.target.writeBytes(Base64.getDecoder().decode(fixture.getJSONObject("gzip").getString(name)))
    }
    fun request(received: Boolean = true): TemporaryRequest {
        val a = checkNotNull(auth)
        val body = JSONObject().put("inventorySnapshotId", manifest.getString("inventorySnapshotId"))
            .put("mappingRevision", manifest.getString("mappingRevision")).put("policyVersion", manifest.getString("policyVersion"))
        return TemporaryRequest(UUID.randomUUID().toString(), a.getString("userId"), a.getString("deviceId"), a.getString("warehouseId"),
            a.getString("serverRecoveryEpoch"), a.getString("userAuthVersion"), a.getString("deviceVersion"), body.toString(), key.toString(),
            if (received) receipt.toString() else null)
    }
    fun saveRequest() = request().also { db.temporary().insertRequest(it) }
    fun activate(row: TemporaryRequest = saveRequest()) = TemporaryActivation(db, this)
        .activate(TemporaryActivation.Input(row, JSONObject(checkNotNull(auth).toString()), payloads))
    fun reopen() { check(ownsDatabase); db.close(); db = PdaDatabase.open(context) }
    override fun close() { if (ownsDatabase) { db.close(); context.deleteDatabase("outbound-pda.db") } }
}
