# Room18 主树 Android 写权转移前的当前状态原文

被替换区原文，仅供历史定向查阅。

<!-- CURRENT_STATE_START -->
## 当前有效状态（唯一接续入口）
### 2026-09-24 R06/E2a：Android 线协议、STOP 签名原语及全设备序列门槛作者自审，第六步仍在开发

- 目标：完成 R01–R09 无需人工/长时测试的开发，逐子项实施、自审、修复。当前沿 R06；R07–R09 不提前判完成。
- 写权：当前助手单写 `D:/acrylic-pda-worktrees/r06-e2a-migration` E2a 业务与 `D:/acrylic-pda-outbound` 管理/历史。旧 R07/R08/R09 任务不自动恢复；生产/原库/设备/正式部署及审批边界不触碰。
- Git：业务树 `codex/r06-e2a-migration` @ `568683c1a1f8257ad0596a654c6b3558c767ed95`，主树 `codex/development` @ `dccb73b10acbb63020beb3cf827b3b4580d3ed1c`。两树均有原有及本轮未提交改动；精确路径见 `docs/history/R06_E2A_ANDROID_WIRE_STATUS_2026-09-24.md`，后续以实际 `git status --short` 核实。未提交/推送/合并/打标签/部署，`out.xlsx`、原资料及旧树未覆盖。
- 后端作者：STOP 独立原对象与 `PARTIAL` 回执；VERIFY 页/STOP/原字节/来源 B/K 核验后建 set/item，并在 B/K 锁序下逐活动包插入 `SOURCE_RESERVED` 窄占用，重复同源不重插，异源冲突拒绝；历史 DER 公钥配置按 kid 验签原回执/归档，未知 key 失败关闭。旧 set manifest_bytes UUID 误读已修复。后端上一版全套 74 XML/463 项、0 失败/错误、1 Windows symlink 跳过；新增 Android 后未重跑后端，非当前全链组合结果。
- Android 作者/自审：新增 `E2aWire`、`E2aItemValidation`、`E2aStopProof` 和 3 份聚焦测试；PAGE/manifest 空集与服务端固定原字节/hash 一致，页/全 set 去重、字段/数字/UUID/限额、篡改及链序负例保守拒绝。STOP core 与服务端向量字节一致；本机签名原语在原公钥、kid、P-256 和 ECDSA 本地验签后才生成 envelope，异公钥/非 P-256/非法高水位拒绝。另 `E2aDeviceCoverage` 在同一 Room 事务内按 256 条分页核 FORMAL/TEMP 全设备审计及捕获连续序列，缺段、重复、计数器过界或事务外访问失败关闭；尚未把停止事件/冻结状态/原键写入 Room，不能称原机停止或签名上传。
- Android 验证：设置 `ANDROID_HOME=C:/Users/qq113/AppData/Local/Android/Sdk`，从管理树 `server/build/` 复制 10 个已有生成夹具到业务树忽略的 `server/build/`，仅为 Android 单测输入，不当作当前业务树服务端生成或组合证据。`./gradlew -p android :app:testDebugUnitTest --tests 'com.acrylic.outbound.pda.evidence.E2a*' --no-daemon` 聚焦三 XML/14 项、0 失败/错误/跳过；Android 全套 70 XML/288 项、0 失败/错误/跳过，主/测试编译通过。`git diff --check` 无空白错误（既有 Windows LF→CRLF 提示）；未跑 APK/模拟器/真机。
- 失败/限制：初始 Android 无 SDK 路径及生成夹具导致任务输入失败；已配置 SDK 并按上述只用于单测的夹具路径重试通过。STOP 本机事务停扫、全设备 audit/捕获高水位连续耐久、原键续传、真实 PAGE→STOP→VERIFY、逐包 K 新写者隔离 MySQL/HTTP/独立消费者仍缺。历史 keyring 仅代码和合成轮换，真实旧公钥清单未配置。此前 attempt2 binlog/SUPER 1419 失败保留，attempt6 非空升级证据是旧代码版本；不能替代本轮组合。R05 code20/Room17、R09 恢复/现场和长时限制仍单列。
- 安全：VERIFY 当前仅 `PARTIAL`；七类生产决定 handler/POST、放行及关闭能力仍关闭，`localDeletionAllowed=false`；不合 main、不部署。19330、19434/19435 无监听，共享 3306/PID 5448 未操作。生产/原库/设备/审批继续阻断。
- 下一断点：先在原 Android Room17 树设计停止事件与原件/原键同事务耐久、失败恢复；已有全设备连续序列只读门槛，不能代替冻结。主树另有未提交 Room18 变更，组合迁移版本须避免覆盖。每子项先自审/修复再前进；后补隔离非空 MySQL 的 K 写者和真实 HTTP 全链，随后核 R07–R09 可开发项。
- 被替换区原文：`docs/history/ORCHESTRATION_STATE_before_android_coverage_2026-09-24.md`；历史索引 `docs/history/README.md`。
<!-- CURRENT_STATE_END -->
