package com.acrylic.outbound.pda

import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.*
import com.acrylic.outbound.pda.rescue.RescueExportFiles
import com.acrylic.outbound.pda.ui.RescueExportActivity
import org.hamcrest.CoreMatchers.containsString
import org.hamcrest.CoreMatchers.not
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import com.acrylic.outbound.pda.IndependentRescueDeviceFixture as F

class IndependentRescueDeviceTest {
    @Test fun originalOfflineIndependentTaskGeneratesAndCopiesThroughRealSaf() {
        F.guard(); val before = F.digest()
        ActivityScenario.launch<RescueExportActivity>(F.intent()).use { scenario ->
            F.unlock(); F.click(R.id.readSnapshot)
            F.await { onView(withId(R.id.generate)).check(matches(isEnabled())) }
            onView(withId(R.id.importReceipt)).check(matches(not(isEnabled())))
            F.text(R.id.packagePassword, F.packagePassword); F.click(R.id.generate)
            F.await { onView(withId(R.id.copyState)).check(matches(withText(containsString("本机包生成并回读通过")))) }
            onView(withId(R.id.summary)).check(matches(withText(containsString("待主管对账"))))
            scenario.onActivity { IndependentSkuDeviceEvidence.capture(it, F.directory, "generated") }
            F.click(R.id.copy); File(F.directory, "ready-for-saf.txt").writeText("Real create-document chooser pending")
            F.await(110) { onView(withId(R.id.copyState)).check(matches(withText(containsString("目标写入与回读一致")))) }
            F.preserve()
        }
        assertEquals(before, F.digest())
        File(F.directory, "business-before-after.json").writeText(JSONObject().put("before", before).put("after", F.digest()).toString())
    }
    @Test fun loggedOutNewProcessRecoversOriginalPackageAndSafCancellation() {
        F.guard(); val before = F.digest()
        if (F.app.session.saved() != null) {
            try { F.app.session.logout() }
            catch (error: com.acrylic.outbound.pda.network.PdaTransportFailure) {
                File(F.directory, "offline-logout-transport.txt").writeText(error.javaClass.name)
            }
        }
        assertNull(F.app.session.saved())
        val original = JSONObject(File(F.directory, "result.json").readText())
        ActivityScenario.launch<RescueExportActivity>(F.intent()).use {
            F.unlock(); F.click(R.id.recoverPackage)
            F.await { onView(withId(R.id.copyState)).check(matches(withText(containsString("原私有包回读通过")))) }
            F.click(R.id.copy); File(F.directory, "ready-for-cancel.txt").writeText("Host must press real system back")
            F.await(90) { onView(withId(R.id.status)).check(matches(withText(containsString("目标选择已取消")))) }
            assertEquals(original.getString("sha256"), RescueExportFiles(F.app).recover(original.getString("packageId"), F.grant()).sha256)
        }
        assertEquals(before, F.digest()); assertNull(F.app.session.saved())
        File(F.directory, "cancel.json").writeText(JSONObject().put("newProcess", true).put("loggedOut", true)
            .put("originalPackagePreserved", true).put("newExternalCopyVerified", false).toString())
    }
    @Test fun failedFileTargetKeepsRecoverableOriginalInAnotherProcess() {
        F.guard(); val before = F.digest(); val original = JSONObject(File(F.directory, "result.json").readText())
        val files = RescueExportFiles(F.app); val access = F.grant(); val recovered = files.recover(original.getString("packageId"), access)
        assertThrows(Exception::class.java) { files.copy(recovered, android.net.Uri.parse("content://missing-independent-rescue-target/no-file"), access) }
        assertEquals(original.getString("sha256"), RescueExportFiles(F.app).recover(recovered.id, access).sha256)
        assertEquals(before, F.digest())
        File(F.directory, "file-failure.json").writeText(JSONObject().put("targetFailed", true).put("originalPackagePreserved", true)
            .put("newProcess", true).put("newExternalCopyVerified", false).toString())
    }
}
