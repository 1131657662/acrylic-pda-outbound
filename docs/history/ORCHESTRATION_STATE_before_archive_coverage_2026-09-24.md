<!-- CURRENT_STATE_START -->
## 当前有效状态（唯一接续入口）
### 2026-09-24 R06/E2a：AUDIT 原件/授权与签名 STOP 端点已局部核验，第六步未完成

- 写权：当前助手单写 `D:/acrylic-pda-worktrees/r06-e2a-migration` 的 E2a 业务；主树 `D:/acrylic-pda-outbound` 仅管理/历史。R09 独立任务不共享目标；旧 B/C/D 不恢复。原库、现网、设备和正式部署/审批未触及。
- Git：业务树 `codex/r06-e2a-migration` @ `568683c1a1f8257ad0596a654c6b3558c767ed95`，99 项未提交/未跟踪（V35/V36、OPEN/PAGE/STOP、审计和专项文档及既有改动）；主树 `codex/development` @ `23ae8c492acf47e89875852d2573392fcf379f80`，原 Android/server/管理及 `docs/out.xlsx` 等 72 项脏改全部保留。准确清单以两树 `git status --short` 为准。未提交、推送、合并或发布。
- 作者/代码：PAGE `AUDIT/AUDIT_RECORDS` 原对象按长度、摘要、规范字节、ID/序号对齐库内事件，禁止超过签名 STOP 审计截点；库内 FORMAL/TEMP 前缀从 1 连续分页核验原授权、原操作员、源代次、payload 摘要及旧 `submitted_event` 冲突。新增只读 STOP 端点检查：签名 `auditThroughSeq` 处必须是指定 ID、原人和原 TEMP 资格的 STOP 事件。自审反例覆盖错 ID/资格/人/截点/类型；不据此生成可信 VERIFY。
- 作者/单测：最近 `./gradlew -p server test --no-daemon`：58 XML、397 项、0 失败、0 错误、1 Windows symlink 跳过；聚焦和 `git diff --check` 通过。末次完整回归在 opt-in IT 扩充之前，扩充仅影响一次性 IT。无独立消费者、完整服务实库/HTTP、Android/APK、模拟器/真机或组合验收。
- 作者/一次性实库：原字节/授权候选 `.local/e2a-v36-audit-attempt4` 1/1，XML SHA-256 `BCDCC0F165AC4E83892A0D5DF164DFA6A0D40BC7A2385071A793F4C001A177ED`；后续 STOP 端点候选全新 `.local/e2a-v36-audit-attempt5` 1/1，XML SHA-256 `E501DE33F37196BA994C2142A956B7CA50230F3EFDFFDE73381200AFDBEA0769`。各有 36 成功 Flyway/0 失败迁移、2 同设备 FORMAL/TEMP 审计；仅隔离库关闭 binlog。PID 23908/8168 核身份后专用账号鉴权优雅关停；19330、19434/19435 无监听，共享 3306/PID 5448 未操作。证据在两 attempt 的 private/ 和 `docs/parallel/R06_E2A_MIGRATION/ACCEPTANCE_STAGES.md`。
- 失败/限制：audit attempt4 首次 SQL 取证查询了不存在的表，未写证据，修正后取证/停机；首次预检因 MySQL 批量转义路径误判，原样输出重新核对后才运行 one-shot。key attempt2 V35 在生产式 binlog/无 SUPER 权限报 1419（原失败库/XML 保留）；本地关闭 binlog 通过不代表生产迁移授权。audit attempt1/2/3 和旧结果只按其当时范围引用，所有 one-shot 数据/日志/失败标记保留。
- 未完：签名归档 `AUDIT_ARCHIVE` 对原归档索引/文件/库内前缀的逐条对应、所有 PAGE 明细覆盖与高水位、历史 keyring、逐包 B/K、真正 set/VERIFY/GET/currentEffect、Android 原机停止耐久及真实 HTTP/设备/现场未完成。人工/长时/真机/现场可后延但必须未验；R05 整体、Android code20/Room17、R09 限制仍单列。七类生产决定 handler/POST/放行仍关闭，`localDeletionAllowed=false`；不合 `main`、不部署。
- 下一断点：沿业务树先确认归档 PAGE 原件的记录 ID/原签名契约，再把签名归档和库内前缀逐条闭环、核 PAGE 全量覆盖及停止高水位；每子项自审修复后再做 set/VERIFY 和读写路径。不要借合成事件或当前服务器 key 代替历史原件；启新实库前重核重型槽。
- 被替换当前区原文：`docs/history/ORCHESTRATION_STATE_before_audit_endpoint_2026-09-24.md`；更早快照见 `docs/history/README.md`。
<!-- CURRENT_STATE_END -->
