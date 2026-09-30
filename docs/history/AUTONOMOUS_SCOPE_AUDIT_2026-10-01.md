# 自主接续范围核对（2026-10-01）

## 接续坐标与证据等级

- 主树 D:/acrylic-pda-outbound，codex/development；输入与实际远端同为 c6bac92b02dc525e1d5b02cbcfaef892e0199bd6。
- 环境快照：.local/autonomous-20260930/s2-20261001-initial/。无在途构建/AVD；两份受保护 PDA 文档摘要一致。D 可用 33924849664 字节。
- 本项为源码及历史证据核对，新增业务测试为 0；不将历史通过标为本日运行验收。版本事实仅见看板生成事实块。

## S2：复用已验范围，保留未覆盖边界

依据 [未提交来源验收](R06_UNSUBMITTED_SOURCE_ACCEPTANCE_2026-09-25.md) 和 [归档信任验收](R06_DRAFT_ARCHIVE_TRUST_ACCEPTANCE_2026-09-25.md)，以及主树 UnsubmittedSource / SourceEvidenceIntake / DraftEvidenceArchives 实现：

1. 已提交归属优先，未提交 fallback 不允许摘要或跨仓冲突降级；原 authorization、原准备包、原记录和审计连续前缀均核验。历史 attempt67 的真实 HTTP/数据库证据覆盖缺件、伪造、跨仓及其他来源不占当前 K。
2. 历史归档由配置可信公钥、持久索引、原签名和原文件共同核验，不接受上传公钥自证。历史 attempt63 覆盖缺历史钥、错仓、被替换事件和损坏归档文件的失败关闭。
3. 因此不重复实现或重跑这两个已验子范围。本次只登记历史复用；新来源完整计划、跨来源续作与原机现场身份不在上述证据范围。
4. 其他未提交 TEMP 的完整计划不在协议快照中，不能由现有记录推导其计划/装车/确认资格。原来源事实通过不等于该来源可以确认。
5. S1 只新增首轮单候选 mapping2 独立消费；多候选排除、mapping3+、高轮次、短装关联组合仍未验。A1.2 不整体收口。

## S3：政策与实现依赖

- P-R06-01 的卸货释放政策已定，不重新询问；但 TEMP REJECT 的独立写授权没有在 CommandPermissions 中注册。R06_LEDGER_RUNTIME/IMPLEMENTATION 明确历史 confirm 资格仅限读，不能升格为 REJECT 写权。
- 按自主目标“政策无法推出则记录并跳过”，暂跳过 S3a 的 REJECT 命令及依赖该终态的整链验收；不虚构权限码或扩大普通确认权限。
- PHYSICAL_DISPOSITION 的 order:dispose 与 REOPEN_WORK 的 order:resume 有既定码，但原停止集合、逐包处置、释放/续作及高轮次仍需独立实现和验收，不能因码存在就认定已完成。它们保留为后续可独立拆分项，不因 REJECT 权限未定而永久跳过。
- 不修改既有首轮记账/拒绝守卫；不改变已验行为或生产默认值。

## 下一步

先核对 R07 的较新实现/实测，消除旧计划的“未实现/未跑”文字与源码冲突，再选择一个有明确政策、可独立验证的真实缺口。未知权限、现场与故障换机边界继续单列。

## S4a：较新证据与真实缺口

- [S39 outbox 记录](R09_C2_B1_3_S39_A2_3_OUTBOX_2026-09-30.md)及 HandoverActivation/HandoverStore 表明 ACTIVATE 出站通知已经实现并有历史服务层验证；旧 S34/S35 的“outbox 完全未实现”不再适用。
- S39 明确写后回滚没有实跑；源码外事务在实际 outbox 后还执行回执和 AuditWriter.append，因而可用 test-only 审计边界注入异常，不需删除数据库守卫或改变产品。
- [主管分离裁定 B](R09_C2_A2_1_RULING_B_IMPLEMENTATION_2026-09-30.md)、现有角色迁移与 Command 授权表明主管角色/分离已经实现；旧“无 SUPERVISOR”说明不能作为重新实施依据。
- 选择 S4b 只补 outbox 写后回滚实测；实际两次 IT 与一次离线单测均未通过，已触发止损。退出标准、失败证据和源码保全见[本日停报](AUTONOMOUS_PROGRESS_2026-10-01.md)。没有取得新的业务验收；不宣称 R07 全量审计完成。
