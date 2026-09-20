# 首轮并行开发整合记录

## 范围与基线

- 整合分支：`codex/first-wave-integration`
- 起始提交：`1ffae5e1afd4d40ced495a04de7e95268b3b9296`（总调度文档冻结点）
- R09 运维恢复与证据方案提交：`584449f0292d9693a5799ce040409acf2d1c5ad7`，合并节点 `b2b21db`
- R09 原始前序提交：`4499716c33fb2295c925d73eec095fb114a0180f`
- 合并方式：`git merge --no-ff --no-commit`；R09/R06 自动合并，R08 两处共享文件冲突逐块处理，全部保留原提交历史。

R09 本轮只涉及 `docs/parallel/R09/`，没有与 R06、R08 的业务源码发生文件级冲突。R06 最终提交 `e658405c3bc7d797356b35ace195f8297c9cdbb6` 以保留历史方式接入，合并节点 `a2824c5`。随后接入 R08 最终提交 `e87434973811fa3e21d21bc3cc227f0a8a3565f7` 及其前序 `effba8f`，合并节点 `6f9b193`，没有读取未提交业务改动作为合入内容。

## 整合树验证

R09 测试依赖 `docs/parallel/R09/ops/lab.py` 指定的本地测试父目录。首次在全新整合树直接运行时，因 `.local/r09/tests` 尚不存在而出现 39 项 `FileNotFoundError`；该失败属于测试环境前置目录缺失，不是业务断言失败。随后仅在当前整合树创建 `.local/r09` 父目录，未复制或初始化原 R09 实验库、未运行种子、未启动服务，也未触碰原工作树的数据库和证据。

实际复跑命令：

```text
D:/acrylic-pda-worktrees/r09-ops/.local/r09-venv/Scripts/python.exe -W error::ResourceWarning -m unittest discover -s docs/parallel/R09/tests
```

结果：`Ran 56 tests in 3.588s`，`OK`。测试生成的整合树临时资料位于被忽略的 `.local/`，不进入提交。

单独修复提交 `7d68bec` 随后让 `tests/backup_factory.py` 自建 `.local/r09` 父目录，再创建受保护的测试目录。修复后在当前工作树复跑 56 项通过（3.388 秒）。该次复跑时父目录已经存在，因此不将其说成干净目录场景已重验；未删除现有测试证据或重跑实验室初始化。

后续从 `a2824c5` 用 `git archive` 导出 R09 源码到新的 `.local/first-wave-20260920/r09-clean-tree`，没有预建其中 `.local` 目录，实际再跑 56 项全部通过（3.599 秒）。这次验证覆盖新目录自建，并将输出保留在 `r09-clean-tests.log`；原实验和测试证据没有删除。

## R06 合入检查

R06 交付范围见 [独立只读待确认审阅](R06_TEMPORARY_REVIEW_GUIDE.md)。合并前逐项读取 OpenAPI、已实现接口记录、ReviewReminder、Web 导航/布局/路由、OrderSubmission 和 package.json 的差异；合并没有冲突，无需手工覆盖共享文件。

- R06 单独合入阶段为契约 0.26.0，`npm run check:contract` 实际通过 106 条路径；后续完整组合为 0.27.0/112 条路径。
- Reminder 保留 FORMAL/TEMP 权限分流和 `sourceType`，正式审批动作对 TEMP 隐藏；临时审阅始终只读、不可放行。
- R06 业务/契约/Web 文件与 `e658405` 完全一致；`git diff --cached --check`、未解决冲突清单、源码冲突标记检查均无问题。
- R06 交付文档内 63 项 IT、32 项单元等属于其执行代理验证；整合的单独 R06 阶段只做静态及契约核验，最终组合重新执行的结果单列如下。

## R08 合入与共享冲突

最终提交的 26 个代码/测试文件与 W1 独立复审的原始内容指纹一致：`ca205d5d683f9132a369831b23c6a47b7d64df8cfd7112e1d61fecb8f9b8ea33`。锁后重新授权问题已由独立复审关闭；本次又比对提交内容与该指纹对应文件，未发现新的业务变化。

仅 `web/src/navigation.ts`、`web/src/router.ts` 发生文本冲突。逐块保留临时待确认、导出中心、超时待审三个入口；保留 R06 的 TEMP/正式提醒分权及 `canNavigate`，同时保留 R08 的 `order:read` 导出登录返回检查。R06 的 ReviewReminder、OrderSubmission、package.json 未被 R08 覆盖；R08 的 OrderDetailPage 文件页及 server 复合构建依赖正常接入。整合没有更改认证、Job/Worker 或记账业务核心。

## 正式契约与整合专属改动

- API 契约统一为 **0.27.0/112 条路径**，完整登记 R08 两个 Controller 的 **6 条路径/8 个操作**，涵盖既有单订单导出/状态/下载和新增日期汇总/中心/重生成。新增 `export-paths.yaml`、`export-center-paths.yaml`、`export-components.yaml`、`export-schemas.yaml`，更新 OpenAPI 索引和已实现接口摘要/错误表。W1 独立核对字段、可空性、分页、权限、CSRF/UUID幂等/恢复头及二进制响应；日期400/数量409、空任务列表200和PDF来源上限优先级均按实际源码修正。
- `R08ExportIT`、`R08ExportFenceIT`、`R08CenterFixture` 保留原数据库安全护栏，仅新增 `127.0.0.1:13316/acrylic_outbound_wave1_20260920` 加专用账号 `outbound_wave1_20260920` 的精确允许。没有改动或运行 `R08PreviewSeedIT`。
- `ExportProducer` 与 `ExportSummaryQuery` 改用同事务数据库 `UTC_TIMESTAMP(6)` 作为快照截止时间；新增 `R08ClockBoundaryIT` 两项回归。认证/Worker/记账公共代码未改，具体失败及修复见下一节。

## 组合验证与失败记录

组合只使用新合成库 `acrylic_outbound_wave1_20260920`，在创建前核对共享 13316 实例的 datadir，并新建仅授权该库的账号。签名、私有文件、日志、XML 均位于整合树忽略目录 `.local/first-wave-20260920/`；没有读写原 R05/R06/R08/R09 业务库或重跑其种子。被忽略的 `docs/sku.txt` 复用已授权同一份样本并核对 SHA-256：`3b6b8902184718c6714fd1ea00f87cd30c8d8527e744dea93c7e16e319495833`。依赖缓存、固定摘要字体和浏览器运行库仅复用构建资产。

| 整合树实际验证 | 结果与证据 |
| --- | --- |
| 报告模块完整测试及分发 | 101 项通过，0 失败/错误/跳过；`test installDist` 成功，`render-final.log`、`render-final-results/`。后续仅 server 时钟修复，不重复此未受影响模块 |
| 最终服务端单元/构建 | 35 项通过，0 失败/错误/跳过；`bootJar` 成功，`server-clock-fixed.log`、`server-clock-fixed-results/test/` |
| 最终组合 MySQL/MockMvc | 18 类 **140 项通过**，0 失败/错误/跳过；包含 R06 原7类63项、R08原6类40项、时钟2项、PcAccess/PcOutbound/SubmissionApi/BackgroundJobWorker代表回归35项，证据 `server-clock-fixed-results/integrationTest/`。这是同一最终源码实际执行数量，不是各分支历史结果相加 |
| Web | 生产构建及类型检查通过，既有大chunk提示保留；`npm run test:temporary-review` 实际7项通过，读取本组合生成的 MockMvc 契约样本；未启动新的浏览器/HTTP监听 |
| OpenAPI | 最终 `npm run check:contract` 通过：112 条路径，6 条R08路径共8操作 |

首次组合服务端138项中137项通过、1项失败，原 `server-final.log` 和 `server-final-results/` 保留。`R08SummaryIT` 在订单完成后立即申请文件，原确认数据库时间为 `2026-09-20T11:39:21.348000Z`，但 JVM 截止时间为 `2026-09-20T11:39:21.346617600Z`；数据库领先约1.4毫秒，导致查询错误排除刚确认订单并固定空快照。该问题不是限流导致，也不是订单编号断言问题。

修复只统一报表截止时间的时钟来源。申请路径仍先 `server_state FOR SHARE`、用户行排他锁、`Access.require`，然后建立 RR 一致性读，再取数据库截止时间；绝不把时刻查询提前到锁后鉴权之前。成员集合同时受事务读视图和数据库截止条件约束，不宣称包括截止时刻前一切后来提交的数据。汇总查询在控制器鉴权后进入只读 RR 事务，使用数据库截止时间和固定成员；申请与查询仍是两次独立快照。原键回放在读取新截止时间之前返回原任务，原快照/成员/截止时间保持。

新增两项测试在新合成库事务的同一连接临时将数据库会话时钟提前10秒，无 sleep；覆盖跨订单/汇总、单订单及重放截止时间不变。旧实现两项均失败（跨订单空成员、单订单409），日志/XML为 `clock-before-fix*`；最终修复后两项及完整140项通过。测试 finally 恢复该连接 `timestamp=0` 并回滚，不改变数据库全局时钟或原实验资料。测试名称不将该事务内夹具描述成另一连接已提交场景。

W2 已独立只读复审两处生产时钟改动、新增回归和三处精确库护栏，并交叉核对授权/事务/幂等/快照校验路径及140项XML，结论无阻断；没有代为改文件或冒称重新运行测试。W1 对正式契约与共享导航/路由终审亦无阻断。

整合业务修复保存为 `e7099a2`，正式契约与组合证据保存为 `04a50bc`。随后按总调度安排保留历史合入 W1 最终方案提交 `9fa0059560880c9182ff8412462ceb74cf09feb3`（含 `a4fadee`、`f6e1c93`），仅增加 [R06 M2b 实施准备](R06_M2B_IMPLEMENTATION_PLAN.md)。方案已由 W2 独立复审；未实现 M2b、未新增迁移或改动已测试的业务代码，因此不重复构建测试。

## 仍未验证或未完成

- 本批源码组合与上述本机自动化验证完成；TEMP写确认/短装处置/受控关联/拒绝实物处理、共享记账/签名成功/归档仍未实现。
- 本轮未运行组合真实HTTP/浏览器、设备/Android/APK构建、异地网络或现场验收；R08分支的5项真实浏览器结果仅属于其提交，不能冒充组合HTTP验证。
- 导出仍仅原确认V1，TEMP、更正净结果及未核验多设备来源不开放；查询规模/索引、自动清理/配额及R09文件引用与异地备份尚待接入。
- R09 的验证仍限于其既有本机文档/脚本/合成夹具范围；不包含灾难重建、外部 UUID 控制、共享写入/Worker 屏障、异地/PITR、生产或现场验收。
- 保持 Flyway V33、Android code20/Room17 版本边界；API契约0.27.0与原server/web工程0.1.0分别记录。没有新增迁移或部署动作。

## 后续动作

1. 独立复审已完成，保存并推送整合分支供总调度核对。
2. 总调度核对交付并冻结管理文档后，再由执行者按既有授权合回 `codex/development`；本阶段不合入 `main`、不部署。
3. 组合库、签名、文件和全部失败/成功证据保留，原恢复断点及所有现网/设备/审批边界继续保持。
