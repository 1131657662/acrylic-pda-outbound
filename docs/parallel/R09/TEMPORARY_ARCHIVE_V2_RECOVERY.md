# TEMP V2 与 FORMAL V1 混合归档快照恢复

2026-09-25 专用恢复子项通过，详见[验收与失败记录](../../history/R09_TEMPORARY_ARCHIVE_RECOVERY_ACCEPTANCE_2026-09-25.md)。当前有效状态以[看板顶部](../../ORCHESTRATION_STATUS.md)为准。

`ops/submission_archives.py` 保留 FORMAL V1 两文件分支，并将 TEMP V2 三文件分流到 `temporary_archives.py`。后者验证原来源/资格/成功回执，再由 `temporary_archive_commands.py`、`command_archive_proofs.py`、`command_archive_rows.py` 验证原确认、独立短装批准、事件/审计/幂等与原 BLOB 引用。`temporary_archive_products.py` 独立计算冻结计划/ACTIVE扫描结果。未知版本、非首映射/首轮或非零候选不在本项支持范围，直接拒绝。

实验入口 `ops/temporary_archive_probe.py` 只允许本任务 `.local/e2a-cross-20260925-N` 的精确 datadir、19330、专用账号和源/恢复 schema，并核 V36。它没有扩大通用 `ops/cli.py` / `consistency.inspect` 的版本支持。后者仍拒绝 V36。

服务端测试树 `tools/e2a-cross-source-verify.ps1 -Run <未使用编号> -ArchiveRecovery` 新建一次性受保护环境，运行 `E2aTemporaryArchiveRecoveryIT`。不得复用已运行编号或将连接改向共享/生产数据库。实际命令通过实验参数创建独立恢复 schema；恢复应用配置只在测试启动器中覆盖。

最终 attempt47 组合：非空 V34→V36；102业务表/15文件和Flyway历史完整比对；3归档/8文件引用/3原决定闭合；11故障测试及恢复后真实HTTP通过。失败attempt44/46和阶段通过45均保留。全Python85项中74通过、11条件skip；同11项在47中实跑。没有独立人员复审。

本项只恢复已保存快照。源/恢复是同一专用MySQL实例的分离schema；未证明异机灾备、截点外重建、库外权威控制、fence、RPO/RTO或历史密钥轮换。所有生产决定/放行/关闭仍未开放，`localDeletionAllowed=false`。
