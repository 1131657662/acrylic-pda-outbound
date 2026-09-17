package com.acrylic.m71demo.device

import android.os.Process
import javax.inject.*

@Singleton
class FaultGate @Inject constructor(private val telemetry: Telemetry) {
    @Volatile var point = "NONE"
    @Volatile var mode = "STORAGE_ERROR"

    fun hit(stage: String, captureId: String) {
        if (point != stage) return
        point = "NONE"
        telemetry.event("FAULT_INJECTED", mapOf("stage" to stage, "mode" to mode, "captureId" to captureId))
        if (mode == "KILL_PROCESS") {
            // Durable test witness; only executed for an armed fault, never during performance runs.
            val file = android.util.AtomicFile(java.io.File(telemetry.directory, "fault-$captureId-$stage.json"))
            val stream = file.startWrite()
            try {
                stream.write(org.json.JSONObject().put("stage", stage).put("captureId", captureId)
                    .put("mode", mode).put("pid", Process.myPid()).put("wallMs", System.currentTimeMillis())
                    .toString().toByteArray())
                file.finishWrite(stream)
            } catch (e: Exception) { file.failWrite(stream); throw e }
            Process.killProcess(Process.myPid())
        }
        throw java.io.IOException("测试故障：$stage；请恢复重试，不能删除数据")
    }
}
