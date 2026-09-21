# R06 E2 非空迁移与验收矩阵

日期：2026-09-21。基线 `c3009b6` / Flyway V33。**下列数据库、并发、崩溃、HTTP 和 Android 场景全部为待验要求，本批未运行。** 已做的只是源码与文档核对，见 [交接](HANDOFF.md)。方案见 [来源模型](SOURCE_OWNERSHIP_MODEL.md) 与 [完整原机证据](ORIGINAL_DEVICE_EVIDENCE.md)。

## 1. 非空升级步骤

1. 由总调度指定唯一迁移/共同 K 执行者，在实施起点重新盘点 Flyway 最大版本、共享文件归属与 C1 所需对象；当前未预占 V34 或其他编号。提交不可修改 V1-V33 历史脚本/checksum。先准备迁移前关系校验和失败报告，再写实际迁移。
2. 从授权的非空 V33 合成副本建立新的隔离升级库。固定精确 host/port/database/datadir，默认不开跑，错误环境在任何 DDL/DML 前硬拒绝。原 R05/R06 恢复库、原种子、旧 lab、现网、设备均不升级或重置。
3. 升级前保存所有旧表原字段、主键/唯一键/FK/CHECK、每表行数及按主键排序的原值摘要，另保存原文件清单及字节 SHA；receipt_text 以原 UTF-8 文本逐字节校验，JSON 字段区分数据库规范值与原文件字节。不用表数相等代替数据比较。
4. 先新增证据/集合/处置等父表和 nullable 归属列，再回填可证明的既有 TEMP K.source_scope。回填只用 `K.submission_id → submission.scope_id → TEMP scope`，核 S/round、原资格、仓库及 K.order_id 与已有 canonical 一致；遇丢失/歧义/冲突停止并保留报告，不猜来源、不改旧 K owner 或周期。无 TEMP S 的正式/PC 行保持新增列 null。
5. 校验新增复合引用和互斥分支后再施加约束，最后允许 order_id 在明示新来源分支中为 null。旧分支仍要求真实 order；同一个 K 只能有一个 owner；null 检查用显式 IS NULL/IS NOT NULL，注意 MySQL CHECK 的 UNKNOWN 不会替代完整分支约束。
6. 所有消费者兼容与 null order 保护先进入同一候选产物；未完成时新写入口保持关闭。没有“新 schema 可写、旧 JAR 仍继续处理新 null 行”的滚动兼容承诺。迁移维护窗口/停写策略由后续实施批验证，本批不执行部署。
7. 升级后逐字段比较旧值，允许变化仅是预先登记的新列回填/新增表/迁移历史；原 K.order_id/submission/各 owner/state/cycle、S/receipt、正文/块、旧表原字段必须一致。正文前 ABORT 的 canonical/assignment/received hash 仍 null，订单/FH 计数器不增，旧权限不自动扩展。
8. 在新副本故障注入中途 DDL/回填失败、重复启动和锁超时，查实际 schema 与 Flyway 历史后走明确恢复方案。MySQL DDL 不能声称整文件事务自动回滚；不自动 repair、关闭 FK、清表或删除失败历史掩盖错误。失败库/日志保留，修复验证使用另一个受控副本。

后续迁移使用现有 MySQL 数据类型与 `SET SESSION lock_wait_timeout=10` 习惯，并实际记录 DDL 阻塞和回填耗时。不能照搬 PostgreSQL 的 NOT VALID/CREATE INDEX CONCURRENTLY。回滚安全策略是保留新数据并关闭新入口或恢复经核验的独立备份；一旦有无订单新 K/新证据，旧 JAR 和简单 DROP COLUMN 不构成可用降级方案。

## 2. V33 必备非空夹具

| 组 | 升级前实际存在的数据 | 升级后保护断言 |
| --- | --- | --- |
| M01 | 正式 REGISTERED/RECEIVING/REVIEW_REQUIRED/SUCCEEDED/ABORTED/REJECTED；有/无扣账原结果 | 原控制/assignment/回执/hash/result/ledger 不变；autoDeduct=false 不补账 |
| M02 | 正式恢复、CONTINUATION_RESERVED、QUARANTINED、PC_RESERVED、PC DISPATCHED/隔离 | 各原 owner 与 CHECK 仍有效；新来源分支不能使用旧 owner 的空组合绕过 |
| M03 | TEMP A 无正文 ABORT；B 部分块 ABORT；收齐块未 finalize；C 完整接受后 ABORT；REVIEW_REQUIRED | 保持每个 null/declared/received 区别；原来源、字节/签名、块索引与 canonical/slot 不变 |
| M04 | TEMP 待审 K、有冲突而未取得 K 的扫描、不同仓库正式/PC 同时存在 | 只回填可证明 TEMP 归属，冲突事实不变成新占用；K 主键仍 bale_id，0 双占用 |
| M05 | 正式/TEMP 混合原设备审计、连续 checkpoint、签名归档、五份准备/激活及 FH 全局日计数器 | 原序号/原字节及引用闭包保持；升级不造事件/发号/重签 |
| M06 | 被故意破坏的 K→S/source/canonical 关系、缺文件、损坏签名分别在独立负向副本 | 前检/核验明确失败，不通过伪归属/空订单/新 key 掩盖；失败报告与正常升级分开 |

M06 负向数据仅在新专用副本生成，不能在原历史恢复库改事实。原 V33 夹具中的合法数据不得为让测试通过而重新 seed 或重新生成旧取消签名。

## 3. 来源、集合、事务与并发待验

| 编号 | 场景 | 必须核对的持久结果 |
| --- | --- | --- |
| E01 | A/B/C 补独立原机证据；同 set/object 原键丢响应重查 | 原 S/receipt/payload/upload/null 不变；相同原响应；无额外订单/FH/assignment/result/ledger |
| E02 | 同键异字节、异 source/S/round/原资格/原人/机/仓 | 明确冲突/无权限，原证据/占用不变；原键不产生第二结果 |
| E03 | 缺/重捕获、改原 ID/hash、遗漏 VOID/失败确认/未决输入、summary 伪造 | 可保存为收到的冲突证据，但不能标完整/释放/续作；错误分类不吞失败 |
| E04 | 审计中段缺口、只提供 TEMP 事件、归档损坏/未知 key、停止后迟到原事实 | 全设备连续性不通过；旧验证保留但 currentEffect 失效；无人工 MAX 修补 |
| E05 | manifest 漏页/混页/多页重试/旧清单游标、32MiB 边界、归档分段 | 同键重放稳定；完整性只在闭合集成立；不截断数据、不泄漏私有路径 |
| E06 | 文件写到一半、耐久前崩溃、文件已存引用未提交、DB 提交丢响应 | 不误报 RECEIVED/完整；孤儿不授能力；重试恢复原引用，引用后读必须核 bytes/hash |
| E07 | SOURCE_RESERVED 与正式 submit/PC 保存/TEMP 接受同时争一包；正反提交次序 | 真正两连接，共同 B/K 唯一裁决；一个占用胜者，另一证据可保留冲突；无空指针/双账/覆盖 owner |
| E08 | null order K 与正式 RecoveryStock、正式/PC 续作/处置、库存全量重新发布 | 旧路径受控冲突不 500；新快照不释放 K/重置 cycle；条码与新准备仍 RESERVED/QUARANTINED |
| E09 | 两来源相同包/不同原 cycle、无当前库存、本来源已知 K 不在集合 | 原占用不被另一 set 覆盖；冲突阻止释放/续作；相同计数但不同包也拒绝 |
| E10 | 部分集合保存已知保守占用，后补完整集合 | 所有历史事实保留，旧 K 精确归属；PARTIAL 不能获得处置授权；全量验证还需反向集合相等 |
| E11 | reject/abort 与 evidence/accept 同时；原 abort 先提交与正文先提交两顺序 | 迟到接受不复活终态；原回执不变；完整正文只在合法先接受路径出现，K 不因终态释放 |
| E12 | RELEASE/QUARANTINE/RETAIN 混合、整车卸下、有隔离/未知包/零电子有效包 | 逐包完整分割，现场证明独立；隔离继续 K；结束不造零包成功、FH、账务或出库放行 |
| E13 | 处置途中审计/回执签名/命令持久失败，重复命令、不同键争同版本 | 全事务回滚或同原回执；无半车释放、双处置，变化通知与 K 同提交 |
| E14 | 关联/current/round/原人提示改变与处置/续作竞争 | L2 集合冲突退出，不持后序锁补 U/C；C1 expected 失效不改旧命令；已提交原处置不重复 |
| E15 | 原人原机续作，原资格到期/撤权/停机，Android 激活存储失败/重启 | 历史证据保留；只有新受控授权+原签名耐久安装才可新增；旧 S 不重开、保留集不丢 |
| E16 | E1 读取与新 owner 转换并发、每个文件接口跨仓/撤权/过期 | 读取固定一致快照并重鉴权；旧字段不升格完整，新来源视图不遗漏占用或暴露他仓 |
| E17 | 恢复模式/跨代次/异机/未知历史 key | 普通新写拒绝，保留原 source epoch/字节/K；不打开 R07/R09 尚未实现白名单 |
| E18 | SUCCEEDED 来源尝试拒绝/卸货/续作、同条码新库存再出现 | 原成功/K/dispatch/ledger 不变，明确走 R07；不得给新周期自动放行 |

并发沿 [M2b C07/C08/C14/C15](../R06_M2B/HANDOFF.md) 补全真实 MySQL 两连接：记录数据库版本/隔离级别、连接 ID、屏障 SQL、P_S 或等价锁等待证据、提交顺序和终态，不能用 sleep/线程未结束冒充已证实某锁。每个负向断言检查回滚后全表/引用/K，不只看 HTTP 错误码。首次失败、修前红测、环境错误与最终通过分别计数。

## 4. 跨端与依赖门槛

| 阶段 | 允许继续 | 尚不能声明 |
| --- | --- | --- |
| E2 文档审查通过 | 唯一实施者冻结关系、命令依赖和迁移计划 | 无数据库支持/证据写/放行能力 |
| 后端非空迁移+共同 K+原机证据服务通过 | 内部合成 API/事务验证，发布新协议向量供 Android 消费 | Android 已完整停止、跨进程可靠恢复、现场实物为空 |
| Android 新证据/停止/签名持久化+HTTP 联调通过 | 日常原机证据链按真实完成范围验收 | 异机代传、跨代次恢复、断电/长时/现场自动通过 |
| 来源处置/续作+C1 命令原子回执通过 | 对应已实现动作按权限启用 | TEMP 最终成功、共享账务、清理能力 |
| R09 新 schema 检查器与完整恢复通过 | 该已验证版本的备份/恢复能力 | 旧 V33 工具自动支持新表、未知 R07 调整链可忽略 |

所有实际测试结果须绑定精确提交、环境与证据目录。第三轮 43 单元/60 IT/153 离线/116 路径是历史组合结果，本批没有复跑，不加到 E2 文档核验数量。
