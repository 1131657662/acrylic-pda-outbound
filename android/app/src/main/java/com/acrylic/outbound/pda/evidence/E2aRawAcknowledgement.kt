package com.acrylic.outbound.pda.evidence

import org.json.JSONObject

internal object E2aRawAcknowledgement {
    fun verify(request: LocalE2aRequest, data: JSONObject) {
        check(request.operation == "RAW" && data.get("status") == "STORED" && data.get("localDeletionAllowed") == false)
        val body = JSONObject(request.requestJson)
        body.keys().forEach { field ->
            val expected = body.get(field); val actual = data.get(field)
            check(if (expected is Number) (actual is Int || actual is Long) && actual.toString() == expected.toString()
                else actual == expected) { "原始材料存储确认不匹配：$field" }
        }
        E2aItemValidation.uuid(data.getString("objectId"))
        check(!data.has("signedReceipt"))
    }
}
