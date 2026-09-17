package com.acrylic.m71demo.device

import com.acrylic.m71demo.data.DemoDatabase

/** Measures existing transaction boundaries without changing their durability or exceptions. */
object TransactionTiming {
    @Volatile var observer: ((String, String, Long, Long, Long) -> Unit)? = null

    fun run(db: DemoDatabase, stage: String, id: String, work: () -> Unit) {
        val sink = observer
        if (sink == null) { db.runInTransaction(work); return }
        val start = android.os.SystemClock.elapsedRealtimeNanos()
        var bodyStart = start
        var bodyEnd = start
        db.runInTransaction {
            bodyStart = android.os.SystemClock.elapsedRealtimeNanos()
            try { work() } finally { bodyEnd = android.os.SystemClock.elapsedRealtimeNanos() }
        }
        val end = android.os.SystemClock.elapsedRealtimeNanos()
        runCatching { sink(stage, id, bodyStart - start, bodyEnd - bodyStart, end - bodyEnd) }
    }
}
