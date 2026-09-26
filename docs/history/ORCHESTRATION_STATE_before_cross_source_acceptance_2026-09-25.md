<!-- CURRENT_STATE_START -->
## 当前有效状态（唯一接续入口）
### 2026-09-24 新会话交接：R06/E2a 跨端来源事实修正红测；前九步目标未完成

- 目标/状态：R01–R09 中无需人工辅助或长时间测试的开发逐子项实施、自审、修复；本会话目标已暂停以便交接，不把 R06 局部成果或旧阶段证据写成第六步/前九步完成。新会话按用户随后提示建立目标，从此断点继续，不重建任务/树或恢复旧派工模式。
- 写权：本会话停止业务写入；新会话接管前核对是否另有活动写者，同一文件、隔离数据库或部署目标只允许一个写者。保留两棵原树和所有未提交改动，不触碰原库、现网、设备、审批和正式部署。
- Git：主树 `D:/acrylic-pda-outbound`，`codex/development` @ `dccb73b10acbb63020beb3cf827b3b4580d3ed1c`；服务端树 `D:/acrylic-pda-worktrees/r06-e2a-migration`，`codex/r06-e2a-migration` @ `568683c1a1f8257ad0596a654c6b3558c767ed95`，主树 `origin` 为 GitHub。两树仍有大量未提交文件；交接时 `git status --short --untracked-files=all` 路径快照见 `docs/history/R06_E2A_SESSION_HANDOFF_STATUS_2026-09-24.md`（主树 121、服务端 145 条，快照本身及本次索引/看板修改另核），新会话重新核实际状态。未提交、推送、合并、打标签或部署；`docs/out.xlsx`、崩溃日志及其他原在途文件不得误暂存/删除。
- Android 作者/自审：Room19 TEMP 冻结事务已保存全设备 FORMAL+TEMP 的 AUDIT/CAPTURE/SCAN 规范对象、冻结正文及已有签名 AUDIT_ARCHIVE 回执/历史公钥原件；重启重验，缺件、篡改、坏签名失败封闭。最新 `./gradlew -p android :app:testDebugUnitTest --no-daemon` 为 77 XML/311 项、0 失败/错误/跳过；未验 APK、模拟器/真机、网络组合。PAGE/manifest/RAW 原键仍未耐久，STOP 未签名，保守网络续传未接。
- 服务端作者/阻断：现有 STOP/VERIFY `PARTIAL`、set/item 与 B/K 窄占用、历史 kid 验签属于在途实现；发现 Android `captureHighWatermark` 是全设备序号，旧 `SourceEvidenceIntakeBusinessFacts` 错以其等于当前 TEMP 捕获数，含先前 FORMAL 捕获的集合会无法 VERIFY。服务端树未提交 `SourceEvidenceIntakeBusinessFacts.java`、新增 `SourceEvidenceIntakeOtherFacts.java`、`SourceEvidenceIntakeSetWriter.java` 与测试正在区分本 TEMP 和其他来源，其他来源须由后台 `submitted_capture`/原正文验证，只让本 TEMP 活动扫码写 K；尚未完成自审或组合验证。
- 当前红测：`SourceEvidenceIntakeBusinessFactsTest.fullDevicePrefixCanContainEarlierFormalCaptureWithoutReservingItsBale` 在第 100 行 Mockito varargs 把实际 `byte[]` 当 `Object[]`，抛 `ClassCastException`；失败 XML 在服务端树 `server/build/test-results/test/TEST-com.acrylic.outbound.temporary.evidence.SourceEvidenceIntakeBusinessFactsTest.xml`。先改测试参数为 `(byte[]) call.getArgument(2)`，复跑 BusinessFacts、LockOrder、SetWriter 聚焦测试，补他源缺失/伪造/跨仓和不占 K 负例，再自审。此前聚焦 17 项通过及服务端全套 74 XML/463 项、0 失败/错误、1 Windows symlink 跳过均属旧版本证据，不是当前服务端通过。
- 独立/组合/环境：新服务端改动未做独立消费者、隔离非空 MySQL、真实 HTTP 或 Android→服务端组合验证；19330/19434/19435 无监听，没有 Java/Gradle 在途重测，重型槽空闲。共享 3306/PID 5448 未操作；attempt2 binlog/SUPER 1419 失败、attempt6 较早代码实库证据保留，不冒充当前通过。
- 安全与历史限制：七类生产决定 handler/POST、放行和关闭能力保持关闭，`localDeletionAllowed=false`；R05 code20/Room17、R09 恢复/现场和人工/长时测试继续单列，真实历史 keyring 尚未部署。R07–R09 按路线逐项核对可开发范围，不能跳步或重复已做的历史工作。
- 下一断点：先修上述测试夹具和服务端跨来源事实链，完成每个子项自审/负例/聚焦→全套→隔离 MySQL/HTTP；再沿主树 Room19 补 PAGE/manifest/root 与 RAW 请求/原键耐久、STOP 签名及 OPEN→PAGE→STOP→VERIFY 丢回执保守续传。新会话先读 `AGENTS.md`、`docs/GIT_WORKFLOW.md` 和本区，核 Git/监听/活动写入，再按用户新提示立目标。
- 旧树规则整理（2026-09-24）：`D:/acrylic-pda-worktrees/r06-e2a-migration/AGENTS.md` 已缩为指向主树规则/本看板的入口；原 36,998 字节全文逐字节归档于该树 `docs/history/AGENTS_R06_E2A_MIGRATION_before_trim_2026-09-24.md`，SHA256 `56967D5485D0AAE392DF20728A5D42D4D9C5FAC1C59F291965D55F8E0B5B580A`。主树 `AGENTS.md` 与业务代码本轮未改；原未提交改动和上述红测断点保持。
- 被替换区原文：`docs/history/ORCHESTRATION_STATE_before_session_handoff_2026-09-24.md`；历史索引 `docs/history/README.md`。
<!-- CURRENT_STATE_END -->
