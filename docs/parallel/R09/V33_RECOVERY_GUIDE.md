# R09：V33 临时提交快照恢复

日期：2026-09-20。本批在 `codex/r09-v33-recovery` 独立工作树完成 V33 TEMP 来源、控制头、正文、分块、取消与 PDA_TEMP 待审订单的恢复核验。固定业务基线为 `90581375128ee426fcf979a76eb44f4bb7734fef`。本说明的检查只证明受控快照内部一致，不授予灾后重建、释放实物、记账、放行或本地删除权限。

## 实现范围

- `temporary_submissions.py` 核对原 `(creatorDeviceId, clientOrderId)` 与原资格、设备、人员、仓库、恢复代次，及唯一 current、首轮、canonical、slot。正文前禁止制造正式订单、assignment、slot、提交明细和占用；正文后须只有一条 PDA_TEMP 待审目标，保持原计划。TEMP 尚只支持 REGISTERED / RECEIVING / REVIEW_REQUIRED / ABORTED，后续成功或拒绝状态拒绝通过，须另批接入。
- `temporary_uploads.py` 验证声明的总大小/块数、稀疏块索引与长度、每块私有文件摘要，完整块集合的合并摘要，以及完整正文与声明摘要一致。分块声明、甚至所有块到达，都不等于已接受正文。
- `temporary_payloads.py` / `temporary_plans.py` 验证实际正文原字节、规范化 JSON、控制头、固定资格基线、计划摘要/目标版本及逐行产品计划、原采集/审计索引摘要和实物占用。已接受的待审冲突可以没有本批占用，不能伪造占用。
- 取消必须保留原来源和签名；`payloadPresent` / 已接收摘要 / canonical 必须相互对应，`mayResumeScanning=false`、`stockReleased=false`。**V33 当前 abort 会清空 issues_json；正文后取消只能核验快照中仍有的占用属于原有效扫码，不能从这份快照证明取消前的完整占用集合。** 本工具不补写任何历史证明或释放库存。
- 复用原资格/准备包和归档验签、所有外键、私有文件引用、FH 日期与计数器核验。FH 仅对照同一快照内已存订单；没有把当前 MAX 当成截点外高水位，没有降低或重建计数器。
- 业务检查保留 V21/V28/V32 路径，并显式增加上述 V33 检查。未知版本拒绝；本轮没有重跑旧 V21/V28/V32 实验库，旧演练证据仍只对应其原版本。

唯一 Java 新增是专用合成夹具 `R09V33FixtureIT`，未修改业务源码、Flyway、OpenAPI、Room、认证、共享控制、Job/Worker 或版本。它必须显式设置 `R09_V33_FIXTURE=true` 才运行，且仍硬校验专用 source 端口、库名和账号。`seed-v33` 在自己的子进程设置此开关，运行前通过实际 MySQL 身份核对专用 datadir/端口/库/账号及空库，并核对 server/report-rendering 原始业务树等于固定基线。已存在的实验库不允许重新 seed。

## 本次证据

第一实验目录为 `.local/r09/20260920T132134Z-f1bb3b`。独立 source / restore 分别绑定 19309 / 19310，datadir 均位于这个新目录；原 V32 实验目录一直保持停机且未改动。HTTP 端口 18309 未启动。

| 项目 | 实际结果 |
| --- | --- |
| Python 运维检查 | 71 项通过；含本批 15 项来源、取消、正文/计划及分块/占用边界用例 |
| 第一份合成数据 | 18 项 MySQL/MockMvc 全通过，0 失败/错误/跳过；其中新增 V33 10 项、正式提交 4 项、成功/审计归档各 1 项、TEMP 审计 2 项；bootJar 通过 |
| V33 状态覆盖 | 正文前取消、只有 manifest、部分块、所有块未 finalize、直接正文幂等重试、乱序完整分块、部分块后取消、完整正文后取消、冲突正文及冲突正文后取消 |
| 完整备份与空库恢复 | 93 张表；原结构、行数/CHECKSUM、规范化全量数据摘要及所有私有文件/配置哈希一致。恢复 16.854 秒仅为本机小合成快照耗时 |
| 原事实 | 201 组外键、35 处文件引用、10 份签名终态；5 个成功、5 个取消；10 包/3,007,500g，4 条账务/−1,203,000g，16 条占用 |
| V33/TEMP/FH | 10 个 TEMP 提交、5 份已接受正文、7 个块、4 个 TEMP 取消；13 份原资格/65 个准备包、12 个原来源、2 条原 TEMP 审计；23 张 FH 订单/1 日计数器 |
| 原归档兼容 | 1 份成功归档/2 处文件引用，3 段审计归档/15 条段内事件、16 条原审计 |
| 普通测试隔离 | 最终夹具未启用时 10 项全部跳过，无 Spring 上下文或 Hikari 初始化；显式启用但错误库时 10 项按预期被硬守卫拒绝，数据库连接未发生 |
| 最终开关正向验证 | 全新第二实验目录 `.local/r09/20260920T141033Z-14ede7` 只 seed 一次，最终 18 项全通过，0 失败/错误/跳过；JAR 和完整业务 inspect 通过。只修改夹具开关，因此未机械重跑不受影响的第一份备份/恢复 |
| 损坏演练 | 最终 17 项损坏拒绝及全部原值检查通过；每个 finally 后核对原值，最终 93 表与全部文件再次一致。工具原始内容指纹见 `VERIFICATION_V33.json` |

备份为 `backups/20260920T135520Z-becfd9`，其 `SHA256SUMS` 摘要为 `cc81b824696b2804699b10cb2b0da80fb6f02c430f2bec25ad94fa9e8e079a33`。原 JUnit/XML 保存在 `initial-integration-results/`；禁用/错误库验证保存独立 XML 和日志。第一份数据使用加注解前的夹具，业务工具未受注解修复影响；最终开启开关的正向验证另用全新实验目录，详见最终 JSON，不将两份夹具字节冒充同一版本。

## 失败与修正记录

1. 第一次 Gradle 在 `prepareFont` 清理预放的 generated 输出后重新联网下载字体，等待过久；核对本次 daemon 后主动停止，未进入 seed，两个库仍为空。保留 `source/fixture.log` 和线程诊断；随后使用独立 `.local` 中校验过 SHA 的字体及显式 `-Pr08FontFile`，44 秒构建/18 场景通过。
2. 独立审查指出专用 `*IT` 类硬守卫会破坏普通全量集成测试，增加显式环境开关和 seed 私有进程启用。普通禁用真实 10 项跳过；第一次错误库探测因没加 `--rerun-tasks` 命中 Gradle 旧结果，不算验证；加参数后实际守卫拒绝，日志均保留。
3. 首次故障演练用 `HEX(numeric)` 保存原值，恢复数字时触发 MySQL 1366。仅恢复副本的一条 scope.work_round 被留为注入值 2；按原备份将该已知字段恢复为 1，重新确认全部 93 表与原快照一致。修正为 `HEX(CAST(field AS BINARY))`，保留数字文本、二进制及 NULL 的原表示。
4. 第二次演练的未知版本测试使用正在改变的 version 作为恢复条件，最终仅 Flyway 的版本字段没恢复。按稳定 installed_rank 恢复原 33 后，全部 93 表再次一致；永久改用不可变主键，并在每个 finally 后立即核对原值。上述故障没有改 source 或任何原业务库。

两套新实验目录均已正常停止，19309 / 19310 / 18309 端口关闭；所有数据库、备份和失败证据保留。独立 W2 已复核最终基线 4、71 项 Python、夹具正反门控与两套实验的不同证据边界，无剩余审查阻断。

## 复现与后续边界

在含本批工具/夹具且 server/report-rendering 业务树仍等于固定基线的工作树，先建立**新的** R09 lab，再运行：

```powershell
# 指向经SHA校验的本机字体；不是密钥或业务资料。
$env:R09_FONT_FILE = '<专用实验目录>/NotoSansCJKsc-Regular.otf'
python docs/parallel/R09/ops/cli.py seed-v33 --lab '<新实验目录>/lab.json'
python docs/parallel/R09/ops/cli.py backup --lab '<新实验目录>/lab.json'
python docs/parallel/R09/ops/cli.py restore --lab '<新实验目录>/lab.json' --backup '<本次完整备份>'
python docs/parallel/R09/ops/cli.py v33-faults --lab '<新实验目录>/lab.json'
python docs/parallel/R09/ops/cli.py stop --lab '<新实验目录>/lab.json'
```

专用 lab 配置、密钥、随机凭据、数据库、备份、JAR、原测试 XML/日志只在受限 `.local` 保存，不进入 Git。故障入口只接受已验证的专用 restore，逐项 finally 还原并核对所有表/文件。当前实现不创建库外 UUID 权威控制、不切代、不解除屏障、不恢复签名成功、不清理整单，不改变 `localDeletionAllowed=false`；共享接入仍按[控制契约](RECOVERY_CONTROL_CONTRACT.md)、[证据契约](RECOVERY_EVIDENCE_CONTRACT.md)及总调度明确的稳定检查点另派任务。
