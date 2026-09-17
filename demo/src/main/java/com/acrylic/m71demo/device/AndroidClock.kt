package com.acrylic.m71demo.device

import android.content.Context
import android.os.SystemClock
import android.provider.Settings
import com.acrylic.m71demo.domain.AppClock
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

@javax.inject.Singleton
class AndroidClock @Inject constructor(@ApplicationContext context: Context) : AppClock {
    private val boot = runCatching { Settings.Global.getInt(context.contentResolver, "boot_count", -1) }.getOrDefault(-1)
    private val fallback = "UNVERIFIED-${java.util.UUID.randomUUID()}"
    override fun wallMs() = System.currentTimeMillis()
    override fun elapsedNs() = SystemClock.elapsedRealtimeNanos()
    override fun bootId() = if (boot >= 0) "boot-$boot" else fallback
}
