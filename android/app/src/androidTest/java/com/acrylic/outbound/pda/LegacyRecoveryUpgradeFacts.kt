package com.acrylic.outbound.pda

import android.database.Cursor
import androidx.test.platform.app.InstrumentationRegistry
import com.acrylic.outbound.pda.data.PDA_DATABASE_VERSION
import com.acrylic.outbound.pda.retention.AuditArchiveVerifier
import com.acrylic.outbound.pda.retention.SubmissionArchiveVerifier
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import java.io.File

/** Consumes retained emulator bytes; does not assert possession of the original Keystore. */
internal class LegacyRecoveryUpgradeFacts(private val app: PdaApplication) {
    val directory = File(app.filesDir, "legacy-recovery-upgrade")
    val input = JSONObject(File(directory, "input.json").readText())
    val database get() = app.database

    fun requireIsolation() {
        check(BuildConfig.DEBUG && android.os.Build.HARDWARE == "ranchu")
        check(InstrumentationRegistry.getArguments().getString("legacyRecoveryUpgrade") == input.getString("runId"))
        check(input.getString("runId") == "20261016-e07legacy01")
        check(input.getString("sourceSha256").matches(Regex("[a-f0-9]{64}")))
    }

    fun originalFacts() {
        val db = database.openHelper.readableDatabase
        assertEquals(PDA_DATABASE_VERSION, db.version)
        val tables = input.getJSONArray("tables")
        for (i in 0 until tables.length()) {
            val table = tables.getJSONObject(i)
            val columns = strings(table.getJSONArray("columns"))
            val name = table.getString("name")
            check((columns + name).all { it.matches(Regex("[A-Za-z0-9_]+")) })
            val selected = columns.joinToString(",") { "`$it`" }
            val actual = db.query("SELECT $selected FROM `$name`").use { cursor ->
                buildList { while (cursor.moveToNext()) add(row(cursor)) }.sorted()
            }
            val expected = table.getJSONArray("rows")
            assertEquals(name, (0 until expected.length()).map { expected.getJSONArray(it).toString() }.sorted(), actual)
        }
        db.query("PRAGMA integrity_check").use { assertTrue(it.moveToFirst()); assertEquals("ok", it.getString(0)) }
        db.query("PRAGMA foreign_key_check").use { assertFalse(it.moveToFirst()) }
    }

    fun signedProofs() {
        val local = checkNotNull(database.submissions().get(input.getString("submissionId")))
        assertEquals("SYNCED", local.state)
        val prepared = database.preparations().orders().single { it.assignmentId == local.assignmentId }
        val archive = checkNotNull(database.archives().get(local.id, "1"))
        val capture = database.captures().page(local.assignmentId, 0, 1).single()
        val context = JSONObject().put("userId", prepared.operatorId).put("deviceId", prepared.deviceId)
            .put("warehouseId", capture.context.warehouseId).put("serverRecoveryEpoch", archive.recoveryEpoch)
        val key = JSONObject(archive.signingKeyJson)
        val request = SubmissionArchiveVerifier.request(local, key)
        val proof = JSONObject(archive.confirmationJson)
        SubmissionArchiveVerifier.verify(local, context, request, proof, key)
        assertFalse(proof.getBoolean("localDeletionAllowed"))
        assertThrows(IllegalStateException::class.java) {
            SubmissionArchiveVerifier.verify(local, context, request,
                JSONObject(proof.toString()).put("localDeletionAllowed", true), key)
        }
        val epochs = database.auditArchives().completed(prepared.deviceId, 2).groupBy { it.recoveryEpoch }
        assertEquals(setOf("1", "2"), epochs.keys)
        val through = database.work().deviceEvents(prepared.deviceId).maxOf { it.localSeq }
        for ((epoch, rows) in epochs) {
            assertEquals(through, AuditArchiveVerifier.prefix(rows, capture.context.warehouseId).last().throughSeq)
            assertEquals(through, checkNotNull(database.audit().checkpoint(epoch, prepared.deviceId)).acknowledgedThrough)
        }
    }

    fun save(label: String) {
        val target = File(directory, "$label.json")
        check(target.createNewFile()) { "Preserve previous phase output" }
        target.writeText(JSONObject().put("pid", android.os.Process.myPid())
            .put("oldTablesPreserved", input.getJSONArray("tables").length()).put("proofsVerified", true)
            .put("mode", database.sync().recovery()!!.mode).put("originalKeystoreVerified", false)
            .put("sourceSha256", input.getString("sourceSha256")).toString())
    }

    private fun strings(values: JSONArray) = (0 until values.length()).map { values.getString(it) }

    private fun row(cursor: Cursor): String = JSONArray().also { values ->
        for (i in 0 until cursor.columnCount) {
            check(cursor.getType(i) != Cursor.FIELD_TYPE_BLOB) { "Unexpected legacy blob" }
            values.put(if (cursor.isNull(i)) JSONObject.NULL else cursor.getString(i))
        }
    }.toString()
}
