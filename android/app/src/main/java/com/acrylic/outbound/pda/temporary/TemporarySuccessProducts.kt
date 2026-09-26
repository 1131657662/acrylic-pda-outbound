package com.acrylic.outbound.pda.temporary

import org.json.JSONObject

internal object TemporarySuccessProducts {
    fun verify(body: JSONObject, receipt: JSONObject) {
        val plan = body.getJSONObject("temporaryOrder").getJSONArray("lines")
        val expected = sortedMapOf<String, Pair<Long, Long>>()
        for (i in 0 until plan.length()) {
            val code = plan.getJSONObject(i).get("deliveryCode") as String
            check(expected.put(code, 0L to 0L) == null)
        }
        val scans = body.getJSONArray("scans")
        val active = (0 until scans.length()).map { scans.getJSONObject(it) }.filter { it.get("status") == "ACTIVE" }
        check(active.isNotEmpty()) { "成功回执缺少有效装车原件" }
        val sequences = active.map { decimal(it, "orderScanSeq") }
        check(sequences.all { it > 0 } && sequences.zipWithNext().all { (a, b) -> a < b })
        for (scan in active) {
            val code = scan.get("deliveryCode") as String; val previous = checkNotNull(expected[code])
            val grams = decimal(scan, "weightGrams"); check(grams > 0)
            expected[code] = Math.addExact(previous.first, 1L) to Math.addExact(previous.second, grams)
        }
        check(expected.values.sumOf { it.first } == decimal(receipt, "actualCount"))
        check(expected.values.fold(0L) { total, item -> Math.addExact(total, item.second) } == decimal(receipt, "actualWeightGrams"))
        check(sequences.first() == decimal(receipt, "firstOrderScanSeq") && sequences.last() == decimal(receipt, "lastOrderScanSeq"))
        check(sequences.last() <= decimal(receipt, "captureHighWatermark"))
        check(items(receipt) == expected) { "成功分产品回执与原计划及有效包不一致" }
    }

    private fun items(receipt: JSONObject): Map<String, Pair<Long, Long>> {
        val result = sortedMapOf<String, Pair<Long, Long>>(); val items = receipt.getJSONArray("items")
        for (i in 0 until items.length()) {
            val item = items.getJSONObject(i)
            check(item.keys().asSequence().toSet() == setOf("deliveryCode", "actualCount", "actualWeightGrams"))
            val code = item.get("deliveryCode") as String
            check(result.put(code, decimal(item, "actualCount") to decimal(item, "actualWeightGrams")) == null) { "成功回执产品重复" }
        }
        return result
    }

    private fun decimal(value: JSONObject, field: String): Long {
        val raw = value.get(field) as String
        check(raw.matches(Regex("0|[1-9][0-9]*"))) { "成功回执整数格式无效：$field" }
        return raw.toLong()
    }
}
