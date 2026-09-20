package com.acrylic.outbound.pda.data

import com.acrylic.outbound.pda.submission.SubmissionRecords
import org.json.JSONObject

/** Both sources share work_device.nextEventSeq; every evidence path must use the same ordering. */
class DeviceAuditTrail(private val db: PdaDatabase) {
    fun page(device: String, after: Long, limit: Int = 200): List<JSONObject> {
        val formal = db.audit().page(device, after, limit).map(SubmissionRecords::event)
        val temporary = db.temporaryScans().audit(device, after, limit).map { it.json() }
        val rows = (formal + temporary).sortedBy { it.getString("localSeq").toLong() }.take(limit)
        check(rows.map { it.getString("id") }.distinct().size == rows.size) { "本设备操作记录ID重复，请保留资料核对" }
        return rows
    }
}
