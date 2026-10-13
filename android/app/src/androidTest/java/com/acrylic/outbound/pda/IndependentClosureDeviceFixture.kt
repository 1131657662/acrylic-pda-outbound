package com.acrylic.outbound.pda

import android.content.Intent
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.*
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.platform.app.InstrumentationRegistry
import com.acrylic.outbound.pda.independent.*
import com.acrylic.outbound.pda.rescue.*
import com.acrylic.outbound.pda.ui.*
import org.json.JSONObject
import java.io.File
import com.acrylic.outbound.pda.IndependentCredentialDeviceFixture as F

/** Original emulator identity, live issuance and product actions; no substitute token or task seeding. */
internal object IndependentClosureDeviceFixture {
    val directory get() = File(F.app.filesDir,"independent-closure-device").apply { mkdirs() }
    val id get() = File(directory,"task-id.txt").readText()
    val repository get() = IndependentScanDeviceFixture.repository
    fun guard() {
        F.guard(); check(InstrumentationRegistry.getArguments().getString("independentClosure") == "true")
    }
    fun text(id: Int,value: String) = onView(withId(id)).perform(independentSkuScroll(),replaceText(value),closeSoftKeyboard())
    fun intent() = Intent(F.app,IndependentWorkActivity::class.java).putExtra("taskId",id)
    fun grant(): RescueReadGrant {
        val owner = checkNotNull(F.app.database.independentTasks().task(id)).operatorId
        return RescueCredentials.shared(F.app,F.app.session).unlock(owner,IndependentRescueDeviceFixture.ownPassword.toCharArray())
    }
    fun snapshot() = RescueRoomSnapshot(F.app.database).read(RescueTarget("INDEPENDENT",id),grant())
    fun ending() = checkNotNull(IndependentClosure.verify(snapshot()))
    fun ready() = F.await { onView(withId(R.id.workManual)).check(matches(isEnabled())) }
    fun closed() = F.await { check(repository.read(id).state.state == "SEALED") }
    fun capture(activity: android.app.Activity,name: String) = IndependentSkuDeviceEvidence.capture(activity,directory,name)
    fun record(name: String,value: JSONObject) = File(directory,name).writeText(value.toString())
    fun preservePackage() {
        val files = RescueExportFiles(F.app);val access = grant()
        val generated = files.recover(files.available(access).first { it.summary.contains(id) }.id,access)
        File(directory,"original.pdarescue").writeBytes(generated.file.readBytes())
        record("package.json",JSONObject().put("taskId",id).put("packageId",generated.id).put("sha256",generated.sha256)
            .put("targetReadBackVerified",true).put("pendingReconciliation",true).put("localDeletionAllowed",false))
    }
}
