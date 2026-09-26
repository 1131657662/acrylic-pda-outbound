<!-- CURRENT_STATE_START -->
## 当前有效状态（唯一接续入口）
### 2026-09-24 R06/E2a：全设备审计库内前缀已校验，第六步未完成

- 写权：当前助手单写 `D:/acrylic-pda-worktrees/r06-e2a-migration` E2a 业务；主树 `D:/acrylic-pda-outbound` 仅管理/历史。R09 另有独立任务，仅在其工作区开发，已通知其本轮 R06 重型槽释放；不共享文件、数据库或部署写入。原库、现网、设备、正式部署/审批未触及。
- Git：业务树 `codex/r06-e2a-migration` @ `568683c1a1f8257ad0596a654c6b3558c767ed95`，原 V35/V36/PAGE/STOP 脏改及本轮审计前缀类、测试、专项文档约 98 项未提交/未跟踪保留。主树 `codex/development` @ `23ae8c492acf47e89875852d2573392fcf379f80`，原 Android/server/管理脏改及 `docs/out.xlsx` 保留，本轮只改管理/历史。准确清单见各树 `git status --short`；未提交、推送、合并、发布、部署。
- 作者/代码：STOP/VERIFY 只读门槛先从已存 OPEN、服务器当前设备公钥重验 STOP 原签名，核 STOP 原回执/对象/文件与完整 PAGE 根/原始对象；本轮新增按签名 `auditThroughSeq` 从 1 逐条分页读 `pda_audit_event` 的原设备 FORMAL/TEMP 共用前缀，重验事件 JSON、摘要、设备/仓/原人/来源及跨 `submitted_event` 同 ID/序号摘要冲突、未来恢复代次，缺口失败关闭。STOP 写入首次读事务及落库锁内仍用相同公钥来源。该前缀不是页内 AUDIT/ARCHIVE 语义或 STOP 端点事件的完整核验，不生成可信 VERIFY。
- 作者/单测与自审：审计前缀 5 项正反例（含正式/临时序列、缺口、篡改、跨表冲突）及前置门槛聚焦通过；最新 `./gradlew -p server test --no-daemon` 57 XML、391 测试/0 失败/0 错误/1 Windows symlink 跳过，`compileTestJava` 与两树 `git diff --check` 通过。无本轮独立消费者、全服务实库/HTTP、Android/APK、模拟器/真机或组合验收。
- 作者/全新隔离实库：`.local/e2a-v36-audit-attempt1` 首次只测库内前缀；自审补跨 `submitted_event` 后另建 `.local/e2a-v36-audit-attempt2` 消费当前候选，在回环 19330、独立 datadir/schema/账号、仅此隔离库关闭 binlog 的非空 V34→V35→V36 上 FORMAL/TEMP 序列及摘要/缺口事务回滚 1/1 通过。最新 XML SHA-256 `A2ACD7FCD3F3780DA2851D7C00A7E15AAD8FD58376B1EDF68BA73866FA3D605D`；事后 36 成功 Flyway/0 失败迁移/2 审计（各类 1）。旧 attempt1 XML SHA-256 `682B2073251559A8FA997C41EF00E9A081BD942D21FB99B05BE320C9FC418F19` 只按旧范围引用；均非完整 VERIFY/生产 binlog 迁移验收。
- 历史失败：key attempt2 启动路径/root TCP 问题后实际 one-shot V35 因 binlog/无 SUPER 1419 失败，XML SHA-256 `82487D653132D8FCF9A089573FC9B119420CB2FF88DA6F741406F9764F69408A`，原库/标记/日志保留；关闭本地 binlog 的通过不能掩盖生产授权阻断。此前 STOP 写入测试旧错误码断言首次失败已修复并留专项记录。
- 环境/槽：本轮 audit attempt1 PID 19076、attempt2 PID 13824 均身份核对后以专用账号鉴权优雅停机；最后检查 19330 无监听、无 R06 重型进程，共享 3306/PID 5448 未操作。重型槽已明确释放并通知 R09；后续运行前重新核对其在途执行与端口。one-shot 目录、私有数据与日志均保留，证据见 `docs/parallel/R06_E2A_MIGRATION/ACCEPTANCE_STAGES.md`。
- 未完/安全：原始 PAGE AUDIT/ARCHIVE 逐字节/语义与签名归档对库内前缀的覆盖、STOP 自身事件/高水位、完整历史 keyring、FORMAL/TEMP 全序与逐包 B/K、set/真实 VERIFY/GET/currentEffect、真实配对审批、全服务实库/HTTP/Android 仍待开发/核验。人工、长时/真机/现场可后延但必须标未验；R05 整体、code20/Room17、R09 限制保持。七类生产决定 handler/POST、放行关闭，`localDeletionAllowed=false`；不合 `main`、不部署。
- 下一断点：沿原树把 PAGE 原始 AUDIT/ARCHIVE、STOP 事件、高水位及库内全设备前缀按原字节/归档签名相互绑定，再做受控 set/VERIFY、逐包和读写路径；先核 R09 重型槽占用，不能并发启新隔离库。第六步未完成自审前不进入 R07。
- 被替换当前区原文：`docs/history/ORCHESTRATION_STATE_before_audit_prefix_2026-09-24.md`；更早快照见 `docs/history/README.md`。
<!-- CURRENT_STATE_END -->
