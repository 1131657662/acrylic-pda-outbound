package com.acrylic.outbound.pda

import com.acrylic.outbound.pda.security.JwtVerifier
import com.acrylic.outbound.pda.work.*
import org.json.JSONObject
import org.junit.Assert.*
import java.io.File

/** Evidence for one new synthetic active job; existing verification packages are never opened. */
internal class RenewalFacts(val app: PdaApplication) {
    val directory = File(app.filesDir, "renewal-recovery").apply { mkdirs() }
    val store = WorkStore(app.database, SessionWorkAccess(app, app.session))
    val processor = WorkProcessor(store) { _, _ -> }
    val baseline get() = read("baseline")
    val id get() = baseline.getString("assignmentId")
    val order get() = store.prepared(id)
    val codes get() = app.database.openHelper.readableDatabase.query(
        "SELECT barcode FROM prepared_bale WHERE assignmentId=? ORDER BY barcode", arrayOf(id)).use { rows ->
        buildList { while (rows.moveToNext()) add(rows.getString(0)) }
    }

    init {
        check(app.packageName == "com.acrylic.outbound.pda.debug.r05renewal")
        check(android.os.Build.MODEL.startsWith("sdk_gphone"))
    }

    fun receive(code: String, source: String = "BROADCAST"): WorkCapture = processor.reception.receive(
        WorkInput(id, code, source, store.access.sample())).also { processor.drain(id) }

    fun save(name: String, data: JSONObject) {
        File(directory, "$name.json").writeText(data.put("pid", android.os.Process.myPid()).toString())
    }

    fun read(name: String) = JSONObject(File(directory, "$name.json").readText())

    fun assertRestart(previous: String) {
        assertNotEquals(read(previous).getInt("pid"), android.os.Process.myPid())
        val session = app.session.requireSession()
        for (field in listOf("userId", "deviceId", "warehouseId", "localSessionId", "serverRecoveryEpoch"))
            assertEquals(field, baseline.getString(field), session.getString(field))
        assertEquals(baseline.getString("publicKey"), app.session.publicKey())
        assertEquals("SCANNING", app.database.work().order(id)!!.state)
        assertEquals(baseline.getString("planSha256"), JwtVerifier.sha(order.planJson.toByteArray()))
        assertEquals(baseline.getString("inventorySha256"), inventoryHash())
        baseline.optString("firstCapture").takeIf { it.isNotEmpty() }?.let {
            assertEquals(baseline.getString("firstAuthorization"), app.database.captures().get(it)!!.context.authorizationId)
            assertEquals("COMMITTED", app.database.captures().get(it)!!.state)
        }
    }

    fun business(): String {
        val tables = listOf("work_order", "work_line", "work_device", "work_capture", "work_scan", "work_guard", "work_event")
        return JwtVerifier.sha(tables.joinToString("\n") { table ->
            app.database.openHelper.readableDatabase.query("SELECT * FROM $table").use { rows ->
                buildList { while (rows.moveToNext()) add((0 until rows.columnCount).joinToString("|") {
                    if (rows.isNull(it)) "<null>" else rows.getString(it)
                }) }.sorted().joinToString("\n")
            }
        }.toByteArray())
    }

    fun inventoryHash(): String = app.database.openHelper.readableDatabase.query(
        "SELECT * FROM prepared_bale ORDER BY barcode").use { rows ->
        val values = buildList { while (rows.moveToNext()) add((0 until rows.columnCount).joinToString("|") {
            if (rows.isNull(it)) "<null>" else rows.getString(it)
        }) }
        JwtVerifier.sha(values.joinToString("\n").toByteArray())
    }
}
