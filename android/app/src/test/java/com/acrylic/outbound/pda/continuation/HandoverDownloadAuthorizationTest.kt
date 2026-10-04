package com.acrylic.outbound.pda.continuation

import com.acrylic.outbound.pda.sync.RecoveryProtection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.SQLiteMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = android.app.Application::class)
@SQLiteMode(SQLiteMode.Mode.NATIVE)
class HandoverDownloadAuthorizationTest {
    @get:Rule val folder = TemporaryFolder()

    @Test fun changedUserAuthorizationVersionCannotImportOldCredential() = runBlocking<Unit>(Dispatchers.IO) {
        rejectsChangedVersion("userAuthVersion")
    }

    @Test fun changedDeviceVersionCannotImportOldCredential() = runBlocking<Unit>(Dispatchers.IO) {
        rejectsChangedVersion("deviceVersion")
    }

    @Test fun existingRecoveryFenceStopsBeforeNetworkRequests() = runBlocking<Unit>(Dispatchers.IO) {
        HandoverDownloadFixture(folder.root).use { f ->
            RecoveryProtection(f.local.db).observe(JSONObject().put("serverRecoveryEpoch", "1")
                .put("serviceMode", "RESTORING"), "1")

            assertThrows(IllegalStateException::class.java) { f.import() }

            f.assertNoImport()
            assertEquals(0, f.server.requestCount)
        }
    }

    @Test fun recoveryFenceObservedDuringDownloadPreventsImport() = runBlocking<Unit>(Dispatchers.IO) {
        HandoverDownloadFixture(folder.root).use { f ->
            f.afterResponse = { if (it.contains("/content/availability?")) RecoveryProtection(f.local.db)
                .observe(JSONObject().put("serverRecoveryEpoch", "1").put("serviceMode", "RESTORING"), "1") }

            assertThrows(IllegalStateException::class.java) { f.import() }

            f.assertNoImport()
        }
    }

    private fun rejectsChangedVersion(field: String) {
        HandoverDownloadFixture(folder.root).use { f ->
            f.afterResponse = { if (it.contains("/content/availability?")) f.auth = JSONObject(f.auth.toString())
                .put(field, "999") }

            assertThrows(IllegalStateException::class.java) { f.import() }

            f.assertNoImport()
        }
    }
}
