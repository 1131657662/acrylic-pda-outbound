# E06 L2 Android 验收记录（未完成）

本记录只收录当前接续切片的 Android/Room 证据，不把单一 Android 运行结果扩写为 E06 L2、D18 L3 或总目标通过。输入为主树 `D:/acrylic-pda-outbound`、分支 `codex/development`、HEAD `601c7a40cd1a683310129bd1e1b53ef2cfc0b476`；执行前的断点见 [E06 L2 接续前看板](ORCHESTRATION_STATE_before_e06_l2_resume_2026-10-15.md)。

## 影响映射与必跑项

| 改动/输入 | 影响层 | 当次清单 | 证据 |
| --- | --- | --- | --- |
| 独立采集、任务、资料、救援与 Room 迁移消费者 | Android 主机测试/构建 | `cleanTestDebugUnitTest`、`testDebugUnitTest --tests '*independent.*'`、`assembleDebug`、`assembleDebugAndroidTest`、`lintDebug` | `.local/goal-20261015/e06-l2-android03/command.json`、`source-before.json` |
| E06 受影响组合的 Web 直接入口 | Chrome/服务 | 1540 Vite 预览及真实 Chrome | `.local/goal-20261014/preserve-1540-e06-l2-web-blocked-01/` |

## Android 深路径运行（保全失败）

`.local/goal-20261015/e06-l2-android02/` 绑定同一 HEAD，使用过深的 Java 临时目录 `.../e06-l2-android02-java-tmp`。90 项独立测试中 31 项失败、0 错误、0 跳过，失败全部为 `android.database.sqlite.SQLiteCantOpenDatabaseException`；Gradle 退出码为 1。完整日志、XML、命令、源文件哈希、进程和磁盘记录均保留，`sourceUnchanged=true`。这是运行器临时目录路径问题的失败指纹，不作为产品失败，也不作为通过；不得覆盖或删除该目录。

## Android 短路径复验（开发证据）

`.local/goal-20261015/e06-l2-android03/` 使用 D 盘短 Java 临时目录 `.local/rtmp-e06-l2-03`，与上项相同的独立测试筛选和离线 Gradle 入口实际执行：90 项测试、0 失败、0 错误、0 跳过，`result.json` 的 `exit=0`，`sourceUnchanged=true`。Gradle `BUILD SUCCESSFUL`（10 分 20 秒），`lintDebug` 退出成功；lint 仅保留既有兼容性/方向性警告。

生成并保留的 APK：

- Debug APK：`.local/goal-20261015/e06-l2-android03/app-debug.apk`，SHA-256 `34DBED215309BE80D0F948CFDCF7AA110BFC3D3E995F2CD2328B76AC759993B8`。
- AndroidTest APK：`.local/goal-20261015/e06-l2-android03/app-debug-androidTest.apk`，SHA-256 `E092AEAA1936BC84A282CE61C382128FC522A3C413F94DE7B9A72EA77F66D797`。

本次只构建了 AndroidTest APK，没有在真实设备或模拟器上执行 instrumentation；因此不能替代 Android 现场、硬件、后台组合或真机文件通道验收。

## Chrome 1540 阻断与保全

1540 组合副本已在 `.local/goal-20261014/preserve-1540-e06-l2-web-blocked-01/` 停机保全：`listeners=0`、`shutdownExit=0`、347 个受保护文件长度/哈希不变、预览与 1519 材料保留但不再运行，数据库 dump SHA-256 为 `5DC963673EDB3453E6F3759BBF2C5EE19B6EBE96EFA1054469BFE76A5571B11C`。启动 5175 Vite 预览被运行器自动审批以 `blocked by policy` 拒绝，未更换包装或工具绕过，未执行 Chrome 场景，也没有业务 HTTP 写入。相关失败日志和数据库不得删除。

## 结论与接续

Android03 仅证明当前 HEAD 下独立 Android/Room 主机验证可在短临时路径完成；Android instrumentation、真实 Chrome 1540、E06 受影响组合、E06 L2 整体及 D18 L3 仍未满足。下一步保全 1540 阻断并推进不依赖该审批的 E09 候选、剩余 D/O/U 闸门；取得实际允许的 Chrome 入口后，再按输入 SHA 补 E06 L2 组合。生产开关、七类生产决定、`localDeletionAllowed=false`、原库/现网和发布边界保持不变。
