# U04 TEMP 队列满边界 L1（2026-10-19）

本切片修复 TEMP 作业入口在队列已满时的显示计数错误。`TemporaryEngine.enqueue` 先递增并发布 `queued`，`trySend` 失败后原实现只递减内部计数而不发布回退值；页面会短暂把被拒收输入也显示为待落盘。修复在释放租约和递减后立即发布实际计数，再设置入口已满的阻断信息。

## 范围与输入

- 影响路径：`android/app/src/main/java/com/acrylic/outbound/pda/temporary/TemporaryEngine.kt`；测试夹具和定向测试分别为 `TemporaryFixture.kt`、`TemporaryEngineTest.kt`。
- 输入为 Robolectric 原生 SQLite 的临时合成契约；服务端 fixture 由隔离测试库 `127.0.0.1:13316` 生成，数据目录为项目 `.local/mysql/data`。系统 MySQL `3306/33060` 未连接。
- 本切片没有安装 APK、启动 ADB/模拟器、连接原库/现网、调用真实 PDA HTTP、使用正式签名或覆盖既有 APK/tar/日志/唯一证据。
- 失败的初始化脚本路径、字体下载超时、隔离库旧 Flyway failed 记录和权限配置均保留在 `.local/u04-temp-queue-run01/`，没有删除或重跑旧 run03。

## 设计与验证

测试在第一条 TEMP 写入的时间校验处阻塞 worker，确认一条在途和 64 条排队输入已被接收；第 66 次调用必须返回 `false`，`queued` 必须仍为 65，阻断文案必须包含“入口已满”，释放后 65 条输入全部持久化。预修复对照的新增测试按预期失败，失败点为 `fullInputQueueShowsOnlyAcceptedInputs` 的计数断言；修复后同一测试类 4/0/0/0 通过。

| 阶段 | 命令/结果 | 证据 |
| --- | --- | --- |
| 服务端 fixture | `integrationTest --tests com.acrylic.outbound.inventory.TemporaryContractFixtureIT -x :report-rendering:prepareFont`，1/0/0/0，退出 0 | `server-fixture04.log`，fixture SHA `8f5c6a507f98b8b27593c51911ccf98b03f890c2d7125fca9210b6dc88e0870a` |
| 预修复对照 | Android `TemporaryEngineTest`，4 项中 1 项失败，退出 1 | `android-test-prefix.log` SHA `4fd16dfa26386b0b7995d862c0c9c8c4d21b39225539316432141a4376ac43d4` |
| 修复后回归 | Android `TemporaryEngineTest`，4/0/0/0，退出 0，5.964s | XML SHA `529571338a334d29a0cf07df06b1b74a31477c616283f39030f38362ab174cc9`；日志 SHA `2c2ebd521b2ca0e2ee4fc6e3f1ec82d4544083a9b92c44bfc27201266a1d3e6e` |

构建使用新的 `.local/u04-temp-queue-run01/gradle-build` 和 D 盘临时目录，没有写入项目默认 Android build 输出。`git diff --check` 通过；生产代码、测试夹具和测试文件分别为 142、77、88 行，未引入新权限、网络入口或密钥。

## 哈希与边界

- 修复前 `TemporaryEngine.kt` blob：`812de14482f15cad91112691661a77e20775d13e`；修复后：`3a148c15b2e8526fbca11849dcd4ab879aebda50`。
- 最终源码 SHA-256：`TemporaryEngine.kt` `e6f5214cf9cefbdbe2301664db3a759e92e7e9bd5c210436099a3469c43d4029`；`TemporaryFixture.kt` `8917abfbe5018e4c148f67533f3424c689515c3f31d2db142d2e7805da788576`；`TemporaryEngineTest.kt` `aaeae0963eb8515e572aca4cda42ab76145b3645ab8cdad53685437e28860c62`。
- 输入命令、环境、分支、HEAD、远端和输入 SHA 在 `command-input.json`（SHA `371820fc5c8391ae70931ed8ef36b9be0da525f3f974e1e51c26d6ef99e38127`）；最终结果索引为 `android-test06-final-result.json`（SHA `de7dae4d585a3d27f068505a245fe63e0cca7571f013aa0a2c854963dae28019`）。
- 与本测试无关但被 Android Gradle 输入校验要求的 fixture 占位文件明确记录在 `fixture-placeholders.json`；它们不是业务事实，不计任何验收。

## 结论

该项只证明 TEMP 原生队列满边界的计数和持久化守恒，属于合成输入的 U04 L1。它不证明正式/TEMP Activity、真实 HTTP/MySQL 业务链、Chrome/Python 消费、原设备身份、D16 合法退出、D18 最终候选或第 8 节闸门；总目标继续 active，`localDeletionAllowed=false` 保持。
