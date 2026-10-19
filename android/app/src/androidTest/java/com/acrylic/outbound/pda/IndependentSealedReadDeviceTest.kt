package com.acrylic.outbound.pda

import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.platform.app.InstrumentationRegistry
import com.acrylic.outbound.pda.rescue.RescueBundle
import com.acrylic.outbound.pda.rescue.RescueSnapshot
import org.hamcrest.CoreMatchers.containsString
import org.hamcrest.CoreMatchers.not
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import com.acrylic.outbound.pda.IndependentCredentialDeviceFixture as F
import com.acrylic.outbound.pda.IndependentClosureDeviceFixture as C

/** Consume retained sealed facts without re-seeding identity or replacing earlier device evidence. */
class IndependentSealedReadDeviceTest {
    @Test fun originalSealedTaskRemainsReadOnlyAfterOfflineRestart() {
        C.guard()
        assertNull(F.app.getSystemService(android.net.ConnectivityManager::class.java).activeNetwork)
        val before = C.snapshot()
        val ending = C.ending().toString()
        val directory = evidenceDirectory()

        ActivityScenario.launch<com.acrylic.outbound.pda.ui.IndependentWorkActivity>(C.intent()).use { scenario ->
            IndependentWorkDevicePanels.details()
            F.await { onView(withId(R.id.workOriginal)).check(matches(withText(containsString("已结束封存")))) }
            onView(withId(R.id.workFinish)).check(matches(not(isEnabled())))
            onView(withId(R.id.workPause)).check(matches(not(isEnabled())))
            onView(withId(R.id.workBack)).perform(independentSkuScroll()).check(matches(isDisplayed()))
            scenario.onActivity { IndependentSkuDeviceEvidence.capture(it, directory, "sealed-read") }
        }

        assertEquals(ending, C.ending().toString())
        assertEquals(facts(before), facts(C.snapshot()))
        File(directory, "result.json").writeText(JSONObject().put("passed", true)
            .put("taskId", C.id).put("originalFactsUnchanged", true).put("offlineNewProcess", true)
            .put("localDeletionAllowed", false).put("physicalDevice", false).toString())
    }

    private fun evidenceDirectory(): File {
        val name = checkNotNull(InstrumentationRegistry.getArguments().getString("e06EvidenceDirectory"))
        require(name.matches(Regex("e06-l2-native-[a-z0-9-]+")))
        return File(F.app.filesDir, name).also { check(!it.exists() && it.mkdir()) }
    }

    private fun facts(snapshot: RescueSnapshot): List<String> = listOf(snapshot.order.toString()) +
        snapshot.lines.map { it.toString() } + snapshot.captures.map { it.toString() } +
        snapshot.scans.map { it.toString() } + snapshot.events.map { it.toString() } +
        snapshot.originals.toSortedMap().map { (name, bytes) -> "$name:${RescueBundle.sha(bytes)}" }
}
