package com.acrylic.outbound.pda.rescue

/** Original ACTIVE facts remain intact after a supervisor disposition; totals exclude them. */
object RescueDispositions {
    fun excluded(snapshot: RescueSnapshot): Set<String> {
        val scans = snapshot.scans.associateBy { it.getString("captureId") }
        val ids = mutableSetOf<String>()
        val evidence = mutableSetOf<String>()
        snapshot.dispositions.forEach { row ->
            val id = row.getString("captureId")
            require(ids.add(id) && evidence.add(row.getString("evidenceId")))
            require(row.getString("evidenceId").isNotBlank())
            require(row.getString("action") in setOf("RELEASE", "QUARANTINE"))
            require(scans[id]?.getString("status") == "ACTIVE") { "原处置缺少原有效扫码" }
        }
        return ids
    }
}
