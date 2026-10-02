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
- `TemporaryRejectionIT` 已加入真实数据库组合测试，但本轮未运行：D 盘可用空间约 20.33 GiB，接近 20 GiB 硬阈值，未启动新的隔离 MySQL。该层保持未验，不能以离线测试替代。

## 提交与边界

- 提交：`eb7ac94`，已推送 `codex/development`。
- 工作树中的其他文档、旧更正链 IT 和脚本差异为用户预存内容，未纳入本批提交。
- 下一步：在不触及磁盘硬阈值且具备隔离 MySQL 时运行 `TemporaryRejectionIT`；通过后再实现 `PHYSICAL_DISPOSITION`，只接受 `VERIFIED_COMPLETE_AT_STOP` 证据并按逐包处置释放占用。
