# TEMP 续作新轮提交协议

本协议实现独立 TEMP 授权的下一轮提交，不扩大生产入口或权限。续作前置沿用原人原机、ABORTED、完整 STOP、全部保留清单和 `order:resume`；普通提交使用 `pda:submit`，确认使用既有独立确认权限。版本事实见看板事实块，实施证据见自主进度。

## 来源和显式引用

新轮正文仍采用既有冻结证据格式，新增 `temporaryContinuation` 对象。它包含且仅包含：

| 字段 | 约束 |
| --- | --- |
| `version` | 整数 `1`，标识本引用结构 |
| `ticketId` | 已消费激活的 TEMP 票据规范 UUID |
| `predecessorSubmissionId` | 票据固定的旧终态提交 UUID |
| `predecessorPayloadSha256` | 旧 canonical 冻结原文 SHA-256，小写十六进制 |
| `retainedManifestSha256` | 票据固定保留清单 SHA-256 |

正文 `source` 是激活回执给出的新 TEMP clientOrderId 与原设备；`authorizationId` 是独立签发的新资格。`workRound` 必须等于票据下一轮，`submissionId` 必须新建，`assignmentId` 和 FORMAL `continuationId` 必须显式为 null。客户端不传或选择 scopeId。服务端经票据定位稳定的原 scope，原 scope 的来源、原授权、旧提交和旧回执不可替换。

## 注册与重放

`TemporaryRoundRegistration` 是尚未接入 HTTP intake 的内部事务原语。调用者先持有既有恢复、身份和仓库锁并核验提交权限；写者再锁原 scope、已有目标/槽、提交、授权及票据。它核验原人原机、新资格/新 client、轮次、代次、前驱原文和可信序号，追加新 REGISTERED 提交及唯一票据绑定。

注册不改变 scope 的当前提交、作业轮次、目标槽或任何 K owner。上传未完成或注册失败不能提前消耗保留占用。同一票据只绑定一个新提交；同身份和同引用重放返回该绑定，换提交 ID、身份、资格、轮次或原文摘要拒绝。绑定、激活记录及绑定后的提交身份由数据库阻止改写和删除；状态演进另由相应事务负责。

前驱原文有两个合法来源：已接受的 `submission_payload`；或完整 STOP 集合所引用的 `LOCAL_FROZEN_PAYLOAD` 原始对象。已接受正文存在时不允许退回另一份本地文件。两条路径均核对原字节、canonical 摘要、原来源与资格；缺文件、哈希不符或不支持的前驱轮次拒绝，不能由 capture 数量推造原文。更高轮次 STOP 的证明链尚未实现，继续拒绝。

## 完整 intake 的后续实施约束

以下是接续实现的验收约束，不能以注册专项通过冒充已实现：

- 新正文的 captures、scans、events 保留前驱原数组的完整、不变前缀；原 capture ID、原 source/authorization、时间、序号及扫描原文不可重写。新增事件不得修改旧 capture，旧 ACTIVE 集必须与固定 retained manifest 完全相等。
- 新 capture 从票据 `nextOrderScanSeq` 连续递增，新事件使用新来源和资格；全设备 capture/localSeq 继续递增。新计划只变更 client/authorization 身份，原计划产品、政策、库存基线和数量保留。不得通过重新扫描旧包或删除旧扫描来规避保留关系。
- 完整验收在同一事务保存收到的原正文、原/新事实引用，选择新轮及提交，逐包把精确票据/evidence-set/cycle/scope owner 转为新提交 REVIEW_RESERVED，再处理新增包。任一 owner 不符全部回滚，通用库存冲突检查不放宽。
- 已有 canonical 目标沿原映射和原计划使用；canonical 为空时仍须在合法完整受理后创建目标，不把注册当正式化。
- 确认读取同一受理原文和独立新授权，重验前驱及保留关系、原政策、目标和精确占用。出库、库存记账、成功回执、票据 COMPLETED 与 K 结算同事务提交，失败和重放不得重复记账。
- 新轮 abort/CANCELLED 的释放政策没有定义，保持拒绝。Android 原子消费后的草稿继续保持 CONTINUATION_BLOCKED；生产命令、放行、扫码、删除和 `localDeletionAllowed` 均关闭。

验证须分别标明：协议单测、数据库迁移/内部事务、真实 HTTP 命令/提交/确认，以及原设备验收。合成 stopped-source 或合成前驱只证明对应技术层；不能代替原 STOP 和实际新轮 intake/记账。
