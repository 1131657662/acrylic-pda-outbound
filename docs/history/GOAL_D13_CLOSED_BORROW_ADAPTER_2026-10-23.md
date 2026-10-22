# D13 闭连接借用适配器开发备份

本片为 `run1663` 的有界开发验证：在借用物理连接暴露前执行显式连接准入，关闭失败时保留 writer/root 围栏。编译 exit0，定向单测 45/0/0/0，双实例集成 1/0/0/0；源实例准入成功，使用不同物理数据库 UUID 的目标实例被拒绝。该结果不代表 D13/D15 整体、全部入口、完整恢复、真实 HTTP、Android、最终候选或第 8 节通过。总目标仍 active、未完成。

## 输入与改动

固定树 `D:/acrylic-pda-outbound`，分支 `codex/development`，输入 HEAD `f3d6b2d1ae0d98c269ed7bde2a58fa51d9dbbba4`。本片九个 B3 路径如下：

- `server/src/main/java/com/acrylic/outbound/recovery/RecoveryRequestAdmittedDataSource.java`
- `server/src/main/java/com/acrylic/outbound/recovery/RecoveryClosedConnectionAdmission.java`
- `server/src/test/java/com/acrylic/outbound/recovery/ClosedBorrowLifecycleFixture.java`
- `server/src/test/java/com/acrylic/outbound/recovery/ClosedBorrowModelProtocol.java`
- `server/src/test/java/com/acrylic/outbound/recovery/ClosedBorrowObservation.java`
- `server/src/test/java/com/acrylic/outbound/recovery/ClosedBorrowTarget.java`
- `server/src/test/java/com/acrylic/outbound/recovery/RecoveryClosedBorrowIdentityIT.java`
- `server/src/test/java/com/acrylic/outbound/recovery/RecoveryClosedBorrowLifecycleTest.java`
- `server/src/test/java/com/acrylic/outbound/recovery/RecoveryClosedConnectionAdmissionTest.java`

适配器新增显式 `ConnectionAdmission` 检查，并在物理连接取得后、代理暴露前执行；连接关闭或准入异常会释放可确认的 lease，关闭异常则 poison writer/root/effect gate 并保留未确认状态。测试夹具覆盖正常借用、准入拒绝、关闭失败、Error 路径及不同数据库 UUID 的双实例反例。默认 Spring wiring 仍使用 effect-only checks，生产入口未改。

源文件 SHA-256（暂存前）：

| 路径 | SHA-256 |
| --- | --- |
| `RecoveryRequestAdmittedDataSource.java` | `714d5fa5fad2bf747c4da1eb2bedbc30de284a294a239ee4eba9d1d61bfac213` |
| `RecoveryClosedConnectionAdmission.java` | `b0f1a5c780ac6ae1199905d97f62e3168a1c036a874c591969e838e7c37be28` |
| `ClosedBorrowLifecycleFixture.java` | `2d0dd0d74d6b436f98482a023b303b1463c9eabedd66067f4cf9e93fa457a6f4` |
| `ClosedBorrowModelProtocol.java` | `2764ef83434bddcf676b492acda341e39fdac6502b4aeaacde2713f04ea1cb4` |
| `ClosedBorrowObservation.java` | `e8335c59bce2eae6bbd4a9e0d63a6a64aa9140910935a72746af89f671cc1579` |
| `ClosedBorrowTarget.java` | `17cc2c6b33ea07b72915310053575915a6a925de044049fcbed1efe44052d341` |
| `RecoveryClosedBorrowIdentityIT.java` | `3aab66a620c2659ec01ebd3b19b89068e8a76329a33ea1af9b94db7890bf4f` |
| `RecoveryClosedBorrowLifecycleTest.java` | `62201aa97c77e2212438d0fb1aa02e17cad2e16c3b399e945c86e64ae74beb87` |
| `RecoveryClosedConnectionAdmissionTest.java` | `38e0413d44b8842317142ff16eb55bbf44e9734a7577cb09f56d0aec41029821` |

## 实际验证

证据根：`.local/goal-20261023-resume/d13-closed-borrow-adapter01/run1663-closed-borrow/`。所有构建、测试、身份和停机输入均保留在该目录；没有重跑旧 run03、旧库或设备测试。

| 项目 | 实际结果 |
| --- | --- |
| 编译 | `gradlew.bat -p server ... --offline --no-daemon testClasses recoveryControlClasspath`，exit0 |
| 定向单测 | `test` 加 8 个模式，45 tests，skipped/failures/errors 均 0，exit0 |
| 双实例集成 | `integrationTest --tests *RecoveryClosedBorrowIdentityIT`，1 test，skipped/failures/errors 均 0，exit0 |
| 认证停机 | `mysqladmin --defaults-file=... shutdown`，exit0；隔离端口 19841 监听已释放 |

实际身份记录：源实例为端口 19841、schema `p5_lost_1663`、UUID `186b7e90-ce50-11f1-bf66-d8bbc13a2b36`；目标实例为端口 19842、同 schema、UUID `1acf8920-ce50-11f1-a026-d8bbc13a2b36`。源实例 before/after 均可查询，目标实例因物理身份不匹配被拒绝。`real-records.tsv` 记录源/目标身份、五张 MODEL 表行哈希、业务查询前后与外部文件守恒；观察期 roots=0，未授予本地删除能力。

输入和结果文件 SHA-256：

| 文件 | SHA-256 |
| --- | --- |
| `result.json` | `20fead23cf80f5e364ec2ffb6bdb18807ad2bb2d8abc59d900b1e364f4082977` |
| `compile-command.json` | `f489611913410e505ba719eca6446305397ac503ebfdc6d9036d1843a1dbbad7` |
| `unit-result.json` | `9b41d71e4100a24b1b9683f08aeebba7f336c8c05e4a7533f4ac39fed18aa100` |
| `integration-result.json` | `90387551469714ec23f4641c98eebb0035f4fa13845b09d7942adf3a372adaf6` |
| `identity-before.stdout` / `identity-final.stdout` | `cf80aae1787091bd267990761c2388d60359c1052ae9534dcebad25e7401a99e`（相同） |
| `real-records.tsv` | `8edff29eed016a12f0f477f365e818270360157d8866000b3b400ac5d181b435` |
| `source-before.json` / `source-after.json` | `8a90017a6b451e89f5893a4c36fe886a6ccc337415440f9e8340f51e5450c929`（相同） |
| `dependencies-before.json` / `dependencies-after.json` | `23431e1ce6b056f893ebd278ad84b98a56e4ebf98e34fea24331034883ce5bc9`（相同） |
| `external-before.json` / `external-after.json` | `dc3048273794bfabc75334a3901cc02fba9fb39d50459e03a07893354dbc802a`（相同） |
| `harness-before.json` / `harness-after.json` | `cf2a7b0b1ece7b4574fda456f044aa502f4c79b5c4ce533dcbb79a6e022250442`（相同） |
| `mysql-stopped.json` | `988c5059dbb3c4615f4a717d3c58f44cda7b9b99fe90546b91ba419385e01aff` |

编译、测试与集成输出中没有硬编码密码、私钥或真实用户资料；测试连接密码来自环境变量/隔离 private 配置。作者、自审和离线证据消费者由同一助手执行，没有独立人员审查。

## 限制与边界

本片没有接线实际 HTTP、Session、worker、文件入口，没有运行完整普通回归、Android/APK、模拟器、真机、部署或 Release，也没有连接原库/现网。适配器默认 wiring 仍不表示所有连接池、初始化 NORMAL、裸 pool 或关闭路径都已覆盖；不同 UUID 的拒绝只证明本夹具的物理借用准入路径。现有失败证据、原资料、原身份/Keystore、原回执、最终 APK、继承改动和隔离证据均保留；本片未清理或删除任何文件。

本片按用户授权仅作为 B3 “开发备份”，不标“验收通过”，不计 D01-D18、O/U 或第 8 节闸门。生产开关、正式签名、原库/现网、`main`、部署和 Release 保持关闭，`localDeletionAllowed=false`；取消链和旧 run03 不恢复。
