package com.acrylic.outbound.pda

import androidx.test.platform.app.InstrumentationRegistry
import com.acrylic.outbound.pda.preparation.LocalActivation
import com.acrylic.outbound.pda.preparation.PackageVerifier
import com.acrylic.outbound.pda.preparation.VerifiedPackage
import com.acrylic.outbound.pda.security.SessionVault
import com.acrylic.outbound.pda.security.TrustedTime
import com.acrylic.outbound.pda.work.*
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import java.time.Instant
import java.util.Base64
import java.util.UUID

/** Fresh synthetic signed formal preparation; product transactions own every saved/void row. */
internal class FormalStatusDeviceFixture {
    val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as PdaApplication
    val root = File(app.filesDir, "formal-status-consumer")
    private val args = InstrumentationRegistry.getArguments()

    fun guard(): File {
        check(BuildConfig.DEBUG && android.os.Build.HARDWARE == "ranchu")
        check(args.getString("formalStatusConsumer") == "synthetic-only")
        check(!BuildConfig.TEMPORARY_HANDOVER_DEVELOPMENT && !BuildConfig.TEMPORARY_CONTINUATION_DEVELOPMENT)
        check(!BuildConfig.RECOVERED_HANDOVER_DEVELOPMENT)
        val run = checkNotNull(args.getString("evidenceRun"))
        check(run.matches(Regex("[a-z0-9-]{1,40}")))
        val input = File(root, "private/contract.json")
        val sha = MessageDigest.getInstance("SHA-256").digest(input.readBytes()).joinToString("") { "%02x".format(it) }
        check(sha == args.getString("contractSha256"))
        androidx.work.WorkManager.getInstance(app).cancelAllWork().result.get()
        app.work.pause(); app.temporaryWork.pause()
        return File(root, run).also { check(it.mkdirs()) { "Preserve earlier evidence" } }
    }

    fun createOriginals(evidence: File) {
        check(app.session.saved() == null && app.session.settings.device.isBlank()) { "Fresh identity required" }
        app.database.openHelper.readableDatabase.query("SELECT COUNT(*) FROM prepared_order").use {
            check(it.moveToFirst() && it.getLong(0) == 0L)
        }
        val fixture = JSONObject(File(root, "private/contract.json").readText())
        val assignment = fixture.getJSONObject("assignment")
        check(assignment.getString("startOrderScanSeq") == "1" && assignment.getString("workRound") == "1")
        val now = Instant.parse(assignment.getString("issuedAt")).plusSeconds(1)
        val session = JSONObject(fixture.getJSONObject("session").toString())
            .put("signingKey", fixture.getJSONObject("signingKey")).put("localSessionId", UUID.randomUUID().toString())
            .put("timeAnchor", TrustedTime.anchor(now.toString(), TrustedTime.sample(app)))
        SessionVault(app).save(session)
        val payloads = unpack(fixture, evidence)
        val prepared = LocalActivation(app.database).activate(VerifiedPackage(assignment, fixture.getJSONObject("receipt"),
            fixture.getJSONObject("signingKey"), session, payloads, now))
        val store = WorkStore(app.database, SessionWorkAccess(app, app.session))
        store.open(prepared.assignmentId)
        val codes = org.json.JSONArray(String(payloads.getValue("inventory"), Charsets.UTF_8))
        saveRows(store, prepared.assignmentId, (0 until codes.length()).map { codes.getJSONObject(it).getString("barcode") })
        File(root, "assignment.txt").writeText(prepared.assignmentId)
        File(evidence, "database-seeded.json").writeText(snapshot())
        File(evidence, "result.json").writeText(JSONObject().put("syntheticOnly", true).put("rows", 22)
            .put("voidRows", 20).put("activeRows", 2).put("retainedRows", 0).put("localDeletionAllowed", false).toString(2))
    }

    private fun unpack(fixture: JSONObject, evidence: File): Map<String, ByteArray> {
        val manifest = JSONObject(fixture.getJSONObject("receipt").getString("manifest"))
        val parts = manifest.getJSONArray("parts")
        return (0 until parts.length()).associate { index ->
            val part = parts.getJSONObject(index); val name = part.getString("name")
            check(name in setOf("plan", "inventory", "availability"))
            val file = File(evidence, "$name.gz")
            check(!file.exists()); file.writeBytes(Base64.getDecoder().decode(fixture.getJSONObject("gzip").getString(name)))
            name to PackageVerifier.unpack(file, part)
        }
    }

    private fun saveRows(store: WorkStore, id: String, codes: List<String>) {
        check(codes.size >= 2)
        val processor = WorkProcessor(store) { _, _ -> }
        repeat(22) { index ->
            val code = if (index < 20) codes.first() else codes[index - 20]
            val capture = processor.reception.receive(WorkInput(id, code, "MANUAL", store.access.sample()))
            processor.drain(id)
            check(app.database.captures().get(capture.id)?.state == "WAITING_CONFIRM")
            processor.saving.confirm(capture.id); processor.drain(id)
            check(app.database.captures().get(capture.id)?.state == "COMMITTED")
            if (index < 20) processor.corrections.voidSelected(id, capture.id, "合成正式筛选：原包未装车，保留原序号")
        }
        val rows = app.database.scans().page(id, 0, 30)
        check(rows.map { it.orderScanSeq } == (1L..22L).toList())
        check(rows.take(20).all { it.status == "VOID" } && rows.drop(20).all { it.status == "ACTIVE" })
        check(app.database.continuations().current(id) == null && app.database.dispositions().all(id).isEmpty())
    }

    fun snapshot() = DraftDiagnosticDatabaseSnapshot(app).read().toString(2)
}
