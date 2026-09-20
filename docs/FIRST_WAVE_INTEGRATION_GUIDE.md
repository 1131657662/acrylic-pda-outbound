# 首轮并行开发整合记录

## 范围与基线

- 整合分支：`codex/first-wave-integration`
- 起始提交：`1ffae5e1afd4d40ced495a04de7e95268b3b9296`（总调度文档冻结点）
- 本次整合：R09 运维恢复与证据方案提交 `584449f0292d9693a5799ce040409acf2d1c5ad7`
- R09 原始前序提交：`4499716c33fb2295c925d73eec095fb114a0180f`
- 合并方式：`git merge --no-ff --no-commit`，自动合并，无冲突；保留 R09 原提交历史。

R09 本轮只涉及 `docs/parallel/R09/`，没有与 R06、R08 的业务源码发生文件级冲突。整合树暂不合入 R06/R08 未完成或未提交的改动；待各执行代理报告最终提交号并完成审查后再继续。

## 整合树验证

R09 测试依赖 `docs/parallel/R09/ops/lab.py` 指定的本地测试父目录。首次在全新整合树直接运行时，因 `.local/r09/tests` 尚不存在而出现 39 项 `FileNotFoundError`；该失败属于测试环境前置目录缺失，不是业务断言失败。随后仅在当前整合树创建 `.local/r09` 父目录，未复制或初始化原 R09 实验库、未运行种子、未启动服务，也未触碰原工作树的数据库和证据。

实际复跑命令：

```text
D:/acrylic-pda-worktrees/r09-ops/.local/r09-venv/Scripts/python.exe -W error::ResourceWarning -m unittest discover -s docs/parallel/R09/tests
```

结果：`Ran 56 tests in 3.588s`，`OK`。测试生成的整合树临时资料位于被忽略的 `.local/`，不进入提交。

## 仍未验证或未完成

- R06 最终提交号尚未报告；R08 授权竞态审查修复及最终提交号尚未报告，暂不合入。
- 尚未运行组合后端构建、契约测试、Android 构建或浏览器测试；不得据此宣称首轮整体通过。
- R09 的验证仍限于其既有本机文档/脚本/合成夹具范围；不包含灾难重建、外部 UUID 控制、共享写入/Worker 屏障、异地/PITR、生产或现场验收。
- 保持 Flyway V33、Android code20/Room17 版本边界；本次没有新增迁移或部署动作。

## 后续动作

1. 等待 R06、R08 执行代理分别提交最终提交号和实际验证结果。
2. 在本分支逐项合入并处理共享导航、OpenAPI 及契约版本 0.27 的兼容性。
3. 使用新的整合专用合成验证环境运行组合回归，保留旧实验库、种子和证据。
4. 组合验证完成后由总调度检查提交、文档和工作树，再决定是否快进回 `codex/development`；本阶段不合入 `main`、不部署。
