package com.acrylic.outbound.pda.ui

import android.app.Activity
import android.os.Looper
import android.widget.LinearLayout
import androidx.lifecycle.viewModelScope
import androidx.test.core.app.ApplicationProvider
import com.acrylic.outbound.pda.PdaApplication
import com.acrylic.outbound.pda.data.PreparedOrder
import com.acrylic.outbound.pda.databinding.OrderCardBinding
import kotlinx.coroutines.Job
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.*
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class) @Config(sdk = [28], application = OrderCardProgressTest.TestApp::class)
class OrderCardProgressTest {
    class TestApp : PdaApplication() { override fun onCreate() {} }
    private lateinit var model: PreparationViewModel
    @Before fun setup() { model = PreparationViewModel(ApplicationProvider.getApplicationContext<TestApp>()) }
    @After fun close() {
        val job = checkNotNull(model.viewModelScope.coroutineContext[Job]); job.cancel()
        val end = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(5)
        while (!job.isCompleted && System.nanoTime() < end) { Shadows.shadowOf(Looper.getMainLooper()).idle(); Thread.sleep(10) }
        check(job.isCompleted); ApplicationProvider.getApplicationContext<TestApp>().database.close()
    }
    @Test fun cardProgressUsesSavedWorkInsteadOfSnapshotInventory() {
        val parent = LinearLayout(ApplicationProvider.getApplicationContext<TestApp>())
        OrderCards(parent, model).render(PreparationUi(local = listOf(LocalCard(prepared(), "资料已就绪", originalOperator = true, savedCount = 7))))
        val card = OrderCardBinding.bind(parent.getChildAt(0))
        assertTrue(card.details.text.contains("本机有效已保存 7 / 本单计划 10 包"))
        assertFalse(card.details.text.contains("库存 999"))
    }
    @Test fun legacyFrozenCardRoutesOnlyToOriginalDetails() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup()
        try {
            val parent = LinearLayout(activity.get())
            OrderCards(parent, model).render(PreparationUi(local = listOf(LocalCard(prepared(), "原件保留", workState = "HANDOVER_FROZEN", originalOperator = true))))
            val card = OrderCardBinding.bind(parent.getChildAt(0)); card.work.performClick()
            assertEquals("核对旧冻结原件", card.work.text.toString())
            assertEquals(WorkDetailsActivity::class.java.name, Shadows.shadowOf(activity.get()).nextStartedActivity.component?.className)
        } finally { activity.pause().stop().destroy() }
    }
    private fun prepared(): PreparedOrder {
        val row = JSONObject().put("vehicleNo", "合成车").put("orderNo", "FH_TEST").put("plannedTotal", "10")
        val plan = JSONObject().put("order", row).put("plan", row)
        return PreparedOrder("assignment", "order", "user", "device", "bundle", "sha", "{}", "{}",
            JSONObject().put("order", plan).toString(), "{}", "signature", "date", 999, "date")
    }
}
