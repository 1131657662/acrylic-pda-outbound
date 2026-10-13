package com.acrylic.outbound.pda

import android.content.Intent
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.*
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.*
import com.acrylic.outbound.pda.device.ScannerReceiver
import com.acrylic.outbound.pda.independent.*
import com.acrylic.outbound.pda.security.TrustedTime
import org.junit.Assert.*
import java.io.File
import com.acrylic.outbound.pda.IndependentCredentialDeviceFixture as F

internal object IndependentScanDeviceFixture {
    val directory get() = File(F.app.filesDir, "independent-scan-device").apply { mkdirs() }
    val repository get() = IndependentTaskRepository(F.app.database, { TrustedTime.sample(F.app) }) {
        val session = F.app.session.requireSession()
        IndependentTaskActor(session.getString("userId"), session.getString("deviceId"), session.getString("warehouseId"))
    }
    fun guard() {
        F.guard(); check(androidx.test.platform.app.InstrumentationRegistry.getArguments().getString("independentScan") == "true")
        assertNull(F.app.getSystemService(android.net.ConnectivityManager::class.java).activeNetwork)
    }
    fun task(id: String) = repository.read(id)
    fun text(id: Int, text: String) = onView(withId(id)).perform(independentSkuScroll(), replaceText(text), closeSoftKeyboard())
    fun authorize() {
        text(R.id.workPassword, F.password); F.click(R.id.workAuthorize)
        F.await { onView(withId(R.id.workManual)).check(matches(isEnabled())) }
    }
    fun broadcast(barcode: String) {
        F.app.sendBroadcast(Intent(ScannerReceiver.ACTION).setPackage(F.app.packageName).putExtra(ScannerReceiver.BARCODE_KEY, barcode))
    }
    fun count(id: String, value: Long) = F.await { assertEquals(value, task(id).state.actualCount) }
    fun available(id: String): List<String> {
        val task = task(id); val codes = task.lines.map { it.deliveryCode }.toSet()
        val rows = checkNotNull(IndependentSkuRepository(F.app.database) { TrustedTime.sample(F.app) }.review(task.task.importId).preview).rows
        return rows.mapNotNull { it.parsedFields }.filter { bale ->
            val db = F.app.database
            bale.deliveryCode in codes && db.scans().guard(bale.barcode) == null && db.temporaryScans().guard(bale.barcode) == null &&
                db.temporaryHandoverGuards().get(bale.barcode) == null && db.independentScans().guard(bale.barcode) == null &&
                db.barcodes().active(task.task.recoveryEpoch, task.task.warehouseId, bale.barcode)?.status.let { it == null || it == "AVAILABLE" }
        }.map { it.barcode }.distinct().take(2).also { assertEquals(2, it.size) }
    }
}
