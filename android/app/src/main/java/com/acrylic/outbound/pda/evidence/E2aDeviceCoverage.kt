package com.acrylic.outbound.pda.evidence

import com.acrylic.outbound.pda.data.PdaDatabase

class E2aDeviceCoverage(private val db: PdaDatabase) {
    data class Boundary(val captureHighWatermark: Long, val auditThroughSeq: Long)

    fun verify(device: String): Boundary {
        check(db.inTransaction()) { "须在停止事务中核验全设备前缀" }
        val counter = checkNotNull(db.work().device(device)) { "原设备序号不存在" }
        check(counter.nextCaptureSeq > 0 && counter.nextEventSeq > 1)
        val capture = Math.subtractExact(counter.nextCaptureSeq, 1)
        val audit = Math.subtractExact(counter.nextEventSeq, 1)
        check(capture <= 9007199254740991L && audit <= 9007199254740991L)
        checkPrefix(capture) { after -> db.audit().captureSequences(device, after) }
        checkPrefix(audit) { after -> db.audit().eventSequences(device, after) }
        return Boundary(capture, audit)
    }

    private fun checkPrefix(through: Long, page: (Long) -> List<Long>) {
        var previous = 0L
        while (previous < through) {
            val rows = page(previous)
            check(rows.isNotEmpty() && rows.size <= 256) { "本机原序列缺段" }
            for (sequence in rows) {
                check(sequence == Math.addExact(previous, 1) && sequence <= through) { "本机原序列重复或缺段" }
                previous = sequence
            }
        }
        check(page(previous).isEmpty()) { "本机原序列超过设备高水位" }
    }
}
