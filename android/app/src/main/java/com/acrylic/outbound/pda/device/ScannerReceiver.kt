package com.acrylic.outbound.pda.device

import android.content.*
import androidx.core.content.ContextCompat

class ScannerReceiver(private val context: Context, private val receive: (String) -> Unit,
    private val stop: (String) -> Unit) : BroadcastReceiver() {
    private var registered = false
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION) return
        val raw = runCatching { intent.getStringExtra(BARCODE_KEY) }.getOrNull()
        if (raw == null || raw.length > 128) {
            stop("扫码广播缺少完整字符串或长度异常，本条未接收，请停扫核对"); return
        }
        receive(raw)
    }
    fun register() {
        if (registered) return
        ContextCompat.registerReceiver(context, this, IntentFilter(ACTION), ContextCompat.RECEIVER_EXPORTED)
        registered = true
    }
    fun unregister() {
        if (!registered) return
        context.unregisterReceiver(this); registered = false
    }
    companion object {
        const val ACTION = "com.android.decodewedge.decode_action"
        const val BARCODE_KEY = "com.android.decode.intentwedge.barcode_string"
    }
}
