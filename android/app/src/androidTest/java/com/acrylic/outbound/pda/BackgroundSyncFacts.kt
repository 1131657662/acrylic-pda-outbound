package com.acrylic.outbound.pda

import com.acrylic.outbound.pda.data.PreparedOrder
import com.acrylic.outbound.pda.security.DeviceKeys
import com.acrylic.outbound.pda.security.SessionVault
import com.acrylic.outbound.pda.work.WorkEvent
import com.acrylic.outbound.pda.work.WorkOrder
import org.json.JSONObject
import org.junit.Assert.*

/** Deliberately synthetic transport evidence, not a signed or executable business assignment. */
internal class BackgroundSyncFacts(private val app: PdaApplication) {
    fun seed(): JSONObject {
        check(app.packageName == "com.acrylic.outbound.pda.debug.r05background")
        app.session.settings.bind(BackgroundSyncServer.URL, BackgroundSyncServer.DEVICE, DeviceKeys().publicKey(true))
        val auth = JSONObject().put("userId", "synthetic-user").put("deviceId", BackgroundSyncServer.DEVICE)
            .put("warehouseId", BackgroundSyncServer.WAREHOUSE).put("serverRecoveryEpoch", "1")
            .put("localSessionId", "synthetic-login").put("accessToken", "synthetic-local-only")
        SessionVault(app).save(auth)
        app.database.runInTransaction {
            app.database.preparations().insertOrder(PreparedOrder("synthetic-assignment", "synthetic-order", "synthetic-user",
                BackgroundSyncServer.DEVICE, "synthetic-bundle", "synthetic-hash", "{\"serverRecoveryEpoch\":\"1\"}",
                "{}", "{}", "{}", "synthetic-not-signed", "2026-09-19T00:00:00Z", 0))
            app.database.work().insertOrder(WorkOrder("synthetic-assignment", "synthetic-order", "TEST", 0, 1))
            app.database.work().insertEvent(event())
        }
        return auth
    }

    fun assertRetained() {
        assertEquals(listOf(event()), app.database.work().deviceEvents(BackgroundSyncServer.DEVICE))
        assertEquals("SCANNING", app.database.work().order("synthetic-assignment")!!.state)
        assertEquals("synthetic-not-signed", app.database.preparations().order("synthetic-assignment")!!.signedManifest)
    }

    private fun event() = WorkEvent("synthetic-event", "synthetic-assignment", null, BackgroundSyncServer.DEVICE,
        "synthetic-user", 1, "TEST_EVENT", "synthetic-local-only", 1000, 1000, 1)
}
