package com.acrylic.outbound.pda.update

/** One process: admission is atomic with installation, including work waiting in the scan queue. */
object UpdateGate {
    class Installing : IllegalStateException("正在安装或核对更新，请先返回应用更新页完成核对")
    private var active = 0
    private var installing = false

    @Synchronized fun enterBusiness(): AutoCloseable {
        if (installing) throw Installing()
        active++
        var closed = false
        return AutoCloseable { synchronized(this) { if (!closed) { closed = true; active-- } } }
    }

    fun <T> business(action: () -> T): T = enterBusiness().use { action() }

    @Synchronized fun beginInstall() {
        check(!installing && active == 0) { "仍有扫码、保存、登录或联网任务，请完成后重试安装" }
        installing = true
    }

    @Synchronized fun endInstall() { installing = false }
}
