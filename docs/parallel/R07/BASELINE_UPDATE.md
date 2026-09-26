# 2026-09-20 已提交依赖复核

本页是当前依赖判断入口，接续而不抹去2026-09-18的[历史证据矩阵](EVIDENCE_BASELINE.md)。本次只读核对源码、契约及交付记录，没有运行这些模块的业务测试。

## 1. 固定读取范围

- R07工作树仍在 `codex/r07-handover-correction-design`，源码起点为 `2369392076083a5a4f59d8ea3d6edebe5067a03f`。没有合并、变基或复制主线实现；工作树内旧源码链接不代表最新实现。
- 本轮依赖基线为 `codex/development` / 本地 `origin/codex/development` 同指的 **`87e1893a49b598bbfdacee1a20d5eb455c5a5903`**。使用 `git show 87e1893:<path>` 读取提交对象，避开主项目未提交文件；未重新联网核验远端尖端。
- 对应契约0.24.0、Android0.14.0-r06-audit/code19/Room16，Flyway最新V32（V31为TEMP审计，V32为订单编号）。这是源码基线，不是现网或设备版本。
- 用户指定的主项目 `D:/acrylic-pda-outbound/docs/REMAINING_DEVELOPMENT_PLAN.md` 当前未跟踪，只作为本次规划依据。2026-09-20 16:38（北京时间）读取的SHA-256为 `5128b4a25338351a97e843a3223d38b7c09a5c6e2498a25daa8bdc47dfe2d273`；本轮设计对应M3/M4及M5的消费边界，不将计划当成功能交付。
- 主项目TEMP冻结/上传、V33、Room17仍在开发，不能作为已交付能力或空闲版本号。没有读取或复制其未提交业务实现。主线进入R07时必须重新固定M1/M2的实际提交。

## 2. 提交与证据链

| 提交/范围 | 已提交事实 | 本轮采用的边界 |
| --- | --- | --- |
| `7d50af0`、`32ff90f` R05收口 | 订单/BARCODE同步、通用审计、任务/提醒、缓存清理、成功/审计归档、更新分发 | 五批开发范围分别收口；整单清理、完整灾后恢复、现场及审批待办仍独立 |
| `8f566e9` 续期恢复 | 原请求丢响应/跨进程恢复，保存前事务复核身份、未决输入、授权链与恢复屏障 | 不再把续期恢复写成待开发；原人原机约束不放宽成换机 |
| `271eff7`、`3bd5875`、`daf4083` R06-A/B | 独立TEMP权限/设备准入、48小时资格、五份资料、Android验签激活与原请求恢复 | 确認权存在不等于确认业务已实现；资料激活不是交接激活或出库 |
| `e0ee649` R06-C | 稳定草稿、逐产品计划、原资格/计划摘要、原周期输入、正式/TEMP全机防重 | 新准备只给新草稿；不是跨设备容器或临时提交终态协议 |
| `bbf9cc2`、`b830f4c` 编号 | `OrderNumbers`、V32 `order_number_counter`，新正式单FH＋北京时间日期＋全局日流水 | 原OB号/UUID/签名不改；R06正式化复用，R09计数器和订单一致恢复 |
| `87e1893` R06-D审计 | TEMP固定原资格绑定、正式/TEMP连续审计、混合签名归档/恢复、正式召回完整设备审计 | 不等于TEMP冻结/上传/确认；仍不支持另一设备在普通审计入口代传 |

主线记录的R06-D验证为261 Android主机、32后端单元、118 MySQL/MockMvc及构建/lint/契约；R05续期为真实本机API七阶段。以上均是相应提交的历史证据，**不是本任务重跑结果**。R06真实HTTP、设备/现场验证缺口继续保留。

固定提交上的交付依据：[R05分批状态](https://github.com/1131657662/acrylic-pda-outbound/blob/87e1893a49b598bbfdacee1a20d5eb455c5a5903/docs/R05_ACCEPTANCE_STATUS.md)、[续期恢复](https://github.com/1131657662/acrylic-pda-outbound/blob/87e1893a49b598bbfdacee1a20d5eb455c5a5903/docs/R05_RENEWAL_RECOVERY_E2E_GUIDE.md)、[TEMP准备](https://github.com/1131657662/acrylic-pda-outbound/blob/87e1893a49b598bbfdacee1a20d5eb455c5a5903/docs/R06_ANDROID_PREPARATION_GUIDE.md)、[TEMP本机作业](https://github.com/1131657662/acrylic-pda-outbound/blob/87e1893a49b598bbfdacee1a20d5eb455c5a5903/docs/R06_LOCAL_WORK_GUIDE.md)、[TEMP审计](https://github.com/1131657662/acrylic-pda-outbound/blob/87e1893a49b598bbfdacee1a20d5eb455c5a5903/docs/R06_TEMPORARY_AUDIT_GUIDE.md)。

## 3. 旧依赖的逐项改判

下表路径均相对仓库根，指固定提交87e1893；精确字段另见[来源、结果与引用契约](SOURCE_RESULT_CONTRACT.md)。

| 旧结论 | 当前代码/契约证据 | 当前结论与R07剩余增量 |
| --- | --- | --- |
| P05-01 / E13：只有ORDER流 | `contracts/barcode-sync-schemas.yaml`、`sync-paths.yaml`；`BarcodeChanges`、Android `BarcodeApply` / `BarcodeWorkGuard` | 已有BARCODE固定基线/增量及事务游标，状态含周期/版本。没有`reopeningId`或新周期证明；AVAILABLE不清guard，R07须新增受控周期恢复证据及客户端替代规则 |
| P05-02 / E14：没有通用审计补传 | `contracts/audit-schemas.yaml` / `audit-paths.yaml`；`PdaAuditService`、`PdaAuditOrigins` | 已有本设备连续ack、原批次恢复、正式/TEMP混合事件及归档。普通批次要求originDeviceId等于认证设备；缺口不能跳过。代传许可与逐来源交接覆盖仍需R07 |
| E02：召回审计必须从0全传 | `recall/RecoveryAudit.java`、`audit/AuditArchiveReader.java` | 已能引用签名连续归档前缀，再补到停止事件，混合TEMP可覆盖。但`auditCoverage.size()==1`仍限制原设备；不是多机证据协议 |
| P05-05：任务/重试待补 | `jobs/BackgroundJobLeases` / `BackgroundJobWorker`；Android `SyncScheduler` / `SyncWorker` | 云端租约/fence和PDA独立同步已有实现；UploadWorker仍只提交。复用内核，新增R07处理器/安全点；提醒不能自动审批 |
| P05-06：归档/清理均待补 | `contracts/archive-schemas.yaml`、`audit-schemas.yaml`；`RetentionCleaner` | 已有成功及审计归档、连续恢复、旧投影清理。`localDeletionAllowed=false`；整单90天清理、跨设备引用闭包及可取回证明仍缺 |
| P05-07：升级只有在途代码 | `update/UpdateSafety.kt`、R05更新/下载/15→16记录 | 更新已提交，现有未决TEMP输入也阻断安装。R07尚无持久交接安全点/新schema能力协商，不可因已有更新功能就激活新清单 |
| P06-01/02：TEMP只有草案 | V30资格/准备、V31 `temporary_work_origin`；`TemporaryDraft` / `TemporaryCapture` | creatorDeviceId＋clientOrderId及首次authorizationId已落地；草稿revision/planSha256及每条原周期已落地。尚无已提交canonical映射/通用scope控制、TEMP终态查询或跨机票据 |
| P06-03/04/05/06：关联、终态、别名、跨机停止 | `contracts/submission-schemas.yaml` 的Control仍只接受FORMAL；R06-D交付下一步 | M1/M2尚缺实际提交。scopeId、canonicalMappingVersion、continuationGrant是候选语义，不能当现成字段接线 |
| E06/09/10：多来源提交/多结果/多流水缺口 | `SubmissionEvidence`、`SubmissionAccounting`、`PcAccounting`、V9/V15及后续迁移链 | 这些缺口仍成立。结果只有首版路径；ledger仍每dispatch唯一、只支持原扣。R07须扩展而非改写原v1/签名正文 |
| P09-06：R09工具只支持V21 | 独立R09 `ops/consistency.py`显式支持21/28、`STATUS.md`记录V28恢复 | 更新为V21/V28工具已完成其记录范围；V29～V32、编号计数器、TEMP来源/混合归档及未来R07结构尚未受测 |

已确认的48小时、B模式、逐产品计划、独立TEMP确认、确认后离场、短装审批、首次整单0包禁止、实物核实后新周期、精确重量、全量库存声明、90天/至少5年、30分钟提醒、原出库日最新净值全部保留。新FH号规则也已确定，不作为新问题提出。

## 4. 关联工作树的观察快照

这些内容当前尚未提交，仅用来避免重复设计；没有将它们作为主线正式契约或代为提交。各任务记录的通过数不计入本任务验证。观察时间：2026-09-20约16:36～16:40（北京时间）。

| 来源 | 观察状态 | SHA-256 |
| --- | --- | --- |
| R08 `docs/parallel/R08/PROGRESS.md` | 源码起点8f566e9；单正式单原确认Web/API/Worker/下载及模板Excel已有本机证据 | `24e06327401e45310d0f96e30e995634a94f6763ebbaad63f6be388378cdeaf2` |
| R08 `docs/parallel/R08/CONFIRMED_V1.md` | PDA原机v1及PC来源适配；多设备/TEMP/净结果尚未接入 | `907b3e42f6b26edc94781b73956ad3014d4517a35442c414e45929d8b81f4a56` |
| R08 `docs/parallel/R08/SCAN_RECORDS.md` | 六列模板显示行号连续，排序仍用原采集序号；姓名来源明确 | `01a9eeb116839256f23cb05078330f6734e68232a1fb22211f3e0dd3a1e5e710` |
| R08 `server/src/main/java/com/acrylic/outbound/exports/PcExportSource.java` | `pc-work:<workId>`投影原pc_entry，非PDA设备身份 | `877a9fe7534d8092c1b2c1b9f3841bc97d37ea665776ffb6a5eb20dc3aa71fff` |
| R09 `docs/parallel/R09/STATUS.md` | 固定32ff90f/API0.21/V28演练；未部署，完整恢复未完成 | `8720f1ae46340038a3529bf72eac6dcc4fdec62162fbd230757f743143924c29` |
| R09 `docs/parallel/R09/VERIFICATION_V28.json` | 独立恢复和失败注入的脱敏汇总；不是V32证据 | `50ceb10823cd7d741e8243306e706c73f1233829b699c79a9f7472a5f7bef9c3` |
| R09 `docs/parallel/R09/ops/consistency.py` | 版本21/28显式检查；未知schema仍拒绝 | `4b2e7f149bc9c039a79d5448803891e3f8756974aec0cab7e941f295a2b32856` |

R08目录为 `D:/acrylic-pda-worktrees/r08-export`，R09为 `D:/acrylic-pda-worktrees/r09-ops`。不依赖本机绝对路径来解析业务证据；可整合依赖须由各任务形成实际提交/版本/验证记录后再锁定。

## 5. 可采用与不可宣称

现在可采用：R05既有基础与R06已落地的原来源字段，用于R07模型、锁序和验收设计。现在不能宣称：TEMP已提交确认、PDA已支持换机、报表已支持净结果、R09已覆盖V32或整单清理可开启。

主线接入时先读[分段实施与交接断点](STAGED_IMPLEMENTATION.md)，再将本页的固定提交替换为该次真正采用的组合提交。不能只把版本号改成新号而省掉字段、迁移、签名和验证差异核对。
