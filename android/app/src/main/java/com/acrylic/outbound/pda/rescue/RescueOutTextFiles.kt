package com.acrylic.outbound.pda.rescue

import android.content.Context
import android.net.Uri

class RescueOutTextFiles(context: Context) {
    private val resolver = context.contentResolver

    fun copy(snapshot: RescueSnapshot, uri: Uri, grant: RescueReadGrant): String {
        val bytes = RescueOutText.encode(snapshot)
        grant.recheck()
        grant.check(snapshot.order)
        resolver.openOutputStream(uri, "wt")?.use { it.write(bytes); it.flush() }
            ?: error("无法打开 out.txt 目标文件")
        grant.recheck()
        val actual = resolver.openInputStream(uri)?.use { it.readBytesBounded() }
            ?: error("无法回读 out.txt 目标文件")
        check(actual.contentEquals(bytes)) { "out.txt 写入与回读不一致" }
        return RescueBundle.sha(actual)
    }

    private fun java.io.InputStream.readBytesBounded(): ByteArray {
        val output = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (true) {
            val count = read(buffer)
            if (count < 0) return output.toByteArray()
            check(count > 0 && output.size() + count <= RescueEnvelope.MAX_BYTES) { "out.txt 文件过大或无法完整读取" }
            output.write(buffer, 0, count)
        }
    }
}
