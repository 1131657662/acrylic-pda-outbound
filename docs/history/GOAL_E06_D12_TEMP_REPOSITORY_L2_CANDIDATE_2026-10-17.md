# E06/D12 TEMP 产品会话与原 ID 恢复 L2 候选登记（2026-10-17）

本单元登记一个新的、一次性的 L2 候选，不宣称通过。范围来自 `E06/D12` 的 TEMP 产品会话、丢响应后的 `RESULT_UNKNOWN`、原 ID 查询恢复和非空迁移守恒；不使用 recovered-confirmation worker/dialog/fixture/bridge，不连接原库或现网，不使用正式签名。

## 输入与写权

- 固定树：`D:/acrylic-pda-outbound`，分支 `codex/development`；登记时 HEAD/远端均为 `e3cebea70eb89576fc014c20128ea4940f3d273b`。
- 唯一写者：本会话 `01a147c5-0495-7703-ba98-2e7637cbc619`；共享 MySQL、`TemporaryAdjustment1364`、ADB 仅观察，隔离候选使用全新目录、数据库名、端口和运行身份。
- 证据根：`.local/goal-20261017-resume/temp-repository-l2-01/`；候选数据库/HTTP/代理端口在运行前逐一核空闲，不复用 675、失败库或旧 run03。
- 输入快照：`input-before.json`、`source-sha-before.json`、`environment-before.json`、`command.json`；候选结束保存 `input-after.json`、`environment-after.json`、连接/停机身份、失败原件和独立消费者结果。

## 改动路径、影响层与必跑清单

| 路径 | 影响层 | 当次必跑 | 复用/排除依据 |
| --- | --- | --- | --- |
| `android/app/src/test/java/com/acrylic/outbound/pda/temporary/TemporaryRepositoryRestoreHttpTest.kt` 与 `TemporaryRepositorySession.kt` | Android 产品 SessionVault/PdaSession/TemporaryRepository、Room 原 ID 恢复 | `testDebugUnitTest` 定向产品测试两阶段；非空迁移前后 Room/SQL/私有文件快照 | 复用既有测试设计但绑定本次源码 SHA；不复用 675 业务结果 |
| `server/src/test/java/com/acrylic/outbound/e2averify/E2aTemporaryRepositoryRestore.java`、`E2aTemporaryRepositoryAndroid.java`、`E2aTemporaryRepositoryProxy.java` | 真实隔离 HTTP/MySQL、非空 Flyway、产品两次 Android JVM、丢 GET 响应/恢复 | `server testClasses`、一次真实隔离 Spring/HTTP/MySQL 运行、认证停机和零监听 | 仅复用 runner 结构；新 run/DB/端口/证据 |
| `tools/e2a-cross-source-verify.ps1` 及 runner/driver | 隔离数据库供给、端口、停机身份和一次性 marker | 新 runner source SHA、输入路径、命令和进程证据 | 运行器保全在 `.local`，不改生产工具；失败不覆盖旧脚本 |
| `.local/.../temp-repository-l2-01` 独立 Python consumer | 外部只读消费 Room、HTTP/JWS、SQL、迁移、文件和停机回执 | 实际读取本次候选输入并输出 PASS/FAIL，失败保留原件 | 不把历史 `consumer07` 结果当本次通过 |

## 退出标准

1. 新隔离数据库使用非空 Flyway 迁移和独立身份，真实 HTTP 服务启动、候选完成、认证停机，连接/停机身份一致且候选端口清零。
2. Android 产品路径实际经历：上游成功响应下行丢失→Room 持久 `RESULT_UNKNOWN`→关闭/恢复→原 ID GET 成功→`SYNCED`，不重复 POST、不改非预期表。
3. Python 独立消费者实际读取本次 Room/HTTP/JWS/SQL/私有文件/迁移与运行回执，核对原 ID、原 payload、签名公钥、受控三表差异、非空表/文件守恒。
4. 该路径无 Web/UI 代码或产品入口影响，因此 Chrome 不适用；候选报告必须明确记录理由，不能把“未跑 Chrome”写成跳过的业务通过。
5. 任一运行器、环境、Android SQLite 或消费者失败均保存失败指纹并保持未通过，不删除输入、日志、隔离数据库、dump、binlog 或原证据。

本登记只允许形成 E06/D12 的局部开发候选；即使本次全绿，也不计 E06/D12 整体、D12、D17、E07、D18 L3 或总目标完成。
