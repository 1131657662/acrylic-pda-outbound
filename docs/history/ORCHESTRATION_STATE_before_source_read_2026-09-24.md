<!-- CURRENT_STATE_START -->
## 当前有效状态（唯一接续入口）
### 2026-09-24 R06/E2a：签名审计归档与 PAGE 全设备前缀覆盖已局部核验，第六步未完成

- 写权：当前助手单写 `D:/acrylic-pda-worktrees/r06-e2a-migration` E2a 业务；主树 `D:/acrylic-pda-outbound` 只写管理/历史。R09 独立任务不共享目标。未触原库、现网、设备、正式部署/审批。
- Git：业务树 `codex/r06-e2a-migration` @ `568683c1a1f8257ad0596a654c6b3558c767ed95`，原 V35/V36/OPEN/PAGE/STOP 及本轮签名归档/覆盖测试和文档均未提交；主树 `codex/development` @ `23ae8c492acf47e89875852d2573392fcf379f80`，原 Android/server/管理脏改与 `docs/out.xlsx` 保留。准确未提交清单以两树 `git status --short` 为准；未提交、推送、合并、发布或部署。
- 作者/代码：PAGE `ARCHIVE/AUDIT_ARCHIVE` 原字节核稳定设备/恢复代次/起点原键、服务器归档索引/签名/耐久正文，逐条核 FORMAL/TEMP 库内原事件及授权；不可用历史 key/文件保留 503，内容冲突 409。PAGE AUDIT 明细和已验签归档范围无缺口、无重叠覆盖签名 STOP 的全设备审计 1..`auditThroughSeq`，完整页数不再等于完整审计。原 AUDIT 原字节和 STOP 事件端点仍按前轮校验；不生成可信 VERIFY。
- 作者/自审：聚焦签名/文件、归档入口、原键、缺口/重叠与 preflight 反例通过；最新 `./gradlew -p server test --no-daemon`：62 XML、412 项、0 失败/0 错误、1 Windows symlink 跳过；`git diff --check` 无错误。先前聚焦因 mock 覆盖标记未更新失败 2 项，第一次全套因合成 PAGE 夹具审计不完整失败 1 项，均修复后重跑通过。误用标准 test 任务筛选 IT 时仅编译且报告无测试，不算 IT 通过。新增 IT 类在最后全套后编译，单独一次性运行通过；没有独立消费者、完整服务 HTTP/Android/APK/模拟器/真机/组合验收。
- 作者/新隔离实库：`.local/e2a-v36-audit-attempt6` 新 datadir/schema/账户、仅此实例关闭 binlog、非空 V34→V35→V36，合成 RSA 签名归档＋耐久文件/真实 JDBC 索引＋2 条 FORMAL/TEMP 原审计的 opt-in IT 1/1；XML SHA-256 `FD1D7E645A6504C7F91580CED9C6FCFD3AF2B77E7D578D700E4C69A1D1A17CB6`，事后 36 成功迁移/0 失败、2 审计（1..2）、1 归档（0..2）。专用 PID 25880 身份/库/账号核对后鉴权关停；19330、19434/19435 无监听，共享 3306/PID 5448 未操作。私有数据/标记/日志原样保留，证据见 `.local/e2a-v36-audit-attempt6/private/` 和 `docs/parallel/R06_E2A_MIGRATION/ACCEPTANCE_STAGES.md`。
- 失败/限制：旧 audit attempt4/5 只证明各自旧代码版本；key attempt2 生产式 binlog/无 SUPER 导致 V35 1419 失败仍保留，隔离库关闭 binlog 通过不能代表生产迁移授权。历史轮换 keyring、所有 source item/捕获高水位与原机停止耐久、逐包 B/K、真实 set/VERIFY/GET/currentEffect、完整服务/HTTP/Android/设备/现场均未完成。人工/长时/真机/现场可后延仍标未验；R05 整体、code20/Room17、R09 限制保持。七类生产决定 handler/POST/放行关闭，`localDeletionAllowed=false`，不合 `main`、不部署。
- 下一断点：沿原业务树开发捕获 `captureHighWatermark`、逐包原事实与 STOP set/VERIFY；先自审并核原机耐久/历史 keyring 约束，不把合成签名或隔离 IT 冒充原身份/生产可用。每子项完成后审查修复再继续；启隔离库前重新核重型槽。
- 被替换当前区原文：`docs/history/ORCHESTRATION_STATE_before_archive_coverage_2026-09-24.md`；更早快照见 `docs/history/README.md`。
<!-- CURRENT_STATE_END -->
