<!-- CURRENT_STATE_START -->
## 当前有效状态（唯一接续入口）
### 2026-09-24 R06/E2a：STOP 写入与前置核验公钥来源对齐，第六步未完成

- 写权：当前助手单写 `D:/acrylic-pda-worktrees/r06-e2a-migration` E2a 业务/隔离环境；主树 `D:/acrylic-pda-outbound` 仅管理/历史。无其他活跃写者，重型槽空闲；原库、现网、设备、生产部署/审批未触及。
- Git：业务树 `codex/r06-e2a-migration` @ `568683c1a1f8257ad0596a654c6b3558c767ed95`，原 V35/V36/PAGE/STOP 约 95 项未提交/未跟踪保留；本轮改内部 STOP 服务/作者测试及 E2a 验收记录。主树 `codex/development` @ `23ae8c492acf47e89875852d2573392fcf379f80`，原 Android/server/管理脏改及 `docs/out.xlsx` 保留，本轮只写管理/历史。准确清单分别见两树 `git status --short`；均未提交、推送、合并、发布、部署。
- 作者/代码：STOP 服务首次解析现位于读事务，用与只读 VERIFY 前置相同的 `SourceEvidenceIntakeDeviceKeys` 服务器公钥来源；仍要求当前设备 ACTIVE，并比对该登记公钥与选择结果。写事务在身份/来源锁内重解析，期间登记或配对变化失败关闭。直接登记当前公钥、已完成配对一致公钥可选；未知旧换 key 不冒充历史可信。STOP/PAGE/OPEN 已存根核对依旧只读，未形成可信 VERIFY 或放行。
- 作者/单测及自审：首次聚焦 12 项中 1 项旧断言仍预期 `STOP_SIGNATURE`，而早期设备 key 冲突现返回 `E2A_STOP_DEVICE_KEY_CHANGED`；修正断言并增 untrusted 负例后聚焦通过。最新完整 `./gradlew -p server test --no-daemon` 为 56 XML、385 测试/0 失败/0 错误/1 Windows symlink 跳过；`git diff --check` 通过。无本轮独立消费者、完整 STOP 服务实库、HTTP、Android/APK 或组合验收。
- 历史作者/隔离实库：上轮 `.local/e2a-v36-key-attempt3` 在专用回环 19330、关闭本地 binlog 的非空 V34→V36 后直接登记/合成配对 JDBC 1/1，XML SHA-256 `8AFF8AA5432F93A7C0784C6B7F0A47ADAACEAA68BFC31800A82530ABB6979662`，事后 36 成功 Flyway/1 配对/0 intake/0 失败迁移。此轮没有重启或重跑，不可将上轮窄通过称为本轮 STOP 服务/生产迁移通过。
- 失败/限制：key attempt2 初启 Windows 参数失效、root TCP 被拒后使用隔离 init-file；实际 one-shot V35 在 binlog/无 SUPER 下 1419 失败，XML SHA-256 `82487D653132D8FCF9A089573FC9B119420CB2FF88DA6F741406F9764F69408A`；失败库和证据完整保留，不能用 attempt3 关闭 binlog 的结果掩盖生产迁移授权。更多旧失败见专项验收记录。
- 环境：19330 无监听；共享 3306/PID 5448 未操作。attempt2 PID 3884 和 attempt3 PID 2504 前轮均经身份认证优雅停机，one-shot 标记、datadir、日志保留；本轮仅单测，无新 DB。证据见 `docs/parallel/R06_E2A_MIGRATION/ACCEPTANCE_STAGES.md`。
- 未完/边界：历史 keyring、全设备审计连续性/原始记录语义、FORMAL/TEMP 全序与逐包 B/K、set/真实 VERIFY/GET/currentEffect、真实配对审批、完整实库/HTTP/Android 仍未验/未开发。人工、长时/真机/现场可后延但标未验；R05 整体、code20/Room17、R09 限制保持。七类生产决定 handler/POST、放行关闭，`localDeletionAllowed=false`；不合 `main`、不部署。
- 下一断点：从既有 `pda_audit_event`/归档及原始 PAGE 对象核实全设备审计连续性和源字段、STOP 高水位；不把 count/MAX 当可信链。之后新隔离 profile 组合核验 STOP 前置、受控 set/VERIFY、逐包和读写路径。第六步未完成自审前不进入 R07。
- 被替换当前区原文：`docs/history/ORCHESTRATION_STATE_before_stop_key_writer_2026-09-24.md`；更早快照见 `docs/history/README.md`。
<!-- CURRENT_STATE_END -->
