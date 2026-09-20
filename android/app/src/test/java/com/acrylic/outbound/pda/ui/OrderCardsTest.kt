package com.acrylic.outbound.pda.ui

import android.app.Activity
import android.content.res.Configuration
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.lifecycle.viewModelScope
import androidx.test.core.app.ApplicationProvider
import com.acrylic.outbound.pda.PdaApplication
import com.acrylic.outbound.pda.data.PreparedOrder
import com.acrylic.outbound.pda.data.ClaimIntent
import com.acrylic.outbound.pda.databinding.OrderCardBinding
import kotlinx.coroutines.cancel
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowAlertDialog

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = OrderCardsTest.TestApp::class, qualifiers = "w320dp-h400dp-mdpi")
class OrderCardsTest {
    class TestApp : PdaApplication() { override fun onCreate() {} }
    private lateinit var model: PreparationViewModel
    @Before fun setup() { model = PreparationViewModel(ApplicationProvider.getApplicationContext<TestApp>()) }
    @After fun close() { model.viewModelScope.cancel() }

    @Test fun availableCardSeparatesVehicleFromFullNewAndLegacyOrderNumbers() {
        for (number in listOf("FH202609200001", "OB01a0bcbae6c37d5788c45bdbfb646e62")) {
            val parent = LinearLayout(ApplicationProvider.getApplicationContext<TestApp>())
            OrderCards(parent, model).render(PreparationUi(section = "AVAILABLE", available = listOf(remote(number))))
            val card = OrderCardBinding.bind(parent.getChildAt(0))
            assertEquals("待领取 · 车号 苏A-测试车", card.title.text.toString())
            assertEquals("订单号 $number\n计划 2 包", card.details.text.toString())
            assertFalse(card.title.text.contains(number))
        }
    }

    @Test fun localCardKeepsOriginalOrderNumberBesideVehicleAndReadiness() {
        val number = "OB01a0bcbae6c37d5788c45bdbfb646e62"
        val parent = LinearLayout(ApplicationProvider.getApplicationContext<TestApp>())
        val detail = JSONObject().put("order", remote(number)).put("plan", remote(number))
        val prepared = PreparedOrder("assignment", "order", "user", "device", "bundle", "sha", "{}", "{}",
            JSONObject().put("order", detail).toString(), "{}", "signature", "2026-09-20T00:00:00Z", 2, "2026-09-20T00:00:00Z")
        OrderCards(parent, model).render(PreparationUi(local = listOf(LocalCard(prepared, "资料已就绪")), username = "operator"))
        val card = OrderCardBinding.bind(parent.getChildAt(0))
        assertEquals("本机 · 车号 苏A-测试车", card.title.text.toString())
        assertTrue(card.details.text.toString().startsWith("订单号 $number\n资料已就绪\n"))
    }

    @Test fun pendingAndUnpreparedCardsDoNotLabelInternalIdAsAnOrderNumber() {
        val parent = LinearLayout(ApplicationProvider.getApplicationContext<TestApp>())
        val pending = ClaimIntent("original-order-id", "user", "device", "key", "body", "1")
        val assigned = JSONObject().put("orderId", "assigned-order-id").put("offlineToken", JSONObject.NULL)
            .put("operatorName", "原人员").put("expiresAt", "2026-09-22T00:00:00Z")
        OrderCards(parent, model).render(PreparationUi(pending = listOf(pending), assigned = listOf(assigned)))
        assertTrue(OrderCardBinding.bind(parent.getChildAt(0)).details.text.startsWith("订单标识 original-order-id\n"))
        assertTrue(OrderCardBinding.bind(parent.getChildAt(1)).details.text.startsWith("订单标识 assigned-order-id\n"))
    }

    @Test fun claimConfirmationShowsFullOrderNumberAndVehicle() {
        val controller = Robolectric.buildActivity(Activity::class.java).setup()
        try {
            val parent = LinearLayout(controller.get())
            OrderCards(parent, model).render(PreparationUi(section = "AVAILABLE", available = listOf(remote("FH202609200001"))))
            OrderCardBinding.bind(parent.getChildAt(0)).action.performClick()
            val dialog = ShadowAlertDialog.getLatestAlertDialog()
            assertTrue(dialog.findViewById<TextView>(android.R.id.message).text.startsWith("订单号 FH202609200001\n"))
            assertEquals("领取 苏A-测试车", org.robolectric.Shadows.shadowOf(dialog).title.toString())
            dialog.dismiss()
        } finally { controller.pause().stop().destroy() }
    }

    @Test fun narrowScreenAndLargeFontKeepFullIdentifiersAndClaimButtonVisible() {
        val app = ApplicationProvider.getApplicationContext<TestApp>()
        for (scale in listOf(1f, 2f)) {
            val context = app.createConfigurationContext(Configuration(app.resources.configuration).apply { fontScale = scale })
            val parent = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
            val number = "OB01a0bcbae6c37d5788c45bdbfb646e62"
            OrderCards(parent, model).render(PreparationUi(section = "AVAILABLE", available = listOf(remote(number))))
            parent.measure(View.MeasureSpec.makeMeasureSpec(296, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
            parent.layout(0, 0, 296, parent.measuredHeight)
            val card = OrderCardBinding.bind(parent.getChildAt(0))
            assertEquals("订单号 $number\n计划 2 包", card.details.text.toString())
            for (view in listOf(card.title, card.details, card.action)) {
                assertTrue(view.layout.height <= view.height - view.compoundPaddingTop - view.compoundPaddingBottom)
                assertTrue(view.right <= card.root.width)
                for (line in 0 until view.lineCount) assertEquals(0, view.layout.getEllipsisCount(line))
            }
            assertTrue(card.action.height >= 52)
        }
    }

    private fun remote(number: String) = JSONObject().put("id", "order").put("orderNo", number)
        .put("vehicleNo", "苏A-测试车").put("plannedTotal", "2")
}
