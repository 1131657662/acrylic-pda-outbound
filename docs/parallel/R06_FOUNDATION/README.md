# R06 来源与命令协议基础

第五轮 W1；起点 `85cb7cef02fb8728599b6801a28824183305dab8`，业务基线 `c393d2d`。本批唯一迁移为 V34，V1–V33 原样保留。没有新增路由、OpenAPI 路径、权限、Android 版本或设备操作。

## 交付边界

V34 新增八张表：`temporary_source_mapping`、`temporary_decision_state`、`source_evidence_object`、`source_evidence_set`、`source_evidence_item`、`source_evidence_verification`、`temporary_command_receipt`、`temporary_decision_event`。证据/台账只具备持久关系基础，没有接收、验证、命令执行器或签发业务回执服务。

共同 `bale_commitment` 主键仍是唯一 `bale_id`。仅新增 `SOURCE_RESERVED` owner，指向真实 TEMP scope 与 evidence set；可没有 order。原六种 owner 分支继续要求非空真实订单，新 evidence owner 不能与旧 owner 混合。`source_warehouse_id` 为关系约束冗余列，和 scope/bale 的复合 FK 同时限制同仓；不是新来源主体。

E2 提案中的 `source_physical_resolution`、逐包处置、`source_continuation`、新隔离/续作 owner 及对应票据留待后续业务实现与独立迁移分配；本批未预占 V35。所有新产品写能力及 `localDeletionAllowed` 保持 false，原 TEMP 正常完整接受仍只进入 REVIEW_REQUIRED。

## 迁移与现有写者

V34 在任何永久 DDL 前以临时 guard 表核对 TEMP scope→origin→authorization 同仓、S 原资格/原人/原机/代次、current S/round 及既有 TEMP K 的 canonical/仓。坏关系触发 `ck_v34_source_preflight`，不猜来源，不伪造订单/assignment，不 repair 失败历史。

TEMP scope 的 mapping 初值为无目标 0、有目标 1；迁移追加 `MIGRATED_BASELINE` 与实际 `observed_at`，不是原业务时间或历史 LINK。decision 初值 0 仅表示新机制无决定。正常新来源保存 `SOURCE_REGISTERED` 基线；既有正常 TEMP 接受追加 `INITIAL_ACCEPTANCE` 映射到版本 1，同时所有取得的 K 保存永久 source scope/仓。

`slot_version` 从 1 起，现有正式完成、续作批准和续作新批选择全部递增；它不改变旧响应。已接入版本事实，但 C1 expected 的实时锁内业务校验尚待命令执行器。K.source_scope 迁移只从 K.submission→S.scope 可证明的 TEMP 行回填；原 owner、order、cycle、state 均不变。

同仓 scope、TEMP-only scope、S/round、原 freeze device、set/object、verification/set、receipt/request/scope/原S身份及 event/receipt 的关系由复合 FK 约束。SQL 不替代以下后续窄写者校验：原 mapping 与 nullable canonical 精确对应、set/hash/verification 语义一致、STOP_PROOF kind、K 非空 order 与当时 canonical 一致、原响应和 token/摘要、request.resource_id 与 receipt.id 双向一致、可信操作者和权限、不可变表禁止业务 UPDATE/DELETE。当前没有新写服务，因此不能把基础表宣称为这些语义已完成。

旧正式恢复对 SOURCE_RESERVED（包括非空同 order）返回占用冲突；正式续作显式拒绝来源证据 owner，批转换只包含旧 owner 状态。正式提交、PC 保存/候选、TEMP 待审、条码/准备投影仍按 K 行存在保守阻止。新库存不能清除 K 或改原周期。

## 协议字节入口

生产入口 [TemporaryProtocol](../../../server/src/main/java/com/acrylic/outbound/temporary/protocol/TemporaryProtocol.java) 独立于旧正文协议。route scope `r06.command.v1`、version 1、issuer `acrylic-outbound`、audience `acrylic-r06-command-receipt-v1` 是本批固定常量，但无实际路由或签发服务使用。

`canonical(byte[])` 接受根 JSON 任意合法值，包括 null；`command(byte[])` 要求 exact command shape；receipt/control hash 要求 object，但不作尚未实施的业务 DTO/可信事实校验。输入与规范输出均最多 262144 字节。UTF-8 严格解码，拒绝 BOM、重复键、尾随 JSON、浮点、超安全整数和孤立代理项；对象键按 Java UTF-16 排序，数组原序，不 trim/Unicode 归一化。

三个摘要域分别为 UTF-8 `ACRYLIC-R06/COMMAND/1\n`、`ACRYLIC-R06/RECEIPT/1\n`、`ACRYLIC-R06/DERIVED-CONTROL/1\n` 加规范 JSON 字节，`\n` 是单个 LF。`originalResponseSha256` 直接散列合法 UTF-8 原字节，包括空白/尾换行，不重编码。C0 原提案/签名向量未改，新向量由独立 W3 消费真实入口验证。

命令沿 C0 BODY 字段完整出现；expected 追加九个 E2 字段：evidenceSetId、evidenceSetSha256、verificationId、verificationVersion、verificationAlgorithmVersion、dispositionManifestSha256、dispositionManifestVersion、retainedManifestSha256、retainedManifestVersion。PHYSICAL_DISPOSITION 要求前五及 disposition 二项非空、retained 二项 null；REOPEN_WORK 要求前五及 retained 二项非空、disposition 二项 null；其余五族九项全 null。所有 UUID 规范小写，摘要小写 SHA-256，版本/轮次为十进制字符串（最长18位）；新物理版本为正数。mapping=0 对应 null canonical/slotVersion/targetRevision。所有字段都参与命令域摘要。

NORMAL_CONFIRM 允许空理由；其余命令理由非空白。理由不修改原值，最多1000 UTF-16 code units。本批 shape/hash 不是决定权限，不因格式合法就授予操作权。客户端 commandId 为 actor/scope 下原键，不是 receipt 全局主键；数据库 receipt 只以服务端 UUID 为主键，无全局 client commandId 唯一索引。

## 实际验证及待验

首个冻结点：作者56单元全部通过，V34新空库迁移、5项消费者IT与JAR通过。作者与 W2 非空升级、W3 跨语言验证分列，不累加为组合结果。PC/投影/重新发布、真实共同K并发及更广旧路径回归仍在后续作者验收队列，当前5项不宣称全面兼容通过。

首轮作者56单元中55通过，1项原样本缺失失败；原样本按授权复制到忽略路径后56项全部通过。首轮日志保留，随后 XML 已被复跑覆盖；最终 XML 单独复制留存。第一次 IT 启动因 Gradle `--tests` 参数位置错误，未运行测试或迁移，日志保留。其后V34先后遇到ALTER缺ADD CHECK、旧索引同名、最终CHECK多括号三处DDL错误，每次5个Context失败属于同根因启动失败，不是15个不同业务场景；三份失败库/日志/XML均保留。修正后的retry04迁移成功，首5IT有1个新测试复用正式/TEMP submissionId导致正确400拒绝；改独立正式fixture后同库复跑5IT全部通过，原失败XML保留。静态核对完整括号、新约束/索引名与V1–V33重复性通过；首次核对脚本因Windows默认GBK读取旧脚本失败，改明确UTF-8后通过。

作者新实例仅 `127.0.0.1:19323` / 本树 `.local/mysql19323/data`。原始库`acrylic_outbound_foundation_20260921`、`_r2`、`_retry03`为失败断点，最终`_retry04`使用独立`foundation_retry04`账号和`.local/private-retry04`，四库均保留。本机启动及每次验证先核端口、datadir、库与账号；原库/旧实例保持。非空 V33 逐字段升级和负向/失败恢复由 W2 的 19324 独立资料验证，不能用作者空库通过替代。

MySQL DDL 隐式提交，V34 后半段失败不能假定文件事务回滚；保留失败库/日志，核 schema/Flyway 后使用新受控副本，不自动清库/repair。新事实存在后旧 JAR 无兼容承诺，也不提供 DROP 新列降级。

旧 R09 consistency 工具仅支持 V21/28/32/33，当前会拒绝 V34，保持该保护；后续独立扩展和完整恢复验收。HTTP、设备、实际上传/命令事务、签名回执、共同记账最终成功、处置/续作、完整 C09/C15 与 R09 fence 均未完成。本批不合 main、不部署、不恢复旧 B/C/D。
