package com.acrylic.outbound.pda.independent

import com.acrylic.outbound.pda.update.UpdateGate
import kotlinx.coroutines.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.*

@RunWith(RobolectricTestRunner::class) @Config(sdk = [28], application = android.app.Application::class)
@SQLiteMode(SQLiteMode.Mode.NATIVE)
class IndependentTaskGateTest {
    @Test fun installationBlocksNewTaskAndRequestUntilGateIsReleased() = runBlocking<Unit>(Dispatchers.IO) {
        IndependentTaskFixture().use { f ->
            val command = f.command(); val grant = f.grant()
            UpdateGate.beginInstall()
            try {
                assertThrows(UpdateGate.Installing::class.java) { f.repository.create(command, grant) }
                assertThrows(UpdateGate.Installing::class.java) { f.repository.prepare(command, grant) }
                assertNull(f.repository.latestRequest()); assertEquals(0L, f.sku.db.independentTasks().count())
            } finally { UpdateGate.endInstall() }
            assertEquals(command.id, f.repository.create(command, grant).task.id)
            UpdateGate.beginInstall(); UpdateGate.endInstall()
        }
    }
}
