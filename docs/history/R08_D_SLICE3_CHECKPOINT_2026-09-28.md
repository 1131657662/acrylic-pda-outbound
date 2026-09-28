# R08 D 片3阶段记录（2026-09-28）

## 1. 结论与停工点

本轮仅获授权接通更正净结果导出的请求面、完善未发布契约草案并修正旧看板。请求实现已作为阶段备份提交并推送；真实 HTTP 申请、渲染和下载链路通过，但文件内容回读发现既有片2的审批人身份输出错误。按用户“真 bug 立即停报告”的硬边界，已停止开发及后续测试，未修改片2。**片3未验收收口。**

- A2 文档纠正：5eff71d，已推送至 origin/codex/development。
- 片3请求链阶段备份：edb041a49d22781951a6b907b78fdc5505f66c78，已推送至同一分支。
- 本页、看板修正、旧看板归档和欠债 v26 单独作文档提交；文档提交后的精确 HEAD 以 Git 为准。
- 源码版本事实仅见[看板生成事实块](../ORCHESTRATION_STATUS.md)。未合 main、未发布契约、未部署或开放生产开关。

## 2. A2 核实与纠错

A2 已由 dcaf7ad 修复，该提交是接续基线 5d4e06c 的祖先。服务端候选接口提供 commandContextSha256，Web 候选组件及 useTemporaryCommand 的取回分支使用该值。

[A3 根因记录](A3_CSRF_ROOT_CAUSE_2026-09-27.md) §6.5 是修复授权前记录，紧接的 §6.6 已记录修复、历史 attempt 377（1/0/0/0）及 Web typecheck 通过。上一轮报告误用历史未完成结论，本轮已修正[欠债清单](R06_DEBT_LIST_2026-09-27.md)表首第5项并追加 v25；v11–v13 原文保留。377 未重跑，不冒充本轮验证。

## 3. 实现清单与兼容范围

edb041a 共12个文件：5个请求/作业相关生产文件、4个测试文件、3个契约草案文件。生产文件均位于 server/src/main/java/com/acrylic/outbound/exports/。

| 文件 | 本轮变化 |
| --- | --- |
| ExportModels.java | Command.basis 允许 CORRECTED；可选 resultVersion 限制至少为1，未传或null仍取1；保留原三参数构造器；Payload可选保存版本并兼容旧形状 |
| ExportProducer.java | 校验 basis/version 组合；CORRECTED 要求大于1；走既有 snapshots.read(scope, format, now, version)；保存版本并纳入幂等比较；更正范围汇总和扫码模板拒绝，保留PDF原始行数上限 |
| ExportJobQueries.java | 从 payload 返回实际版本，旧 payload 仍返回1 |
| ExportRetry.java | 重试保留原版本 |
| ExportDownloads.java | 更正下载文件名标明 corrected-v<version>；原确认文件名不变 |
| server/src/test/java/com/acrylic/outbound/exports/ExportVersionCompatibilityTest.java | 新增缺省版本、旧构造器及旧 payload 兼容性单测 |
| server/src/test/java/com/acrylic/outbound/inventory/CorrectionExportHttpClient.java | 真实 Web 登录、会话和CSRF客户端；隔离夹具临时凭据覆盖后恢复 |
| server/src/test/java/com/acrylic/outbound/inventory/CorrectionExportHttpChecks.java | 真实HTTP申请/状态/列表/下载、实际worker、缺省与显式v1、版本选择、幂等和重试断言 |
| server/src/test/java/com/acrylic/outbound/e2averify/E2aCorrectionChainIT.java | 在既有首个有效v2后添加3行检查入口，复用更正链 |
| contracts/export-schemas.yaml | 补 basis、可选 resultVersion 及默认值/兼容描述 |
| contracts/export-paths.yaml | 补指定更正版本导出的请求与响应说明 |
| contracts/export-components.yaml | 补版本选择等错误说明 |

契约变更仅为未发布草案，未独立递增版本，contracts/openapi.yaml 未改。未改片1/片2快照与渲染逻辑、ConfirmedV1Adapter、R05/R06/R07已验证业务代码、生产配置默认值或迁移。

## 4. 当次验证与证据等级

| 验证 | 当次结果 | 适用范围 |
| --- | --- | --- |
| ExportVersionCompatibilityTest | 4/0/0/0 | 缺省版本及旧payload/构造器兼容 |
| ExportRecoveryViewTest | 2/0/0/0 | 既有恢复视图定向回归 |
| compileTestJava | 成功 | 主树测试源码编译 |
| report-rendering installDist | 成功 | 重建真渲染子进程安装目录；渲染源码未改 |
| attempt 487 / E2aCorrectionChainIT | 1/0/0/0，started/completed及HTTP完成marker齐全 | 真MySQL、真服务19434、真实HTTP；更正前置走既有PDA Bearer链，导出入口使用其实际Web会话/CSRF |
| XLSX及PDF内容回读 | **FAIL** | 版本和数量重量匹配，但审批人身份错误；IT绿不覆盖内容失败 |
| R08ExportIT | **本轮未运行** | 发现真实缺陷后停测；历史run27/28的14/14不能替代本轮默认v1完整回归 |
| 独立审查、PDF视觉版式 | 未做 | 作者检查与文本提取不能代替独立审查或视觉验收 |

487通过HTTP申请指定v2的XLSX/PDF，实际worker沿既有快照与渲染路径出文件，再经HTTP下载。断言覆盖缺省v1与显式v1等价、旧payload兼容、非法组合拒绝、v2重放、改变版本时幂等冲突、状态/列表版本、重试及文件名。完整原确认回归仍缺，不将有限兼容断言写成R08全套保持。

## 5. 已确认阻断：审批人身份输出

源码定位：CorrectionAdjustmentSource.java 第33行SQL直接读取 d.approved_by；第102–103行 approver() 对返回值调用 String.valueOf。对应迁移 V43__correction_model.sql 第57行将 approved_by 定义为 BINARY(16)。JDBC返回 byte[]，因而输出Java数组对象标识 [B@…，不能代表稳定的审批人身份。

| 样本 | 回读结果 |
| --- | --- |
| 历史attempt 486 XLSX | [B@61427cd3、[B@ca72afb，证明不是片3引入 |
| 新attempt 487 XLSX 调整凭据!S4:S5 | [B@522c6b13、[B@75841f63 |
| 新attempt 487 PDF与保存快照 | [B@19ef457b、[B@c776de |

缺陷进入 snapshot.adjustments[].approver、XLSX审批人列和PDF审批人文本，不同导出实例还会产生不同对象标识。此处属于既有片2保护面，**本轮未修**。需要用户另外授权最小稳定身份转换及内容断言，才能继续验收。

其余已回读内容：XLSX含7表，原确认2包/601.5kg、调整-2包/-601.5kg、净0包/0kg、2条调整凭据、版本有效明细0行；PDF通过pypdf提取2页、2440字符，NFKC规范化后口径/版本/数量重量可对照。审批人仍错误，不能据此声明XLSX/PDF内容全部正确。PDF文本级读取已做到，视觉版式未验。

## 6. 失败、警告和本机证据

- 没有新增红色IT attempt；487 IT绿，但独立内容验收明确失败。失败文件与日志保留。
- 487正常停库后，harness第273行对空cleanup变量调用 Test-Path -LiteralPath，产生清理警告；进程最终退出0，19330监听释放。未修工具、未手动删除证据。
- 诊断过程中出现控制台GBK输出错误、Python stdin转义导致换行语法错误、缺少PyYAML、工具JavaScript模板引号错误；已改用保存的诊断脚本完成内容回读，未安装或修复依赖。YAML自动解析/契约schema验证未完成，不声称通过。最后的工具引号错误发生在文档创建前，没有业务文件副作用。
- 文档链接校验首轮因工具字符串中的正则转义丢失而误报；改正校验表达式后，看板和本页链接全部通过。旧看板归档与替换前Git原文比对一致；事实块校验及文档上限通过。全量暂存diff空白检查提示归档原有的EOF空行，按保留历史原文要求不删；改动文档使用通常空白检查，归档单独仅豁免这一历史EOF空行。

487证据根：D:/acrylic-pda-worktrees/r06-e2a-migration/.local/e2a-cross-20260925-487/。

- integration-test.log、TEST-E2aCorrectionChainIT.xml、test-started.marker、test-completed.marker、d-slice3-http-completed.marker。
- storage/corrected-http/corrected.xlsx、corrected.pdf、snapshot.json、pdf-extracted.txt、content-readback.json（明确FAIL）。
- 主树 D:/acrylic-pda-outbound/.local/d-slice3-20260928/ 保存 attempt-487-console.log、sync-manifest.json、before/、verification-tree-status.txt 和 content_readback.py。
- 主树 server/build/test-results/test/ 保存两个单测XML结果。

上述.local证据及临时密钥均未纳入提交；本机保留不等于异地备份。

## 7. 工作树、环境与写权断点

主树 D:/acrylic-pda-outbound，codex/development；本轮代码阶段备份为edb041a。当前助手单写者，无子代理，无发现的其他活动写者。看板替换前旧文完整归档至 ORCHESTRATION_STATE_before_D_SLICE3_2026-09-28.md，旧“R07-3下一步开局”等不再作为当前状态。

验证树 D:/acrylic-pda-worktrees/r06-e2a-migration 保持detached ea4d166ef9db2ea7711c1006cff208f3ff88495c，原有脏文件保留。同步前逐项对照主树改动前Git基线，核对既有目标文件内容；仅同步9个Java请求/测试路径，原有文件前像及新增文件清单保存在上述本机目录。未reset、合并或代提交该树。

487及渲染已结束，无在途重型任务；19330/23308/19434无监听。系统MySQL 3306未操作。attempt下次从488+查重分配；R08专用run29未使用。验证树保留，不退役。

## 8. 待用户裁决与未验范围

1. 是否授权只修片2审批人稳定身份输出，并补XLSX/PDF内容断言、真实HTTP复验及R08ExportIT原确认全套回归。未经授权不继续。
2. D四项导出边界仍未开工：v2→v3多版本链、撤销后快照、抬头更正、部分更正净结果非空样本。既有更正业务链经过相应业务动作，不替代对应导出验收。待片3阻断处理后另行确认。
3. 不自动切入CommandBusinessIntegrity、最新有效版本汇总、剩余非e2a回归或R09。既有政策、原资料/旧BCD、现场与生产限制继续见看板及欠债清单。

## 9. 后续授权修复与验收盲区（2026-09-28）

用户随后授权片2审批人ID编码最小修复。089b335沿项目BIN_TO_UUID惯例仅改查询一列；attempt488真实HTTP导出1/0/0/0，XLSX/PDF审批人值与库内16字节一致，R08 run29当次14/14。前文“未修/未重跑”保留为历史停工证据，现已由本次验证补齐；详见[修复验收记录](R08_D_APPROVER_ENCODING_FIX_2026-09-28.md)。

**验收盲区**：片2attempt486没验“审批人ID内容正确性”，只验字段存在、表/凭据数量及数量/重量。非空的[B@…因此漏过。教训是验“值正确”，不能只验“字段非空”；后续内容断言须覆盖所有关键字段的值与权威来源。本次已新增审批人规范UUID、与库内原始字节相等及XLSX/PDF逐值回读断言。

D四项边界（v2→v3、撤销后、抬头更正、部分更正）仍未开工，继续交回用户决策。
