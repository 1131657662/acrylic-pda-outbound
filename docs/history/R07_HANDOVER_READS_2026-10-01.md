# R07 交接上下文与原回执读取验收（2026-10-01）

## 输入与实现

树 `D:/acrylic-pda-outbound`，分支 `codex/development`，输入及已核远端 `99861666ee796841c5ed72d07aa2c20e9e3b0c15`。按[方案](AUTONOMOUS_PLAN_2026-10-01_HANDOVER_READS.md)补齐既有Web命令面缺少的读取入口；依据[接口提案](../parallel/R07/INTERFACE_PROPOSAL.md)的同仓读取、原动作身份查原结果和[交接设计](../parallel/R07/HANDOVER_DESIGN.md)的原键恢复约束。

新增4份主代码：`HandoverReadController`、`HandoverReadService`、`HandoverReadStore`、`HandoverReadReceipt`。GET提供REQUEST及案件三种后续命令的expected和四维事实、验签后的原REQUEST正文；原动作人按案件/键/类型读取精确原回执，身份/仓库/权限每次重验，原设备绑定不得被Web身份越过。精确路由和错误边界见[接口说明](../parallel/R07/HANDOVER_READS.md)。

读取采用只读一致快照；不执行命令、不重签。复用旧签名验证并额外核原命令/响应字节、行绑定与案件/人/仓/expected/event/time。不改变旧命令、签名器、历史迁移或默认开关；新控制器与服务仅在既有交接开关显式启用时存在，所有能力位false。

## 当次验证

| 层次 | 结果与证据 |
| --- | --- |
| 定向单测 | 2套件、22项、0失败/0错误/0跳过；4种命令原字节、历史密钥/未知密钥、5种行身份不匹配、9类坏件及开关注册。`offline-1/` |
| 完整服务端离线回归 | 142套件、2915项、0失败/0错误/1既有skip；主/test编译成功。`full-offline-1/`，与上批基线相比新增22项 |
| 脚本与前置 | parseErrors=0，执行前核编译源码、八项保护文件、全部既有server主代码/资源摘要；新隔离schema、一次性provision/execution标记，无自动重试。`pre-execution/` |
| 812真实Web HTTP/MySQL | **1/0/0/0，18.961s，Gradle exit0**；默认关闭上下文无新控制器，开启后GET上下文真实消费四个POST并保存原响应。首次attempt即通过 |
| 原件恢复与读取守恒 | 四种原回执初次/应用上下文重建后GET均逐字节等于POST；6组查询前后每组16业务表typed全行相同，由独立脚本再次按文件SHA核对。`audit/` |

812共 **32次业务HTTP请求：28 GET、4 POST；200×16、401×1、403×2、404×13**。登录/CSRF请求不混入业务计数。实际链为 GET来源expected→REQUEST→GET案件expected→APPROVE→GET→PREPARE→GET→ACTIVATE；四个POST的expected来自产品GET，没有直接读取SQL expected来代替消费。

反例包括匿名、无交接权限、同仓他人查询原回执、请求人与审批人的交叉取件、另仓参数、错案件/错命令类型/不存在键或案件。成功响应核no-store/nosniff。完成后仅一份活动资格、激活回执与待发布outbox、非空保留占用仍在；没有调用outbox消费者。

5组运行期查询和1组重建后查询共6组16表守恒：`outbound_order`、`order_assignment`、`offline_authorization`、`submission`、`submission_scope`、`order_submission_slot`、`handover_case`、`handover_evidence`、`handover_checklist`、`handover_command_receipt`、`api_idempotency_request`、`operation_event`、`order_sync_outbox`、`bale_commitment`、`dispatch_record`、`inventory_ledger`。会话表不在这个无业务副作用声明内。

## 证据、磁盘与保护

- 证据根 `.local/autonomous-20261001/handover-reads/`；输入/远端/写者/监听/全部保护摘要在 `before/`。新隔离库/原HTTP、XML、日志、原件及typed快照在 `.local/e2a-cross-20260925-812/`。
- 812的 `handover-http/http.tsv` 与每个响应文件记录真实HTTP；`read-command-*`、`read-original-*` 为四种命令及原字节；`read-*-before/after.json` 为业务全表快照。`audit/result.json`、`snapshot-pairs.json`、`originals.json`已独立回读核对。
- D可用：**31652630528 → 31579275264字节**（批次开始→812保全收尾），全程高于20GiB；离线回归后31596146688字节。
- 真机22D2910042仅 `adb devices -l` 枚举，批次及812前后相同；定向设备命令0、AVD0。无在途测试，19330/19434释放，系统MySQL未操作。
- 812认证停机，完整dump与292文件ZIP逐项摘要回读一致后仅清本批新datadir。ZIP SHA-256 `42218EBAE71DED7B53CB8C381C3D729B711AB7B2F72AAADB246980A113F6D8B4`，原库/历史attempt/811失败均保留。
- 八项保护文件前后摘要不变，未编辑或暂存；旧产品主代码/资源无变化，本批只有新增主代码与测试。`closeout-812/summary.json` 的 `productUnchanged` 指before清单内的既有文件，不表示没有新增功能。
- 工具失败如实记录：整文件替换时工具不允许同一补丁内Delete/Add同路径，整包未应用；一次编排读取因未定义输出变量未展示结果。这些均在812之前，没有启动数据库/测试或改变产品源码；后续整文件写入、解析、摘要复核通过。无业务失败轮被隐藏。

作者、自审、执行者均为同一助手。全部本机证据/私钥/数据库/APK均不随Git上传；源码备份不等于本机证据已异地保全。实际提交/推送SHA见本批 `commit-review/git-final.json` 与Git历史，不预填未发生坐标。

## 未验与接续

本批是Web查询必要能力的完成，不是R07整体或PDA交接交付。合成FORMAL来源、已终结批次、停止证明和测试身份前置；不是原PDA真实恢复包/Keystore/现场来源。没有PDA GET别名、设备绑定实跑、并发撤权、真库坏回执专项、独立JVM/OS重启/断电、页面、完整证据包/清单下载、故障交接、未冻结无提交来源或TEMP交接验收。坏件与密钥轮换证据来自单测，不冒称HTTP真库坏件。

[811首次同键并发](R07_HANDOVER_FIRST_REPLAY_FAILURE_2026-10-01.md)、[交接同步消息](R07_HANDOVER_SYNC_STATUS_GAP_2026-10-01.md)、[更正实际账务贡献](R07_CORRECTION_ACCOUNTING_GAP_2026-10-01.md)、分页/P08/抬头/526停报项不被GET通过覆盖；本批没有修改这些算法或重跑811。R06政策未决、高轮次/跨来源、R05原资料/旧BCD、R09恢复/现场/长时边界继续保留。

下一步优先核对已可验证的原申请/审批内容在客户端的耐久存储和展示，使用这些读取入口接完整消费闭环；缺陷相关执行动作仍阻断，不以页面显示替代原键/消息/账务修复。目标active，局部完成不扩展到R08/R09/R10。开关默认false，localDeletionAllowed=false；不部署、不接原库现网、不合main、不打标签/Release。
