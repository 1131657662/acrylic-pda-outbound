# 第三轮整合记录

2026-09-20，执行者W1。独立工作树 `D:/acrylic-pda-worktrees/third-wave-integration`，分支 `codex/third-wave-integration`，固定起点 `d32e846618882f2c463ea2b0addb26c0d01ddd36`。主协调五份管理文档、主目录原电子表格和原业务环境不在本树操作范围。

## 第一阶段：E1原来源证据

已审E1提交 `8cb9e2aca582ca6ad9a4a5b3e49f3c000e60da84` 以 `merge --no-ff --no-commit` 合入，合并节点 `424cda53ba50634d60ebcf14b53633c9f1aaa7d1`。自动合并无冲突；精确19文件与作者已审Git内容一致，暂存差异检查通过。作者审查后只有指南追加最终证据，18个代码/测试/片段原始指纹未变。

正式登记三个GET：`/temporary-source-evidence/{submissionId}`、`/payload`、`/chunks/{index}`，分别引用E1片段的Detail/Payload/Chunk，统一契约 **0.29.0/116路径**。核心行为和未验边界见[E1交付](R06_SOURCE_EVIDENCE_GUIDE.md)；不新增写确认、关联、释放、续扫或删除能力。Flyway V33、Android code20/Room17及server/web工程0.1.0保持。

作者26项MySQL/MockMvc、8项专项单元、JAR及临时116路径组合通过均对应 `8cb9e2` 与其独立19319合成环境；该环境已由作者停止，证据保留。这些不是本树组合新跑，尚未包含L1新的共享G前缀。

本树第一阶段实际运行 `npm ci --ignore-scripts --no-audit --no-fund` 成功，未修改锁文件；`npm run check:contract` 通过正式116路径，日志在ignored `.local/third-wave-20260920/openapi-e1.log`。接口说明与本指南严格UTF-8及42个实际本地链接核对通过，`git diff --check`通过；没有把轻量契约检查说成组合业务验证。

## 后续组合检查点

L1和C0仅在独立审查通过且主协调交付精确SHA后合入，不消费作者未提交代码。组合将使用新合成库 `acrylic_outbound_wave3_20260920` 和独立账号/签名/私有目录；先核对共享13316实例datadir且没有同名库，再创建限定环境。不得读取或重置原R05/R06/R09库，不运行旧种子、R09恢复/故障演练、HTTP或设备。

计划新跑IdentityLock专项、TemporarySourceEvidence专项、PublicationConcurrencyIT、R08AuthorizationRaceIT，确切数量以本次XML为准；后端单元/JAR、正式116路径和C0离线检查另列。只按准确库名/账号追加必要测试护栏，保留硬校验；不重跑无关Web、报表、Android或依赖真实Android输入的旧例。作者L1独立大套件及其中1项真实输入缺失跳过单列，不冒充本树组合结果。

本阶段尚无组合通过结论。完成后记录首轮失败、实际修正、最终源码/证据对应关系并推送整合分支，经总调度冻结管理文档后另派合回开发分支；不自行操作主目录、main、发布或现网。
