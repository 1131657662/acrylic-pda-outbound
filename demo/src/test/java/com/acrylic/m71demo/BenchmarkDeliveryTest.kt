package com.acrylic.m71demo

import com.acrylic.m71demo.fixtures.BenchmarkDelivery
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class BenchmarkDeliveryTest {
    @Test fun delayedBroadcastCannotBeConfirmedBeforeItsOwnReception() = runBlocking {
        val gate = BenchmarkDelivery()
        val pending = async(start = CoroutineStart.UNDISPATCHED) { gate.sendAndAwait("current") {} }
        gate.accepted("previous")
        yield()
        assertFalse(pending.isCompleted)
        gate.accepted("current")
        pending.await()
        assertTrue(pending.isCompleted)
    }

    @Test fun sendFailureDoesNotLeaveTheNextRunWaitingOnOldInput() = runBlocking {
        val gate = BenchmarkDelivery()
        try {
            gate.sendAndAwait("failed") { throw SecurityException("protected broadcast") }
            fail("Expected send failure")
        } catch (_: SecurityException) { }
        gate.sendAndAwait("next") { gate.accepted("next") }
    }

    @Test fun cancelledRunCannotBorrowALaterConfirmation() = runBlocking {
        val gate = BenchmarkDelivery()
        val pending = launch(start = CoroutineStart.UNDISPATCHED) { gate.sendAndAwait("cancelled") {} }
        pending.cancelAndJoin()
        gate.accepted("cancelled")
        gate.sendAndAwait("new") { gate.accepted("new") }
    }
}
