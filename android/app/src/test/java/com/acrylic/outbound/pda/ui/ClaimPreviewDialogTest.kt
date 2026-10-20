package com.acrylic.outbound.pda.ui

import android.app.Activity
import android.app.AlertDialog
import android.widget.TextView
import kotlinx.coroutines.CompletableDeferred
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = android.app.Application::class)
class ClaimPreviewDialogTest {
    @Test fun completeServerPreviewShowsAllFactsAndCancelDoesNotClaim() = activity { activity ->
        var claims = 0
        val dialog = ClaimPreviewDialog(activity, { preview() }) { _, _ -> claims++ }.show()
        val message = dialog.findViewById<TextView>(android.R.id.message).text.toString()
        for (fact in listOf("订单号 SYNTH-ORDER-01", "车号 合成车", "仓库 合成仓", "计划 2 包", "003 · 合成型号 · 合成产品 · 2 包"))
            assertTrue(message, message.contains(fact))
        assertTrue(dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled)
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE).performClick()
        assertEquals(0, claims)
    }

    @Test fun explicitConfirmationPassesOriginalCheckedPreviewExactlyOnce() = activity { activity ->
        val original = preview(); var claims = 0; var received: JSONObject? = null
        val dialog = ClaimPreviewDialog(activity, { original }) { value, _ -> claims++; received = value }.show()
        val button = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
        button.performClick(); button.performClick()
        assertEquals(1, claims); assertSame(original, received)
    }

    @Test fun missingWarehouseOrProductOrMismatchedTotalCannotBeConfirmed() = activity { activity ->
        for (value in listOf(preview().apply { remove("warehouseName") }, preview().put("lines", JSONArray()),
            preview().put("plannedTotal", "3"))) {
            var claims = 0
            val dialog = ClaimPreviewDialog(activity, { value }) { _, _ -> claims++ }.show()
            assertFalse(dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled)
            assertTrue(dialog.findViewById<TextView>(android.R.id.message).text.contains("尚未领取"))
            dialog.dismiss(); assertEquals(0, claims)
        }
    }

    @Test fun networkFailureAndCancelledLoadingNeverClaim() = activity { activity ->
        var claims = 0
        val failed = ClaimPreviewDialog(activity, { error("合成网络中断") }) { _, _ -> claims++ }.show()
        assertFalse(failed.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled); failed.dismiss()
        val result = CompletableDeferred<JSONObject>()
        val loading = ClaimPreviewDialog(activity, { result.await() }) { _, _ -> claims++ }.show()
        assertFalse(loading.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled)
        loading.dismiss(); result.complete(preview())
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
        assertEquals(0, claims)
    }

    private fun activity(test: (Activity) -> Unit) {
        val controller = Robolectric.buildActivity(Activity::class.java).setup()
        try { test(controller.get()) } finally { controller.pause().stop().destroy() }
    }

    private fun preview() = JSONObject().put("id", "synthetic-order-id").put("rowVersion", "1").put("status", "READY")
        .put("orderNo", "SYNTH-ORDER-01").put("vehicleNo", "合成车").put("warehouseId", "synthetic-warehouse-id")
        .put("warehouseName", "合成仓").put("plannedTotal", "2").put("lines", JSONArray().put(JSONObject()
            .put("deliveryCode", "003").put("model", "合成型号").put("name", "合成产品").put("plannedCount", 2)))
}
