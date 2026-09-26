# 2026-09-25 管理文档精简前原文

用户要求核对旧目标是否过期、整理接续提示并精简管理文档。本次先在 2026-09-25 22:18（Asia/Shanghai）复制以下七份工作区原文，再逐文件校验 SHA-256 完全一致；含既有未提交改动，未用 HEAD 覆盖工作区内容。

- [AGENTS.md](AGENTS.md)
- [Git工作流](docs/GIT_WORKFLOW.md)
- [原状态看板及完整历史](docs/ORCHESTRATION_STATUS.md)
- [原开发进度](docs/DEVELOPMENT_PROGRESS.md)
- [原十步路线](docs/DEVELOPMENT_ROADMAP.md)
- [原剩余计划与旧多会话指令](docs/REMAINING_DEVELOPMENT_PLAN.md)
- [原历史索引](docs/history/README.md)

[manifest.json](manifest.json)保存原仓库路径、字节数和SHA-256。原文件保持原字节，因此其中相对链接仍以manifest所列原仓库路径为基准解析；不要把归档中的“当前”、旧角色、旧任务或接续指令当作有效指令。

整理后的当前入口为[主看板](../../ORCHESTRATION_STATUS.md)。本次只整理文档并回读测试证据，没有执行产品测试、业务修改、数据库操作、提交、推送或发布。已核对旧任务末条进度、Room27源码版本、13XML共61项（60通过/1跳过/0失败）；这些是上一开发批次结果，不是本次重跑。

61项报告及原日志另存于本机 outputs/management-refresh-20260925-01a0d8e5/，不包含私钥或凭据。原Room26/attempt57验收仍按其原范围引用。原始失败、R05/现场/R09未验均通过原文保留，当前待办重新进入主看板和剩余计划。

官方AGENTS指导参考：[OpenAI Docs](https://developers.openai.com/codex/guides/agents-md)。本次没有改Codex全局设置或提高指令加载上限；采取长期规则、当前事实、验收索引、范围与历史各有入口的文档结构。
