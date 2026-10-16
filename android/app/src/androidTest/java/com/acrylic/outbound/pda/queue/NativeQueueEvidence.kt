package com.acrylic.outbound.pda.queue

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.acrylic.outbound.pda.BuildConfig
import com.acrylic.outbound.pda.data.PdaDatabase
import com.acrylic.outbound.pda.device.ScannerReceiver
import org.json.JSONObject
import org.junit.Assert.*
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Evidence and one-shot named database only; no shared source-specific business behavior. */
internal class NativeQueueEvidence(source: String) : AutoCloseable {
    val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private val args = InstrumentationRegistry.getArguments()
    val phase = checkNotNull(args.getString("queuePhase")).also { check(it in setOf("pause", "restore")) }
    private val run = checkNotNull(args.getString("evidenceRun")).also { check(it.matches(Regex("[a-z0-9-]{1,40}"))) }
    private val input = File(context.filesDir, "foreground-queue-input/$run")
    val data = JSONObject(File(input, "$source.json").readText())
    val id = data.getString("id")
    val captureId = data.getString("captureId")
    val barcode = data.getString("barcode")
    val directory = File(context.filesDir, "foreground-queue-evidence/$run/$source-$phase")
    private val target = context.getDatabasePath("synthetic-foreground-$source-$run.db")
    val db: PdaDatabase

    init {
        check(BuildConfig.DEBUG && android.os.Build.HARDWARE == "ranchu")
        check(args.getString("nativeForegroundQueue") == "true" && data.getBoolean("syntheticOnly"))
        check(directory.mkdirs()) { "Preserve previous phase evidence" }
        androidx.work.WorkManager.getInstance(context).cancelAllWork().result.get()
        val seed = File(input, "$source.db")
        assertEquals(data.getString("sha256"), sha(seed))
        if (phase == "pause") { check(!target.exists()); seed.copyTo(target) } else check(target.isFile)
        db = Room.databaseBuilder(context, PdaDatabase::class.java, target.name).build()
        result("start", JSONObject().put("seedSha256", sha(seed)).put("databaseSha256", sha(target)))
    }

    fun await(condition: () -> Boolean) {
        val until = System.nanoTime() + TimeUnit.SECONDS.toNanos(30)
        while (!condition()) { check(System.nanoTime() < until) { "Native queue condition timed out" }; Thread.sleep(10) }
    }

    fun broadcast() {
        val completed = CountDownLatch(1)
        val last = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) { completed.countDown() }
        }
        context.sendOrderedBroadcast(Intent(ScannerReceiver.ACTION).setPackage(context.packageName)
            .putExtra(ScannerReceiver.BARCODE_KEY, barcode), null, last, null, 0, null, null)
        check(completed.await(10, TimeUnit.SECONDS)) { "Ordered native broadcast did not finish" }
    }

    fun result(name: String, value: JSONObject) {
        val file = File(directory, "$name.json"); check(!file.exists())
        value.put("pid", android.os.Process.myPid()).put("captureId", captureId)
            .put("id", id).put("syntheticOnly", true).put("phase", phase)
        file.writeText(value.toString(2))
    }

    private fun sha(file: File) = MessageDigest.getInstance("SHA-256").digest(file.readBytes())
        .joinToString("") { "%02x".format(it) }

    override fun close() {
        db.close()
        target.copyTo(File(directory, "after.db"))
    }
}
