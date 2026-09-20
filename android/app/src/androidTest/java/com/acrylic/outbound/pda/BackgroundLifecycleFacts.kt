package com.acrylic.outbound.pda

import android.util.AtomicFile
import com.acrylic.outbound.pda.retention.AuditArchiveVerifier
import com.acrylic.outbound.pda.security.DeviceKeys
import com.acrylic.outbound.pda.security.JwtVerifier
import org.json.JSONObject
import org.junit.Assert.*
import java.io.File
import java.net.URI

/** Retained synthetic API fixture only. Never seeds, resets or mutates business records. */
internal class BackgroundLifecycleFacts(val app: PdaApplication) {
    val directory = File(app.filesDir, "background-lifecycle").apply { mkdirs() }
    private val original = JSONObject(File(app.filesDir, "e2e-evidence.json").readText())
    private val baseline = File(directory, "facts.json")
    val device: String get() = original.getString("deviceId")

    fun requireFixture() {
        check(app.packageName == "com.acrylic.outbound.pda.debug.r05backgroundapi")
        check(android.os.Build.MODEL.startsWith("sdk_gphone"))
        assertEquals(device, app.session.settings.device)
        assertEquals(device, app.session.requireSession().getString("deviceId"))
        assertEquals("SYNCED", app.database.submissions().get(original.getString("submissionId"))!!.state)
    }

    fun restoreDirectEndpoint() {
        requireFixture()
        val file = AtomicFile(File(app.noBackupFilesDir, "device-binding.json"))
        val raw = file.readFully()
        val binding = JSONObject(String(raw, Charsets.UTF_8))
        val old = URI(binding.getString("server"))
        check(old.scheme == "http" && old.host in setOf("localhost", "127.0.0.1", "10.0.2.2"))
        check(old.path == "/api/v1/" && old.userInfo == null && old.query == null)
        check(binding.getString("device") == device && binding.getString("key") == DeviceKeys().publicKey(false))
        val backup = File(directory, "original-binding.json")
        if (!backup.exists()) backup.writeBytes(raw)
        binding.put("server", "http://10.0.2.2:18091/api/v1/")
        val stream = file.startWrite()
        try { stream.write(binding.toString().toByteArray()); file.finishWrite(stream) }
        catch (error: Exception) { file.failWrite(stream); throw error }
        assertEquals("http://10.0.2.2:18091/api/v1/", app.session.settings.server)
    }

    fun preserveBaseline() {
        requireFixture()
        if (!baseline.exists()) baseline.writeText(snapshot().toString())
        assertPreserved()
    }

    fun assertPreserved() {
        requireFixture()
        val saved = JSONObject(baseline.readText()); val current = snapshot()
        saved.keys().forEach { assertEquals(it, saved.get(it), current.get(it)) }
        val auth = app.session.requireSession()
        val prefix = AuditArchiveVerifier.prefix(app.database.auditArchives().rows("1", device), auth.getString("warehouseId"))
        assertEquals(15L, prefix.last().throughSeq)
        assertEquals(15L, app.database.audit().checkpoint("1", device)!!.acknowledgedThrough)
        val bytes = "background-lifecycle-key-check".toByteArray()
        val publicKey = java.security.KeyFactory.getInstance("EC").generatePublic(java.security.spec.X509EncodedKeySpec(
            java.util.Base64.getDecoder().decode(DeviceKeys().publicKey(false))))
        val signature = java.security.Signature.getInstance("SHA256withECDSA")
        signature.initVerify(publicKey); signature.update(bytes)
        assertTrue(signature.verify(java.util.Base64.getDecoder().decode(DeviceKeys().sign(String(bytes)))))
    }

    private fun snapshot(): JSONObject {
        val result = JSONObject().put("device", device).put("key", DeviceKeys().publicKey(false))
            .put("login", app.session.requireSession().getString("localSessionId"))
        val db = app.database.openHelper.readableDatabase
        val tables = mutableListOf<String>()
        db.query("SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%'").use { rows ->
            while (rows.moveToNext()) tables += rows.getString(0)
        }
        for (table in tables.filterNot { it in setOf("local_cloud_order", "local_cloud_barcode", "local_sync_state", "room_master_table", "android_metadata") }) {
            check(table.matches(Regex("[a-z_]+")))
            val rows = mutableListOf<String>()
            db.query("SELECT * FROM `$table`").use { cursor ->
                while (cursor.moveToNext()) rows += (0 until cursor.columnCount).joinToString("\u0000") {
                    if (cursor.isNull(it)) "<null>" else cursor.getString(it)
                }
            }
            result.put(table, JwtVerifier.sha(rows.sorted().joinToString("\n").toByteArray()))
        }
        return result
    }
}
