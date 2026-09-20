package com.acrylic.outbound.pda.temporary

import com.acrylic.outbound.pda.security.JwtVerifier
import com.acrylic.outbound.pda.submission.SubmissionJson
import org.json.*

object TemporaryPlanDigest {
    fun digest(draft: TemporaryDraft, lines: List<TemporaryLine>): String {
        return JwtVerifier.sha(SubmissionJson.canonical(body(draft, lines)).toByteArray(Charsets.UTF_8))
    }
    fun body(draft: TemporaryDraft, lines: List<TemporaryLine>): JSONObject =
        JSONObject().put("clientOrderId", draft.clientOrderId).put("creatorDeviceId", draft.creatorDeviceId)
            .put("authorizationId", draft.authorizationId).put("revision", draft.revision.toString())
            .put("vehicleNo", draft.vehicleNo).put("containerNo", draft.containerNo).put("reason", draft.reason)
            .put("lines", JSONArray(lines.sortedBy { it.deliveryCode }.map {
                JSONObject().put("deliveryCode", it.deliveryCode).put("productId", it.productId).put("model", it.model)
                    .put("name", it.name).put("plannedCount", it.plannedCount.toString())
            }))
}
