package com.acrylic.outbound.pda

import org.json.JSONArray
import org.json.JSONObject
import java.util.Base64

internal object R06A11DeviceSnapshot {
    fun capture(work: R06A11DeviceWork): JSONObject {
        val requests = JSONArray()
        for (row in work.requests()) requests.put(JSONObject()
            .put("key", row.originalKey).put("position", row.position).put("operation", row.operation)
            .put("requestSha256", row.requestSha256).put("requestJson", row.requestJson))
        val items = JSONArray()
        for (row in work.db.localDraftStops().page(work.id, 0)) items.put(JSONObject()
            .put("ordinal", row.ordinal).put("sha256", row.bytesSha256)
            .put("raw", Base64.getEncoder().encodeToString(row.rawBytes)))
        val events = work.db.temporaryScans().events(work.id).count { it.type == "LOCAL_EVIDENCE_STOP" }
        check(events == 1 && items.length() > 0 && requests.length() > 2)
        return JSONObject().put("requests", requests).put("items", items).put("stopEvents", events)
            .put("stop", work.stop().toString())
            .put("signature", checkNotNull(work.db.localDraftStopSignatures().get(work.id)).toString())
            .put("transfer", checkNotNull(work.db.localDraftTransfers().get(work.id)).toString())
    }
}
