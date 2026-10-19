package com.acrylic.outbound.pda

import android.content.Intent
import android.view.WindowManager
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.*
import com.acrylic.outbound.pda.independent.IndependentCredentialVault
import com.acrylic.outbound.pda.security.JwtVerifier
import com.acrylic.outbound.pda.ui.IndependentCredentialActivity
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.Base64
import com.acrylic.outbound.pda.IndependentCredentialDeviceFixture as F

class IndependentCredentialDeviceTest {
    private fun intent() = Intent(F.app, IndependentCredentialActivity::class.java)
    @Test fun registeredOriginalDeviceProvisionsThroughProductPage() {
        F.guard()
        val input = JSONObject(File(F.directory, "input.json").readText())
        F.app.session.login(input.getString("username"), input.getString("password"))
        androidx.work.WorkManager.getInstance(F.app).cancelAllWork().result.get()
        ActivityScenario.launch<IndependentCredentialActivity>(intent()).use {
            F.await { onView(withId(R.id.provision)).check(matches(isEnabled())) }
            F.secret(); F.click(R.id.provision); F.message("已保存签名独立身份")
            F.secret(); F.click(R.id.verify); F.message("独立身份与可信期限已核验")
        }
        val entry = F.active()
        File(F.directory, "original-receipt.json").writeText(entry.getString("receipt"))
        File(F.directory, "public-material.json").writeText(JSONObject().put("signingKey", entry.getJSONObject("trustedKey"))
            .put("devicePublicKey", F.app.session.publicKey()).toString())
        File(F.directory, "private-http-context.json").writeText(JSONObject().put("session", F.app.session.requireSession())
            .put("key", checkNotNull(IndependentCredentialVault(F.app).read()).getString("active"))
            .put("body", JSONObject().put("deviceId", F.app.session.settings.device)
                .put("deviceKeySha256", JwtVerifier.sha(Base64.getDecoder().decode(F.app.session.publicKey())))).toString())
        F.record("provision.json", JSONObject().put("actualProductPage", true).put("nativeKeystore", true)
            .put("credentialId", JSONObject(entry.getString("receipt")).getString("credentialId")))
    }
    @Test fun offlineNewProcessVerifiesOriginalAndRejectsWrongPassword() {
        F.guard(); val original = File(F.directory, "original-receipt.json").readText()
        assertEquals(original, F.active().getString("receipt"))
        ActivityScenario.launch<IndependentCredentialActivity>(intent()).use {
            F.secret("wrong-independent-native-passphrase"); F.click(R.id.verify); F.message("独立采集凭据无效")
            F.secret(); F.click(R.id.verify); F.message("独立身份与可信期限已核验")
        }
        assertEquals(original, F.active().getString("receipt"))
        F.record("offline-restart.json", JSONObject().put("newProcess", true).put("wrongPasswordRejected", true)
            .put("sameSignedOriginal", true).put("localDeletionAllowed", false))
    }
    @Test fun localRevocationPreservesOriginalAndBlocksNewCapture() {
        F.guard(); val original = F.active().getString("receipt")
        ActivityScenario.launch<IndependentCredentialActivity>(intent()).use {
            F.click(R.id.revoke); F.message("本机新增采集已撤销")
            F.secret(); F.click(R.id.verify); F.message("本机独立采集资格已失效")
        }
        assertEquals(original, F.active().getString("receipt"))
        F.record("local-revoke.json", JSONObject().put("originalPreserved", true).put("captureBlocked", true))
    }
    @Test fun activeCredentialIsBlockedAfterActualLogout() {
        F.guard()
        F.credentials.provision(F.password.toCharArray())
        F.credentials.verifyLocally(F.password.toCharArray())
        val original = F.active().getString("receipt")
        F.app.session.logout()
        assertNull(F.app.session.saved())
        ActivityScenario.launch<IndependentCredentialActivity>(intent()).use {
            F.secret(); F.click(R.id.verify); F.message("请先登录")
            F.click(R.id.reload); F.message("已读取原件")
        }
        assertEquals(original, F.active().getString("receipt"))
        F.record("logout.json", JSONObject().put("originalPreserved", true).put("captureBlocked", true))
    }
    @Test fun actualLayoutAllowsLargeTextAndKeyboard() {
        F.guard()
        ActivityScenario.launch<IndependentCredentialActivity>(intent()).use { scenario ->
            F.await { onView(withId(R.id.password)).check(matches(isEnabled())) }
            val scale = F.app.resources.configuration.fontScale.toString()
            scenario.onActivity { activity ->
                assertTrue(activity.window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE != 0)
                IndependentSkuDeviceEvidence.capture(activity, F.directory, "layout-$scale-top")
            }
            onView(withId(R.id.password)).perform(independentSkuScroll(), click())
            F.await { onView(withId(R.id.password)).check(matches(isDisplayed())) }
            F.await { scenario.onActivity { activity ->
                assertTrue(androidx.core.view.ViewCompat.getRootWindowInsets(activity.window.decorView)
                    ?.isVisible(androidx.core.view.WindowInsetsCompat.Type.ime()) == true)
            } }
            scenario.onActivity { activity -> IndependentSkuDeviceEvidence.capture(activity, F.directory, "layout-$scale-keyboard") }
            androidx.test.espresso.Espresso.closeSoftKeyboard()
            onView(withId(R.id.back)).perform(independentSkuScroll()).check(matches(isDisplayed()))
            scenario.onActivity { activity -> IndependentSkuDeviceEvidence.capture(activity, F.directory, "layout-$scale-bottom") }
        }
    }
    @Test fun actualRebootPreservesOriginalButBlocksCapture() {
        F.guard()
        val old = JSONObject(File(F.directory, "provision.json").readText())
        assertTrue(F.app.resources.configuration.fontScale > 0)
        assertTrue(com.acrylic.outbound.pda.security.TrustedTime.sample(F.app).boot > old.getInt("bootCount"))
        val original = File(F.directory, "original-receipt.json").readText()
        assertEquals(original, F.active().getString("receipt"))
        ActivityScenario.launch<IndependentCredentialActivity>(intent()).use {
            F.secret(); F.click(R.id.verify); F.message("本机独立采集资格已失效")
            F.click(R.id.reload); F.message("已读取原件")
        }
        assertEquals(original, F.active().getString("receipt"))
        F.record("reboot.json", JSONObject().put("actualReboot", true).put("captureBlocked", true).put("originalPreserved", true))
    }
    @Test fun knownPermissionRevocationBlocksOfflineCaptureAndPreservesOriginal() {
        F.guard(); val original = F.active().getString("receipt")
        assertFalse(F.active().getBoolean("locallyRevoked"))
        F.credentials.verifyLocally(F.password.toCharArray())
        ActivityScenario.launch<IndependentCredentialActivity>(intent()).use {
            F.secret(); F.click(R.id.provision); F.message("没有执行此PDA操作的权限")
            F.secret(); F.click(R.id.verify); F.message("本机独立采集资格已失效")
        }
        assertEquals(original, F.active().getString("receipt"))
        F.record("known-revocation.json", JSONObject().put("realHttpPermissionDenied", true)
            .put("localCaptureBlocked", true).put("originalPreserved", true))
    }
    @Test fun offlineNewProcessKeepsKnownRevocationAndOriginal() {
        F.guard(); val original = F.active().getString("receipt")
        val manager = F.app.getSystemService(android.net.ConnectivityManager::class.java)
        assertNull(manager.activeNetwork)
        assertTrue(F.active().getBoolean("locallyRevoked"))
        ActivityScenario.launch<IndependentCredentialActivity>(intent()).use {
            F.secret(); F.click(R.id.verify); F.message("本机独立采集资格已失效")
            F.click(R.id.reload); F.message("已读取原件")
        }
        assertEquals(original, F.active().getString("receipt"))
        F.record("known-revocation-offline.json", JSONObject().put("newProcess", true)
            .put("networkDisabled", true).put("captureBlocked", true).put("originalPreserved", true))
    }
}
