package com.acrylic.outbound.pda.independent

import kotlinx.coroutines.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.*
import java.util.concurrent.*

@RunWith(RobolectricTestRunner::class) @Config(sdk = [28], application = android.app.Application::class)
@SQLiteMode(SQLiteMode.Mode.NATIVE)
class IndependentTaskRaceTest {
    @Test fun concurrentOriginalRetriesCreateOneTaskAndOneAudit() = runBlocking<Unit>(Dispatchers.IO) {
        IndependentTaskFixture().use { f ->
            val command = f.command(); val grant = f.grant(); val gate = CountDownLatch(1)
            val pool = Executors.newFixedThreadPool(2)
            try {
                val results = (1..2).map { pool.submit<IndependentTaskView> { gate.await(); f.repository.create(command, grant) } }
                gate.countDown(); assertEquals(results[0].get(40, TimeUnit.SECONDS), results[1].get(40, TimeUnit.SECONDS))
                assertEquals(1L, f.sku.db.independentTasks().count()); assertEquals(1, f.repository.read(command.id).events.size)
            } finally { pool.shutdownNow() }
        }
    }
    @Test fun preparedOriginalStaysFixedIfAnotherFileIsActivatedBeforeCreate() = runBlocking<Unit>(Dispatchers.IO) {
        IndependentTaskFixture().use { f ->
            val command = f.command(); val grant = f.grant(); val request = f.repository.prepare(command, grant)
            f.sku.activate("004A0000001")
            val task = f.repository.create(command, grant)
            assertEquals(request.importId, task.task.importId); assertEquals("003", task.lines.single().deliveryCode)
            assertEquals(f.active.imported.sha256, task.task.skuSha256)
        }
    }
    @Test fun rebootAndIdentitySwitchPreservePreparedRequestWithoutCreatingTask() = runBlocking<Unit>(Dispatchers.IO) {
        IndependentTaskFixture().use { f ->
            val command = f.command(); val grant = f.grant(); val request = f.repository.prepare(command, grant)
            val sample = f.identity.sample; f.identity.sample = sample.copy(boot = sample.boot + 1)
            assertThrows(IllegalStateException::class.java) { f.repository.create(command, grant) }
            f.identity.sample = sample; f.identity.session.put("localSessionId", "changed")
            assertThrows(IllegalStateException::class.java) { f.repository.create(command, grant) }
            assertEquals(request, f.repository.latestRequest()); assertNull(f.repository.request(command.id).second)
        }
    }
}
