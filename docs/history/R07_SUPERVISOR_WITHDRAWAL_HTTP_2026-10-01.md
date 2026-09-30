# R07 分离身份下的误批撤销 HTTP 专项（2026-10-01，Asia/Shanghai）

## 依据与范围

输入与已核远端为 codex/development 的 8eede2e1a95579a070a6a489e0227dcdaa304e8f。依据已定政策 BUSINESS_POLICY_R07 P-R07-08“审批人发起撤销 → 申请人确认”，及剩余计划第4行“防双端激活、重复调整；逐笔调整和最新净结果可追溯”，补迁移后 SUPERVISOR/建档身份分离下的真实 HTTP 撤销证据。

只新增三份测试域 Java 文件，复用已验更正夹具和既有产品实现；隔离进程打开既有更正命令与回环 HTTP Cookie 配置。产品源码、既有测试、迁移、契约和默认开关未改。来源、身份和隔离密钥均为合成夹具，不是现场身份或真实 Keystore。

## 退出标准与实际结果

| 标准 | attempt798 的证据 |
| --- | --- |
| 撤销发起者必须是原审批人 | 原主管真实 HTTP WITHDRAW 返回 200；建档人和另一主管均 403 CORRECTION_PERMISSION_DENIED；文档状态保持 EFFECTIVE |
| 确认者必须是原申请人 | 原申请人真实 HTTP WITHDRAW_CONFIRM 返回 200；主管和另一建档人均 403；非申请人尝试不改变状态 |
| 两阶段状态与原事实 | WITHDRAW 后为 WITHDRAW_PENDING，账务和结果仍 version=2、601501g；确认后为 WITHDRAWN，结果 version=3、包数仍2、重量恢复601500g |
| 逐笔反向记账 | 原 WEIGHT_DIFF 行 quantity_delta=0 / weight_delta_g=1 完整保留；新增一条 WD:<document>:<entry> 行，reverses_entry_id 指向原行、weight_delta_g=-1；恰好一条 effect |
| 回执、幂等与签名 | CREATE/APPROVE/WITHDRAW/WITHDRAW_CONFIRM 四条回执；四次成功响应均独立 JCA 公钥验签；两阶段原字节重放一致，同键异字节均 409 IDEMPOTENCY_CONTENT_MISMATCH |
| 原事实投影 | 来源、原件、映射、承诺、出库、库存账务等列入快照的投影前后相同；未声称每表每列完整一致 |

本项一次 attempt，无失败或重跑。row_version 因四条命令回执为5，不等同于结果版本；结果版本明确为1→2→3。撤销占用回退、append-only 触发器、过期/拒绝语义仍是政策记录中的缺口，本项不扩展。

## 实跑与自审

- 离线编译成功；既有隔离守卫 3 tests / 0 failures / 0 errors / 0 skipped；本轮脚本 parseErrors=0。
- attempt798：1 test / 0 failures / 0 errors / 0 skipped，javaExit=0；真实 HTTP、四份响应、撤销证据、XML、日志和 marker 保留。
- 最终完整服务端单测：134 suites / 2847 tests / 0 failures / 0 errors / 1 既有 skip。
- 同一助手实施与自审：检查真实不同会话、归属拒绝、pending 无副作用、逐笔 reversal、同键回放和签名来源；未改产品代码、冻结 Primary 白名单或生产开关；无独立人员复审。

## 证据与资源

- .local/autonomous-20261001/resume-withdrawal/：preflight、offline、full-regression、closeout、整批脚本和摘要。
- .local/e2a-cross-20260925-798/：result.json、withdrawal-evidence.json、四份响应、real-records.tsv、原 XML/日志与 marker。
- 已认证停库；SQL dump 摘要通过，完整 ZIP 的 290 个文件逐项长度/SHA-256 回读一致后，才清理本批新建 data。datadir-file-hashes.json、datadir-cleanup.json、ZIP/dump 均保留；历史 attempt 不动。
- D 可用：执行前 33796857856，执行后 33581219840；最终收尾前 33522900992，保全清理后 33723797504 字节，均高于20 GiB。
- 真机 22D2910042 起止及最终枚举逐字节相同；目标设备命令0、AVD0。两份受保护 PDA 文档摘要一致；19330/19434 无监听，原服务不动。

## 仍未验与接续

本项关闭“分离身份下的两阶段撤销真实 HTTP”证据缺口，不关闭 P-R07-08 全部缺口或 A2.4/R07。占用回退、append-only 触发器、撤销过期/拒绝政策仍未验；附件真实字节上传/存储、故障换机、R1 写后回滚、已知抬头撤销缺陷和 R06 高轮次边界继续保留。

整体目标 active、未完成；不做 Android/APK/lint、模拟器/真机、生产或部署；不接原库/现网、不合 main、不打标签/Release。版本事实见看板生成事实块。
