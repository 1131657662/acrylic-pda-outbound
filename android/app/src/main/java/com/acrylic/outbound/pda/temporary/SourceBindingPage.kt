package com.acrylic.outbound.pda.temporary

import com.acrylic.outbound.pda.data.PdaDatabase
import org.json.JSONObject

/** Validates server projections inside the same Room transaction that installs the complete page. */
internal class SourceBindingPage(private val db: PdaDatabase) {
    fun install(data: JSONObject): Int {
        val items = data.getJSONArray("items"); val scopes = mutableSetOf<String>(); var installed = 0
        for (index in 0 until items.length()) {
            val row = items.getJSONObject(index)
            check(row.get("installRequired") is Boolean) { "来源绑定安装标记无效" }
            check(scopes.add(TemporaryVerifier.uuid(row.getString("scopeId")))) { "来源绑定重复" }
            if (!row.getBoolean("installRequired")) continue
            val next = binding(row); val prior = db.sourceBindings().get(next.scopeId)
            if (prior != null) requireForward(prior, next)
            if (prior == null || prior != next.copy(installedAt = prior.installedAt)) {
                db.sourceBindings().install(next); installed++
            }
        }
        return installed
    }

    private fun binding(row: JSONObject): LocalSourceBinding {
        for (key in listOf("scopeId", "clientOrderId", "canonicalOrderId", "mappingId")) TemporaryVerifier.uuid(row.getString(key))
        check(TemporaryVerifier.positive(row.getString("mappingVersion")) > 1 && row.getString("mappingOrigin") == "CONTROLLED_LINK") {
            "来源绑定缺少人工关联依据"
        }
        TemporaryVerifier.positive(row.getString("workRound"))
        check(row.getString("phase") in setOf("FROZEN", "CONTINUING", "COMPLETED", "CLOSED")) { "来源绑定阶段无效" }
        check(row.getString("orderNo").isNotBlank() && row.getString("orderNo") != "null") { "来源绑定抬头缺失" }
        return LocalSourceBinding(row.getString("scopeId"), row.getString("clientOrderId"), row.getString("canonicalOrderId"),
            row.getString("mappingVersion"), row.getString("mappingOrigin"), row.getString("workRound"),
            row.getString("phase"), row.getString("orderNo"), System.currentTimeMillis())
    }

    private fun requireForward(prior: LocalSourceBinding, next: LocalSourceBinding) {
        check(prior.clientOrderId == next.clientOrderId) { "来源绑定原来源已变化，原资料保留" }
        val oldVersion = TemporaryVerifier.positive(prior.mappingVersion)
        val version = TemporaryVerifier.positive(next.mappingVersion)
        check(version >= oldVersion && (version != oldVersion || prior.canonicalOrderId == next.canonicalOrderId)) {
            "人工关联版本倒退或同版本目标冲突"
        }
        val oldRound = TemporaryVerifier.positive(prior.workRound); val round = TemporaryVerifier.positive(next.workRound)
        check(round >= oldRound) { "来源绑定作业轮次倒退" }
        if (round == oldRound) {
            check(prior.phase !in setOf("COMPLETED", "CLOSED") || next.phase == prior.phase) { "来源绑定终态不能倒退" }
        }
    }
}
