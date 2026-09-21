# R06 E2a 合同实施计划

本计划只描述未来实现顺序；当前提交仅包含设计文档，无源码、迁移、OpenAPI 或测试行为变化。

## 1. 先决审查与最小 schema 缺口

1. W2 只读核对 V34 实际 DDL、外键和索引，确认复用 `source_evidence_object/set/item/verification` 的列、容量和关系；`manifest_bytes` 上限为 256 KiB，`stop_object_id` 与 `audit_through_seq` 均为非空约束，OPEN 不预插 set。
2. 单独确认 `temporary_command_receipt` 的七类 `command_type` CHECK 不承载证据接收；使用最小 `source_evidence_intake_receipt` 结构（原键、principal/route、canonical request bytes/hash、原响应 bytes/hash、server RSA keyId/signature、状态、object/set/verification 引用和当前效力动态评估），不复制 source evidence 明细。迁移编号和最终 DDL 由主协调指定的唯一迁移执行者分配。
3. intake receipt 必须以 `(principal,route,originalKey)` 唯一；receipt→set 使用 V34 现有 `(id,scope_id,submission_id,work_round)` 父键，receipt→verification 使用现有 `(id,set_id,scope_id)` 父键并传递同一 set 上下文。原回执 INSERT-only；不存在的 FK/CHECK/容量缺口返回明确错误，不按幂等回放成功。

## 2. 协议与实现顺序

1. 使用合同已固定的 `routeScope=r06.e2a.evidence.v1`、`issuer=acrylic-outbound`、`aud=acrylic-r06-e2a-intake-v1`、schemaVersion=1、RS256/base64url 无填充及摘要域；EC 公钥为标准 base64→DER X.509 SPKI，`keyId=SHA-256(DER)` 小写十六进制，P-256 `SHA256withECDSA` 签名为标准 base64 的 ASN.1 DER。补充 Python/Java/Android 交叉向量，但不把向量结果预写成通过。
2. 实现规范 JSON、原键幂等、原响应原字节保存和 `currentEffect` 分离。认证/格式/篡改错误在持久化前拒绝；未知历史 key 只允许 `UNVERIFIABLE` 非可信证据。
3. 实现 manifest/page 流式校验：manifest 受 V34 256 KiB 限制，page 256 KiB、object 32 MiB、4096 pages、128 MiB/set，且所有上限同时满足；严格全序、去重、缺页、cursor、终止标记和同 manifest 续传。缺页阶段保存 PARTIAL，finalize/VERIFY 缺页失败，不生成完整验证；已独立核验且满足 B/K 条件的逐包事实仍可保守 `SOURCE_RESERVED`，未经核验不得占用。技术上限不得变成业务包数限制。
4. 实现 STOP_PROOF 与全设备审计核验：先记录不含 manifest/set/page 摘要和最终签名的停止审计事件，再由 `STOP_PROOF.core` 绑定已计算 manifestId/version、manifestBytesHash、setHash，签名只覆盖域+canonical(core)；FORMAL/TEMP 共用 localSeq，明细包含 INVALID/VOID/失败/丢弃/未决；未有 Android 停止/重启耐久或 EC keyring 时保持 PARTIAL/UNVERIFIABLE。
5. 沿现有实际锁入口只调用一次：E `server_state` 读屏障 → G `user_management_guard` 写屏障 → UUID 排序 `app_user`/设备/session → `temporary_authorization` → `temporary_work_origin` → `submission_scope` → `temporary_decision_state` → `outbound_order`/slot → `submission`；随后按固定表序和 UUID 顺序锁 evidence set/object/item/verification/intake rows，再按 bale_id 锁 B/K。原键第一次查在新写权和 epoch 判定前，完整锁后第二次查仍在新写权前。文件先耐久、后 DB 引用；只有 intake 自然键唯一冲突在回滚后的独立读事务重放原回执，其它 FK/CHECK/锁错误明确失败。
6. `OPEN/PAGE/STOP/VERIFY` 按合同固定路由和阶段执行：OPEN 只建 intake envelope，STOP 匹配后才建 V34 set，VERIFY 追加 verification。只读查询返回原接收回执、证据封套和动态 currentEffect；不调用七类命令，不创建 S/FH/assignment/payload/upload，不开启终态能力。E2a PDA 本人读写使用新 `pda:recover` 接点；现有 Web `temporary_order:confirm` 读取实现只复用来源关系校验，不冒称为 intake 写入口。

## 3. 验收阶段

先做纯规范/摘要向量，再做内部合成 handler 的事务故障矩阵；随后在隔离 V34 非空库验证并发、权限、文件耐久和恢复。Android 仅在服务器协议固定后接入本地 Room 停止事务、EC 签名、分页续传和重启恢复。每阶段分别记录源码、构建、数据库、HTTP、Android 和现场状态，不能以内部合成通过替代真实设备证据。

验收输出必须保留：原键首次请求/重试/丢响应证据、STOP raw 与 EC keyId、manifest/page/item 原字节及摘要、服务器原响应 raw、验证 findings、文件/DB 故障前后状态、锁等待/冲突、权限撤销、未知 key 和所有负向结果。`VERIFIED_COMPLETE_AT_STOP` 只在原机停止事件与耐久证据齐全时允许；否则是 PARTIAL/UNVERIFIABLE。

## 4. 交付边界

实现完成前不合 `main`、不部署、不启用生产决定 handler/POST，不改变 V34、正常确认空理由、`localDeletionAllowed=false` 或 R09。实现者提交时须使用四段正文：改动、验证、未完成/已知问题、版本/兼容；本设计提交本身注明“仅设计，无运行行为，未测试”，并交 W2 schema 审、W3 协议负向审和主协调最终整合。
