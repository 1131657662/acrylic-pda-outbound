# E08/D17/U04 TEMP 明细页键盘可达性修复 L1（2026-10-18）

## 范围与结论

本切片修复 `TemporaryDetailsActivity` 在 200% 字号、键盘可见时定位按钮不可达的问题，并重新执行合成隔离 Android L1。根 `ScrollView` 只消费系统栏 inset，内容容器增加 IME 与底部系统栏的差值作为滚动 spacer；定位按钮改为单行“定位原记录”，完整条码取末次原记录移入输入说明。未恢复接收确认、激活、多跳或换机承接链。

run09（480×800、density 240、font scale 2.0）和 run10（同一视口、font scale 1.0）均实际运行 `TemporaryDetailsDeviceTest` 并以 instrument 原文 `OK (1 test)` 判定通过。结果仍只计合成 Android UI L1 开发备份，不计 D17 整体、E06 L2、D18 L3、第8节闸门或总目标。

## 改动路径与直接验证

| 路径 | 影响层 | 直接验证 | SHA-256 |
| --- | --- | --- | --- |
| `android/app/src/main/java/com/acrylic/outbound/pda/ui/TemporaryDetailsActivity.kt` | TEMP 明细页系统栏/IME inset 与滚动内容 | run09/run10 instrument | `F7DAD1A82D939BBB3574BB03BEF600DBB25573381257F96DC753C4F213A4E916` |
| `android/app/src/main/res/layout/activity_temporary_details.xml` | 内容容器 ID、定位说明与单行按钮 | run09/run10 视觉/可达性断言 | `257616DBF9EE1752003B50C9E3D931682699858BF1906E08EF849E3068F79072` |
| `android/app/src/androidTest/java/com/acrylic/outbound/pda/TemporaryDetailsDeviceUi.kt` | 安全区、IME、滚动和文字断言 | run09/run10 设备辅助器 | `514BB565747C65D54D139A7C5A4093F632F1E7F9AEA1540870715FEABFFB527C` |

测试主体 `TemporaryDetailsDeviceTest.kt` 未改，SHA `DBC970E8C96B33F8B841FE2E31C5747B6327208905146ACBAC9A7C6A4FC9840D`。

## 构建与设备结果

- `:app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug --no-daemon`：BUILD SUCCESSFUL。
- run09：新隔离 Pixel_6、端口 5594、480×800/density 240/font scale 2.0；分页、筛选、原序号/完整条码定位、IME 下定位、无匹配、身份拒绝恢复、资格说明、返回及受控取消/救援入口通过。证据 `evidence.tar` SHA `14AF9A24D730CB69C32995E7D92F35957B00D2E7AF2AEACE439CE3FA776F2FE2`。
- run10：新隔离 Pixel_6、端口 5596、480×800/density 240/font scale 1.0；同一行为回归通过。证据 `evidence.tar` SHA `F8330FF473C22525BE10ED63545797C77A89048946A091331EC1BED586237D35`。
- 两次均为 Debug 测试包，streamed install 成功；USB `22D2910042` 未安装、未操作，目标 AVD 与端口已停止。
- APK SHA：`app-debug.apk`=`83452A516EA39E5EBF70B0D81C08CA975999B9DAA03CADDD34EB2AECC13031AC`；`app-debug-androidTest.apk`=`FCB6A302EC994091713F7301EC962A4D5AAEB88791998428D90E5C8A5117AC2D`。

## 数据与失败保全

- 输入 `synthetic-temporary.db` 与两次 `db-after-device.db` 均为 `8D8469A0A98AEA02983EA699FEB1B3B23370C16E23C6EFB7D946FCB6B6C9B5FC`。`sqlite-verify.json`（SHA `B24D1D6F64B0C5F8D2775EE5E0B28D8C4C8B934ABAA777F52BA64B7B98048E2D`）记录两份库 `integrity_check=ok`、表集合/计数/逐表行哈希/schema 全相等；12 张非空表包含 `temporary_capture` 50 行、`temporary_scan` 46 行、`temporary_event` 193 行、`temporary_part_chunk` 5 行及 2 行产品明细。
- run03、run04、run06、run07、run08 的失败证据均保留；run05 通过但未采用 480×800 视口，标为无效视口证据。run06/run07/run08 的失败分别保全了原断言、滚动状态和按钮两行高度事实。
- run09/run10 evidence 目录内所有文字/PNG 文件非空并已抽查键盘、首屏、末页、筛选和资格对话框。

## 限制

本切片使用合成身份、合成数据库、Debug 签名和隔离 AVD，未执行真实 HTTP/MySQL、Chrome、Python 外部消费、非空迁移、原模拟器、真机或恢复全链。D17 整体、E06 L2、D18 L3、第8节闸门和总目标继续未完成。
