package com.acrylic.outbound.pda

import androidx.test.platform.app.InstrumentationRegistry
import com.acrylic.outbound.pda.security.SessionVault
import com.acrylic.outbound.pda.security.TrustedTime
import com.acrylic.outbound.pda.temporary.*
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import java.util.UUID

/** Signed synthetic network/session boundary; all originals are saved by product writers. */
internal class AdjustmentStatusDeviceFixture {
    val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as PdaApplication
    val root = File(app.filesDir, "adjustment-status-consumer")
    private val args = InstrumentationRegistry.getArguments()

    fun guard(): File {
        check(BuildConfig.DEBUG && android.os.Build.HARDWARE == "ranchu")
        check(args.getString("adjustmentStatusConsumer") == "synthetic-only")
        check(!BuildConfig.TEMPORARY_HANDOVER_DEVELOPMENT && !BuildConfig.TEMPORARY_CONTINUATION_DEVELOPMENT)
        check(!BuildConfig.RECOVERED_HANDOVER_DEVELOPMENT)
        val run = checkNotNull(args.getString("evidenceRun"))
        check(run.matches(Regex("[a-z0-9-]{1,40}")))
        val input = File(root, "private/contract.json")
        val hash = MessageDigest.getInstance("SHA-256").digest(input.readBytes()).joinToString("") { "%02x".format(it) }
        check(hash == args.getString("contractSha256"))
        androidx.work.WorkManager.getInstance(app).cancelAllWork().result.get()
        app.work.pause(); app.temporaryWork.pause()
        return File(root, run).also { check(it.mkdirs()) { "Preserve earlier evidence" } }
    }

    fun createOriginals(evidence: File) {
        check(app.session.saved() == null && app.session.settings.device.isBlank()) { "Fresh isolated identity required" }
        app.database.openHelper.readableDatabase.query("SELECT COUNT(*) FROM temporary_draft").use {
            check(it.moveToFirst() && it.getLong(0) == 0L)
        }
        val contract = JSONObject(File(root, "private/contract.json").readText())
        val connection = R06A11FixtureConnection(contract, R06A11StopSigner())
        val session = JSONObject(connection.session.toString()).put("timeAnchor",
            TrustedTime.anchor(connection.now().toString(), TrustedTime.sample(app)))
        SessionVault(app).save(session)
        TemporaryRepository(app.database, connection, File(evidence, "downloads")).prepare {}
        val store = TemporaryWorkStore(app.database, connection) { TrustedTime.sample(app) }
        val id = UUID.randomUUID().toString()
        File(root, "order.txt").writeText(id)
        val repository = TemporaryDraftRepository(store)
        val created = repository.create(id)
        val codes = store.source(id).bales.keys.sorted()
        check(codes.size >= 2)
        val lines = store.source(id).products.keys.map { it to "30" }
        val planned = repository.save(id, TemporaryPlanInput(created.revision, "合成状态筛选", "", "隔离只读消费者", lines))
        repository.start(id, planned.revision)
        saveRows(store, id, codes)
        File(evidence, "database-seeded.json").writeText(snapshot())
        File(evidence, "result.json").writeText(JSONObject().put("syntheticOnly", true).put("rows", 22)
            .put("voidRows", 20).put("activeRows", 2).put("retainedRows", 0).put("localDeletionAllowed", false).toString(2))
    }

    private fun saveRows(store: TemporaryWorkStore, id: String, codes: List<String>) {
        val processor = TemporaryProcessor(store) {}
        repeat(22) { index ->
            val code = if (index < 20) codes.first() else codes[index - 20]
            val capture = processor.reception.receive(TemporaryInput(id, code, "MANUAL", store.sample()))
            processor.drain(id)
            check(app.database.temporaryScans().capture(capture.id)?.state == "WAITING_CONFIRM")
            processor.saving.confirm(capture.id); processor.drain(id)
            check(app.database.temporaryScans().capture(capture.id)?.state == "COMMITTED")
            if (index < 20) processor.corrections.voidSelected(id, capture.id, "合成输入未装车，保存筛选原序号")
        }
        val rows = app.database.temporaryScans().allScans(id)
        check(rows.map { it.orderScanSeq } == (1L..22L).toList())
        check(rows.take(20).all { it.status == "VOID" } && rows.drop(20).all { it.status == "ACTIVE" })
    }

    fun snapshot() = DraftDiagnosticDatabaseSnapshot(app).read().toString(2)
}
