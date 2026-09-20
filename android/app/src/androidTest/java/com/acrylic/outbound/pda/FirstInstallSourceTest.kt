package com.acrylic.outbound.pda

import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Read-only installation evidence for the opt-in browser/system-installer verification. */
@RunWith(AndroidJUnit4::class)
class FirstInstallSourceTest {
    @Test fun systemInstalledCandidateRetainsFirstInstallTimestamp() {
        check(Build.MODEL.startsWith("sdk_gphone") && Build.VERSION.SDK_INT >= 30)
        check(BuildConfig.PDA_UPDATE_CHANNEL == "staging")
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        val source = context.packageManager.getInstallSourceInfo(context.packageName)
        assertEquals("com.google.android.packageinstaller", source.installingPackageName)
        assertEquals(BuildConfig.VERSION_CODE.toLong(), info.longVersionCode)
        assertEquals(info.firstInstallTime, info.lastUpdateTime)
        val evidence = JSONObject().put("installingPackageName", source.installingPackageName)
            .put("initiatingPackageName", source.initiatingPackageName)
            .put("versionCode", info.longVersionCode).put("versionName", info.versionName)
            .put("firstInstallTime", info.firstInstallTime).put("lastUpdateTime", info.lastUpdateTime)
        File(context.filesDir, "system-install-source-evidence.json").writeText(evidence.toString())
    }
}
