package com.acrylic.outbound.pda.ui

import com.acrylic.outbound.pda.independent.*

internal object IndependentScreenFixture {
    fun ui(vehicle: String = "合成车号") : IndependentWorkUi {
        val task = IndependentTask(id = "task", importId = "import", skuSha256 = "synthetic-sha",
            credentialId = "credential", signedCredential = "", trustedKeyJson = "", timeAnchorJson = "",
            localSessionId = "session", operatorId = "operator", deviceId = "device", warehouseId = "warehouse",
            recoveryEpoch = "epoch", vehicleNo = vehicle, reason = "测试独立采集", plannedTotal = 100,
            commandSha256 = "synthetic-command", createdWall = 1, createdElapsed = 1, createdBoot = 1)
        return IndependentWorkUi(task = IndependentTaskView(task,
            listOf(IndependentTaskLine("task", "003", "型号", "合成品名", 100)),
            IndependentTaskState("task", actualCount = 1, weightGrams = 1250), emptyList()), ready = true)
    }

    fun candidate(state: String) = IndependentCaptureView(
        IndependentCapture(id = "capture", taskId = "task", sequence = 2, deviceId = "device", deviceSequence = 2,
            rawInput = "003A0000001", barcode = "003A0000001", inputSource = "MANUAL", wall = 1, elapsed = 1,
            boot = 1, capturedTrustedAt = "synthetic-time"),
        IndependentCaptureState("capture", state = state)
    )
}
