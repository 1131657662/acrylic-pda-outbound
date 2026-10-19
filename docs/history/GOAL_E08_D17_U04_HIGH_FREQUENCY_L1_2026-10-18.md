# E08/D17/U04 高频首页导航 L1（2026-10-18）

## 范围与结论

本切片只验证继承的 `HighFrequencyOfflineDeviceTest` 中不需要临时准备包的首页导航测试。目标是确认“作业/数据/我的”入口在已登录产品状态下可达，并不恢复 recovered-confirmation、TEMP 接收确认、激活或换机承接链。

当前树为 `D:/acrylic-pda-outbound`，分支 `codex/development`，HEAD 与开发远端均为 `93710ec5cff979a567654084c80abe089e9a73b7`。本聊天是唯一写者；USB `22D2910042` 只读枚举，未安装或操作。启动命令请求端口 5600 和 `.local/d17-high-frequency-run01/userdata`，但该 datadir 不存在，模拟器日志明确记录 `Invalid -datadir directory`；进程随后实际使用了默认 `C:\Users\qq113\.android\avd\Pixel_6.avd` 数据目录并已停机。

结果为 **L1 证据无效，不能计通过**：实际运行的默认 AVD 没有绑定设备/人员会话，`MainActivity` 按产品逻辑将 `navigation` 设为 `GONE`，所以 `workPage` 为零尺寸；Espresso 在第一个已登录页面断言处失败。由于请求的独立 datadir 启动失败，本次断言不能作为隔离设备上的产品验收，也不能通过伪造登录、直接显示导航或改写测试来补记通过。默认 AVD 的现状和本次失败日志保留，不清除或回滚。

## 路径与验证

| 路径 | 影响层 | 验证/处置 |
| --- | --- | --- |
| `android/app/src/androidTest/java/com/acrylic/outbound/pda/HighFrequencyOfflineDeviceTest.kt` | U01/U04 Android 首页与高频入口消费者 | `compileDebugAndroidTestKotlin` 成功；首页导航 instrumentation 1 项实际执行但因未登录前置失败；包含扫码/暂停/恢复的后续测试未运行 |
| `android/app/src/main/java/com/acrylic/outbound/pda/ui/MainActivity.kt` | 登录态导航可见性 | 源码逻辑明确：`loggedIn=false` 时 `navigation=GONE`；本轮没有修改 |
| Debug APK / AndroidTest APK | 客户端构建 | `assembleDebug` 与 `assembleDebugAndroidTest` 成功；APK 哈希见本报告末尾；构建本身不构成隔离设备业务验收 |

实际命令：

```text
gradlew.bat -p android :app:compileDebugAndroidTestKotlin --no-daemon --console=plain
gradlew.bat -p android :app:assembleDebug :app:assembleDebugAndroidTest --no-daemon --console=plain
adb -s emulator-5600 shell am instrument -w -r -e highFrequency true -e evidenceRun d17-high-frequency-run01 -e class com.acrylic.outbound.pda.HighFrequencyOfflineDeviceTest#realHomeNavigationKeepsMaintenanceOutsideWorkPage com.acrylic.outbound.pda.debug.test/androidx.test.runner.AndroidJUnitRunner
```

第一次编译使用新 D 盘 `GRADLE_USER_HOME`，因缺少 Kotlin DSL `metadata.bin` 在进入编译前失败；切回现有共享 Gradle 依赖、仅把 TEMP/TMP 放在 D 盘后复验成功。该运行器失败保留在本轮命令输出记录中。模拟器启动的 `-datadir` 前置失败原文保留在 `emulator.stdout.log`。

## 证据与解锁条件

- 失败原文：`.local/d17-high-frequency-run01/instrument-home.txt`，SHA-256 `10ABDEA1946D31FB00583C1365CD398A6E2D1CC1CE971FC0BF03608EA087AA02`。
- AVD 日志：`.local/d17-high-frequency-run01/emulator.stdout.log`；第 5 行为 `Invalid -datadir directory: D:\\acrylic-pda-outbound\\.local\\d17-high-frequency-run01\\userdata`，因此不能证明独立 datadir，实际设备序列 `EMULATOR36X6X11X0` 属于默认 `Pixel_6.avd` 数据目录。`emulator.stderr.log` 仅含启动器短日志；字号 `1.0`，硬键盘设置 `0`。
- 产品 APK：`android/app/build/outputs/apk/debug/app-debug.apk`，SHA-256 `83452A516EA39E5EBF70B0D81C08CA975999B9DAA03CADDD34EB2AECC13031AC`。
- 测试 APK：`android/app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk`，SHA-256 `FCB6A302EC994091713F7301EC962A4D5AAEB88791998428D90E5C8A5117AC2D`。
- 直接解锁条件：先创建并核验实际存在的 D 盘 datadir，再启动模拟器并确认启动日志无 `Invalid -datadir`，随后用可审计的设备绑定与人员登录或已确认身份的隔离准备包完成前置；不得复用本次默认 AVD、合成登录、旧 AVD 状态或 USB 真机替代。隔离事实确认后只重跑该首页测试，再决定是否进入扫码/暂停/恢复路径。

本切片没有真实 HTTP/MySQL、业务数据库、Chrome、Python 消费或迁移写入；本次隔离前置失效，故不计 U04、D17、E06 L2、D18 L3、第 8 节闸门或总目标通过。取消的确认/激活链继续保全且不入队。
