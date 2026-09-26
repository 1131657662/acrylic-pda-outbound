package com.acrylic.outbound.pda.evidence

import android.content.Intent
import android.os.Looper
import android.widget.Button
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import com.acrylic.outbound.pda.PdaApplication
import com.acrylic.outbound.pda.R
import com.acrylic.outbound.pda.ui.LocalDraftEvidenceActivity
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.*
import org.robolectric.annotation.Config
import org.robolectric.annotation.SQLiteMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = com.acrylic.outbound.pda.PdaTestApplication::class)
@SQLiteMode(SQLiteMode.Mode.NATIVE)
class LocalDraftEvidenceUiTest {
    @Test fun inaccessibleOriginalsNeverEnableStopOrTransferEvenAfterRefresh() {
        val app = ApplicationProvider.getApplicationContext<PdaApplication>()
        val intent = Intent(app, LocalDraftEvidenceActivity::class.java).putExtra("clientOrderId", "unavailable-original")
        val controller = Robolectric.buildActivity(LocalDraftEvidenceActivity::class.java, intent).setup()
        try {
            val page = controller.get(); awaitChecked(page)
            assertFalse(page.findViewById<Button>(R.id.upload).isEnabled)
            assertFalse(page.findViewById<Button>(R.id.freeze).isEnabled)
            page.findViewById<Button>(R.id.refresh).performClick(); awaitChecked(page)
            assertFalse(page.findViewById<Button>(R.id.upload).isEnabled)
            assertFalse(page.findViewById<Button>(R.id.freeze).isEnabled)
            assertTrue(page.findViewById<TextView>(R.id.status).text.contains("不得放行"))
        } finally {
            controller.pause().stop().destroy(); app.temporaryWork.close(); app.database.close()
            androidx.work.testing.WorkManagerTestInitHelper.closeWorkDatabase()
        }
    }
    private fun awaitChecked(page: LocalDraftEvidenceActivity) {
        val deadline = System.nanoTime() + 5_000_000_000
        while (System.nanoTime() < deadline) {
            Shadows.shadowOf(Looper.getMainLooper()).idle()
            if (page.findViewById<Button>(R.id.refresh).isEnabled && page.findViewById<TextView>(R.id.error).text.isNotEmpty()) return
            Thread.sleep(10)
        }
        fail("Evidence page did not finish checking unavailable originals")
    }
}
