# R06 TEMP 拒绝子项验收（2026-10-02）

## 范围

本批只实现 M2b-3 的拒绝段：有权复核人通过 `REJECT` 命令记录 TEMP 拒绝，拒绝原因进入不可变命令正文与提交 `issues_json`；提交转为 `REJECTED`，原 `REVIEW_RESERVED` 占用保留。物理处置、完整停止证据和释放占用不在本批自动发生。

## 实现

- 新增隔离可组装的 `TemporaryRejectionOperation`，要求人工候选上下文、禁止原提交人员自决，并在同一命令事务中写拒绝状态、订单事件和命令回执。
- 命令权限映射、已知命令集合、回执决定校验、历史完整性校验和当前效力读取支持 `REJECT`。
- 生产 `outbound.temporary.commands.enabled` 默认仍关闭；本批没有迁移、部署、原库或现网操作。

## 验证

- `server` 完整离线 `test`：151 个测试类、2969 项，0 失败、0 错误、1 个既有 skip。
- 临时命令包定向回归：47 项通过。
- `TemporaryRejectionOperationTest`：通过，覆盖拒绝原因、拒绝状态、订单事件和保留占用的调用契约。
- `TemporaryRejectionIT` 真实隔离 MySQL attempt 856：1 类、1 测试、0 失败、0 错误、0 跳过；固定隔离端口 `19434`，测试结束后端口释放，系统 MySQL 未触碰。
- attempt 853 的 NPE（IT 缺少 Spring Boot 上下文）及 854/855 的 runner 收尾失败均保留；856 修正后通过。清理后首次离线回归因字体下载超时未进入测试，随后跳过仅下载字体的 `prepareFont` 任务重跑定向两项单测并通过。

## 提交与边界

- 产品提交：`eb7ac94`；真实 IT 装配与 runner 收口提交：`f015954`，均已推送 `codex/development`。
- 工作树中的其他文档、旧更正链 IT 和脚本差异为用户预存内容，未纳入本批提交。
- 下一步：目标按用户指令暂停；恢复后再实现 `PHYSICAL_DISPOSITION`，只接受 `VERIFIED_COMPLETE_AT_STOP` 证据并按逐包处置释放占用。

## 本次清理

- 删除三份未被文档或工具引用的重复数据库快照：`before-0.1.18.tar`、`after-0.1.19-before-new-work.tar`、`final-debug-0.1.19.tar`，合计约 4.1 GiB。
- 删除主树及旧 worktree 的可再生 `build`、`.gradle`、`node_modules` 缓存；这些目录可由 Gradle/npm 重建。
- `.local`、`history-database.tar`、crash-smoke 的故障前/故障中/恢复快照、当前 Demo 包及文档引用的历史证据均保留；`localDeletionAllowed=false` 下 runner 不再自动删除 `.local` 数据目录。
