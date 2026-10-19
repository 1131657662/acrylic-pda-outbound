package com.acrylic.outbound.pda.independent

import androidx.test.core.app.ApplicationProvider
import com.acrylic.outbound.pda.PdaApplication
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking

/** Synthetic authority at the runtime boundary; Activity, engine and file Room stay real. */
internal class IndependentActivityFixture : AutoCloseable {
    val app = ApplicationProvider.getApplicationContext<PdaApplication>()
    val scan = runBlocking(Dispatchers.IO) { IndependentScanFixture() }
    val id get() = scan.command.id
    val engine = IndependentScanEngine(scan.task.sku.db, scan.environment) { scan.task.grant() }

    init {
        check(runtime("owner") == null && runtime("engine") == null)
        bind("owner", app)
        bind("engine", engine)
    }

    fun <T> read(action: (IndependentScanFixture) -> T): T = runBlocking(Dispatchers.IO) { action(scan) }

    override fun close() {
        engine.close()
        bind("engine", null)
        bind("owner", null)
        runBlocking(Dispatchers.IO) { scan.close() }
    }

    private fun runtime(name: String): Any? = field(name).get(null)
    private fun bind(name: String, value: Any?) = field(name).set(null, value)
    private fun field(name: String) = IndependentWorkRuntime::class.java.getDeclaredField(name).apply { isAccessible = true }
}

/** No scheduler, HTTP or platform Keystore initialization in this synthetic Activity host. */
class IndependentActivityTestApp : PdaApplication() {
    override fun onCreate() = Unit
}
