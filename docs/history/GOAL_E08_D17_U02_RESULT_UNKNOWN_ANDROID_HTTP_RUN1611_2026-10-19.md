# E08/D17/U02 结果未知页面真实 HTTP Android 候选 run1611（2026-10-19）

本报告登记一次新的隔离真实客户端候选。范围是正式订单准备、提交响应丢失后的 `RESULT_UNKNOWN` 页面，以及原批次查询后恢复；输入、身份和签名均为隔离开发事实，不是原 PDA、现场回执或生产验收。它只计 U02/D17 的有界 Android L1 候选，不计 U02/D17 整体、E06 L2、D18 L3、第8节或总目标完成。

## 输入与路径

- 工作树 `D:/acrylic-pda-outbound`，分支 `codex/development`，输入 HEAD/远端 `8e1eb87e034bad6a994cfdc1f791c7adf1246454`；继承改动全部保留。本轮唯一源码测试改动是 `android/app/src/androidTest/java/com/acrylic/outbound/pda/PreparationEndToEndTest.kt`（SHA-256 `C43276B8010E9FDC6F8D4C9B04AB6184756466A3BC1247A8ACC2010F2710F475`）。
- 隔离证据根 `.local/goal-20261019-resume/u02-result-unknown-run01/real-http-1611/`；数据库 attempt `.local/e2a-cross-20260925-1611/`，端口 MySQL `19371`、HTTP `19471`、emulator `5571`。fixture 为新建隔离用户、仓库、订单，基址为 emulator 可达的 `http://10.0.2.2:19471/api/v1/`。
- 先执行 AndroidTest 编译；正确的 `android` 项目任务 `:app:assembleDebug :app:assembleDebugAndroidTest` 成功。Debug APK SHA-256 `339C809FAF1853797745A0346A073BC8A5A71A64D1ABDCA68926A42FBEBB6976`，测试 APK SHA-256 `83F7837EE5BCD2461FFB43C8B0753D59A880A384782EC3E490E8470EA694DF7C`。第一次错误根因是从根项目请求不存在的 `:android` 任务，日志保留，未改源码。

## 实际验证

- HTTP 服务连接真实隔离 MySQL；Android `PreparationEndToEndTest#registeredDeviceLogsInClaimsDownloadsAndRecoversLocalActivation` 在独立 `Pixel_6`/`emulator-5571` 上报告 `OK (1 test)`、退出 0。产品实际完成设备登记、登录、订单领取、准备、页面渲染、扫码/手输、冻结、丢失上传响应、结果未知页、原批次查询、审批后恢复上传和数据库重开。
- 结果未知页断言在第二次上传前实际检查：状态显示“上传结果待核对”，主动作显示“先查询原批次结果”，重复上传按钮隐藏，冻结正文提示保留。最终 `e2e-evidence.json` 的 `lostResponseRecovered=true`、`shortShipmentApproved=true`、`signedReceiptVerified=true`、`databaseReopened=true`。
- 1611 数据库身份为 `acrylic_e2a_cross_20260925_1611` / `e2a_cross_20260925_1611@127.0.0.1`、datadir 为该 attempt、binlog 开启；最终事实为 1 用户、1 订单、1 领取、1 准备、1 submission，submission 状态 `SUCCEEDED`。停库前认证 mysqldump 成功，634,624 字节，SHA-256 `0AC62223F7910EDA81F226119985CB0A7851C2BA9CABEAE01885BE56170CD96E`；随后认证 `mysqladmin shutdown` 退出 0。
- HTTP PID 与 5571 emulator 已停止，收口后目标监听 `19371/19471/5571` 均不存在；共享 ADB `5037` 和其他既有隔离进程未操作。原 1610 断言时序失败、未认证 dump 证据继续保留，不能用本轮覆盖。

## 逐路径影响、复用与限制

| 路径 | 影响层 | 必跑清单与结果 | 复用依据 |
| --- | --- | --- | --- |
| `android/app/src/androidTest/java/com/acrylic/outbound/pda/PreparationEndToEndTest.kt` | Android 真实 HTTP 结果未知页面断言 | Kotlin 编译、Debug/AndroidTest 构建；独立 emulator + 真实 HTTP/MySQL 1 test 通过 | 本轮源码 SHA；失败时序修正前的 1610 日志保留 |
| `android/app/src/main/...` 产品业务入口 | SubmissionActivity/Repository 实际消费 | 通过页面行为和最终 `SYNCED` 结果验证；本轮未改产品源码 | 继承产品字节，沿既有 U02 状态语义和前序 L1 路径复用；不替代完整 L2 |
| `.local/.../real-http-1611/e2e-fixture.json` | 隔离输入 | fixture 实际被设备消费；SHA `34CCFE3C6C7716D8E67D172CF6939B2A336E9A0F0227B4F8F8D754007C095F3B` | 新隔离生成，不能代表原身份/现场资料 |
| `.local/.../real-http-1611/e2e-evidence.json`、`android-preparation-result-unknown.log`、`logcat.txt` | 证据/独立回读 | 设备结果、instrumentation `OK`、Activity 生命周期和 Room 重开保全 | 本轮实际产物；原始 logcat 保留，系统网络噪声不作业务结论 |
| `.local/.../real-http-1611/private-final-database.sql` 与 facts/identity | MySQL/迁移事实 | 认证身份、业务计数、dump、认证停机 | 本轮 1611 隔离库；未连接原库/现网 |

本轮没有执行 Chrome、Python 消费、非空迁移组合、原模拟器恢复、外置介质、真实现场身份、权限/并发矩阵或 D18 全链，因此不能把本轮 Android L1 与历史片段拼成 L2/L3。生产七类决定能力、TEMP 生产归档和 `localDeletionAllowed=false` 均保持原边界；取消的换机承接、多跳和接收者确认/激活链未恢复。

## 证据摘要

| 文件 | SHA-256 |
| --- | --- |
| `real-http-1611/android-preparation-result-unknown.log` | `57636533899FF26215AB211ABB3ABE517B10708423BC78AF82E6CB28AD966FE0` |
| `real-http-1611/e2e-evidence.json` | `DE3DF7B3D5E96BA118F66934685FF3F5B41F716644584DFEE10A8FEC5ACCBDFA` |
| `real-http-1611/private-final-database-facts.tsv` | `55E9F0985AED3DE5AEC20C7B8BF3572925C7C876DD32D0B74AEEAA182A5B5009` |
| `real-http-1611/private-database-identity.tsv` | `67E5C85224F596F82101BC57066F08B3785C0B336CDACEBC477555E9FE0DE7D5` |
| `real-http-1611/private-dump-summary.json` | `0D3AF1F0285A67A9E03D4ADE07475147B53F799BDDEAA51A5C74D412BA7CCA38` |

证据索引、最终 Git 状态和 C/D/E 余量分别保存在 `real-http-1611/evidence-index-run1611.json`、`git-state-final.txt` 和 `disk-final.json`。清理脚本在无监听/无目标进程守卫下删除已认证停机的 1611 datadir 347 个文件、222,329,091 字节及三份凭据；完整输入/哈希/命令见 `cleanup-before-run1611.json`，dump 与业务结果证据保留，清理后 D 盘可用空间记录在 `cleanup-after-run1611.json`。`-datadir` 参数未使默认 Pixel_6 AVD 改用证据目录，默认 AVD 文件未清理或回滚。下一步继续处理 U02/U04 其他未验入口和统一候选缺口；本轮不重启 1610、不清理失败证据，不标记总目标完成。
