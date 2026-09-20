package com.acrylic.outbound.pda

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.acrylic.outbound.pda.security.JwtVerifier
import com.acrylic.outbound.pda.update.UpdateGate
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Run each method separately with a host force-stop between phases; never reseed an existing app. */
@RunWith(AndroidJUnit4::class)
class RenewalRecoveryDeviceTest {
    private val app get() = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as PdaApplication

    @Test fun seedActiveWorkAndPendingProtection() = UpdateGate.business {
        val facts = RenewalFacts(app)
        check(!File(facts.directory, "baseline.json").exists()) { "Preserve existing renewal fixture" }
        val fixtureFile = File(app.filesDir, "e2e-fixture.json")
        val fixture = JSONObject(fixtureFile.readText())
        RenewalProxy().use { proxy ->
            val key = app.session.publicKey()
            val device = RenewalFixtureAdmin(fixture).use { it.register(key) }
            app.session.settings.bind(proxy.base, device, key)
            app.session.login(fixture.getString("username"), fixture.getString("password"))
            val listed = app.repository.list("pda/orders", "cursor").single { it.getString("id") == fixture.getString("orderId") }
            val order = app.repository.prepare(app.repository.claim(listed, "R05续期合成活动货堆")) {}
            val baseline = JSONObject(app.session.requireSession().toString())
            for (secret in listOf("accessToken", "refreshToken", "signingKey", "timeAnchor")) baseline.remove(secret)
            baseline.put("assignmentId", order.assignmentId).put("orderId", order.orderId).put("publicKey", key)
                .put("planSha256", JwtVerifier.sha(order.planJson.toByteArray())).put("inventorySha256", facts.inventoryHash())
            facts.save("baseline", baseline)
            facts.store.open(facts.id)
            val first = facts.receive(facts.codes[0])
            baseline.put("firstCapture", first.id).put("firstAuthorization", first.context.authorizationId)
            facts.save("baseline", baseline)
            pendingProtection(facts, proxy)
            assertTrue(fixtureFile.delete())
        }
    }

    private fun pendingProtection(facts: RenewalFacts, proxy: RenewalProxy) {
        val original = facts.order
        val manual = facts.receive(facts.codes[1], "MANUAL")
        assertEquals("WAITING_CONFIRM", app.database.captures().get(manual.id)!!.state)
        val before = facts.business()
        assertThrows(IllegalStateException::class.java) { app.renewals.renew(original) }
        assertEquals(before, facts.business()); assertEquals(original, facts.order)
        facts.processor.corrections.discard(manual.id, "验收明确放弃未确认手输")
        val invalid = facts.receive("INVALID")
        assertNotNull(app.database.captures().failure(facts.id))
        assertThrows(IllegalStateException::class.java) { app.renewals.renew(original) }
        assertTrue(proxy.exchanges.isEmpty())
        facts.processor.corrections.acknowledge(invalid.id)
        facts.save("seed", JSONObject().put("pendingAndFailureBlockedBeforeNetwork", true).put("business", facts.business()))
    }

    @Test fun committedResponseLostKeepsOriginalAuthorization() = UpdateGate.business {
        val facts = RenewalFacts(app); facts.assertRestart("seed")
        RenewalProxy().use { proxy ->
            val original = facts.order; val before = facts.business()
            proxy.loseResponse.set(true)
            assertThrows(java.io.IOException::class.java) { app.renewals.renew(original) }
            assertEquals(200, proxy.exchanges.single().getInt("status"))
            assertEquals(original, facts.order); assertEquals(before, facts.business())
            facts.save("lost", JSONObject().put("exchange", proxy.exchanges.single())
                .put("original", original.assignmentJson).put("business", before))
        }
    }

    @Test fun newProcessResumesOriginalRenewal() = UpdateGate.business {
        val facts = RenewalFacts(app); facts.assertRestart("lost")
        RenewalProxy().use { proxy ->
            val original = facts.order
            assertEquals(facts.read("lost").getString("original"), original.assignmentJson)
            val result = app.renewals.renew(original)
            assertRecovered(facts, proxy, "lost")
            assertEquals(1, JSONArray(result.authorizationHistoryJson).length())
            // A stale page may replay the old request, but must not replace a newer local chain.
            assertThrows(IllegalStateException::class.java) { app.renewals.renew(original) }
            assertEquals(result, facts.order)
            facts.save("recovered", JSONObject().put("authorization", result.assignmentJson)
                .put("business", facts.business()).put("staleReplayRejected", true))
        }
    }

    @Test fun latePendingInputDefersLocalAuthorizationCommit() = UpdateGate.business {
        val facts = RenewalFacts(app); facts.assertRestart("recovered")
        RenewalProxy().use { proxy ->
            val original = facts.order
            var pending = ""
            proxy.afterRenewal = { pending = facts.receive(facts.codes[1], "MANUAL").id }
            assertThrows(IllegalStateException::class.java) { app.renewals.renew(original) }
            assertEquals(200, proxy.exchanges.single().getInt("status"))
            assertEquals(original, facts.order)
            assertEquals("WAITING_CONFIRM", app.database.captures().get(pending)!!.state)
            assertEquals(1, app.database.captures().pendingCount(facts.id))
            facts.processor.corrections.discard(pending, "续期响应等待期间新增输入，核对后明确放弃")
            facts.save("late", JSONObject().put("exchange", proxy.exchanges.single())
                .put("original", original.assignmentJson).put("pendingCapture", pending).put("business", facts.business()))
        }
    }

    @Test fun newProcessRecoversDeferredCommit() = UpdateGate.business {
        val facts = RenewalFacts(app); facts.assertRestart("late")
        RenewalProxy().use { proxy ->
            assertEquals(facts.read("late").getString("original"), facts.order.assignmentJson)
            val renewed = app.renewals.renew(facts.order)
            assertRecovered(facts, proxy, "late")
            assertEquals(2, JSONArray(renewed.authorizationHistoryJson).length())
            facts.save("late-recovered", JSONObject().put("authorization", renewed.assignmentJson).put("business", facts.business()))
        }
    }

    private fun assertRecovered(facts: RenewalFacts, proxy: RenewalProxy, phase: String) {
        val saved = facts.read(phase); val old = saved.getJSONObject("exchange"); val next = proxy.exchanges.single()
        for (field in listOf("key", "body", "bodySha256")) assertEquals(field, old.getString(field), next.getString(field))
        assertEquals(old.getJSONObject("response").getJSONObject("data").toString(), facts.order.assignmentJson)
        assertEquals(saved.getString("business"), facts.business())
    }
}
