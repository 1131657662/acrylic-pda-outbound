package com.acrylic.outbound.pda.ui

import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction

/** Reads untrusted material only; authority is checked by the existing import flow. */
internal object SupervisorMaterialFile {
    const val MAX_BYTES = 131_072

    fun read(input: InputStream): String {
        val bytes = input.use { source ->
            val output = ByteArrayOutputStream(); val buffer = ByteArray(8192)
            while (true) {
                val count = source.read(buffer)
                if (count < 0) break
                check(count > 0 && output.size() + count <= MAX_BYTES) { "主管材料过大或读取未完成" }
                output.write(buffer, 0, count)
            }
            output.toByteArray()
        }
        val decoder = Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
        return decoder.decode(ByteBuffer.wrap(bytes)).toString().also {
            check(it.isNotBlank()) { "主管材料为空" }
        }
    }
}
