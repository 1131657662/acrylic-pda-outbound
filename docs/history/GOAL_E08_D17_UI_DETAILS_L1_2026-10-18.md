# E08/D17/U04 TEMP 明细页独立 Android L1（2026-10-18）

## 范围与结论

本切片验证有效范围内的 TEMP `TemporaryDetailsActivity` 原记录页，覆盖分页、筛选、完整条码定位、键盘与安全区、资格说明、跨身份拒绝、返回和受控辅助入口。输入是既有 `host04-input` 的合成隔离快照；不调用接收确认、激活、多跳或换机承接链。run02 在新隔离 `Pixel_6` 上通过 `OK (1 test)`，计作 U04/D17 的合成 Android UI L1 开发备份；不计 D17 整体、E06 L2、D18 L3、第8节闸门或总目标。

## 路径、影响与复用

| 路径 | 影响层 | 本次验证 | 复用依据 |
| --- | --- | --- | --- |
| `android/app/src/main/java/com/acrylic/outbound/pda/ui/TemporaryDetailsActivity.kt` | Android TEMP 明细页面、筛选/定位/分页/身份围栏 | 独立 instrumentation `TemporaryDetailsDeviceTest` | SHA-256 `8531C383833C8C65293B549E50E357504540B4762272723252085FFE63025ABF` |
| `android/app/src/main/java/com/acrylic/outbound/pda/ui/TemporaryDetailsText.kt` | 原记录只读文本 | 同上，实际首屏/末页/筛选/键盘回收 | SHA-256 `7D81F6E843A17EA02985EDFB8C30BDC053B929F99A6E3E7CA93768B32E7162EA` |
| `android/app/src/androidTest/java/com/acrylic/outbound/pda/TemporaryDetailsDeviceTest.kt` | 设备行为断言 | run02 1/1 通过 | SHA-256 `DBC970E8C96B33F8B841FE2E31C5747B6327208905146ACBAC9A7C6A4FC9840D` |
| `android/app/src/androidTest/java/com/acrylic/outbound/pda/TemporaryDetailsDeviceUi.kt` | 安全区、滚动、视觉回收 | run02 首屏/末页/筛选/键盘/资格对话框非空证据 | SHA-256 `9199BD8A217B9352BAEE039ABE704895CBAC94365F4E41EDBDE2A00CE2EF05D6` |
| `android/app/src/test/java/com/acrylic/outbound/pda/temporary/TemporaryDetailsReaderTest.kt` | Room 只读 reader | 既有 host04 定向单测按路径+SHA复用 | SHA-256 `7B9AB67705FC1D76491E8E147656B522B2AE40B6C18B2D73CF9D63E7D576391B` |

## 输入、命令与产物

输入绑定自 `.local/goal-20261016-resume/temporary-details01/host04-input/`：

- `identity.json`: `D0E9B51E0ECAF269830AC550997EA6E4D8961E82BCC9D932F5DDDDFDEF71DE6D`
- `synthetic-session.json`: `76C136DD828563BB300CEA652B560E0D7A2D5EC8DCD91025C0A521878042C43D`
- `synthetic-temporary.db`: `8D8469A0A98AEA02983EA699FEB1B3B23370C16E23C6EFB7D946FCB6B6C9B5FC`

run02 runner 为 `.local/goal-20261018-resume/d17-ui-details-run02.py`，使用新数据目录 `.local/goal-20261018-resume/d17-ui-details-run02/pixel6-data`、端口 `5584`，安装当前 Debug APK 与 AndroidTest APK 后执行：

```text
adb -s emulator-5584 shell am instrument -w -r -e temporaryDetails synthetic-1703 -e evidenceRun d17-ui-details-run02 -e class com.acrylic.outbound.pda.TemporaryDetailsDeviceTest com.acrylic.outbound.pda.debug.test/androidx.test.runner.AndroidJUnitRunner
```

APK SHA-256：`app-debug.apk`=`0D5BD1B5581C16EDF46900263280A67D01DC1BF8CF351F3FB7F0EF9A35B799ED`；`app-debug-androidTest.apk`=`1724F25B2892D7B4F670EC66DFC1B8F1E740994FCD44C64040E3C4A7F84AE548`。两次 streamed install 均为 `Success`。

## 实际结果

- instrumentation exit `0`，`OK (1 test)`；覆盖 1/20、21/20、41/10 分页与倒页重建、四类筛选、原序号/完整条码/无匹配定位、输入法可见、合成错误身份拒绝后恢复、资格说明、返回及取消/救援入口受控状态。
- 输入 DB 与设备回收 `db-after-device.db` SHA 均为 `8D8469A0A98AEA02983EA699FEB1B3B23370C16E23C6EFB7D946FCB6B6C9B5FC`；两份 `PRAGMA integrity_check` 为 `ok`，逐表列和值完全相等。非空事实包括 `temporary_capture` 50 行、`temporary_scan` 46 行、`temporary_event` 193 行、`temporary_line` 2 行、`temporary_part_chunk` 5 行及 1 行 `temporary_draft`。
- `.local/goal-20261018-resume/d17-ui-details-run02/evidence.tar` SHA `3893E76872DED2051B669BC0AB9A1F090FF8130A0078A1F82D33CBEB8C0B5DF9`，内含非空 `first/last/pending/keyboard/qualification` PNG 与文字证据；已抽查首屏和键盘定位画面，文字未被横向截断，安全区与输入控件可见。
- run02 完成后 AVD/端口 `5584/5585` 已停止；USB `22D2910042` 未安装、未启动、未操作。共享 MySQL、D:\jiachengfangzhi Vite/uvicorn 和既有 AVD 未连接或停止。

## 失败与限制

首次 runner `.local/goal-20261018-resume/d17-ui-details-run01/` 在安装前因未预创建 `-datadir`，日志含 `Invalid -datadir directory`，180 秒后超时；目标进程按命令行核对后停止，结果和日志保留。预创建目录后 run02 成功，未修改产品源码。该环境失败不计为产品业务失败，也不清除既有 run03 失败证据。

本切片使用合成身份、合成输入、Debug 签名和隔离 AVD，不能替代原 PDA、原身份、真实 HTTP/MySQL、Chrome、Python 消费、非空迁移、原模拟器或恢复全链。D17 整体、E06 L2、D18 L3、第8节闸门和总目标继续未完成。

