package com.acrylic.outbound.pda.independent

import android.database.Cursor
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.acrylic.outbound.pda.BuildConfig
import com.acrylic.outbound.pda.data.PdaDatabase
import com.acrylic.outbound.pda.security.TimeSample
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/** Reuses retained synthetic signatures and time; never opens the original app business database. */
internal class NativeIndependentQueueFixture : AutoCloseable {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val args = InstrumentationRegistry.getArguments()
    private val run = checkNotNull(args.getString("evidenceRun")).also { check(it.matches(Regex("[a-z0-9-]{1,40}"))) }
    private val directory = File(context.filesDir, "independent-queue-evidence/$run")
    private val target = context.getDatabasePath("synthetic-independent-queue-$run.db")
    val db: PdaDatabase
    val task: IndependentTask
    val capture: IndependentCapture
    val feedback = ConcurrentLinkedQueue<String>()
    val hold = AtomicBoolean(false)
    val entered = CountDownLatch(1)
    val release = CountDownLatch(1)
    private val environment: IndependentScanEnvironment
    private val grant: IndependentCaptureGrant
    var engine: IndependentScanEngine

    init {
        check(BuildConfig.DEBUG && android.os.Build.HARDWARE == "ranchu")
        check(args.getString("independentQueue") == "true")
        check(!directory.exists() && !target.exists()); check(directory.mkdirs())
        androidx.work.WorkManager.getInstance(context).cancelAllWork().result.get()
        val source = File(context.filesDir, "independent-queue-input/$run.db")
        val hash = MessageDigest.getInstance("SHA-256").digest(source.readBytes()).joinToString("") { "%02x".format(it) }
        assertEquals(args.getString("inputSha256"), hash)
        check(target.canonicalPath != context.getDatabasePath("outbound-pda.db").canonicalPath)
        source.copyTo(target)
        db = Room.databaseBuilder(context, PdaDatabase::class.java, target.name).build()
        task = db.openHelper.readableDatabase.query("SELECT id FROM independent_task").use {
            check(it.moveToFirst()); checkNotNull(db.independentTasks().task(it.getString(0))).also { _ -> check(!it.moveToNext()) }
        }
        capture = db.independentScans().captures(task.id).first().capture
        val snapshot = IndependentCredentialSnapshot(IndependentCredentialProof.verify(task.signedCredential,
            JSONObject(task.trustedKeyJson)), task.trustedKeyJson, task.timeAnchorJson, task.localSessionId)
        grant = IndependentCaptureGrant(snapshot.proof) { snapshot }
        environment = IndependentScanEnvironment(::sample, ::actor, feedback = {
            assertFalse(db.inTransaction())
            if (it == "SAVED") assertEquals(1L, db.independentScans().total(task.id).count)
            feedback.add(it)
        })
        engine = newEngine()
        snapshot("before")
    }

    fun open() {
        engine.resume(task.id)
        await { engine.state.value.task != null && engine.state.value.queued == 0 }
        engine.authorize(task.id, charArrayOf())
        await { engine.state.value.queued == 0 && (engine.state.value.ready || engine.state.value.candidate != null) }
    }

    fun restart() { engine.close(); engine = newEngine(); open() }
    private fun newEngine() = IndependentScanEngine(db, environment) { grant }
    private fun sample(): TimeSample = JSONObject(task.timeAnchorJson).let {
        TimeSample(it.getLong("elapsed"), it.getLong("wall"), it.getInt("boot"))
    }

    private fun actor(): IndependentTaskActor {
        if (hold.compareAndSet(true, false)) { entered.countDown(); check(release.await(30, TimeUnit.SECONDS)) }
        return IndependentTaskActor(task.operatorId, task.deviceId, task.warehouseId)
    }

    fun await(condition: () -> Boolean) {
        val end = System.nanoTime() + TimeUnit.SECONDS.toNanos(30)
        while (!condition()) { check(System.nanoTime() < end) { "Native queue did not reach expected state: ${engine.state.value}" }; Thread.sleep(10) }
    }

    fun record(phase: String) {
        val captures = db.independentScans().captures(task.id)
        val json = JSONObject().put("syntheticOnly", true).put("phase", phase).put("taskId", task.id)
            .put("captureId", capture.id).put("captures", captures.size).put("saved", db.independentScans().total(task.id).count)
            .put("queued", engine.state.value.queued).put("ready", engine.state.value.ready)
            .put("blocked", engine.state.value.blocked).put("feedback", JSONArray(feedback.toList()))
        File(directory, "result.json").writeText(json.toString(2))
    }

    private fun snapshot(name: String) {
        val sql = db.openHelper.readableDatabase
        val tables = sql.query("SELECT name FROM sqlite_master WHERE type='table' ORDER BY name").use {
            buildList { while (it.moveToNext()) add(it.getString(0)) }
        }
        val json = JSONObject()
        tables.forEach { table ->
            sql.query("SELECT * FROM \"${table.replace("\"", "\"\"")}\"").use { cursor ->
                val rows = JSONArray(); while (cursor.moveToNext()) rows.put(row(cursor)); json.put(table, rows)
            }
        }
        File(directory, "$name.json").writeText(json.toString(2))
    }

    private fun row(cursor: Cursor) = JSONObject().apply {
        for (index in 0 until cursor.columnCount) put(cursor.getColumnName(index), when (cursor.getType(index)) {
            Cursor.FIELD_TYPE_NULL -> JSONObject.NULL
            Cursor.FIELD_TYPE_BLOB -> java.util.Base64.getEncoder().encodeToString(cursor.getBlob(index))
            else -> cursor.getString(index)
        })
    }

    override fun close() {
        release.countDown(); engine.close()
        try { snapshot("after") } finally { db.close(); target.copyTo(File(directory, "after.db")) }
    }
}
