package com.acrylic.outbound.pda.security

import android.content.Context
import android.os.SystemClock
import android.provider.Settings
import org.json.JSONObject
import java.time.Instant
import kotlin.math.abs

data class TimeSample(val elapsed: Long, val wall: Long, val boot: Int)

object TrustedTime {
    fun sample(context: Context) = TimeSample(SystemClock.elapsedRealtime(), System.currentTimeMillis(),
        Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT, -1))

    fun anchor(serverTime: String, sample: TimeSample) = JSONObject().put("server", serverTime)
        .put("elapsed", sample.elapsed).put("wall", sample.wall).put("boot", sample.boot)

    fun now(anchor: JSONObject, sample: TimeSample): Instant {
        val elapsed = sample.elapsed - anchor.getLong("elapsed")
        check(sample.boot >= 0 && sample.boot == anchor.getInt("boot") && elapsed >= 0) { "设备已重启，请联网校验授权时间" }
        check(abs(sample.wall - anchor.getLong("wall") - elapsed) <= 120_000) { "设备时间发生变化，请联网校时" }
        return Instant.parse(anchor.getString("server")).plusMillis(elapsed)
    }
}
