package com.acrylic.outbound.pda

import android.content.Intent
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.*
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.*
import com.acrylic.outbound.pda.independent.IndependentSkuRepository
import com.acrylic.outbound.pda.security.TrustedTime
import com.acrylic.outbound.pda.ui.IndependentSkuActivity
import org.junit.Assert.*
import org.junit.Test
import org.json.JSONObject
import java.io.File
import com.acrylic.outbound.pda.IndependentCredentialDeviceFixture as F

/** Host selects the retained original sample in actual Android DocumentsUI; no catalog rows are seeded. */
class IndependentTaskSourceDeviceTest {
    @Test fun actualSafPreparesOriginalSourceForTaskOnRetainedRegisteredDevice() {
        F.guard(); val directory = File(F.app.filesDir, "independent-task-device").apply { mkdirs() }
        val repository = IndependentSkuRepository(F.app.database) { TrustedTime.sample(F.app) }
        ActivityScenario.launch<IndependentSkuActivity>(Intent(F.app, IndependentSkuActivity::class.java)).use {
            F.await { onView(withId(R.id.choose)).check(matches(isEnabled())) }
            F.click(R.id.choose)
            File(directory, "choose-source.txt").writeText("Host select E06_TASK_SOURCE.txt in actual DocumentsUI")
            F.await(120) { onView(withId(R.id.activate)).check(matches(isEnabled())) }
            val review = repository.latest()!!
            if (review.preview!!.duplicateRows > 0) F.click(R.id.confirmDuplicates)
            F.click(R.id.activate); F.message("资料已启用")
            it.onActivity { activity -> IndependentSkuDeviceEvidence.capture(activity, directory, "source-active") }
        }
        val original = repository.active()!!; assertTrue(original.preview!!.productCount > 100)
        File(directory, "source-original.bin").writeBytes(repository.original(original.imported.id))
        File(directory, "source.json").writeText(JSONObject().put("actualSaf", true).put("importId", original.imported.id)
            .put("sha256", original.imported.sha256).put("rows", original.preview!!.validRows).toString())
    }
}
