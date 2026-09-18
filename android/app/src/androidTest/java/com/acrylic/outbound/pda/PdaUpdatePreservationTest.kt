package com.acrylic.outbound.pda

import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.acrylic.outbound.pda.security.DeviceKeys
import com.acrylic.outbound.pda.security.SessionVault
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.security.KeyFactory
import java.security.MessageDigest
import java.security.Signature
import java.security.spec.X509EncodedKeySpec
import java.util.Base64

/** Two explicit phases around the system installer; never clears or seeds business data. */
@RunWith(AndroidJUnit4::class)
class PdaUpdatePreservationTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val baseline get() = File(context.filesDir, "updates/upgrade-baseline.json")

    @Test fun captureBeforeSystemInstall() {
        check(!baseline.exists()) { "已有升级基线，须保留并先核对" }
        baseline.parentFile!!.mkdirs()
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        baseline.writeText(JSONObject().put("versionCode", info.longVersionCode)
            .put("firstInstallTime", info.firstInstallTime).put("facts", snapshot()).toString())
    }

    @Test fun verifyAfterSystemInstall() {
        val before = JSONObject(baseline.readText())
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        assertTrue(info.longVersionCode > before.getLong("versionCode"))
        assertEquals(before.getLong("firstInstallTime"), info.firstInstallTime)
        assertEquals(before.getString("facts"), snapshot())
        File(context.filesDir, "updates/upgrade-verified.json").writeText(JSONObject()
            .put("fromVersionCode", before.getLong("versionCode")).put("toVersionCode", info.longVersionCode)
            .put("factsUnchanged", true).put("privateKeySignatureVerified", true).toString())
    }

    private fun snapshot(): String {
        val facts = JSONObject().put("key", verifyOriginalKey())
        val privateFiles = JSONObject()
        for (name in listOf("device-binding.json", "pairing.enc", "session.enc")) {
            val file = File(context.noBackupFilesDir, name)
            privateFiles.put(name, if (file.exists()) sha(file.readBytes()) else JSONObject.NULL)
        }
        val session = SessionVault(context).read()
        facts.put("sessionReadable", session != null).put("privateFiles", privateFiles)
        SQLiteDatabase.openDatabase(context.getDatabasePath("outbound-pda.db").path, null, SQLiteDatabase.OPEN_READONLY).use { db ->
            assertEquals(9, db.version)
            val tables = JSONObject()
            db.rawQuery("SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%' ORDER BY name", null).use { names ->
                while (names.moveToNext()) { val name = names.getString(0); tables.put(name, table(db, name)) }
            }
            facts.put("tables", tables)
        }
        return facts.toString()
    }

    private fun table(db: SQLiteDatabase, name: String): JSONObject {
        require(name.matches(Regex("[A-Za-z0-9_]+")))
        return db.rawQuery("SELECT * FROM `$name`", null).use { cursor ->
            val rows = mutableListOf<String>()
            while (cursor.moveToNext()) {
                val values = JSONArray()
                for (i in 0 until cursor.columnCount) values.put(when (cursor.getType(i)) {
                    Cursor.FIELD_TYPE_NULL -> JSONObject.NULL
                    Cursor.FIELD_TYPE_BLOB -> Base64.getEncoder().encodeToString(cursor.getBlob(i))
                    else -> cursor.getString(i)
                })
                rows.add(values.toString())
            }
            JSONObject().put("count", rows.size).put("sha256", sha(rows.sorted().joinToString("\n").toByteArray()))
        }
    }

    private fun verifyOriginalKey(): String {
        val keys = DeviceKeys(); val public = Base64.getDecoder().decode(keys.publicKey(false))
        val challenge = "application-update-${java.util.UUID.randomUUID()}"
        val signature = Base64.getDecoder().decode(keys.sign(challenge))
        assertTrue(Signature.getInstance("SHA256withECDSA").run {
            initVerify(KeyFactory.getInstance("EC").generatePublic(X509EncodedKeySpec(public)))
            update(challenge.toByteArray()); verify(signature)
        })
        return sha(public)
    }

    private fun sha(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
}
