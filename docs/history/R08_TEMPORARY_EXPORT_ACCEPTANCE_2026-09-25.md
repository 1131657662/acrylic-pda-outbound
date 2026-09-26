# R08 TEMP 原确认导出验收（2026-09-25）

本子项开发及隔离验证完成；前九步目标 active。当前助手实现、自审及执行所有检查，没有独立人员验收。未提交、推送、整合、合入 main 或部署。

## 版本、范围与标准

主树 codex/development @ dccb73b10acbb63020beb3cf827b3b4580d3ed1c；服务端树 codex/r06-e2a-migration @ 568683c1a1f8257ad0596a654c6b3558c767ed95。完整未提交/未跟踪文件见同目录 R08_TEMPORARY_EXPORT_WORKTREE_STATUS_2026-09-25.md。两树 origin 均为 https://github.com/1131657662/acrylic-pda-outbound.git。

验收范围为已完成 TEMP 首轮、mapping1 的 ORIGINAL_CONFIRMED 只读导出。必须验证原提交正文、原签名确认 BLOB、身份/映射/事件/审计、计划与库存快照；保留原 TEMP creatorDevice/clientOrder、捕获ID、原设备序号、order_scan_seq、人员/时间/条码/重量。正常及独立主管批准的短装都需真实 HTTP 下载三种文件、日期汇总、原键重放及固定快照重新生成。资料缺失、伪造、跨仓或截点不符必须拒绝，不新增导出任务、不执行决定、不释放占用。

## 实际实现与自审

- 服务端新增 TemporaryExportEvidence/Plan/Source，复用 CommandReceiptIntegrity 及 TemporarySubmissionPlan/Evidence/Snapshot；不重新使用批准人今天的权限否定历史有效批准。
- ExportFacts、ExportSnapshotReader、ExportRangeReader 接受严格核验的 PDA_TEMP；保持 31日/100单/5000包及 PDF1000包边界。
- 报表增加 TEMP_PDA 渠道与原设备来源校验；不伪造 FORMAL orderId。标题明确首次确认，长来源引用分行，避免 Excel 行高裁切。原快照中的固定版本净贡献列不等于后续调整净结果。
- 两树 Web 详情显示“原确认文件”，汇总包含已核验临时来源。主树新增真实浏览器用例。只定向同步6个主树已提交的扫码姓名修复文件，未全量合树。
- FORMAL PDA/PC 使用真实数据库夹具调用 reader 回归；这不是全套历史 R08 IT 重跑。

## 当次实际验证

- 报表模块全套 123/123，12份 XML；installDist 通过。
- 服务端全套 494项：493通过、1 Windows 符号链接条件 skip，78份 XML。
- Web build 通过（既有大 chunk 警告）；OpenAPI 121 paths validated。
- attempt53：独立非空 MySQL V34→V36，专用 datadir/schema/user/ACL/binlog/19330，真实 HTTP 服务端 IT1/1，Chrome1/1（正常和短装各一页、下载及汇总、无横向溢出）。已认证 shutdown；未动共享3306。
- 12份实际下载文件（10 XLSX/2 PDF）以独立读取路径对照原 snapshot 回读通过；此处“独立”指解析路径，不是独立人员。两份 PDF 共4页逐页渲染目视，无裁切。表格核过扫码记录、汇总、明细和来源证明。
- 2026-09-25 接续复核：1049文件 manifest 全部一致；上述 XML 数量与汇总一致。未重复运行同批测试。

## 失败、限制与保留

首次 Gradle 参数顺序错误；focused2 为51项50通过/1 PDF环境失败（浏览器路径被配置覆盖），本机 init 改为 projectsEvaluated 使用已有 Chromium1187 后全套通过。attempt49 因隔离 HTTP secure cookie 配置遗漏发生 CSRF403；attempt50 末尾 FORMAL/PC 夹具重复政策409，改为独立身份/仓库；51/52通过，53为最终产品入口/长引用修复后的全链。所有失败库、日志及中间证据保留。

Artifact Tool 预览将文本003显示为3，实际 XLSX经 openpyxl核为字符串003。pypdf 初次文本检查受字体空格及控制台 GBK影响，最终 NFKC/去空白检查通过。既有模板默认 style 警告保留，检查未重写原文件。原生 Excel 显示/打印未验。

生产 CommandOperation bean 仍为空，七类生产决定入口、TEMP生产归档入口、放行/关闭能力不开放；localDeletionAllowed=false。TEMP多设备/更高轮次/后续净结果未实现于本项。独立人员、真机/Keystore、现场、生产权限及审批未验。

## 证据

本机根：outputs/r06-e2a-takeover-20260925-01a0d612/。

- temp-export-server-final-xml/、temp-export-rendering-final-xml/ 及对应 log。
- temp-export-attempt49-* 至 temp-export-attempt53-*；53-files/ 为12份文件及 snapshot，53-browser/ 为页面截图与结果摘要。
- temp-export-files-verified.json SHA256 e053eb682706f88363ccf200a38e7b0e3e105ce07877d258d32fac31e0e5b494。
- attempt53-integration-result.xml SHA256 45f130d1b7ac62d13dfc9ed09c96e8f18f9acc537d05481dcfc00fc1266b699d。
- temp-export-source-manifest.json：1049文件，SHA256 4d3178cc593e5d8d1226df52465a25895c174409a8cdb4101295d4f3ba32d5c0。
- temp-export-final-visuals/ 和 normal/short 各2页PNG。私密密钥/凭据及原 browser.log 不复制普通证据或 Git。

下一断点：R06/E2a 未冻结、整单零包、未决输入证据；现有 LocalPayload 仅覆盖有原提交的已冻结 ABORT。下次专用 run 从54检查分配。旧当前区已保存 ORCHESTRATION_STATE_before_temporary_export_2026-09-25.md。
