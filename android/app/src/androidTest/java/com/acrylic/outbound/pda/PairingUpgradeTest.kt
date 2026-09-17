package com.acrylic.outbound.pda

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.acrylic.outbound.pda.security.JwtVerifier
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class PairingUpgradeTest {
    private val app get() = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as PdaApplication
    @Test fun seedOnVersionSix() {
        assertEquals(6L, app.packageManager.getPackageInfo(app.packageName, 0).longVersionCode)
        val fixtureFile = File(app.filesDir, "pairing-fixture.json"); val fixture = JSONObject(fixtureFile.readText())
        val key = app.session.publicKey(); val device = RecallAdmin(fixture).register(key)
        app.session.settings.bind(fixture.getString("baseUrl"), device, key)
        app.session.login(fixture.getString("username"), fixture.getString("password"))
        val order = app.repository.list("pda/orders", "cursor").single { it.getString("id") == fixture.getString("orderId") }
        val assignment = app.repository.claim(order, "保留旧身份及原单升级验证"); val prepared = app.repository.prepare(assignment) {}
        val id = prepared.assignmentId
        val barcode = app.database.openHelper.readableDatabase.query("SELECT barcode FROM prepared_bale WHERE assignmentId=? LIMIT 1", arrayOf(id))
            .use { assertTrue(it.moveToFirst()); it.getString(0) }
        app.work.resume(id); await { app.work.state.value.ready }; app.work.receive(barcode, "BROADCAST")
        await { app.database.work().order(id)?.actualCount == 1L }; app.work.pause()
        val frozen = app.freezer.freeze(id, "升级前保留未上传批次")
        val evidence = JSONObject().put("device", device).put("server", app.session.settings.server).put("keySha256", JwtVerifier.sha(key.toByteArray()))
            .put("assignmentId", id).put("submissionId", frozen.id).put("databaseSha256", snapshot())
        File(app.filesDir, "pairing-upgrade-before.json").writeText(evidence.toString())
    }
    @Test fun verifyOnVersionSeven() {
        assertEquals(7L, app.packageManager.getPackageInfo(app.packageName, 0).longVersionCode)
        val before = JSONObject(File(app.filesDir, "pairing-upgrade-before.json").readText())
        assertEquals(before.getString("device"), app.session.settings.device); assertEquals(before.getString("server"), app.session.settings.server)
        assertEquals(before.getString("keySha256"), JwtVerifier.sha(app.session.publicKey().toByteArray()))
        assertEquals(before.getString("databaseSha256"), snapshot())
        assertNotNull(app.database.submissions().get(before.getString("submissionId")))
        assertThrows(IllegalStateException::class.java) { app.pairing.start(app.session.settings.server) }
        assertEquals(before.getString("databaseSha256"), snapshot())
        val fixtureFile = File(app.filesDir, "pairing-fixture.json"); val fixture = JSONObject(fixtureFile.readText()); fixtureFile.delete()
        app.session.login(fixture.getString("username"), fixture.getString("password"))
        assertEquals(before.getString("device"), app.session.call("pda/devices/me").getString("id"))
        before.put("upgradeFrom", 6).put("upgradeTo", 7).put("allBusinessRowsPreserved", true).put("originalKeyLoginVerified", true)
        File(app.filesDir, "pairing-upgrade-evidence.json").writeText(before.toString())
    }
    private fun snapshot(): String {
        val db = app.database.openHelper.readableDatabase
        val tables = db.query("SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%' ORDER BY name").use { cursor ->
            buildList { while (cursor.moveToNext()) add(cursor.getString(0)) }
        }
        val rows = tables.flatMap { table ->
            require(table.matches(Regex("[A-Za-z0-9_]+")))
            db.query("SELECT * FROM $table").use { c -> buildList {
                while (c.moveToNext()) {
                    val values = JSONArray(); for (i in 0 until c.columnCount) values.put(if (c.isNull(i)) JSONObject.NULL else
                        if (c.getType(i) == android.database.Cursor.FIELD_TYPE_BLOB) java.util.Base64.getEncoder().encodeToString(c.getBlob(i)) else c.getString(i))
                    add("$table:$values")
                }
            } }
        }
        return JwtVerifier.sha(rows.sorted().joinToString("\n").toByteArray())
    }
    private fun await(condition: () -> Boolean) {
        val deadline = android.os.SystemClock.elapsedRealtime() + 20000
        while (!condition() && android.os.SystemClock.elapsedRealtime() < deadline) Thread.sleep(25)
        assertTrue("Upgrade seed did not become ready", condition())
    }
}
