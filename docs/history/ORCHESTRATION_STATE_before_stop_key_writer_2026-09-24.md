<!-- CURRENT_STATE_START -->
## 当前有效状态（唯一接续入口）
### 2026-09-24 R06/E2a：直接登记公钥来源已验证，第六步仍未完成

- 写权：当前助手单写 `D:/acrylic-pda-worktrees/r06-e2a-migration` 的 E2a 业务/隔离环境；主树 `D:/acrylic-pda-outbound` 仅管理/历史。无旧代理或并发写者，重型槽空闲。原库、现网、设备、正式部署与审批未触及。
- Git：业务树 `codex/r06-e2a-migration` @ `568683c1a1f8257ad0596a654c6b3558c767ed95`，约 95 项未提交/未跟踪（以该树 `git status --short` 为准），保留 V35/V36/PAGE/STOP 原脏改；本轮修正设备公钥选择器、IT 和专项验收记录。主树 `codex/development` @ `23ae8c492acf47e89875852d2573392fcf379f80`，保留 Android/server/管理原脏改及 `docs/out.xlsx`；本轮只写管理与历史。两树均未提交、推送、合并、发布或部署。
- 作者/代码：STOP 只读门槛先核对已存 OPEN 原字节，再由服务器选择当前设备登记公钥；无配对关联的管理员直接登记允许，存在配对则须已完成、批准字段存在且配对公钥/摘要与登记一致，再重验原 STOP 签名。它仍核原回执对象、STOP 文件、PAGE 根及逐条文件；失败关闭，不生成可信 VERIFY。旧公钥轮换与未知历史 key 仍不能作为可信来源。
- 作者/单测：本轮公钥选择器与前置门槛聚焦测试通过；最新完整 `./gradlew -p server test --no-daemon` 为 56 XML、384 测试/0 失败/0 错误/1 Windows symlink 跳过。`compileTestJava`、`git diff --check` 通过。无本轮独立消费者、Android/APK、真实 HTTP 或组合验收；旧结果不冒充本轮验证。
- 作者/隔离实库：全新 `.local/e2a-v36-key-attempt3`（回环 19330、独立 data/schema/账号，**仅此隔离库关闭 binlog**）非空 V34→V35→V36 后，直接登记与合成完成配对公钥、跨仓拒绝及 key-hash 篡改回滚 1/1 通过。XML SHA-256 `8AFF8AA5432F93A7C0784C6B7F0A47ADAACEAA68BFC31800A82530ABB6979662`；事后 SQL 36 成功 Flyway、1 配对、0 intake、0 失败迁移。不是生产迁移授权或真实配对审批/完整前置门槛验证。
- 失败保留：attempt2 初启 Windows 空格参数失效；重启后 root TCP 禁止，改一次性 init-file 配置；实际 one-shot V35 因 binlog/无 SUPER 报 1419，XML SHA-256 `82487D653132D8FCF9A089573FC9B119420CB2FF88DA6F741406F9764F69408A`，35 行含失败迁移。该库/标记/日志保留且不重跑。attempt3 与其独立，关闭 binlog 的通过不得覆盖生产开启 binlog 的 V35 授权限制。
- 环境：attempt2 PID 3884、attempt3 PID 2504 均身份核对后经专用账号优雅关停；19330 无监听。共享 3306/PID 5448 未操作；各 one-shot datadir、私有账号与失败证据保留。专项目录 `docs/parallel/R06_E2A_MIGRATION/ACCEPTANCE_STAGES.md` 记录作者证据，旧 STOP/PAGE 结果仅按旧范围引用。
- 限制/安全：原始审计连续性、FORMAL/TEMP 全序和明细语义、历史 keyring、逐包 B/K、set/真实 VERIFY/GET/currentEffect、真实配对审批、HTTP/Android、原机重启耐久仍未验/未开发。人工、长时/真机/现场可后延并显式标未验；R05 整体、code20/Room17 和 R09 限制不变。七类生产决定 handler/POST、放行关闭，`localDeletionAllowed=false`；不合 `main`、不部署。
- 下一断点：沿原树完善可信历史公钥/原始审计及全设备连续性，在全新隔离 profile 消费完整前置门槛，随后受控 set/VERIFY、逐包事实、GET/HTTP/Android。第六步代码与自审、非人工可完成验收未完成前不进入 R07。
- 被替换当前区原文：`docs/history/ORCHESTRATION_STATE_before_direct_key_2026-09-24.md`；更早快照见 `docs/history/README.md`。
<!-- CURRENT_STATE_END -->
