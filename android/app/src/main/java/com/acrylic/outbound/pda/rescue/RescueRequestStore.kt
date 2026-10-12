package com.acrylic.outbound.pda.rescue

import org.json.JSONObject

/** Save intent before send; responses cannot replace the original text, signature or key. */
internal class RescueRequestStore(private val storage: RescueCredentialStorage) {
    @Synchronized fun prepare(intent: RescueRequestIntent): RescueRequestIntent {
        val root = storage.read() ?: JSONObject().put("entries", JSONObject())
        val entries = root.getJSONObject("entries")
        val pending = entries.keys().asSequence().map { decode(entries.getJSONObject(it)) }.firstOrNull {
            it.receipt == null && it.target == intent.target && listOf("deviceId", "operatorId", "warehouseId").all { field ->
                it.document.getString(field) == intent.document.getString(field)
            }
        }
        if (pending != null) {
            check(pending.document.getString("rescuerId") == intent.document.getString("rescuerId") &&
                pending.document.getString("reason") == intent.document.getString("reason") && pending.keySha256 == intent.keySha256) { "此原单已有未确认申请，请先按原键重试或查询" }
            return pending
        }
        check(entries.length() < 20) { "本机救援申请超过20份；已有原件保留" }
        check(!entries.has(intent.key)) { "原申请键已存在，不替换原件" }
        entries.put(intent.key, encode(intent)); storage.write(root)
        return decode(checkNotNull(storage.read()).getJSONObject("entries").getJSONObject(intent.key)).also { check(it == intent) }
    }
    @Synchronized fun list(identity: RescueReadIdentity): List<RescueRequestIntent> {
        val entries = storage.read()?.getJSONObject("entries") ?: return emptyList()
        return entries.keys().asSequence().map { decode(entries.getJSONObject(it)) }.filter { it.belongs(identity) }.toList()
    }
    @Synchronized fun get(key: String): RescueRequestIntent = decode(checkNotNull(storage.read()).getJSONObject("entries").getJSONObject(key))
    @Synchronized fun receipt(key: String, response: JSONObject): RescueRequestIntent {
        val root = checkNotNull(storage.read()); val entry = root.getJSONObject("entries").getJSONObject(key)
        val intent = decode(entry)
        RescueRequestDocument.uuid(response.getString("requestId"))
        check(response.getString("requestSha256") == RescueBundle.sha(intent.originalText.toByteArray(Charsets.UTF_8))) { "申请回执摘要与原件不一致" }
        check(intent.receipt == null || sameReceipt(JSONObject(intent.receipt), response)) { "原申请回执不一致，不替换原件" }
        if (intent.receipt == null) entry.put("receipt", response.toString())
        storage.write(root); return get(key)
    }
    @Synchronized fun material(key: String, response: JSONObject): RescueRequestIntent {
        val root = checkNotNull(storage.read()); val entry = root.getJSONObject("entries").getJSONObject(key)
        val intent = decode(entry); val receipt = JSONObject(checkNotNull(intent.receipt))
        check(sameReceipt(receipt, response.getJSONObject("request")) && response.getString("originalText") == intent.originalText &&
            response.getString("deviceSignature") == intent.signature && matchingScope(intent, response)) { "服务端材料与原申请不一致" }
        check(response.getString("status") in setOf("REQUESTED", "AUTHORIZED", "EXPIRED", "REVOKED") && response.get("localDeletionAllowed") == false)
        intent.material?.let { previous ->
            val old = JSONObject(previous)
            for (field in listOf("authorization", "revocation")) if (!old.isNull(field))
                check(!response.isNull(field) && old.getJSONObject(field).toString() == response.getJSONObject(field).toString()) { "已取得的主管签名原件不替换或撤回" }
            check(old.getString("status") != "REVOKED" || response.getString("status") == "REVOKED") { "原撤销终态不复活" }
        }
        entry.put("material", response.toString()); storage.write(root); return get(key)
    }
    private fun matchingScope(intent: RescueRequestIntent, response: JSONObject): Boolean {
        val scope = response.getJSONArray("orderScope")
        return scope.length() == 1 && scope.getJSONObject(0).let {
            it.keys().asSequence().toSet() == setOf("source", "localOrderId") &&
                it.getString("source") == intent.target.source && it.getString("localOrderId") == intent.target.localOrderId
        }
    }
    private fun sameReceipt(a: JSONObject, b: JSONObject) = a.getString("requestId") == b.getString("requestId") && a.getString("requestSha256") == b.getString("requestSha256")
    private fun encode(intent: RescueRequestIntent) = intent.body.put("keySha256", intent.keySha256).put("epoch", intent.epoch)
        .put("receipt", intent.receipt ?: JSONObject.NULL).put("material", intent.material ?: JSONObject.NULL)
    private fun decode(row: JSONObject) = RescueRequestIntent(row.getString("originalText"), row.getString("signature"),
        row.getString("keySha256"), row.getString("epoch"), if (row.isNull("receipt")) null else row.getString("receipt"),
        if (row.isNull("material")) null else row.getString("material"))
}
