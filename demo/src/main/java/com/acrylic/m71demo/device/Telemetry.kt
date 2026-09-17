package com.acrylic.m71demo.device

import android.content.Context
import android.os.*
import dagger.hilt.android.qualifiers.ApplicationContext
import org.json.JSONObject
import javax.inject.*

@Singleton
class Telemetry @Inject constructor(@ApplicationContext context: Context) {
    private val boot = runCatching { android.provider.Settings.Global.getInt(context.contentResolver, "boot_count", -1) }.getOrDefault(-1)
    val directory = java.io.File(context.getExternalFilesDir(null) ?: context.filesDir, "evidence").apply { mkdirs() }
    private val thread = HandlerThread("demo-diagnostics").apply { start() }
    private val handler = Handler(thread.looper)
    private val file = java.io.File(directory, "events-${System.currentTimeMillis()}.ndjson")
    private val pending = java.util.concurrent.atomic.AtomicInteger()
    val dropped = java.util.concurrent.atomic.AtomicInteger()

    fun event(type: String, fields: Map<String, Any?> = emptyMap()) {
        if (pending.incrementAndGet() > 256) { pending.decrementAndGet(); dropped.incrementAndGet(); return }
        val json = JSONObject(fields).put("type", type).put("wallMs", System.currentTimeMillis())
            .put("elapsedNs", SystemClock.elapsedRealtimeNanos()).put("diagnosticBoot", boot).toString()
        handler.post {
            try { file.appendText(json + "\n", Charsets.UTF_8) }
            catch (_: java.io.IOException) { dropped.incrementAndGet() }
            finally { pending.decrementAndGet() }
        }
    }

    fun memory() {
        val info = Debug.MemoryInfo()
        Debug.getMemoryInfo(info)
        event("MEMORY", mapOf("pssBytes" to info.totalPss.toLong() * 1024,
            "nativeHeapBytes" to Debug.getNativeHeapAllocatedSize(), "diagnosticDropped" to dropped.get()))
    }
}
