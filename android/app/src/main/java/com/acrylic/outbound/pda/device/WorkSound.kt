package com.acrylic.outbound.pda.device

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import com.acrylic.outbound.pda.R

class WorkSound(context: Context) {
    private val prefs = context.getSharedPreferences("work-sound", Context.MODE_PRIVATE)
    private val pool = SoundPool.Builder().setMaxStreams(2).setAudioAttributes(AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()).build()
    private val loaded = java.util.concurrent.ConcurrentHashMap.newKeySet<Int>()
    private val ids: Map<String, Int>
    private var last = 0
    @Volatile var error = ""
        private set
    var enabled: Boolean
        get() = prefs.getBoolean("enabled", true)
        set(value) { prefs.edit().putBoolean("enabled", value).apply() }
    init {
        pool.setOnLoadCompleteListener { _, id, status -> if (status == 0) loaded.add(id) }
        ids = mapOf("SAVED" to pool.load(context, R.raw.saved, 1), "ERROR" to pool.load(context, R.raw.error, 1))
    }
    @Synchronized fun play(type: String) {
        if (!enabled) return
        if (type == "ERROR") pool.stop(last)
        val id = ids[type] ?: return
        last = if (id in loaded) pool.play(id, 1f, 1f, 1, 0, 1f) else 0
        error = if (last == 0) "声音未就绪，请以页面保存状态为准" else ""
    }
}
