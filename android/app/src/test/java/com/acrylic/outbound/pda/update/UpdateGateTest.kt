package com.acrylic.outbound.pda.update

import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class UpdateGateTest {
    @After fun releaseInstaller() { UpdateGate.endInstall() }

    @Test fun installWaitsForEveryQueuedBusinessOperation() {
        val scan = UpdateGate.enterBusiness(); val save = UpdateGate.enterBusiness()
        try {
            scan.close(); scan.close()
            assertThrows(IllegalStateException::class.java) { UpdateGate.beginInstall() }
        } finally { scan.close(); save.close() }
        UpdateGate.beginInstall()
        assertThrows(UpdateGate.Installing::class.java) { UpdateGate.enterBusiness() }
    }

    @Test fun failedBusinessActionReleasesItsLease() {
        assertThrows(IllegalArgumentException::class.java) { UpdateGate.business { throw IllegalArgumentException() } }
        UpdateGate.beginInstall()
    }

    @Test fun cancelInstallAllowsOriginalBusinessToResume() {
        UpdateGate.beginInstall(); UpdateGate.endInstall()
        assertEquals("original-request", UpdateGate.business { "original-request" })
    }

    @Test fun uploadInAnotherThreadBlocksInstallationUntilCompleted() {
        val started = CountDownLatch(1); val finish = CountDownLatch(1)
        val thread = Thread { UpdateGate.business { started.countDown(); finish.await(5, TimeUnit.SECONDS) } }
        thread.start()
        try {
            assertTrue(started.await(5, TimeUnit.SECONDS))
            assertThrows(IllegalStateException::class.java) { UpdateGate.beginInstall() }
        } finally { finish.countDown(); thread.join(5000) }
        UpdateGate.beginInstall()
    }
}
