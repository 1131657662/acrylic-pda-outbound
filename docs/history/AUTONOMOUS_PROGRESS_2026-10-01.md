# 自主开发停报（2026-10-01，Asia/Shanghai）

本记录的三次失败发生在 UTC 2026-09-30；本地日期为 2026-10-01。**目标未完成，已触发同一子项连续三次失败止损。** 795 未分配/创建，停止后没有继续改测试或运行第四次验证。本次仅提交范围核对及停报文档，不把失败源码作为已验代码推送。

## 1. 真实断点与已完成范围

- 主树 D:/acrylic-pda-outbound，codex/development；输入与已核远端 c6bac92b02dc525e1d5b02cbcfaef892e0199bd6。版本事实仅见[看板生成事实块](../ORCHESTRATION_STATUS.md)。
- A1.1 保持 801050baf7cae3ef8e9a7774d72fe96c4ee72efb 的[既定验收](R06_A1_1_CODEX_2026-09-30.md)，不重跑已验批次。
- S1 实现 f892586580423abedbe5b38721cbf89b3e908245、文档同步 c6bac92b02dc525e1d5b02cbcfaef892e0199bd6 已推送；真实 HTTP 与 Python 独立消费范围见[前阶段进度](AUTONOMOUS_PROGRESS_2026-09-30.md)。
- 本轮 S2/S4a 源码及历史范围核对记录见[范围审计](AUTONOMOUS_SCOPE_AUDIT_2026-10-01.md)。历史复用不算本轮运行验收。

## 2. 方案与子项状态

| 子项 | 本轮结果 | 下一边界 |
| --- | --- | --- |
| S2 来源可信事实 | 既有未提交来源/归档信任历史实测可复用，未重做或重跑 | 未提交 TEMP 完整计划、现场可信身份、跨来源续作仍未验；A1.2 未整体收口 |
| S3a TEMP 拒绝 | 缺独立写授权依据，按目标政策未知规则跳过 | P-R06-01 卸货释放政策已定；REJECT 不能借历史 confirm 读资格执行 |
| S3b 释放/续作 | 未启动 | order:dispose/order:resume 有既定码，但原停止/逐包处置/高轮次必须独立实现验证 |
| S4a R07 实际缺口 | 确认 outbox、主管角色分离、部分反例/并发已有较新实现/历史验证；不照抄旧“全未实现” | 未完成全 R07 清单审计 |
| S4b outbox 写后回滚 | 新测试开发未完成；2 次 IT 失败＋1 次离线单测失败 | 触发止损；未到 ACTIVATE 故障点，没有回滚通过证据 |

本轮实现/验收提交 SHA：**无**。文档提交与实际远端 SHA 记录在 .local/autonomous-20261001/stop-closeout/git-final.json，或本报告最后一次 Git 提交；不能将其算作业务交付。

## 3. 三次失败（没有自动掩盖或刷绿）

| 次序 | 实际执行 | 结果与发生位置 | 定性 |
| --- | --- | --- | --- |
| 1 | attempt793，E2aHandoverRollbackIT | 1 test / 1 failure / 0 error / 0 skip。构造器查询未占用包返回零行，尚未调用 REQUEST | 新夹具假设错误：基础种子唯一包已 SOURCE_RESERVED。随后只在新测试内增加独立合成包/占用，旧包不动 |
| 2 | attempt794，同一 IT | 1/1/0/0。REQUEST→APPROVE→PREPARE 完成，ACTIVATE 前 baseline snapshot 因 LocalDateTime 无序列化模块失败 | 新测试快照编码错误；注册时间模块并补离线单测，未改产品 |
| 3 | HandoverRollbackFactsTest 离线单测 | 1/1/0/0。规范化行数据抛 INVALID_SUBMISSION；未创建下一数据库 attempt | 测试误把数据库原始值交给业务协议规范化器。协议仅接受文本/null/布尔/安全整数；BinaryNode、超过安全整数范围等原始数据库值不在其适用域。不是产品规范化器应放宽的理由 |

第三次后只做证据保全与断点收尾。两次真实库均已认证 shutdown；未关闭原服务。三次失败全部归属本测试子项，不拆名清零；也不写成“三次数据库 attempt”。尚无实际产品缺陷证据。

## 4. 自主决策与源码保全

1. S2 用源码与较新历史证据消除“待实现”的旧描述；未将原始记录信任等同于原未提交 TEMP 完整计划通过。
2. REJECT 独立写权限无依据，按自主目标登记跳过；不恢复旧“所有产品代码都禁止”的已废授权限制。
3. S4b 选择补真实事务回滚证据：测试专用 Primary AuditWriter 先真实 INSERT 审计，再抛异常，计划证明 outbox/资格/回执/案卷/轮次/审计全回滚及原键恢复。**该故障点没有实际到达。**
4. 新测试使用迁移后既定 SUPERVISOR 角色和分离身份；没有给 WAREHOUSE_ADMIN 补回权限。原机停止、原 ABORT 和附加包都是明确合成夹具，不声称设备半场、实物或现场证据有效。
5. 七份新测试草稿已逐文件 SHA-256 校验后移出活动测试目录，完整保存在 .local/autonomous-20261001/stopped-source/server/src/test/java/com/acrylic/outbound/e2averify/。不把已知红测留入默认测试发现范围；未删除源码证据，也未改动原有测试文件。对应 source-manifest.json 保留原路径/摘要。
6. 产品源码、历史迁移、契约、默认开关、ConfirmedV1Adapter 以及两份受保护 PDA 文档零改动。测试仅在隔离进程启用 handover 命令，生产默认仍 false；localDeletionAllowed=false。
7. 停止时清理仅限本轮新建 793/794 datadir：认证停库、完整 SQL dump、完整 ZIP，逐个回读校验各 290 个文件的 SHA-256 后才移除可重建 datadir。初次 archive 计数把 1 个空目录也算文件，守卫提前阻止删除；后续按 290 文件＋1 目录验证，原失败脚本/说明保留。历史 attempt 与失败证据均未删。

## 5. 待决策与接续条件

- **本次止损**：需要用户解除本次三次失败停止点，才能继续该测试子项；不自分配 795，不转去其他子项绕过全局止损。重新进入时先核完整工作区、监听和源草稿摘要。
- 修正方向仅限测试：使用独立、确定性的数据库快照编码，显式保留字节、时间、null 和大整数；先离线验证，不能改 SubmissionJson 的已验协议允许集来迁就快照。此方向尚未实现、尚未通过。
- 业务未决继续保留：TEMP REJECT 写权限、候选排除授权；既定卸货释放、人工关联与跨来源独立审计政策不重新提问。
- 主管角色分离已有较新实现/历史验证，不能再列为“没有主管角色”；剩余 R07 附件/故障/更正等项须按实际源码另核，不以本轮未深入冒称全部未实现。

## 6. 未验边界

S4b 的写后真实回滚、非空占用守恒、同键恢复、重启读回均**未验**；794 准备完成只说明前置走通，不算这些退出标准通过。本轮未跑全仓回归、真实业务 HTTP、outbox worker、Android/APK/lint、模拟器或真机测试；启动了回环 HTTP 服务不等于测了 HTTP 业务链。

A1.2/A1.3/R07 均未整体收口；mapping3+、高轮次、候选排除、短装关联组合、跨来源续作、FORMAL 独立时间盒、现场身份/Keystore/真实竞态/断电、R05 原资料/旧 BCD、R09 keyring/lease/W-2 及其既有待验项保持。未部署、不接原库/现网、不合 main、不打标签/Release。

## 7. 证据索引

| 证据 | 落点 |
| --- | --- |
| 开始 HEAD/远端/保护摘要/进程与磁盘 | .local/autonomous-20260930/s2-20261001-initial/ |
| 初次及修正后的离线编译、parseErrors=0 | .local/autonomous-20261001/offline-1/、offline-2/、recheck/offline-1/、recheck-2/offline-1/ |
| 两次 IT 原 XML、日志、失败定性、dump、ZIP、marker | .local/e2a-cross-20260925-793/、.local/e2a-cross-20260925-794/ |
| 第三次单测红日志与 XML | .local/autonomous-20261001/recheck-2/snapshot-unit.log；stop-closeout/TEST-com.acrylic.outbound.e2averify.HandoverRollbackFactsTest.xml |
| 七份未完成源码与路径/摘要 | stopped-source/；stop-closeout/source-manifest.json |
| 止损、资源、真机及最终 Git | stop-closeout/stop-condition.json、disk-*.json、device-isolation.json、protected.json、git-final.json |

同一助手作者/自审，无独立人员复审。本次没有新增通过的测试结果。编译成功和脚本 parseErrors=0 不能替代运行验收。

## 8. 磁盘与真机

- D 开始 33924849664 字节；793 后 33708392448；两轮失败及保全过程的清理前见 stop-closeout/disk-before.json；保全清理后 33896718336 字节。所有观测高于 20 GiB。
- 22D2910042 的 adb devices -l 起止快照逐字节一致；目标设备命令 0、AVD 启动 0。每轮 device-isolation.json 与总收尾快照保留。
- 19330/19434 已释放；原服务未停。七份草稿只保存在本机 .local，不在 Git 远端备份范围内。

## 9. 自动接续第二轮止损复核

- 输入及已核远端 e615156645073ab287d6d228494db7ee73e037d8；证据 .local/autonomous-20261001/revalidation-2/。
- 上一目标轮有实际推进（新增失败证据、源码保全及停报提交）；本轮没有业务推进，只复核同一个 SUBITEM_THREE_CONSECUTIVE_FAILURES 阻断。一般“继续目标”没有解除目标内三次失败停止线。
- 七份草稿摘要、两份失败库 ZIP/dump 摘要与三份 1/1/0/0 XML 均一致；795 不存在，活动重型任务和测试端口监听均为 0。不是等待仍在运行的测试，也没有重开已终止的会话。
- 真机清单相同、目标设备命令 0；两份受保护文档摘要一致。D 可用 33896501248 字节。
- 同一阻断累计两轮目标观测，未达三轮 blocked 审计门槛；目标保持 active、未完成。本轮仅保存文档复核，不修源码、不跑测试、不另换子项绕过止损。提交结果见 revalidation-2/git-final.json。
