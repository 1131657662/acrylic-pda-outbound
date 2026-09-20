# 第三轮整合记录

2026-09-20，执行者W1。独立工作树 `D:/acrylic-pda-worktrees/third-wave-integration`，分支 `codex/third-wave-integration`，固定起点 `d32e846618882f2c463ea2b0addb26c0d01ddd36`。主协调五份管理文档、主目录原电子表格和原业务环境不在本树操作范围。

## 第一阶段：E1原来源证据

已审E1提交 `8cb9e2aca582ca6ad9a4a5b3e49f3c000e60da84` 以 `merge --no-ff --no-commit` 合入，合并节点 `424cda53ba50634d60ebcf14b53633c9f1aaa7d1`。自动合并无冲突；精确19文件与作者已审Git内容一致，暂存差异检查通过。作者审查后只有指南追加最终证据，18个代码/测试/片段原始指纹未变。

正式登记三个GET：`/temporary-source-evidence/{submissionId}`、`/payload`、`/chunks/{index}`，分别引用E1片段的Detail/Payload/Chunk，统一契约 **0.29.0/116路径**。核心行为和未验边界见[E1交付](R06_SOURCE_EVIDENCE_GUIDE.md)；不新增写确认、关联、释放、续扫或删除能力。Flyway V33、Android code20/Room17及server/web工程0.1.0保持。

作者26项MySQL/MockMvc、8项专项单元、JAR及临时116路径组合通过均对应 `8cb9e2` 与其独立19319合成环境；该环境已由作者停止，证据保留。这些不是本树组合新跑，尚未包含L1新的共享G前缀。

本树第一阶段实际运行 `npm ci --ignore-scripts --no-audit --no-fund` 成功，未修改锁文件；`npm run check:contract` 通过正式116路径，日志在ignored `.local/third-wave-20260920/openapi-e1.log`。接口说明与本指南严格UTF-8及42个实际本地链接核对通过，`git diff --check`通过；没有把轻量契约检查说成组合业务验证。

## 第一阶段组合计划（实际结果见后文）

L1和C0仅在独立审查通过且主协调交付精确SHA后合入，不消费作者未提交代码。组合将使用新合成库 `acrylic_outbound_wave3_20260920` 和独立账号/签名/私有目录；先核对共享13316实例datadir且没有同名库，再创建限定环境。不得读取或重置原R05/R06/R09库，不运行旧种子、R09恢复/故障演练、HTTP或设备。

计划新跑IdentityLock专项、TemporarySourceEvidence专项、PublicationConcurrencyIT、R08AuthorizationRaceIT，确切数量以本次XML为准；后端单元/JAR、正式116路径和C0离线检查另列。只按准确库名/账号追加必要测试护栏，保留硬校验；不重跑无关Web、报表、Android或依赖真实Android输入的旧例。作者L1独立大套件及其中1项真实输入缺失跳过单列，不冒充本树组合结果。

上述为第一阶段检查点，当时尚无组合通过结论；后文记录已完成结果。经总调度冻结管理文档后另派合回开发分支；不自行操作主目录、main、发布或现网。

## 第二阶段组合结果

已审 L1 提交 `45a1c14841159992c20688ba6e4972b6bf41c781` 保留历史合入为 `250235e`，24文件与作者Git内容逐项一致、无冲突；两个经 W3 复审的组合测试护栏另存 `a3db1a8`。已审 C0 保留历史合并节点为 `4429f75`，16文件与作者Git内容一致、无冲突。

已在本树专用合成范围执行组合检查：共享 MySQL 实例 `127.0.0.1:13316` 保持运行，仅使用新库 `acrylic_outbound_wave3_20260920` 与账号 `outbound_wave3_20260920`，数据库/账号在创建前核对为不存在；环境含本轮独立 RSA 开发签名、私有文件目录和授权 `docs/sku.txt` 样本副本。原 R05/R06/R09 库、恢复断点、设备及现网未读取或重置。

实际结果：43 项后端单元测试、60 项指定 IT（26 `IdentityLock*`、26 `TemporarySourceEvidence*`、2 `PublicationConcurrencyIT`、6 `R08AuthorizationRaceIT`）全部通过，0 失败、0 错误、0 跳过；`bootJar` 成功，构建耗时约 1 分 39 秒。逐套件测试数、失败/错误/跳过字段及 XML SHA-256 固定在 ignored `.local/third-wave-20260920/server-first-summary.json`，原始结果目录为 `.local/third-wave-20260920/server-first-results`。IdentityLock 实际执行了 P_S 三张表的锁断言；本轮未设置 `L1_LOCK_EVIDENCE`，因此不把作者专项保存的9组锁JSON写成本轮新证据。

组合护栏只对两个fixture追加精确的第三轮 URL/账号成对条件，`IdentityLockFixture` 运行时还按账号确认 `DATABASE()`；其它原 L1、wave1 和 R08 专用条件保持。此次不包含依赖真实 Android 输入的历史例，作者专项的一项 skip 单独保留在 [R06身份锁记录](R06_IDENTITY_LOCK_GUIDE.md)，不计入本轮60项。

JAR 位于本机 `server/build/libs/acrylic-outbound-server-0.1.0.jar`，SHA-256为 `cab8b04ba8376da9464450d90e21c76b5d2af6a16a12e08fb0e03b650b9724ea`，仅本机构建，没有发布。共享实例实际datadir核对为 `D:/acrylic-pda-outbound/.local/mysql/data`；独立账号只获新库权限及 `performance_schema.data_locks`、`data_lock_waits`、`threads` 三张表SELECT，未授予PROCESS。配置、密钥、真实样本与所有本机证据均留在ignored路径，不随Git提交。

## C0离线提案复核

经 W3 复审的 C0 提交 `009faa99adaeb876ca3989bf5c87982e9b6f44fe` 已保留历史合入。本树重新运行 [C0离线提案](parallel/R06_COMMAND_VECTORS/README.md)，实际 **153 项通过、0 失败、0 错误、0 跳过，1.366 秒**，日志为 `.local/third-wave-20260920/c0-final.log`。控制字符修正前70项有10项失败的原始记录、修后153项记录及限制仍见作者 [STATUS](parallel/R06_COMMAND_VECTORS/STATUS.md)；W3记录的16文件Git内容字典聚合SHA为 `e6eab1eb1d940a5591eae7b79dfdd4dc63b081f870b8e35e790ed91ad2dc060c`。17份签名仍为合成RSA夹具，C0字段/hash/audience尚未接入生产API、数据库台账、Android或现网。

## 最终轻量检查与边界

本树 `web` 套件 `npm --prefix web run check:contract` 实际通过，OpenAPI 3.1 共 **116 paths**；日志为 `.local/third-wave-20260920/openapi-final-corrected.log`。最终检查首次误在仓库根运行npm，因根目录没有package.json返回ENOENT，日志 `openapi-final.log` 保留；改为准确 `web` 工作范围后通过，没有改业务代码来使检查通过。此前 E1作者19文件与116路径检查、L1作者404项不同IT通过并另1项真实 Android 输入缺失skip，均按作者提交和指南单列，未冒充组合结果。服务器 Java 测试进程已退出，wave3 合成库、账号、签名、私有文件和证据保留；共享 MySQL 实例不停止。

本轮未执行真实 HTTP、设备/Android、M71、现场、R09恢复、无关 Web/report/Android 测试或现网部署；未设置 `L1_LOCK_EVIDENCE` 也不影响本轮真实 P_S 查询断言，但限制原始锁JSON留存。源码、契约、C0向量和上述证据之间的SHA/路径在提交前复核；本分支不自行合回 `codex/development`、`main` 或发布。

最终四份相关文档严格UTF-8、53个本地链接通过；62个第三轮差异文件保存Git blob与工作区原字节SHA到 ignored `.local/third-wave-20260920/final-source-manifest.json`。服务器受测源码/测试与C0合入前保持一致，C0精确16文件与已审来源一致，主协调五份管理文档相对固定起点无变化；`git diff --check`通过。XML安全摘要与原件逐项核对，无需重新执行已通过的组合测试。
