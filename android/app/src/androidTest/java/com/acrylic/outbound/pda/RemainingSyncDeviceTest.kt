package com.acrylic.outbound.pda

import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.acrylic.outbound.pda.security.DeviceKeys
import org.json.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.security.*
import java.security.spec.X509EncodedKeySpec
import java.util.Base64

/** Explicit phases on an existing device. Never seeds, clears, scans, claims or submits orders. */
@RunWith(AndroidJUnit4::class)
class RemainingSyncDeviceTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val baseline get() = File(context.filesDir, "sync-checks/before-15.json")

    @Test fun captureBeforeUpgrade() {
        check(!baseline.exists()) { "已有基线，请保留原证据" }
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        val facts = privateFacts()
        SQLiteDatabase.openDatabase(context.getDatabasePath("outbound-pda.db").path, null, SQLiteDatabase.OPEN_READONLY).use { db ->
            assertEquals(9, db.version); facts.put("tables", tables(db))
        }
        baseline.parentFile!!.mkdirs()
        baseline.writeText(JSONObject().put("versionCode", info.longVersionCode).put("firstInstallTime", info.firstInstallTime).put("facts", facts).toString())
    }
    @Test fun verifyMigrationPreservesOriginalTablesIdentityAndKey() {
        val before = JSONObject(baseline.readText()); val info = context.packageManager.getPackageInfo(context.packageName, 0)
        assertEquals(15L, info.longVersionCode); assertEquals(before.getLong("firstInstallTime"), info.firstInstallTime)
        val app = context.applicationContext as PdaApplication
        assertEquals(14, app.database.openHelper.readableDatabase.version)
        val original = before.getJSONObject("facts"); val actual = privateFacts()
        for (key in actual.keys()) assertEquals(key, original.get(key), actual.get(key))
        SQLiteDatabase.openDatabase(context.getDatabasePath("outbound-pda.db").path, null, SQLiteDatabase.OPEN_READONLY).use { db ->
            val oldTables = original.getJSONObject("tables"); val current = tables(db)
            for (name in oldTables.keys()) assertEquals(name, oldTables.getJSONObject(name).toString(), current.getJSONObject(name).toString())
        }
        File(baseline.parentFile, "migration-verified.json").writeText(JSONObject().put("from", before.getLong("versionCode"))
            .put("to", info.longVersionCode).put("roomVersion", 11).put("originalTablesAndPrivateIdentityPreserved", true).toString())
    }
    @Test fun synchronizeExistingFactsWithoutChangingBusinessRecords() {
        val app = context.applicationContext as PdaApplication
        val original = businessTables()
        val recoveryBefore = app.database.sync().recovery()
        val messages = com.acrylic.outbound.pda.update.UpdateGate.business {
            listOf(app.sync.refreshOrders(200), completeBarcodeSync(app), app.auditSync.upload())
        }
        assertTrue(messages[0].contains("已同步")); assertTrue(messages[1].contains("已同步")); assertTrue(messages[2].contains("已补传"))
        assertEquals(original, businessTables())
        val auth = app.session.requireSession(); val epoch = auth.getString("serverRecoveryEpoch"); val warehouse = auth.getString("warehouseId")
        assertEquals(epoch, app.database.sync().recovery()!!.observedServerEpoch)
        if (recoveryBefore?.mode == "RECONCILING") assertEquals("RECONCILING", app.database.sync().recovery()!!.mode)
        val state = checkNotNull(app.database.sync().state(epoch, warehouse, "BARCODE"))
        assertNotNull(state.cursor); assertNotNull(state.activeBaselineId)
        val device = auth.getString("deviceId"); val last = app.database.work().device(device)?.nextEventSeq?.minus(1) ?: 0
        assertEquals(last, app.database.audit().checkpoint(epoch, device)?.acknowledgedThrough ?: 0)
        File(baseline.parentFile, "https-sync-verified.json").writeText(JSONObject().put("messages", JSONArray(messages))
            .put("barcodeSequence", state.sequence).put("acknowledgedAuditSequence", last).put("businessRecordsUnchanged", true).toString())
    }
    private fun completeBarcodeSync(app: PdaApplication): String {
        repeat(20) {
            val message = app.barcodeSync.refreshBarcodes(200)
            if (message.contains("已同步")) return message
            check(message.contains("仍在同步")) { message }
        }
        error("测试次数内条码同步未完成，保留断点")
    }
    private fun businessTables(): String = SQLiteDatabase.openDatabase(context.getDatabasePath("outbound-pda.db").path,
        null, SQLiteDatabase.OPEN_READONLY).use { db ->
        val all = tables(db)
        for (name in all.keys().asSequence().toList()) if (name.startsWith("local_cloud_") ||
            name in setOf("local_sync_state", "local_audit_checkpoint", "local_recovery_control")) all.remove(name)
        all.toString()
    }
    private fun privateFacts(): JSONObject {
        val facts = JSONObject(); val keys = DeviceKeys(); val public = Base64.getDecoder().decode(keys.publicKey(false))
        val challenge = "r05-sync-${java.util.UUID.randomUUID()}"
        assertTrue(Signature.getInstance("SHA256withECDSA").run {
            initVerify(KeyFactory.getInstance("EC").generatePublic(X509EncodedKeySpec(public)))
            update(challenge.toByteArray()); verify(Base64.getDecoder().decode(keys.sign(challenge)))
        })
        facts.put("key", sha(public))
        for (name in listOf("device-binding.json", "pairing.enc", "session.enc")) {
            val file = File(context.noBackupFilesDir, name); facts.put(name, if (file.exists()) sha(file.readBytes()) else JSONObject.NULL)
        }
        return facts
    }
    private fun tables(db: SQLiteDatabase): JSONObject {
        val result = JSONObject()
        db.rawQuery("SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%' AND name!='room_master_table' ORDER BY name", null).use { names ->
            while (names.moveToNext()) {
                val name = names.getString(0); require(name.matches(Regex("[A-Za-z0-9_]+")))
                db.rawQuery("SELECT * FROM `$name`", null).use { cursor ->
                    val rows = mutableListOf<String>()
                    while (cursor.moveToNext()) rows.add(row(cursor))
                    result.put(name, JSONObject().put("count", rows.size).put("sha256", sha(rows.sorted().joinToString("\n").toByteArray())))
                }
            }
        }
        return result
    }
    private fun row(cursor: Cursor): String = JSONArray().also { values ->
        for (i in 0 until cursor.columnCount) values.put(when (cursor.getType(i)) {
            Cursor.FIELD_TYPE_NULL -> JSONObject.NULL
            Cursor.FIELD_TYPE_BLOB -> Base64.getEncoder().encodeToString(cursor.getBlob(i))
            else -> cursor.getString(i)
        })
    }.toString()
    private fun sha(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
}
