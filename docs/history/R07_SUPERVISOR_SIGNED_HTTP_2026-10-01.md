# R07 主管真实签名审批 HTTP 专项（2026-10-01，Asia/Shanghai）

## 依据与范围

输入与已核远端为 codex/development 的 67af2c542143552a092f55262268454b233baede。依据[剩余计划](../REMAINING_DEVELOPMENT_PLAN.md) A2.4“主管审批……逐条实现并验证”，补[裁定 B](R09_C2_A2_1_RULING_B_IMPLEMENTATION_2026-09-30.md) §2 主管通过权限后止于 SIGNING_KEY_UNAVAILABLE、§7 独立审批人只在旧 schema 角色上完成整链之间的证据缺口。

仅新增五份测试域 Java 文件。使用既有迁移创建的 SUPERVISOR；只在隔离进程开启既有更正命令和回环 HTTP Cookie 配置。产品源码、既有测试、迁移、契约与默认开关均未改。TEMP 成功源由显式服务装配产生，身份、来源和隔离密钥均为合成夹具，不是现场身份、Keystore 或产品 TEMP 确认 HTTP 验收。

## 退出标准与实际结果

| 标准 | attempt797 的证据 |
| --- | --- |
| 迁移后的审批/建档权限分离 | 普通建档人有 outbound:correct、无 correction:approve；主管相反；夹具查询实际角色关系断言 |
| 真实会话、CSRF 与拒绝 | Web 登录、CREATE/APPROVE 均真实 HTTP；建档人审批、主管建档各 403 CORRECTION_PERMISSION_DENIED；临时双角色仍 403 CORRECTION_SELF_APPROVAL_DENIED，随后撤回该测试成员关系 |
| 主管成功审批并取得有效签名 | APPROVE 200 / EFFECTIVE，actor 为独立主管；用隔离公钥与 JCA 独立验证 RS256、kid、issuer、audience 和签内外正文一致，不调用产品签名服务验签 |
| 原键恢复与精确一次 | 同请求重放的响应原字节相等；同键改 reason 返回 409 IDEMPOTENCY_CONTENT_MISMATCH；一条更正 ledger、一条 effect、两条 CREATE/APPROVE receipt |
| 原事实与净结果 | 原结果及所列来源/原件/映射/占用/出库/库存账务投影不变；净结果 version=2、包数仍 2、重量由 601500g 到 601501g，调整 quantity_delta=0 / weight_delta_g=1 |
| 能力边界 | writeConfirmationAvailable、releaseAllowed、mayResumeScanning、stockReleased、localDeletionAllowed 全 false；成功更正不自行放行或删除 |

本项一次 attempt，没有失败或重跑。数据库级不可变性只覆盖测试明确列出的投影，不宣称每表每列完整快照一致。JCA 签名计算独立，但 JSON 解析/协议规范化复用产品工具，因此不将其称为完整协议独立消费者。

## 实跑与自审

- 离线 Java 编译成功；既有隔离守卫 3 tests / 0 failures / 0 errors / 0 skipped；脚本 parseErrors=0。
- attempt797：1 test / 0 failures / 0 errors / 0 skipped，javaExit=0；start/completed marker、实际 HTTP 响应及原 XML 均保留。
- 最终完整服务端单测：134 suites / 2847 tests / 0 failures / 0 errors / 1 既有 skip。
- 同一助手实施与自审：检查真实 HTTP、权限分离、原字节重放、验签来源、拒绝后无生效、精确一次与原事实投影；无新产品 Bean/Primary，无冻结白名单修改。无独立人员复审。

## 证据与资源

- .local/autonomous-20261001/resume-supervisor/：preflight、offline、full-regression、closeout；每阶段脚本及摘要保留。
- .local/e2a-cross-20260925-797/：result.json、supervisor-evidence.json、create-response.json、approve-response.json、real-records.tsv、原 XML/日志与 marker、设备及磁盘快照。
- 已认证停库；完整 SQL dump 摘要通过、完整 ZIP 的 290 个文件逐项长度/SHA-256 回读一致后，才清理本批新建 data。datadir-file-hashes.json、datadir-cleanup.json 和 ZIP/dump 均留存；历史 attempt 不动。
- D 可用：执行前 33844604928、执行后 33629073408；最终收尾前 33592737792、保全清理后 33793564672 字节，均高于 20 GiB。
- 真机 22D2910042 起止及最终枚举逐字节相同；目标设备命令 0、AVD 0。两份保护 PDA 文档摘要一致。19330/19434 无监听，无本批重型任务；原服务不动。
- 本批实际提交与远端核验见 resume-supervisor/closeout/git-final.json；不预写自身提交 SHA。

## 仍未验与接续

本项只关闭“迁移后独立主管的真实 HTTP 签名生效”证据缺口，不关闭 A2.4 或 R07。附件仍只验证声明，无真实字节上传/存储验收；故障换机、已知抬头撤销缺陷、R1 写后事务回滚及现场恢复边界保持。R06 mapping3+、高轮次、排除授权、短装关联组合、跨来源续作和 FORMAL 独立时间盒继续按原边界处理。

整体目标 active，继续 R06/R07 独立可执行子项。未做 Android/APK/lint、模拟器/真机、生产或部署；不接原库/现网、不合 main、不打标签/Release。版本事实见看板生成事实块。
