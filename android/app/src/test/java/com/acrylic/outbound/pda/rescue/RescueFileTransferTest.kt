package com.acrylic.outbound.pda.rescue

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.OutputStream

class RescueFileTransferTest {
    @get:Rule val folder = TemporaryFolder()
    @Test fun targetReadbackMustMatchOriginalAndCannotMutateSource() {
        val source = folder.newFile().apply { writeBytes(ByteArray(100_000) { (it % 253).toByte() }) }
        val original = source.readBytes(); val output = ByteArrayOutputStream()
        val digest = RescueFileTransfer.copy(source, { output } to { ByteArrayInputStream(output.toByteArray()) })
        assertEquals(RescueBundle.sha(original), digest)
        assertArrayEquals(original, source.readBytes())
    }
    @Test fun readOnlyCorruptAndTruncatedTargetsNeverReportVerifiedCopy() {
        val source = folder.newFile().apply { writeText("synthetic-encrypted-package") }
        assertThrows(IllegalStateException::class.java) {
            RescueFileTransfer.copy(source, { null } to { ByteArrayInputStream(source.readBytes()) })
        }
        for (bad in listOf(byteArrayOf(), byteArrayOf(1), source.readBytes().dropLast(1).toByteArray())) {
            assertThrows(IllegalStateException::class.java) {
                RescueFileTransfer.copy(source, { ByteArrayOutputStream() } to { ByteArrayInputStream(bad) })
            }
        }
        assertEquals("synthetic-encrypted-package", source.readText())
    }
    @Test fun fullDiskOrDisconnectedMediaKeepsOriginalWithoutCallingReadback() {
        val source = folder.newFile().apply { writeBytes(ByteArray(100_000) { 7 }) }
        val before = source.readBytes(); var readCalled = false
        val failing = object : OutputStream() {
            override fun write(value: Int) { throw IOException("synthetic media disconnected") }
        }
        assertThrows(IOException::class.java) {
            RescueFileTransfer.copy(source, { failing } to { readCalled = true; ByteArrayInputStream(before) })
        }
        assertFalse(readCalled); assertArrayEquals(before, source.readBytes())
    }
}
