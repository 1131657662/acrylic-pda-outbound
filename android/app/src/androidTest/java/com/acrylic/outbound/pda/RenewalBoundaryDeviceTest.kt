package com.acrylic.outbound.pda

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.acrylic.outbound.pda.data.PreparedOrder
import com.acrylic.outbound.pda.security.JwtVerifier
import com.acrylic.outbound.pda.sync.RecoveryProtection
import com.acrylic.outbound.pda.update.UpdateGate
import com.acrylic.outbound.pda.work.*
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant

@RunWith(AndroidJUnit4::class)
class RenewalBoundaryDeviceTest {
    private val app get() = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as PdaApplication

    @Test fun completedRenewalSurvivesRestartAndExpiryBoundary() = UpdateGate.business {
        val facts = RenewalFacts(app); facts.assertRestart("late-recovered")
        assertEquals(facts.read("late-recovered").getString("authorization"), facts.order.assignmentJson)
        assertEquals(2, JSONArray(facts.order.authorizationHistoryJson).length())
        val capture = facts.receive(facts.codes[1])
        assertEquals("COMMITTED", app.database.captures().get(capture.id)!!.state)
        assertEquals(JSONObject(facts.order.assignmentJson).getString("offlineAuthorizationId"), capture.context.authorizationId)
        assertNotEquals(facts.baseline.getString("firstAuthorization"), capture.context.authorizationId)
        assertEquals(2L, app.database.scans().total(facts.id).count)
        expiryBoundary(facts)
        facts.save("expiry", JSONObject().put("oldAndNewCaptureAuthorizationPreserved", true)
            .put("syntheticTimeBoundaryOnly", true).put("business", facts.business()))
    }

    private fun expiryBoundary(facts: RenewalFacts) {
        val assignment = JSONObject(facts.order.assignmentJson)
        val expires = Instant.parse(assignment.getString("expiresAt"))
        val auth = app.session.requireSession(); val key = JSONObject(facts.order.signingKeyJson)
        JwtVerifier.authorization(assignment, auth, key, expires.minusSeconds(1))
        val actual = SessionWorkAccess(app, app.session)
        val boundary = object : WorkAccess by actual {
            override fun authorize(order: PreparedOrder): Instant {
                actual.identity(order)
                JwtVerifier.authorization(JSONObject(order.assignmentJson), auth, key, expires)
                return expires
            }
        }
        val before = facts.business()
        assertThrows(IllegalStateException::class.java) {
            WorkReception(WorkStore(app.database, boundary)).receive(WorkInput(facts.id, facts.codes[0], "BROADCAST", actual.sample()))
        }
        assertEquals(before, facts.business())
    }

    @Test fun recoveryBarrierArrivingDuringRenewalPreservesLocalCredential() = UpdateGate.business {
        val facts = RenewalFacts(app); facts.assertRestart("expiry")
        RenewalProxy().use { proxy ->
            val original = facts.order; val before = facts.business()
            proxy.afterRenewal = {
                RecoveryProtection(app.database).observe(JSONObject().put("serverRecoveryEpoch", "1").put("serviceMode", "RECONCILING"))
            }
            assertThrows(IllegalStateException::class.java) { app.renewals.renew(original) }
            assertEquals(200, proxy.exchanges.single().getInt("status"))
            assertEquals(original, facts.order); assertEquals(before, facts.business())
            assertEquals("RECONCILING", app.database.sync().recovery()!!.mode)
            assertThrows(IllegalStateException::class.java) { app.renewals.renew(original) }
            assertEquals(1, proxy.exchanges.size)
            facts.save("barrier", JSONObject().put("exchange", proxy.exchanges.single())
                .put("lateAndInitialBarrierBlocked", true).put("business", before))
        }
    }
}
