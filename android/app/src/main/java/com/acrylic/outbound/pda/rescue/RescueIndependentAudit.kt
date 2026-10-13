package com.acrylic.outbound.pda.rescue

import com.acrylic.outbound.pda.security.TimeSample
import com.acrylic.outbound.pda.security.TrustedTime
import org.json.JSONObject
import java.time.Instant

/** Reject incomplete local audit/state projections before sealing a public copy. */
internal object RescueIndependentAudit {
    private val types = setOf("CREATE_PLANNED", "INDEPENDENT_STARTED", "INDEPENDENT_RECEIVED", "INDEPENDENT_WAITING_CONFIRM",
        "INDEPENDENT_CONFIRM_REQUESTED", "INDEPENDENT_INVALID", "INDEPENDENT_SAVED", "INDEPENDENT_VOID",
        "INDEPENDENT_BUSINESS_REJECTED", "INDEPENDENT_FAILURE_ACKNOWLEDGED", "INDEPENDENT_DISCARDED")
    fun verify(snapshot: RescueSnapshot, source: JSONObject) {
        state(snapshot)
        val id = source.getJSONObject("task").getString("id")
        val credential = source.getJSONObject("credential")
        check(snapshot.events.isNotEmpty() && snapshot.events.first().getString("type") == "CREATE_PLANNED")
        snapshot.events.forEachIndexed { index, row ->
            check(row.getString("taskId") == id && row.getLong("sequence") == index + 1L && row.getLong("orderPosition") == index + 1L)
            check(row.getString("type") in types); RescuePublicEvidence.verify(JSONObject(row.getString("detail")))
            val trusted = TrustedTime.now(source.getJSONObject("timeAnchor"), TimeSample(row.getLong("elapsed"), row.getLong("wall"), row.getInt("boot")))
            check(trusted >= Instant.parse(credential.getString("issuedAt")) && trusted < Instant.parse(credential.getString("expiresAt")))
        }
        check(snapshot.events.count { it.getString("type") == "CREATE_PLANNED" } == 1)
        check(snapshot.events.count { it.getString("type") == "INDEPENDENT_STARTED" } == if (snapshot.order.getString("state") == "SCANNING") 1 else 0)
        originalEvents(snapshot, "INDEPENDENT_RECEIVED", snapshot.captures.map { it.getString("id") })
        originalEvents(snapshot, "INDEPENDENT_SAVED", snapshot.scans.map { it.getString("captureId") })
        originalEvents(snapshot, "INDEPENDENT_VOID", snapshot.scans.filter { it.getString("status") == "VOID" }.map { it.getString("captureId") })
    }
    private fun originalEvents(snapshot: RescueSnapshot, type: String, ids: List<String>) {
        val actual = snapshot.events.filter { it.getString("type") == type }.map { JSONObject(it.getString("detail")).getString("captureId") }
        check(actual.size == ids.size && actual.toSet() == ids.toSet())
    }
    private fun state(snapshot: RescueSnapshot) {
        val state = JSONObject(snapshot.originals.getValue("independent-state.json").toString(Charsets.UTF_8))
        check(state.getString("taskId") == snapshot.order.getString("localOrderId"))
        check(state.getString("state") == snapshot.order.getString("state") && state.getString("state") in setOf("PLANNED", "SCANNING"))
        check(state.getString("state") != "PLANNED" || snapshot.captures.isEmpty())
        check(snapshot.dispositions.isEmpty())
        check(snapshot.captures.filter { it.getString("state") == "COMMITTED" }.map { it.getString("id") }.toSet() ==
            snapshot.scans.map { it.getString("captureId") }.toSet())
        val facts = RescueFacts.summary(snapshot)
        check(state.getLong("actualCount") == facts.getLong("active") && state.getLong("weightGrams") == facts.getLong("weightGrams"))
        check(state.getLong("nextScanSeq") == snapshot.captures.size + 1L && state.getLong("nextEventSeq") == snapshot.events.size + 1L)
    }
}
