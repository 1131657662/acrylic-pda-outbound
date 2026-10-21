package com.acrylic.outbound.pda.ui

import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream
import org.junit.Assert.*
import org.junit.Test

class SupervisorMaterialFileTest {
    @Test fun completeUtf8MaterialPreservesOriginalWhitespaceAndClosesInput() {
        val original = "  {\"signedAuthorization\":\"synthetic.原件.signature\"}\r\n"
        var closed = false
        val input = object : ByteArrayInputStream(original.toByteArray(Charsets.UTF_8)) {
            override fun close() { closed = true; super.close() }
        }
        assertEquals(original, SupervisorMaterialFile.read(input)); assertTrue(closed)
    }

    @Test fun exactByteLimitIsAcceptedAndExcessIsRejected() {
        val exact = "a".repeat(SupervisorMaterialFile.MAX_BYTES)
        assertEquals(exact, SupervisorMaterialFile.read(exact.byteInputStream()))
        assertThrows(IllegalStateException::class.java) { SupervisorMaterialFile.read((exact + "a").byteInputStream()) }
    }

    @Test fun invalidUtf8AndBlankMaterialAreRejected() {
        assertThrows(java.nio.charset.CharacterCodingException::class.java) {
            SupervisorMaterialFile.read(byteArrayOf(0xc3.toByte(), 0x28).inputStream())
        }
        assertThrows(IllegalStateException::class.java) { SupervisorMaterialFile.read(" \r\n".byteInputStream()) }
    }

    @Test fun readFailureClosesSourceAndDoesNotReturnPartialMaterial() {
        var closed = false
        val input = object : InputStream() {
            override fun read(): Int = throw IOException("synthetic failure")
            override fun close() { closed = true }
        }
        assertThrows(IOException::class.java) { SupervisorMaterialFile.read(input) }; assertTrue(closed)
    }
}
