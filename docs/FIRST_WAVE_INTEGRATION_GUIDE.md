# 首轮并行开发整合记录

## 范围与基线

- 整合分支：`codex/first-wave-integration`
- 起始提交：`1ffae5e1afd4d40ced495a04de7e95268b3b9296`（总调度文档冻结点）
- R09 运维恢复与证据方案提交：`584449f0292d9693a5799ce040409acf2d1c5ad7`，合并节点 `b2b21db`
- R09 原始前序提交：`4499716c33fb2295c925d73eec095fb114a0180f`
- 合并方式：`git merge --no-ff --no-commit`，自动合并，无冲突；保留 R09 原提交历史。

R09 本轮只涉及 `docs/parallel/R09/`，没有与 R06、R08 的业务源码发生文件级冲突。R06 最终提交 `e658405c3bc7d797356b35ace195f8297c9cdbb6` 以保留历史方式接入，合并节点 `a2824c5`。随后接入 R08 最终提交 `e87434973811fa3e21d21bc3cc227f0a8a3565f7` 及其前序 `effba8f`，没有读取未提交业务改动作为合入内容。

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

- OpenAPI 暂为 R06 的 0.26.0，合并树 `npm run check:contract` 实际通过：106 条路径。R08 接入后才统一升级为 0.27.0。
- Reminder 保留 FORMAL/TEMP 权限分流和 `sourceType`，正式审批动作对 TEMP 隐藏；临时审阅始终只读、不可放行。
- R06 业务/契约/Web 文件与 `e658405` 完全一致；`git diff --cached --check`、未解决冲突清单、源码冲突标记检查均无问题。
- R06 交付文档内 63 项 IT、32 项单元及 Web 构建等属于执行代理对应提交的验证记录，本次只做静态与契约核验，没有重复宣称或重跑其完整测试。

## R08 合入与共享冲突

最终提交的 26 个代码/测试文件与 W1 独立复审的原始内容指纹一致：`ca205d5d683f9132a369831b23c6a47b7d64df8cfd7112e1d61fecb8f9b8ea33`。锁后重新授权问题已由独立复审关闭；本次又比对提交内容与该指纹对应文件，未发现新的业务变化。

仅 `web/src/navigation.ts`、`web/src/router.ts` 发生文本冲突。逐块保留临时待确认、导出中心、超时待审三个入口；保留 R06 的 TEMP/正式提醒分权及 `canNavigate`，同时保留 R08 的 `order:read` 导出登录返回检查。R06 的 ReviewReminder、OrderSubmission、package.json 未被 R08 覆盖；R08 的 OrderDetailPage 文件页及 server 复合构建依赖正常接入。整合没有更改认证、Job/Worker 或记账业务核心。

## 仍未验证或未完成

- R08 正式 OpenAPI 和组合 0.27.0 接入正在完成；两个分支的历史通过数不能相加冒充组合结果。
- 尚未运行组合后端构建、契约测试、Android 构建或浏览器测试；不得据此宣称首轮整体通过。
- R09 的验证仍限于其既有本机文档/脚本/合成夹具范围；不包含灾难重建、外部 UUID 控制、共享写入/Worker 屏障、异地/PITR、生产或现场验收。
- 保持 Flyway V33、Android code20/Room17 版本边界；本次没有新增迁移或部署动作。

## 后续动作

1. 完成 R08 正式 6 个路径/8 个操作的 OpenAPI，统一契约版本 0.27.0。
2. R08 测试安全护栏只增加本次专用组合库及专用账号的精确允许，不开放其他库，不改预览种子。
3. 使用新的整合专用合成验证环境运行组合回归，保留旧实验库、种子和证据。
4. 组合验证完成后由总调度检查提交、文档和工作树，再决定是否快进回 `codex/development`；本阶段不合入 `main`、不部署。
