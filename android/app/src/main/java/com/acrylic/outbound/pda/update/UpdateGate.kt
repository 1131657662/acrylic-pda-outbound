package com.acrylic.outbound.pda.update

/** One process: admission is atomic with installation, including work waiting in the scan queue. */
object UpdateGate {
    class Installing(message: String = "正在安装或核对更新，请先返回应用更新页完成核对") : IllegalStateException(message)
    private var active = 0
    private var installing = false
    private var maintaining = false
    private var background = false
    class Busy : IllegalStateException("正在作业，稍后继续同步")

    @Synchronized fun enterBackground(): AutoCloseable {
        requireIdle()
        if (background) throw Busy()
        background = true
        var closed = false
        return AutoCloseable { synchronized(this) { if (!closed) { closed = true; background = false } } }
    }

    @Synchronized fun requireIdle() {
        if (active != 0 || installing || maintaining) throw Busy()
    }

    /** Network never holds this monitor; a bounded commit atomically precedes the next scan admission. */
    @Synchronized fun <T> commitWhenIdle(action: () -> T): T {
        requireIdle()
        return action()
    }

    @Synchronized fun enterBusiness(): AutoCloseable {
        if (installing) throw Installing()
        if (maintaining) throw Installing("正在清理过期缓存，请稍后重试")
        active++
        var closed = false
        return AutoCloseable { synchronized(this) { if (!closed) { closed = true; active-- } } }
    }

    fun <T> business(action: () -> T): T = enterBusiness().use { action() }

    @Synchronized fun beginInstall() {
        check(!installing && !maintaining && !background && active == 0) { "仍有扫码、保存、登录、联网或清理任务，请完成后重试安装" }
        installing = true
    }

    @Synchronized fun endInstall() { installing = false }

    /** Skip a busy app; never wait on the scan queue or block a transaction under this monitor. */
    fun <T> maintenance(action: () -> T): T? {
        synchronized(this) {
            if (active != 0 || installing || maintaining || background) return null
            maintaining = true
        }
        try { return action() }
        finally { synchronized(this) { maintaining = false } }
    }
}
