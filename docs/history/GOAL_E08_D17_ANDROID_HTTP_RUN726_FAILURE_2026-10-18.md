# E08/D17 Android 真实 HTTP 候选 run726 运行器失败（2026-10-18）

本报告登记 run726 的一次性隔离候选失败。失败发生在 Android 测试夹具构造阶段，未进入产品业务请求、Room 写入或业务结果核验；原始日志、XML、隔离数据库和认证停机事实均保留。

## 输入与停机

- 工作树为 `D:/acrylic-pda-outbound`，分支 `codex/development`，运行前 HEAD 为 `93710ec5cff979a567654084c80abe089e9a73b7`，远端一致。
- 命令为 `powershell -NoProfile -File tools/e2a-cross-source-verify.ps1 -Run 726 -Android -DbPort 19338 -HttpPort 19461`。
- attempt 为 `.local/e2a-cross-20260925-726/`，隔离库为 `acrylic_e2a_cross_20260925_726`；连接和停机身份文件端口、datadir、库、账号、binlog 字段一致，`19338/19461/19462/19463` 结束时均无监听。
- MySQL 初始化、Flyway 非空迁移及 Spring HTTP 服务均完成；Android 子进程退出码为 1，`test-completed.marker` 和 `android-result.json` 均不存在。

## 实际失败

- `integration-result.xml`：`E2aAndroidHttpIT`，`tests=1, skipped=0, failures=1, errors=0`；断言位于 `E2aAndroidProcess.run`，期望 Android 进程码 0，实际为 1。
- `android-result.xml`：`E2aRealHttpRecoveryTest`，`tests=1, skipped=0, failures=1, errors=0`；`E2aRealHttpConnection.kt:28` 的构造检查失败。
- 根因是测试输入生成器 `E2aAndroidPreparation` 固定写入 `http://127.0.0.1:19434/api/v1/`，而隔离 runner 将 `E2A_HTTP_PORT=19461` 传入 Android 测试。运行器因此在构造真实网络客户端前拒绝了端口不一致，尚未发生业务 HTTP 写入。
- 该失败归因于测试夹具/运行器，不能计为 Android 产品行为通过，也不能计 E08/D17、E06 L2、D18 L3 或第 8 节闸门。

## 修复与定向验证

- `server/src/test/java/com/acrylic/outbound/inventory/E2aAndroidPreparation.java` 已改为使用现有 `E2aTestPorts.http()` 生成 `baseUrl`；生产代码、默认生产配置和业务权限未改。
- 显式设置 `E2A_HTTP_PORT=19461` 后串行执行 `gradlew.bat -p server compileTestJava --no-daemon --max-workers=1 --console=plain` 成功。一次并行默认端口编译因两个 Gradle 进程同时清理共享测试输出目录失败，已保留为验证执行冲突，不作为产品失败计数。
- 修复后必须使用全新 attempt、全新隔离库和未占用端口重新执行 Android/真实 HTTP 组合；不得复用 run726 的输入或数据库。

## 证据哈希

| 路径 | SHA-256 |
| --- | --- |
| `.local/e2a-cross-20260925-726/android-result.xml` | `1A3EEA8E9DD38496B409D755A7D8E3FA8BBCE3167A0C8FD190195000590B61B4` |
| `.local/e2a-cross-20260925-726/integration-result.xml` | `E0A59E2C6F67569668D01C1B1EEF7CDE1518695AA3EB41FCF472DFBAA507C41E` |
| `.local/e2a-cross-20260925-726/private/android-http.log` | `07ED7D7FFE37CE6CAD238855A7072F308A818420B3049B2011A7A19BAB964367` |
| `.local/e2a-cross-20260925-726/private/android-http-input.json` | `AA2D1F887509379F310E4DB62EE5DA30EABBF8812F5002FB285BBBC9C52C624D` |
| `.local/e2a-cross-20260925-726/connected-identity.tsv` | `54A5508C99497B0315AEA056A6039DF254398E8B0A3E0DEFF65C56DDFD2A8D9A` |
| `.local/e2a-cross-20260925-726/shutdown-identity.tsv` | `54A5508C99497B0315AEA056A6039DF254398E8B0A3E0DEFF65C56DDFD2A8D9A` |
| `.local/goal-20261018-resume/e07-android-http-run01/snapshot-hashes.json` | `6B3D3D73CD041897494CB333C9DE326E607EB01F97BD18AB6FDBA58394F33749` |
| `server/src/test/java/com/acrylic/outbound/inventory/E2aAndroidPreparation.java` | `4183FD5C55522D3EAF787FB9D2D995C87D2EE553FE412D648199D6F865455497` |

## 范围结论

run726 仅证明旧夹具端口契约错误及隔离环境已认证停机。它没有提供 Android 客户端、真实 HTTP 业务链、Chrome、外部 Python、原模拟器、现场身份或外置介质证据；修复后的新候选通过前不得复用本次失败作为任何 L2/L3 或总目标完成依据。
