package com.acrylic.outbound.pda

import android.database.Cursor
import androidx.test.platform.app.InstrumentationRegistry
import com.acrylic.outbound.pda.security.DeviceKeys
import com.acrylic.outbound.pda.security.SessionVault
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import java.io.File
import java.security.*
import java.security.spec.X509EncodedKeySpec
import java.util.Base64

/** Read-only fingerprints used on both authentic APKs, without fixed Room schemas. */
internal class IsolatedUpgradeFacts(private val app: PdaApplication) {
    val directory = File(app.filesDir, "isolated-upgrade")
    val version get() = app.packageManager.getPackageInfo(app.packageName, 0).longVersionCode

    fun requireEmulator() {
        check(android.os.Build.MODEL.startsWith("sdk_gphone"))
        check(app.packageName == "com.acrylic.outbound.pda.staging")
        check(InstrumentationRegistry.getArguments().getString("isolatedUpgrade") == "PdaUpgradeVerify")
    }

    fun snapshot(): JSONObject {
        val info = app.packageManager.getPackageInfo(app.packageName, 0)
        val result = JSONObject().put("versionCode", version).put("firstInstallTime", info.firstInstallTime)
            .put("roomVersion", app.database.openHelper.readableDatabase.version)
            .put("identity", identity()).put("tables", tables())
        app.database.openHelper.readableDatabase.query("PRAGMA foreign_key_check").use { assertFalse(it.moveToFirst()) }
        app.updates.verifyInstalledData()
        return result
    }

    fun identity(): JSONObject {
        val result = JSONObject().put("publicKeySha256", key())
        for (name in listOf("device-binding.json", "pairing.enc", "session.enc")) {
            val file = File(app.noBackupFilesDir, name)
            assertTrue("Missing original identity file: $name", file.isFile)
            result.put(name, sha(file.readBytes()))
        }
        assertNotNull(SessionVault(app).read())
        return result.put("sessionReadable", true)
    }

    fun tables(): JSONObject {
        val result = JSONObject(); val db = app.database.openHelper.readableDatabase
        db.query("SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%' AND name!='room_master_table' ORDER BY name").use { names ->
            while (names.moveToNext()) {
                val name = names.getString(0); require(name.matches(Regex("[A-Za-z0-9_]+")))
                db.query("SELECT * FROM `$name`").use { cursor ->
                    val rows = mutableListOf<String>()
                    while (cursor.moveToNext()) rows.add(row(cursor))
                    result.put(name, JSONObject().put("count", rows.size)
                        .put("sha256", sha(rows.sorted().joinToString("\n").toByteArray())))
                }
            }
        }
        return result
    }

    fun assertOriginalRows(original: JSONObject, actual: JSONObject) {
        for (name in original.keys()) assertEquals(name, original.getJSONObject(name).toString(), actual.getJSONObject(name).toString())
    }

    private fun row(cursor: Cursor) = JSONArray().also { values ->
        for (index in 0 until cursor.columnCount) values.put(when (cursor.getType(index)) {
            Cursor.FIELD_TYPE_NULL -> JSONObject.NULL
            Cursor.FIELD_TYPE_BLOB -> Base64.getEncoder().encodeToString(cursor.getBlob(index))
            else -> cursor.getString(index)
        })
    }.toString()

    private fun key(): String {
        val keys = DeviceKeys(); val encoded = Base64.getDecoder().decode(keys.publicKey(false))
        val challenge = "isolated-upgrade-${java.util.UUID.randomUUID()}".toByteArray()
        assertTrue(Signature.getInstance("SHA256withECDSA").run {
            initVerify(KeyFactory.getInstance("EC").generatePublic(X509EncodedKeySpec(encoded)))
            update(challenge); verify(Base64.getDecoder().decode(keys.sign(String(challenge))))
        })
        return sha(encoded)
    }

    private fun sha(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
}
