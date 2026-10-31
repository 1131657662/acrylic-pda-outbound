package com.acrylic.outbound.pda.preparation

import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class ContractFixtureTest {
    @Test
    fun readRejectsEmptyJsonObjectBeforeConsumerParsing() {
        withFixture("{}") { error ->
            assertTrue(error.message.orEmpty().contains("assignment"))
        }
    }

    @Test
    fun readRejectsFormalFixtureWithScalarRequiredField() {
        withFixture("{\"assignment\":true}") { error ->
            assertTrue(error.message.orEmpty().contains("assignment"))
        }
    }

    private fun withFixture(text: String, assertError: (IllegalStateException) -> Unit) {
        val file = File.createTempFile("contract-fixture-", ".json")
        val previous = System.getProperty("pda.contractFixture")
        try {
            file.writeText(text)
            System.setProperty("pda.contractFixture", file.absolutePath)
            val error = assertThrows(IllegalStateException::class.java) { ContractFixture.read() }
            assertError(error)
        } finally {
            if (previous == null) System.clearProperty("pda.contractFixture")
            else System.setProperty("pda.contractFixture", previous)
            file.delete()
        }
    }
}
