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

    @Test fun maintenanceExcludesNewBusinessInstallationAndAnotherCleaner() {
        val started = CountDownLatch(1); val finish = CountDownLatch(1)
        val thread = Thread { UpdateGate.maintenance { started.countDown(); finish.await(5, TimeUnit.SECONDS) } }
        thread.start()
        try {
            assertTrue(started.await(5, TimeUnit.SECONDS))
            assertThrows(UpdateGate.Installing::class.java) { UpdateGate.enterBusiness() }
            assertThrows(IllegalStateException::class.java) { UpdateGate.beginInstall() }
            assertNull(UpdateGate.maintenance { "unexpected" })
        } finally { finish.countDown(); thread.join(5000) }
        assertEquals("resumed", UpdateGate.business { "resumed" })
    }

    @Test fun failedMaintenanceReleasesBusinessAdmission() {
        assertThrows(IllegalArgumentException::class.java) { UpdateGate.maintenance { throw IllegalArgumentException() } }
        assertEquals("resumed", UpdateGate.business { "resumed" })
        UpdateGate.beginInstall()
    }

    @Test fun backgroundYieldsToNewScanBeforeCommit() {
        UpdateGate.enterBackground().use {
            UpdateGate.enterBusiness().use {
                assertThrows(UpdateGate.Busy::class.java) { UpdateGate.commitWhenIdle { fail("scan owns admission") } }
            }
            assertEquals("saved", UpdateGate.commitWhenIdle { "saved" })
        }
    }

    @Test fun backgroundExcludesAnotherWorkerInstallerAndCleaner() {
        UpdateGate.enterBackground().use {
            assertThrows(UpdateGate.Busy::class.java) { UpdateGate.enterBackground() }
            assertThrows(IllegalStateException::class.java) { UpdateGate.beginInstall() }
            assertNull(UpdateGate.maintenance { "unexpected" })
        }
        UpdateGate.beginInstall()
    }

    @Test fun failedBackgroundReleasesAdmission() {
        assertThrows(IllegalArgumentException::class.java) {
            UpdateGate.enterBackground().use { throw IllegalArgumentException() }
        }
        UpdateGate.enterBackground().close()
        assertEquals("cleaned", UpdateGate.maintenance { "cleaned" })
    }
}
