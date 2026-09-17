package com.acrylic.m71demo.ui

object ErrorText {
    fun describe(code: String): String = mapOf(
        "BARCODE_FORMAT" to "条码格式错误：应为11位大写字母或数字",
        "BARCODE_NOT_IN_SNAPSHOT" to "库存中没有此条码，请核对完整号码",
        "PRODUCT_MISMATCH" to "产品不属于本单计划",
        "BARCODE_ALREADY_OCCUPIED" to "重复扫码或已被其他测试单占用",
        "PLAN_EXCEEDED" to "该产品已满计划，本包未保存",
        "ORDER_TOTAL_EXCEEDED" to "订单总计划已满，本包未保存",
        "DISPATCH_CYCLE_CONFLICT" to "出库周期已改变，旧扫码不能升级",
        "AUTH_EXPIRED" to "48小时测试授权到期，本包未保存",
        "AUTH_REBOOT_REVIEW" to "设备重启，原授权需复核，本包未保存",
        "AUTH_CLOCK_CHANGED" to "设备时间异常，本包未保存",
        "AUTH_INVALID" to "测试授权无效，本包未保存",
        "AUTH_DEVICE_MISMATCH" to "原设备与授权不一致，本包未保存",
        "RECOVERY_EPOCH_MISMATCH" to "恢复代次改变，必须对账",
        "ORDER_NOT_SCANNING" to "订单已冻结，不能保存",
    )[code] ?: code
}
