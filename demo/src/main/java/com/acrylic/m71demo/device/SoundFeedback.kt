package com.acrylic.m71demo.device

import android.content.Context
import android.media.*
import com.acrylic.m71demo.R
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.*

@Singleton
class SoundFeedback @Inject constructor(@ApplicationContext context: Context, private val telemetry: Telemetry) {
    private val prefs = context.getSharedPreferences("demo", Context.MODE_PRIVATE)
    private val pool = SoundPool.Builder().setMaxStreams(2).setAudioAttributes(AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()).build()
    private val loaded = java.util.concurrent.ConcurrentHashMap.newKeySet<Int>()
    private val ids: Map<String, Int>
    private var lastStream = 0
    @Volatile var lastError: String = ""
        private set
    var enabled: Boolean
        get() = prefs.getBoolean("sound", true)
        set(value) { prefs.edit().putBoolean("sound", value).apply() }

    init {
        pool.setOnLoadCompleteListener { _, id, status -> if (status == 0) loaded.add(id) }
        ids = mapOf("SAVED" to pool.load(context, R.raw.saved, 1), "ERROR" to pool.load(context, R.raw.error, 1))
    }

    @Synchronized fun play(type: String, captureId: String = "") {
        if (!enabled) {
            telemetry.event("SOUND_DISABLED", mapOf("sound" to type, "captureId" to captureId))
            return
        }
        val id = ids[type] ?: return
        if (type == "ERROR") pool.stop(lastStream)
        val stream = if (id in loaded) pool.play(id, 1f, 1f, 1, 0, 1f) else 0
        lastStream = stream
        lastError = if (stream == 0) "音效未就绪或播放失败，请以保存状态为准" else ""
        telemetry.event("SOUND_REQUEST", mapOf("sound" to type, "captureId" to captureId,
            "streamId" to stream, "actualAcousticOnsetMeasured" to false))
    }
}
