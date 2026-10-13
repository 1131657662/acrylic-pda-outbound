package com.acrylic.outbound.pda.independent

import com.acrylic.outbound.pda.rescue.*
import org.json.JSONObject
import java.io.File
import java.security.KeyPairGenerator
import java.security.Signature
import java.security.spec.ECGenParameterSpec
import java.util.Base64

internal class IndependentRescueFixture : AutoCloseable {
    private val keys = KeyPairGenerator.getInstance("EC").apply { initialize(ECGenParameterSpec("secp256r1")) }.generateKeyPair()
    val scan = IndependentScanFixture(deviceKeyDigest = RescueBundle.sha(keys.public.encoded))
    val db get() = scan.task.sku.db
    val target = RescueTarget("INDEPENDENT", scan.command.id)
    val grant get() = RescueReadGrant(RescueReadIdentity(scan.task.actor.deviceId, scan.task.actor.operatorId, scan.task.actor.warehouseId)) {}
    val signer = RescueSigner(keys.public.encoded) { text -> Signature.getInstance("SHA256withECDSA").run {
        initSign(keys.private); update(text.toByteArray(Charsets.UTF_8)); Base64.getEncoder().encodeToString(sign())
    } }
    fun read() = RescueRoomSnapshot(db).read(target, grant)
    fun digest(): String {
        val sqlite = db.openHelper.readableDatabase
        val tables = sqlite.query("SELECT name FROM sqlite_master WHERE type='table' ORDER BY name").use { c ->
            buildList { while (c.moveToNext()) add(c.getString(0)) }
        }
        val text = tables.joinToString("\n") { table ->
            sqlite.query("SELECT * FROM `$table`").use { c ->
                val rows = buildList { while (c.moveToNext()) add((0 until c.columnCount).joinToString("|") {
                    if (c.getType(it) == android.database.Cursor.FIELD_TYPE_BLOB) Base64.getEncoder().encodeToString(c.getBlob(it)) else c.getString(it).orEmpty()
                }) }
                table + rows.sorted().joinToString("\n")
            }
        }
        return RescueBundle.sha(text.toByteArray(Charsets.UTF_8))
    }
    fun vector(snapshot: RescueSnapshot) {
        val directory = File(checkNotNull(System.getenv("INDEPENDENT_RESCUE_VECTOR_DIR"))).apply { mkdirs() }
        directory.resolve("independent.zip").writeBytes(RescueBundle.build(snapshot, signer))
        val trust = JSONObject().put(scan.task.actor.deviceId, JSONObject().put("publicKeyDerBase64", Base64.getEncoder().encodeToString(keys.public.encoded))
            .put("operatorIds", listOf(scan.task.actor.operatorId)).put("warehouseIds", listOf(scan.task.actor.warehouseId)))
        directory.resolve("trust.json").writeText(trust.toString())
    }
    override fun close() = scan.close()
}
