package com.acrylic.m71demo.fixtures

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeout
import java.util.concurrent.atomic.AtomicReference

/** Wait for this exact test input to enter the scan queue before simulating Enter. */
class BenchmarkDelivery {
    private data class Pending(val id: String, val received: CompletableDeferred<Unit>)
    private val waiting = AtomicReference<Pending?>()

    suspend fun sendAndAwait(id: String, send: () -> Unit) {
        val pending = Pending(id, CompletableDeferred())
        check(waiting.compareAndSet(null, pending)) { "已有测试输入等待接收" }
        try {
            send()
            withTimeout(2000) { pending.received.await() }
        } finally { waiting.compareAndSet(pending, null) }
    }

    fun accepted(id: String) {
        waiting.get()?.takeIf { it.id == id }?.received?.complete(Unit)
    }
}
