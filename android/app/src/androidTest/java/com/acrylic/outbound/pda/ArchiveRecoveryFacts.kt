package com.acrylic.outbound.pda

import android.database.Cursor
import com.acrylic.outbound.pda.security.DeviceKeys
import com.acrylic.outbound.pda.security.JwtVerifier
import org.json.*
import org.junit.Assert.*
import java.io.File
import java.security.*
import java.security.spec.X509EncodedKeySpec
import java.util.Base64

/** Read-only comparisons for the existing isolated archive test identity. */
internal class ArchiveRecoveryFacts(private val app: PdaApplication) {
    val directory = File(app.filesDir, "archive-recovery").apply { mkdirs() }
    val baseline get() = File(directory, "baseline.json")
    val original get() = JSONObject(File(app.filesDir, "e2e-evidence.json").readText())
    val device get() = original.getString("deviceId")
    val submission get() = original.getString("submissionId")

    fun requireIsolated() {
        check(android.os.Build.MODEL.startsWith("sdk_gphone"))
        check(app.packageName == "com.acrylic.outbound.pda.debug.r05archive")
        check(app.session.settings.device == device)
        val uri = java.net.URI(app.session.settings.server)
        check(uri.scheme == "http" && uri.host in setOf("localhost", "127.0.0.1") && uri.path == "/api/v1/")
    }

    fun snapshot(): JSONObject = JSONObject().put("businessTables", businessTables()).put("key", key())
        .put("binding", JwtVerifier.sha(File(app.noBackupFilesDir, "device-binding.json").readBytes()))
        .put("submissionArchive", app.database.archives().get(submission, "1")!!.confirmationJson)
        .put("auditArchive", app.database.auditArchives().rows("1", device).single().proofJson)

    fun assertPreserved() {
        val expected = JSONObject(baseline.readText()); val actual = snapshot()
        for (name in expected.keys()) assertEquals(name, expected.get(name).toString(), actual.get(name).toString())
        assertEquals("SYNCED", app.database.submissions().get(submission)!!.state)
    }

    fun evidence(name: String, details: JSONObject) {
        details.put("pid", android.os.Process.myPid()).put("deviceId", device)
        File(directory, "$name.json").writeText(details.toString())
    }

    private fun businessTables(): JSONObject {
        val result = JSONObject(); val db = app.database.openHelper.readableDatabase
        val changing = setOf("room_master_table", "local_audit_archive", "local_audit_checkpoint", "local_recovery_control")
        db.query("SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%' ORDER BY name").use { names ->
            while (names.moveToNext()) {
                val name = names.getString(0)
                if (name in changing) continue
                require(name.matches(Regex("[A-Za-z0-9_]+")))
                db.query("SELECT * FROM `$name`").use { cursor ->
                    val rows = mutableListOf<String>()
                    while (cursor.moveToNext()) rows.add(row(cursor))
                    result.put(name, JSONObject().put("count", rows.size)
                        .put("sha256", JwtVerifier.sha(rows.sorted().joinToString("\n").toByteArray())))
                }
            }
        }
        return result
    }

    private fun row(cursor: Cursor) = JSONArray().also { values ->
        for (index in 0 until cursor.columnCount) values.put(when (cursor.getType(index)) {
            Cursor.FIELD_TYPE_NULL -> JSONObject.NULL
            Cursor.FIELD_TYPE_BLOB -> Base64.getEncoder().encodeToString(cursor.getBlob(index))
            else -> cursor.getString(index)
        })
    }.toString()

    private fun key(): String {
        val keys = DeviceKeys(); val public = Base64.getDecoder().decode(keys.publicKey(false))
        val challenge = "archive-recovery-${java.util.UUID.randomUUID()}"
        assertTrue(Signature.getInstance("SHA256withECDSA").run {
            initVerify(KeyFactory.getInstance("EC").generatePublic(X509EncodedKeySpec(public)))
            update(challenge.toByteArray()); verify(Base64.getDecoder().decode(keys.sign(challenge)))
        })
        return JwtVerifier.sha(public)
    }
}
