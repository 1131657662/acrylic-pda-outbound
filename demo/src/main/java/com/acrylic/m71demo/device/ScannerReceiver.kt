package com.acrylic.m71demo.device

import android.content.*
import androidx.core.content.ContextCompat

class ScannerReceiver(private val context: Context, private val telemetry: Telemetry,
    private val receive: (String, String, String?) -> Unit) : BroadcastReceiver() {
    private var registered = false
    private val softwareReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action != SOFTWARE_ACTION) return
            val id = runCatching { intent.getStringExtra(TEST_ID_KEY) }.getOrNull()
            if (id.isNullOrBlank()) {
                telemetry.event("INPUT_REFUSED", mapOf("reason" to "MISSING_TEST_CAPTURE_ID"))
                return
            }
            accept(intent, "TEST_INJECTION", id)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION) return
        accept(intent, "BROADCAST", null)
    }

    private fun accept(intent: Intent, source: String, testId: String?) {
        val started = android.os.SystemClock.elapsedRealtimeNanos()
        val value = runCatching { intent.getStringExtra(BARCODE_KEY) }.getOrNull()
        if (value != null) receive(value, source, testId)
        else telemetry.event("BROADCAST_NO_STRING", mapOf("keys" to intent.extras?.keySet()?.take(10)?.joinToString(),
            "fallbackType" to intent.extras?.get(FALLBACK_KEY)?.javaClass?.simpleName))
        telemetry.event("BROADCAST_CALLBACK", mapOf("callbackDurationNs" to android.os.SystemClock.elapsedRealtimeNanos() - started,
            "rawInput" to value?.take(128), "rawLength" to value?.length, "captureId" to testId,
            "broadcastAction" to intent.action, "source" to source))
    }

    fun register() {
        if (registered) return
        ContextCompat.registerReceiver(context, this, IntentFilter(ACTION), ContextCompat.RECEIVER_EXPORTED)
        try {
            ContextCompat.registerReceiver(context, softwareReceiver, IntentFilter(SOFTWARE_ACTION),
                ContextCompat.RECEIVER_NOT_EXPORTED)
        } catch (e: Exception) { context.unregisterReceiver(this); throw e }
        registered = true
    }

    fun unregister() {
        if (!registered) return
        context.unregisterReceiver(this)
        context.unregisterReceiver(softwareReceiver)
        registered = false
    }

    companion object {
        const val ACTION = "com.android.decodewedge.decode_action"
        const val BARCODE_KEY = "com.android.decode.intentwedge.barcode_string"
        const val FALLBACK_KEY = "com.android.decode.intentwedge.barcode_data"
        const val SOFTWARE_ACTION = "com.acrylic.m71demo.SOFTWARE_SCAN"
        const val TEST_ID_KEY = "demo_test_capture_id"
    }
}
