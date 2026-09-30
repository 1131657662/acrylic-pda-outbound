# R06 正式单反向候选独立消费（2026-10-01，Asia/Shanghai）

## 验收范围与依据

输入树 D:/acrylic-pda-outbound，codex/development，输入/远端 7fb315cdd5938b7e17e2f2ffd09b4571ee148704。版本事实只见[看板生成事实块](../ORCHESTRATION_STATUS.md)。作者、自审、运行均为同一助手；独立消费者指独立 Python 实现，不是独立人员复审。

依据[剩余计划](../REMAINING_DEVELOPMENT_PLAN.md) A1.2“非零候选由实际消费者验证”及 [M2b](../R06_M2B_IMPLEMENTATION_PLAN.md) 正式单侧反向提示的成功 TEMP 可见、不可自动关联、仓库隔离要求，本批退出标准为：

1. 已真实确认成功的 TEMP，在之后建立的正式单查询中仍为非零候选。
2. 从未过滤的原始关系重建初始映射、当前修订、产品交集、接收时间窗、状态、排序与分页，不把服务响应复制为期望值。
3. advisoryOnly=true、releaseAllowed=false；跨仓请求 404 WAREHOUSE_NOT_FOUND；查询前后所列业务字段逐值不变。
4. 原始请求摘要/数量与成功结果有数据库证据；篡改响应、权限、原件摘要、业务投影的消费者反例必须失败。

## 实现与证据等级

- 新增 ReverseCandidateFacts / ReverseCandidateVectors / E2aReverseCandidateVectorsIT，以及 reverse_candidates.py / test_reverse_candidates.py。
- TEMP 接收与 NORMAL_CONFIRM 使用既有测试夹具和显式服务装配；NORMAL_CONFIRM **不是 HTTP 产品入口验收**。正式单创建、会话登录/CSRF、反向 GET 与异仓拒绝均为本机隔离真实 HTTP。
- Python 仅消费实际 HTTP 输出和原始 SQL 投影：不导入产品候选查询 DTO，不把服务上下文摘要当作来源权威。checkedAt 只校验带时区的格式，不作为授权依据。
- 业务投影含来源/映射/订单/修订/明细/作业/结果以及占用、dispatch、ledger、decision 的列集；结论限这些列集，不宣称全库所有字段或系统会话表完全不写。
- 测试建单和身份为合成 fixture；不替代现场来源、真实用户授权、Keystore、设备操作或可信身份供给。未改产品源码、迁移、接口及生产默认值。

## 实际运行

| 运行 | 结果 | 定性 |
| --- | --- | --- |
| 新增用例初次离线审阅 | 修正草稿异仓预期为 404；缩减参数；脚本 parseErrors=0，Java/Python 编译通过，既有隔离守卫 3/0/0/0 | 此前 403 草稿未上库；不是产品差异 |
| attempt795 | Java IT 1/1/0/0；Python 未运行 | 登录返回 ACCESS_DENIED。新 IT 未设回环 HTTP Cookie 参数，尚未到建单/候选断言；不是候选业务失败 |
| HTTP 前置修正 | 仅新增 IT 显式传测试参数 server.servlet.session.cookie.secure=false；离线编译与守卫 3/0/0/0、parseErrors=0 | 与既有 E2aTemporaryCandidatesIT / E2aTemporaryDecisionProtocolIT 相同的自包含前置；不改产品 secure 默认值，不以重试掩盖问题 |
| attempt796 | Java IT 1/0/0/0；Python 18/18、0 skip | SUCCEEDED TEMP 非零候选、原始关系独立重建、异仓拒绝、所列业务投影不变；13 项响应篡改及原件/权限/业务投影反例 |
| 完整服务端单测 | 134 suites / 2847 tests / 0 failures / 0 errors / 1 既有 skip | 本批最终源码；R1 已停止草稿不在活动发现范围。没有把前面的既有守卫红轮当作通过 |

**本子项通过；A1.2 未整体完成。** mapping3+、高轮次、多候选排除授权、短装关联组合、跨来源续作、FORMAL 独立时间盒及现场条件不由本结果覆盖。

## 证据与保全

- 初始/修订离线：.local/autonomous-20261001/resume-reverse/；Cookie 修订、完整回归、自审、收尾：.local/autonomous-20261001/resume-reverse-http/。
- 795：TEST-*.xml、integration-test.log、classification.json、失败源副本 source/、SQL dump 与 ZIP 均保留。
- 796：TEST-*.xml、reverse-candidate-vectors.json、python-consumer.log、real-records.tsv、test-started/completed.marker、result.json、SQL dump 与 ZIP 均保留。TSV 为真正 tab 分隔。
- 两库认证停机后各 290 个文件逐项回读 ZIP、SHA-256/字节数/文件数一致，才清理**本批新建** datadir。历史 attempt 和所有失败日志未删；此处 ZIP 含隔离凭据/数据，只留本机 .local，不提交 Git。
- 795 D 可用：33901170688 → 33685639168 字节；796：33685008384 → 33469947904。收尾前 33447997440，保全清理后 33848803328 字节，均高于 20GB。
- 两轮 22D2910042 的 adb devices -l 前后逐字节一致；收尾与初始也一致。目标设备命令 0，AVD 0；测试端口释放，原服务不动。两份受保护文档摘要一致。

## 后续边界

R1/S4b 因新增 Primary 触发既有隔离守卫而停止，未取得写后真实回滚、非空 K 守恒、同键恢复和重启证据，详见[当日报告 §11 起](AUTONOMOUS_PROGRESS_2026-10-01.md)。不能为该测试放宽冻结白名单。

本批无 Android/APK/lint、模拟器/真机、产品入口开放或部署；localDeletionAllowed=false，不接原库现网，不合 main，不打标签。实现提交与远端核验写入 .local/autonomous-20261001/resume-reverse-http/closeout/git-final.json；正文不预写尚未产生的 SHA。
