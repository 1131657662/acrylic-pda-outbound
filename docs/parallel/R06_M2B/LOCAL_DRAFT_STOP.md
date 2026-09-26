# 未提交临时草稿的本机停止快照

2026-09-25，Room24本机存储、Room25同事务原机签名、V37独立原件接收及Room26原键/回执耐久恢复。当前事实与验证见主树 docs/ORCHESTRATION_STATUS.md 顶部及 history/R06_DRAFT_TRANSFER_ACCEPTANCE_2026-09-25.md。独立草稿域不替代已有S的E2a协议。

## 入口与边界

`LocalDraftStopRepository.stop(clientOrderId)` 仅接收原人/原机/原仓/原资格资料仍可核验、尚无 temporary_submission 的 DRAFT 或 SCANNING；允许零包、未完整填写的草稿、原资格到期，以及本单存在 RECEIVED、WAITING_CONFIRM、CONFIRM_REQUESTED、INVALID、BUSINESS_REJECTED。仍保留现有登录能力/版本/恢复保护，不越过失效身份。

本项为 Room24 资料保全基础，目前没有产品按钮或自动后台调用。产品接线须先把已接收的内存队列按同一个应用写者耐久落盘，再在该队列中执行停止；本仓储方法只覆盖已经耐久的 Room 记录。数据库事务保证并发落盘与停止按顺序发生，不表示内存中尚未接收成功的输入已保存。

本机新 evidenceId 只标识资料包，**不是业务 submissionId、服务器回执或已验证 E2a source set**。LOCAL_EVIDENCE_STOP 是新的本地审计事件；其 detail 只含 evidenceId，不生成 FROZEN 或现有 E2a STOP。Room25在原停止事务内以原机已绑定密钥签住来源、metadataSHA、root、数量和全设备截点；V37独立OPEN/ITEM/VERIFY原件接收已过attempt56真实HTTP，VERIFY仅ORIGINALS_STORED且businessFactsStatus=NOT_EVALUATED。Room26原键与原回执仓储耐久恢复已过attempt57三次丢回执/重开；产品按钮、后台任务和扫码队列中的停止入口仍未接线。

## 原子保存与回读

一个 Room 事务内：核原身份与来源 → 写本地停止审计 → 核全设备捕获/审计连续高水位 → 保存原元数据与分页原件 → 封存摘要/数量/字节数 → 将草稿设为 EVIDENCE_STOPPED → 回读校验。任何阶段失败，事件、设备计数器、快照和草稿状态全部回滚。

元数据保存原草稿全部字段及产品计划、原资格请求/签名回执/公钥、manifest摘要、原epoch/snapshot、停止事件及全设备边界。明细包含截止点之前全设备 FORMAL/TEMP 捕获、扫描、审计，以及已有归档签名原件与原公钥；保留未决、失败、撤销、丢弃等状态。准备资料原分块继续保留并在打开时沿既有签名链核验，不以当前云端资料替代。

根摘要使用域 `acrylic-pda-local-draft-stop-v1`，绑定规范元数据、每条 ordinal/kind/recordId/deviceSeq/status/字节摘要/字节数，原件保存 canonical UTF-8。元数据上限128KiB、单件32MiB、总量128MiB、25万条、归档4096份；资源不足失败封闭。BLOB按128KiB片段读取，避开 CursorWindow 对单行大BLOB限制。

重读核摘要、根、连续边界、当前本来源原输入/扫描/审计及原资格；其他来源在截点后继续合法变化不改写已保存的快照。重复停止返回同一evidenceId及原件；坏件/缺件不能补造。STOPPED状态或保留的停止审计禁止继续扫码、确认、失败确认、放弃、撤销、改计划和冻结；已有guard保留，不生成取消、业务提交、扣账或清理。

## Room兼容

Room23→24新增 local_draft_stop 与 local_draft_stop_item，两表以原草稿为外键链，原表无重写。迁移不为旧资料补造停止，已有Room19–23冻结、上传、成功与归档表保留。APK code20未变；这不是正式版本或可降级承诺。

## 后续

原机签名、服务器结构闭合及独立原件接收分别见history/R06_DRAFT_SIGNATURE_ACCEPTANCE_2026-09-25.md、R06_DRAFT_SNAPSHOT_ACCEPTANCE_2026-09-25.md、R06_DRAFT_INTAKE_ACCEPTANCE_2026-09-25.md。Room24→25只新增签名表；旧无签名停止保留原件但不能追补原停止签名。V37核原准备DB/历史keyring及登记设备key；不把事后传输签名称为原停止事务签名。下一继续Android原键/原回执耐久与保守恢复，再接可信业务事实。目前LocalPayload仍仅接受ABORTED有原冻结正文的来源，不能把本地evidenceId改作S或伪造STOP/FROZEN。服务器收件不会自动占用其他来源K。

七类生产决定、TEMP生产归档、放行和清理能力继续关闭，localDeletionAllowed=false。真机/Keystore/OS死亡断电/现场未验；不把本机快照收口称为完整未冻结E2a接收完成。
