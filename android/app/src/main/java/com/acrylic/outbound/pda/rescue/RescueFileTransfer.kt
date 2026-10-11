package com.acrylic.outbound.pda.rescue

import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest

/** Only byte-for-byte target readback can report a verified copy. No business status changes. */
object RescueFileTransfer {
    fun copy(source: File, target: Pair<() -> OutputStream?, () -> InputStream?>): String {
        check(source.isFile && source.length() in 1..RescueEnvelope.MAX_BYTES.toLong() + 64)
        val before = digest { source.inputStream() }
        source.inputStream().use { input ->
            checkNotNull(target.first()) { "目标文件不可写；原应急包保留" }.use { output -> input.copyTo(output); output.flush() }
        }
        val after = digest(target.second)
        check(before == after && before == digest { source.inputStream() }) { "目标回读不一致；不能当作完整副本，原包保留" }
        return before
    }
    fun digest(open: () -> InputStream?): String {
        val sha = MessageDigest.getInstance("SHA-256")
        var length = 0L
        checkNotNull(open()) { "文件不可回读；原应急包保留" }.use { input ->
            val buffer = ByteArray(65536)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                check(count != 0) { "文件回读没有进展" }
                length += count
                check(length <= RescueEnvelope.MAX_BYTES.toLong() + 64) { "目标文件超过上限" }
                sha.update(buffer, 0, count)
            }
        }
        check(length > 0) { "目标文件为空，不能报完成" }
        return sha.digest().joinToString("") { "%02x".format(it) }
    }
}
