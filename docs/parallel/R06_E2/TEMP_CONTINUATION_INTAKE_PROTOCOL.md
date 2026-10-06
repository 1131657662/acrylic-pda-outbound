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

`TemporaryRoundRegistration` 是独立 HTTP intake 使用的内部事务原语。调用者先持有既有恢复、身份和仓库锁并核验提交权限；写者再锁原 scope、已有目标/槽、提交、授权及票据。它核验原人原机、新资格/新 client、轮次、代次、前驱原文和可信序号，追加新 REGISTERED 提交及唯一票据绑定。

注册不改变 scope 的当前提交、作业轮次、目标槽或任何 K owner。上传未完成或注册失败不能提前消耗保留占用。同一票据只绑定一个新提交；同身份和同引用重放返回该绑定，换提交 ID、身份、资格、轮次或原文摘要拒绝。绑定、激活记录及绑定后的提交身份由数据库阻止改写和删除；状态演进另由相应事务负责。

前驱原文有两个合法来源：已接受的 `submission_payload`；或完整 STOP 集合所引用的 `LOCAL_FROZEN_PAYLOAD` 原始对象。已接受正文存在时不允许退回另一份本地文件。两条路径均核对原字节、canonical 摘要、原来源与资格；缺文件、哈希不符或不支持的前驱轮次拒绝，不能由 capture 数量推造原文。更高轮次 STOP 的证明链尚未实现，继续拒绝。

## 累积证据验证

`TemporaryRoundEvidence` 实现内部累积证据验证，输入为前驱原文、原/新控制头、已验证的原/新计划、锁定票据事实及保留明细。它不授予提交或扫描权限，不替代外层资料、快照、锁与库存校验。

新正文的 captures、scans、events 必须保留前驱原数组的完整、不变前缀；原 capture ID、source/authorization、时间、序号及扫描原文不可重写。新增事件和新增 scan 不得重新引用旧 capture，旧 ACTIVE 集必须与固定保留明细完全相等。原扫描仍按原资格有效窗口验证，不套用新授权窗口。

新增 capture 按票据 `nextOrderScanSeq` 继续连续编号，新事件使用新来源和资格；设备 capture/localSeq 继续递增。新计划只变更 client/authorization 身份，原产品、政策、库存基线和计划数量保留。只有保留包且无新增输入时，仍要求新来源的新 FROZEN 事件与完整累计统计。接收原文字节不会被该验证器重写；首轮调用只允许一个原资格。

## 受限完整 intake

独立 POST `/api/v1/pda/temporary-orders/continuation-submissions` 与 GET 同路径 `/{id}` 由 `outbound.temporary.continuation-intake.enabled` 控制，缺省关闭。POST 只接受不超过 1MiB 的完整正文；尚无续作分块协议，不可回退普通首轮上传。读取与提交均要求原人原机及 `pda:submit`；写入还遵守恢复和身份锁。

完整验收同事务注册、重读原文、分段验资格/快照、保存原文和事实、选择 scope/slot 新轮、转移精确 K owner，并落为 REVIEW_REQUIRED。文件字节在数据库回滚后作为证据保留，数据库不留下部分接受结果。同键同正文恢复已接受状态，异文拒绝。已有目标仅支持初始映射的 PDA_TEMP 待审记录，核对固定版本、基线、车柜信息和逐行计划；受控 LINK 目标继续拒绝。尚无目标时只在完整接受事务中正式化。

原 ACTIVE 包必须全部保留，精确核对票据、scope、warehouse、evidence-set、cycle；先按包顺序加锁，再从 TEMP_CONTINUATION_RESERVED 转为新提交 REVIEW_RESERVED，新增包继续普通库存冲突检查。新授权 download-ack 缺失会保留待审问题，票据 ACTIVATED 不代替资料下载确认。待审回执显式给出 `localDeletionAllowed=false`，不允许继续扫描或释放库存。

## 确认及后续边界

以下确认记账仍待接续实现，不能以受理通过冒充记账完成：

- 确认读取同一受理原文和独立新授权，重验前驱及保留关系、原政策、目标和精确占用。出库、库存记账、成功回执、票据 COMPLETED 与 K 结算同事务提交，失败和重放不得重复记账。
- 新轮 abort/CANCELLED 的释放政策没有定义，保持拒绝。Android 原子消费后的草稿继续保持 CONTINUATION_BLOCKED；生产命令、放行、扫码、删除和 `localDeletionAllowed` 均关闭。

验证须分别标明：协议单测、数据库迁移/内部事务、真实 HTTP 命令/提交/确认，以及原设备验收。合成 stopped-source 或合成前驱只证明对应技术层；不能代替原 STOP 和实际新轮 intake/记账。
