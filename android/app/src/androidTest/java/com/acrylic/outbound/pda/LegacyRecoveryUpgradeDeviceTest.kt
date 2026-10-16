package com.acrylic.outbound.pda

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.acrylic.outbound.pda.sync.RecoveryProtection
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Separate native phases on a fresh copy, with a real host force-stop between them. */
@RunWith(AndroidJUnit4::class)
class LegacyRecoveryUpgradeDeviceTest {
    private fun facts(): LegacyRecoveryUpgradeFacts {
        val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as PdaApplication
        return LegacyRecoveryUpgradeFacts(app).also {
            it.requireIsolation()
            androidx.work.WorkManager.getInstance(app).cancelAllWork().result.get()
        }
    }

    @Test fun originalNonemptyRecoveryDatabaseUpgradesAndVerifiesStoredProofs() {
        val facts = facts()
        assertFalse(File(facts.directory, "upgrade.json").exists())
        facts.originalFacts()
        facts.signedProofs()
        assertEquals("RECONCILING", facts.database.sync().recovery()!!.mode)
        assertThrows(IllegalStateException::class.java) { RecoveryProtection(facts.database).requireNormal() }
        facts.save("upgrade")
    }

    @Test fun freshProcessNormalObservationKeepsOriginalFactsAndRecoveryBarrier() {
        val facts = facts()
        val previous = JSONObject(File(facts.directory, "upgrade.json").readText())
        assertNotEquals(previous.getInt("pid"), android.os.Process.myPid())
        facts.originalFacts()
        facts.signedProofs()
        val protection = RecoveryProtection(facts.database)
        val before = facts.database.sync().recovery()!!
        protection.observe(JSONObject().put("serverRecoveryEpoch", before.observedServerEpoch).put("serviceMode", "NORMAL"))
        assertEquals(before, facts.database.sync().recovery())
        assertThrows(IllegalStateException::class.java) { protection.requireNormal() }
        facts.originalFacts()
        facts.save("restart")
    }
}
