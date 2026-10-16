# 合成隔离整单清理消费者

从显式声明的合成 SQLite 原件创建完整备份和候选副本，实际恢复并核对全表、结构及数据库元数据后，按固定计划在候选副本删除一笔整单事实。此工具没有生产模式。它不会连接 Android、HTTP 或 MySQL，不会改原件，也不创建生产删除资格。

只接受初轮 FORMAL、已同步成功且停止重试的唯一当前提交、FROZEN 订单和原签归档确认。目标订单存在有效防重、未决输入、续作、实物处置、更正、独立来源、交接或其他不透明引用时拒绝。其他订单的 `work_guard` 仅在其原 capture/ACTIVE scan/订单、来源、条码及周期全部匹配时允许原样保留。无关审计命名空间须通过独立固定公钥、原签、事件摘要/字节、连续前缀、完整检查点及恢复原证明核验，其审计/依赖事实全部保留。引用目标（包括事件ID）、交叉归属和不一致记录仍拒绝，其他复杂引用表非空仍保守阻断。带正常有效防重的目标成功订单尚不能清理，不得移除guard制造资格。

## 输入和运行

使用 Python 3.11+，依赖见 `requirements.txt`；优先复用已安装依赖。全部输入、证据和候选必须位于本项目 `.local/`，拒绝目录穿越、链接、reparse point、硬链接和未停止的 WAL 输入。输入父路径须足够短以容纳 Windows 的结果文件名。该路径检查面向已确认的单写者离线工作区，不声称抵御同一账号恶意并发替换文件；出现未知写者必须先保全并停止消费。

`synthetic-only.json` 字段：

| 字段 | 含义 |
| --- | --- |
| `scope` / `production` | 必须为 `SYNTHETIC_GENERATED_ONLY` / `false` |
| `source` / `sourceSha256` | 已停止的合成 SQLite 绝对路径及原字节 SHA-256 |
| `schemaSha256` | 仓库最大数字 Room schema 文件的原字节 SHA；结构需通过固定 identity 审查 |
| `submissionId` | 唯一待清理成功批次的原 ID |
| `keySha256` | 原归档行公钥对象 canonical JSON 的 SHA，独立固定于输入 |
| `auditKeySha256` | 有无关审计时必填；审计原公钥对象 canonical JSON 的独立 SHA，不能用目标公钥替代 |
| `now` | 显式模拟 UTC 时间，最多纳秒；严格超过完成后90天且归档仍有效 |

```powershell
python -m tools.pda_retention prepare D:/acrylic-pda-outbound/.local/<case>/synthetic-only.json
```

返回新建的 `retention-<uuid>` 和 `planSha256`。源库完整保留；`backup.sqlite` 是实际恢复备份，`candidate.sqlite` 是唯一业务写入目标。`plan.json` 保存全表前事实与逐表选择，初始 `permit.json` 为 `allowed:false`。核对合成资格、计划 SHA、runId 和备份后，只对该新运行授予独立合成许可：保持 permit 中 `planSha256` / `runId` 原值，将 `allowed` 改为 JSON 布尔 `true`。

```powershell
python -m tools.pda_retention consume D:/acrylic-pda-outbound/.local/<case>/retention-<uuid> --expected-plan-sha256 <sha> --enable-isolated-deletion
```

缺显式开关或许可、许可撤回/异文、正文/签名/原件/备份/候选漂移均拒绝。原归档必须仍为 `localDeletionAllowed:false`；合成许可不改变该字段。事务实际取得 `BEGIN IMMEDIATE` 锁后重新读许可，删除后核全表受控差异、外键与完整性，再读许可后提交。同计划重放返回 `REPLAY_NO_WRITE`。授权检查以提交前最后一次读取为时点，不承诺与外部文件撤权形成跨资源原子事务。

`attempt-*.json` 写入 STARTED 后才可能修改候选；成功另写 `*.result.json`。进程失败或结果文件缺失时必须保留全部原件并检查候选事实，不能根据 CLI 非零声称未写入。完整备份可通过 `tools.pda_retention.store.restore` 恢复到另一个全新 `.local` 路径；不得覆盖现有源、候选或证据。

## 验证与重建

```powershell
$env:PDA_RETENTION_TEST_ROOT='D:/acrylic-pda-outbound/.local/<new-evidence-directory>'
python -m unittest discover -s tools/pda_retention/tests -t . -v
```

每个 case 创建独立合成库，测试结束不删除数据库和日志。工厂依赖本机 `server/build/pda-archive-fixture.json` 的原正文、成功回执、归档签名及公钥；不包含私钥。该文件由 `server/src/test/java/com/acrylic/outbound/inventory/SubmissionArchiveIT.java` 中 `durableCopiesProduceStableSignedConfirmationAndAndroidContract` 生成。缺失时须按项目隔离 IT 流程分配新的数据库运行该方法；不得直接连接共享 MySQL、复用失败库或用 DTO 冒充原设备事实。工厂基于实际 schema 构造数据库，没有声称它来自 Android 产品链。

完整套件还需对子进程设置 `PDA_RETENTION_AUDIT_SOURCE`（已停止、单文件、完整两代原签审计及依赖的SQLite副本）、`PDA_RETENTION_AUDIT_SOURCE_SHA256` 和 `PDA_RETENTION_AUDIT_KEY_SHA256`。来源与独立公钥须按原证据核定；已有输入及复制/WAL恢复步骤见审计L1报告，不连接或修改原捕获库。审计case将其与目标夹具组合，明确属于合成输入，不代表原PDA同库现场事实。缺输入时停止，不补造签名或跳过测试声称通过。

正式范围、输入 SHA、实际 CLI / 并发 / 撤权 / 回滚 / 恢复结果和保留失败见 [首切片L1报告](../../docs/history/GOAL_D16_WHOLE_ORDER_L1_2026-10-16.md)、[无关防重引用L1报告](../../docs/history/GOAL_D16_UNRELATED_GUARD_L1_2026-10-16.md)和[无关审计引用L1报告](../../docs/history/GOAL_D16_UNRELATED_AUDIT_L1_2026-10-16.md)。本工具不是 D16 整体、E08 L2 或 D18 L3 的通过证明。
