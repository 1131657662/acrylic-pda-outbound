# 2026-09-23 E2a attempt6 前当前区原文快照

<!-- CURRENT_STATE_START -->
## 当前有效状态（唯一接续入口）
### 2026-09-23 09:01 R06 E2a 三次 one-shot 验收失败与进程收尾（未提交）

- 主树 `codex/development` @ `23ae8c492acf47e89875852d2573392fcf379f80`，仅本看板未提交及原 `docs/out.xlsx` 未跟踪（SHA `C3BABA70…` 保持）；原迁移树 `codex/r06-e2a-migration` @ `568683c1a1f8257ad0596a654c6b3558c767ed95`，本任务未提交的验证代码/测试/专项文档保留。未推送、合并、部署；仅本任务接替原树 E2a 本机验证代码写权，远端部署及其他数据库不动。
- 聚焦验证最新实际 `E2aMysqlLauncherTest` 3、`E2aMigrationEnvironmentTest` 10、`E2aSnapshotFilesTest` 2，合计 **15/0失败/0错误/0跳过**，`compileTestJava` 成功、`git diff --check` 无空白错误。启动器修复二次中断退出核实、Windows PowerShell 子进程污染模块路径，并使受保护的单用户 ACL 重复核对幂等；新增 attempt3 精确 DB/账号/datadir/storage/signing/private/evidence/marker 护栏，测试拒绝旧两次配置。生产 V35 未改。
- **动态失败分别保留，不计通过**：attempt1 初始化前 ACL 模块加载失败（`.local/e2a-launcher-started.marker`、`e2a-dynamic-20260923.stdout.txt`）；attempt2 MySQL 初始化后第二次 `Set-Acl` 因 `SeSecurityPrivilege` 失败，`.local/mysql19329-attempt2/data`、marker、stdout 与 `.local/evidence-e2a-attempt2/integrationTest-failure.xml`（SHA `D49C8B42…`）保留；attempt3 初始化及启动后监听 PID `23260` 是启动进程 `9872` 的子进程，runner 严格同 PID 检查失败，marker 记 `stop-failed`，stdout 与 `.local/evidence-e2a-attempt3/integrationTest-failure.xml`（SHA `5A2580E5…`）保留。attempt1 的 Gradle XML 因后续同测试任务覆盖，只有原 stdout/marker/本轮调用记录可用，不能声称三份 XML 均在。三次资源证据均固定 `fa7981d` 原 SHA；旧 marker 的 SHA 在 attempt3 前后未变。
- attempt3 遗留实例经独立核验可执行文件、127.0.0.1:19329 监听 PID、精确 `mysql19329-attempt3/data` 命令行一致；root TCP 被 `ERROR 1130 Host 127.0.0.1 is not allowed` 拒绝，无法管理端优雅停机，故仅对已核 PID `23260` 执行 `Stop-Process`。09:01 复核该 PID 与 19329 监听均 0，其余 mysqld PID 4412/5448 未动；attempt3 数据目录可能需 crash recovery，**保留不重开/清理/重播**。三次均未启动 V34 seed/V35、28案或 before/after 保留比较；`e2a-target35-attempt3.marker` 不存在。
- 下一步先重设计隔离验收启动器：服务器启动即安全创建精确测试账号（避免 root TCP bootstrap 被拒）、识别并约束 Windows mysqld 子进程监听、成功和异常路径均能经鉴权正常停止并核 0 监听；针对身份/失败路径做独立测试与审查。现有 attempt1/2/3 路径不可重用，不再盲开 attempt4；E2a 非空迁移、生产 handler/POST/决定/放行仍关闭。原 R05/现场/R09 限制保持。

### 2026-09-23 08:43 R06 E2a 原树接续与首次动态失败（本任务，未提交）

- 已核主树 `codex/development` @ `23ae8c492acf47e89875852d2573392fcf379f80`（相对本地 `origin/codex/development` ahead 7，仅原 `docs/out.xlsx` 未跟踪）；原迁移树 `D:/acrylic-pda-worktrees/r06-e2a-migration` / `codex/r06-e2a-migration` @ `568683c1a1f8257ad0596a654c6b3558c767ed95` 起初干净，旧任务非运行态。本任务接替该树 E2a 验证代码写权；不接管远端部署/DB，未提交、推送、合并或部署。
- 原树未提交修改：`server/src/test/java/com/acrylic/outbound/e2averify/{E2aMysqlLauncher.java,E2aMigrationRunner.java}`，新增 `E2aMysqlLauncherTest.java`；初始化清理二次中断后继续有界等待、保留原失败及中断标志，退出未确认则明确 suppressed；PowerShell 子进程移除污染的 `PSModulePath`，避免加载 Codex PowerShell 模块副本而使 Windows PowerShell 5 的 ACL/CIM 探针失败。聚焦 `test` 实际 **11 项（launcher 3、environment 8）/0 失败/0 错误/0 跳过**，`compileTestJava` 成功、`git diff --check` 无差异错误。
- **一次 opt-in 动态尝试失败，不能计作 V34→V35 通过**：`integrationTest --tests E2aMigrationExecutionIT` 1 项失败于初始化前 `Cannot restrict launcher-private ACL`；原因为继承的 `PSModulePath` 使子进程 `Get-Acl/Set-Acl` 模块自动加载失败，已用单独 ACL 探针复现并针对性修复/测试。V35 唯一 classpath 资源证据写入 `.local/evidence-e2a/v35-resource.json`（raw `DF180DAC…` / canonical `CE433B38…` 与固定 `fa7981d` 一致）；`.local/e2a-launcher-started.marker` 保留 `started/failed: IllegalStateException/stopped`，`.local/mysql19329/data` 已创建但未初始化，`.local/e2a-target35.marker` 不存在，19329 无监听/无该端口 mysqld。失败 stdout `.local/e2a-dynamic-20260923.stdout.txt` 与 Gradle XML 保留；ACL 探针目录亦保留。**不删除、不重置、不在原 one-shot 路径重跑**。
- 下一步先独立审查修复与原失败证据，再由当前明确写权持有者分配全新且精确护栏的隔离命名空间/证据路径后，才可重新执行 S1–S4；旧失败目录与 marker 原样保留。原看板 E2a 编译/静态准备结论仍有效，但本轮非空迁移、28 案 SQL、保留比较均未通过，R06 E2a/决定/放行仍关闭。

### 2026-09-22 23:40 PDA 升级通道核查（现场可用线，只登记未改动产品代码）

- 现场设备 code15（`0.11.0-r05-sync-test`）被公开清单 `minSupportedVersionCode=16` 挡住 App 内升级，属发布时有意设置（清单自述“旧版本覆盖升级尚需单独核验”）；15→16 的系统覆盖升级已由隔离模拟器验过（Room11→14、身份/会话/未上传冻结批次保留）。公开 code16 与清单哈希一致（11173106B / `9bc281b2…`），用户已选手动安装。
- **新发现缺口（此前未记录）**：App 侧 `UpdateManifestVerifier.kt:31` 把可接受的服务端兼容值硬编码为 `{0.17.0,0.18.0,0.19.0,0.20.0}`，而 test 后端与发布清单契约已是 **0.30.0**；面向 R06 新版 APK 的诚实兼容声明不落在旧机白名单内，声明 0.18.0 又与后端不符。故 **code15/16 → R06 包无法诚实走 App 内升级**，只能系统安装器手动装包；已在 `docs/R05_PENDING_ACCEPTANCE.md` R05-OPS-01 登记。现场项沿用该文件 R05-FIELD-01..05，不新建重复清单。
- **本地候选包已构建核对（未发布、未安装）**：`outputs/pda-candidate/Acrylic-PDA-0.15.0-r06-submission-test-code20.apk`，包名 `com.acrylic.outbound.pda.staging` / code20 / `0.15.0-r06-submission-test` / Room17 / minSdk28；签名摘要 `2437447b…`（与公开 code16 同一 debug 证书，可覆盖安装），11731828B、sha256 `a1535dc8…`；内嵌服务地址 `https://outbound-test.nbsmzwl.cn/api/v1/` 与更新公钥均与线上一致。Room 迁移与主机测试已跑：`testStagingUnitTest` **67 类 / 274 项 / 0 失败 / 0 错误 / 0 跳过**（含以 schema14 重建并断言打开后为 17、原表逐行保留的迁移用例 13 项）；fixtures 由 10 个 `integrationTest` 用例生成（BUILD SUCCESSFUL）。为生成 fixtures 启动了本机 MySQL `127.0.0.1:13316`（workspace 实例，未停止）。**真机/Android9 仍未验**；R06 独立确认/正式化/放行仍关闭。
- **已在实机 M71 安装并验证迁移（2026-09-23 00:00）**：`22D2910042` / Android 9 / SDK28，原为 code15/Room11；`adb install -r` 成功、`firstInstallTime` 保持（数据未清）、versionCode 20；启动后 Room **11→17** 完成，原 20 张表**行数逐表不变**（含 1 条已成功上传的冻结提交、6 条云端条码/订单缓存、1 条 REJECTED claim_intent）、新增 13 张新表、`quick_check` ok、外键 0 违规，应用进程存活且窗口正常出帧、日志无 FATAL/Room 异常。装前已拉取 db+wal+shm 二进制备份到 `.local/tmp/m71-before/`。**用户已目视确认订单列表可见原单 `R02-PDA-01`/`997R0200001`；随后两次 `SyncWorker` SUCCESS，ORDER 序列 16→23、BARCODE 6→1156（`lastSuccessAt` 更新为 2026-09-22T16:03Z），云端缓存订单 6→9、条码 6→1150，恢复模式仍 NORMAL——升级后登录态/设备绑定与同步链路在 Android9 实机可用**。**限制**：该机 `screencap` 返回 0 字节无法截屏；重试/回网撤权/续期/显示细节仍待验，R05-FIELD-01 未整体关闭。

### 2026-09-22 23:25 R08 SCAN_XLSX 姓名口径放宽并已部署 test（已提交推送）

- 按用户确认去掉“必须含中文汉字”：姓名非空且非占位（拒空/空白、`原人员ID:` 前缀、人员ID含无连字符写法、账号名），**中英文姓名均可**；判定统一由新增 `report-rendering` 的 `OperatorNames.usable` 提供，服务端 `ExportOperatorNames`（并读 username）与 `ScanRecordExcelRenderer.validate` 共用；错误码不变，提示改为“扫码人姓名缺失或无效…”。
- 本地验证：`ScanRecordExcelRendererTest` 10 项 + 新 `OperatorNamesTest` 15 项通过（同模块 116 项中 2 项 PDF 因本机缺 Playwright chromium-1187 失败，属环境缺件）；`compileTestJava`/`bootJar`/`installDist` BUILD SUCCESSFUL。`R08ExportIT` **未跑**（需 R08/wave1 合成库，当前未运行）。
- **已部署 test 并真实 HTTP 验证**：overlay 发布 `releases/v34-386739c-r08name-01`（仅替换 `server.jar` `5767ea05…` 与 `report-rendering…/acrylic-report-rendering.jar` `684ee017…`，manifest/source-sha256 重写后本包 `verify_backend_release.py` 通过）；`activate_backend.sh` EXIT=0，API/worker 23:25:26/23:25:41 active、18085 监听、csrf 200；三格式全部 OK/SUCCEEDED（XLSX 13493B、PDF 360560B、**SCAN_XLSX 4314B**，列 E 实测 `test_pda01`），旧 `EXPORT_OPERATOR_NAME_UNAVAILABLE` 阻塞已消除；旧发布与 `web/APK` 未动、库仍 V34 无迁移。证据 `deployment-evidence/r08name-20260922/`；回滚=对 `v34-386739c-02` 重跑同一 `activate_backend.sh`。代码与文档改动已提交并推送到远端 `codex/development`（本机 `origin` 的 HTTPS 通道不可用，推送与 fetch 走 SSH 地址，tracking ref 已同步；SHA 以 Git 记录为准）。test 与 Git 的代码漂移已消除。

### 2026-09-22 23:05 只读核对（最新；test 部署线以此为准，取代下方同线旧结论）

- 核对者为本会话（DSH `session-4913edce-5187-4359-bca3-93830acba979`），**仅读取**：未写远端、未改数据库、未跑测试/构建、未提交未推送。核对时刻 2026-09-22 23:05 +08:00。
- **test 已实际切换并激活**：`/opt/acrylic-outbound-test/current -> releases/v34-386739c-02`（symlink 21:21）；`deployment-evidence/v34-20260922-complete/activation-live.txt` = `Verified backend package: v34-386739c-02` → `Activated backend /opt/acrylic-outbound-test/releases/v34-386739c-02; Web and APK publication untouched.`。`acrylic-outbound-test.service` 21:21:33 起 active running（pid 3150868，ExecMainStatus=0）、`acrylic-outbound-test-worker.service` 21:21:50 起 active（pid 3150993），均运行 `current/server.jar`；test API 绑 `127.0.0.1:18085`，`OUTBOUND_MIGRATE=false`（写已开放，已不在停写态）；`/actuator/health` 返回 401（需鉴权，服务可达）。
- **库已到 V34**：`acrylic_outbound_test` 的 Flyway max=34 / 34 条 / success 34；`live-migration.log` 记 validate 34 migrations、current version 23、applied 11、`now at version v34`（21:20）。
- **原值保留已落盘**：`preservation-result.json` 原 84 表 / 70327 行 / 33 文件，变化仅 flyway_schema_history+11、permission+4、role+3、role_permission+4，`unexpected` 为空；备份校验 `database.sql.gz/database-facts.json/private-files-and-config.tar.gz/release.txt/manifest.json/active-services.txt` 全 OK；`previous-release.txt = r05-sync-20260919-01`。
- **真实 HTTP 导出副本结果**：XLSX SUCCEEDED / http200 / 13495B / sha256 `e0689a61…`；PDF SUCCEEDED / http200 / 360561B / sha256 `369a9d4c…`；**SCAN_XLSX 未通过**：`BLOCKED_BY_EXISTING_NAME` / `EXPORT_OPERATOR_NAME_UNAVAILABLE`（既定中文姓名校验限制，未修改人员资料）。**因此不得写作「三格式通过」或「三格式全绿」**。
- 其他已核证据：激活测试 8/8 OK、checkpoint 7/7 PASS、`nginx -t` ok、副本内容核对 7 工作表 / 1 包 / 300.5kg / 2 页 / `contentMatch: true`。PDA/APK 发布物未动：`update-records/` 仅 `r05-download-16`、`updates/` 仅 staging + `update-public-key.base64`、`web-releases/` 仅 `pda-download-20260919-01`（均 09-18/09-19）。
- **下方被取代的旧结论（原文保留作历史，不无痕覆盖）**：①「live 目标 current=r05-sync-20260919-01、API PID2930319、worker inactive/PID0、V23；尚未切换」；②「已开始 test 停写备份/迁移，未在此断点声明切换完成；成功后仍停写待激活」；③「live 未切」；④「原列逐值/文件完整保留仍待核；三格式/143/正式切换尚未回报通过」。四点均以本轮远端证据更新。
- **未消除的缺口**：SCAN_XLSX 仍受阻（非产品失败、非已通过）；本轮未见 143/SIGTERM 停止语义的新动态证据（unit 已配 `SuccessExitStatus=143` 与 `TimeoutStopSec=60`）；副本侧遗留常驻 JVM pid 3136227（`--server.port=18089 --spring.main.web-application-type=none`，20:12 起未退出），下方未登记，需确认是否应结束；副本 `acrylic-outbound-v34-verify-{api,worker}-20260922` 仍 active（API `127.0.0.1:18088`，20:12 起）。
- **本地与下方一致（本轮复验）**：主树 `codex/development` @ `93bae37`，`origin/codex/development` 同 SHA、0/0（仅本地 remote-tracking 缓存）；未提交 `AGENTS.md`/`docs/GIT_WORKFLOW.md`/本看板 + 新增 2 个 history 快照 + 原未跟踪 `docs/out.xlsx`；E2a 三树 `319305f`（仅 `.local-e2a-vector.log` 未跟踪）/`568683c`（干净）/`79cad44`（仅 `bridge-classes/` 未跟踪）逐条相符；本机 `13316/18088/19321–19329` 无监听、无 java/gradle 执行者；本地库 28 个 schema 中无 e2a/v34/v35/migrate，最新 schema 时间 2026-09-20。
- **本次未能核实（限制，不得当作已核）**：远端实时 Git ref 无法联网核验（HTTPS 走 schannel 报 `SEC_E_NO_CREDENTIALS`；git-over-ssh 受沙箱管道限制报 `couldn't create signal pipe, Win32 error 5`；私有仓库匿名 GitHub API 返回 404；原生 OpenSSH 可认证为 `1131657662`，说明网络与密钥正常）；nginx `proxy_pass` 站点文件未取到（`conf.d` 内 grep 为空）。

### 2026-09-22 本轮 test 部署接管（优先于下述旧断点）

> **本段为接续前记录，读时降级为历史**：其 test 部署线结论（「已开始停写备份/迁移、未声明切换完成」「成功后仍停写待激活」「live 未切」「原列逐值/文件保留仍待核」「三格式/143/正式切换尚未回报通过」）均已被上方「2026-09-22 23:05 只读核对」按远端证据取代；未删原文仅为保留接续前状态。

- 用户在本任务明确确认“请帮我完成且部署”，随后再次“继续”。当前任务 `01a0c928-470d-7943-88ec-afe3390967ee` 直接持有原 sixth-wave-integration、test API/Worker/Web/库及本次管理记录写权；20:54任务列表核旧总调度/部署/导出任务均notLoaded，本机无旧ssh/scp/java执行者，不唤醒旧代理、不新建任务/树。
- 业务固定 `386739cfbe450e0cb58937b0745c6a74bf4c7297` / API0.30.0 / V34；部署修正已提交 `0b1094ee93d2987a4b4c578996e99c28488808b4`，原三文件修改已保留接续。修正版 `/opt/acrylic-outbound-test/releases/v34-386739c-02`；旧候选未覆盖。
- 本轮作者部署验证：Linux激活8/8、启停7/7、Bash语法及systemd配置通过；副本XLSX/PDF成功、真实HTTP下载/hash通过，7工作表与2页PDF核对1包300.5kg。原01/02英文姓名使SCAN_XLSX仍受既定中文姓名校验限制，未修改人员资料。
- 已开始test停写备份/迁移；未在此断点声明切换完成。精确状态以远端证据 `/opt/acrylic-outbound-test/deployment-evidence/v34-20260922-complete/` 为准；执行 `migrate-v34.sh`，成功后仍停写待激活。PDA/APK、main和生产不动；E2a冻结继续。
- 替换前完整结论见 [部署接管前快照](history/ORCHESTRATION_STATE_before_test_deploy_2026-09-22.md)。下述旧身份、写权与运行状态仅是接管前记录，部署结束后整理当前区。

规则更新时间：2026-09-22（Asia/Shanghai）。本次仅恢复默认调度与模型配置；下列业务、环境与执行者信息除明确标注外沿用19:48断点，未复验远端、监听或测试。本区结束即停止读取。

- **默认模式已恢复**：取消固定主协调/三执行者、禁止主协调开发及强制转派。当前助手可在已核写权内直接完成工作；未经用户要求不主动创建子代理。成本角色已移出加载目录，固定子代理模型/推理强度/并发数已移除；本机全局模型恢复调整前备份的 `gpt-6-astra-pro / xhigh`。配置解析与差异已核，现有会话实际模型未声称已热切换；重新启动 Codex 后继续。此条优先于下文及其他文档中的旧编排规则。
- **本次核对**：主树 `codex/development` / `93bae37aa399598be15d1fd82a3c551f70497892`，origin仍原仓库。修改前仅原 `docs/out.xlsx` 未跟踪；本次另修改 AGENTS、Git工作流、看板并新增历史快照，未提交/推送。任务列表显示旧总调度与部署任务均 systemError，不将旧代理身份视为活跃；本任务仅配置/规则写权，不接管业务、DB、重型槽或部署。
- **历史保留**：恢复前完整当前区见 [默认恢复前快照](history/ORCHESTRATION_STATE_before_default_restore_2026-09-22.md)。原业务准备、作者、独立、组合结果及失败/未验继续如下；下一次业务接续先核写权和实际环境，沿原树/提交继续。

### 当前目标与下一步

- 继续R06 E2a非空V34→V35验收：固定生产fa7981d不变，最新整合候选568683c。准备、作者、独立、组合证据分列，不称E2a整体完成。
- **当前优先级调整**：部署旧执行者反复空Payload且全部停写，总调度将已确认可用的launcher_finish从E2a冻结转派原sixth-wave-integration部署runtime；已收到精确交接并完成官方Chromium安装/副本1..34全success核验；现在配置/启动副本runtime与真实导出。e2a_migration只保存管理断点后冻结。
- E2a暂停新业务执行；后续先补/核初始化二次中断后进程退出确认与启动前实际环境/证据目录；按槽排期聚焦护栏测试，再分配19329进行target34→seed→before→target35→28案negative→preservation。失败库、marker、文件/日志保留，禁止reset/repair/删证据/重复seed。
- 独立test部署优先继续副本API/Worker/三格式真实HTTP、原列逐值与文件保留验收，再按用户已有授权切test。E2a不占远端部署/DB写权。

### Git、原树与执行权

- 主目录 `D:/acrylic-pda-outbound` / `codex/development`：最近已推管理SHA `48873f7ae3053629af6a4907f6b9ccf796fe95fc`，本轮仅主协调看板待执行者窄保存；保存后用Git核新SHA。origin仍既有项目远端。原未跟踪docs/out.xlsx保留。
- 唯一总调度 `01a0c7c8-b72e-7980-91a3-3e594373319f`，只维护五管理；旧01a0c70f已交接停写，旧BCD不恢复，无新增app任务/树。执行树根D:/acrylic-pda-worktrees，同名codex短分支。

| 职责 / 执行者 | 原树 | 本地与远端已核SHA | 当前状态 |
| --- | --- | --- | --- |
| W1 `/root/e2a_migration` | r06-e2a-foundation | 319305f741112d92323aec1ac29af1d2e53fc284 | 种子静态完成；现只读消费者，业务写权冻结；仅原.local-e2a-vector.log未跟踪 |
| W2 `/root/launcher_finish` | r06-e2a-migration | 568683c1a1f8257ad0596a654c6b3558c767ed95 | 整合/启动器修正已推、树干净；作者窄编译通过，当前冻结待排期 |
| W3当前冻结 | r06-e2a-protocol | 79cad44199c5e30c1f9d92f3537b9c17cca8f734 | 4control+24negative已推；仅原bridge-classes未跟踪 |

- migration普通merge为47ef1a3（foundation）和5beea79（protocol），两最终源SHA均ancestor；生产E2aProtocol/V35与fa7981d diff为空。07cab59/799f49f旧失败历史已由b3fe56d+3aa624d保留，不amend/reset/force。
- 两E2a执行者曾429 retry中断，18:54沿原身份MESSAGE+followup续接；旧negative_matrix/seed_finish等停。当前最多2E2a+1部署，阶段结束冻结业务写权，管理保存由执行者明确路径执行。

### 准备、作者、独立与组合证据

- **准备完成**：foundation0ef7f29修receipt别名/requireOne、SCAN/真实cycle、集合计数；319305f返回本次精确fixture IDs、第二合成用户、CREATE_NEW e2a.csv/真实SHA。protocol79cad441修15字段构造、事务外异常存证、28案文档；缺第二用户/scope则准备失败。
- **作者/当前编译**：568683c的server compileTestJava通过，主协调实读migration/.local/compileTestJava-20260922-191827.stdout.txt，BUILD SUCCESSFUL，4 tasks/1 executed/3 up-to-date；191705前一版编译也成功（3 executed），后因源码小修重编。未跑本轮JUnit/DB/HTTP。
- **独立/静态**：e2a_migration审protocol79cad441+foundation319305f未发现确定SQL字段绑定阻断（其foundation部分只计作者自查）。4合法引用形状与24负向错误映射静态一致；实际错误码仍需MySQL验证。
- **独立/启动器**：ee12eba/c6bcb9c审c5462e9；复审9be99b8发现CRLF hash阻断与stopped顺序误报；568683c增量只读复核确认这两项已修。primary/suppressed和owned process清理已补，初始化二次中断残余限制保留。
- **资源证据准备**：V35 Git blob cdfd21cddc0b16284b388026b6d0c3da53878498与fa7981d一致。Windows raw SHA DF180DAC681899C21BE94820267208C29091F39ABB4C2ABABB205DD2F97C9515；仅CRLF→LF后canonical CE433B38BDC06A2050C3E4A6C4BA413C8FF57E55EDC1477A65D78667F30F420C。运行时验证唯一classpath资源，CREATE_NEW v35-resource.json记录URL/双hash/固定生产SHA；当前未启动，JSON不是已生成动态证据。
- **历史窄测**：c5462e9环境护栏8项、ebbaa63路径helper2项XML均0失败/错误/跳过，仅对应旧版本；协议fa7981d作者9与8241c53独立25桥接为历史通过，未重跑。旧完整服务器139/140（SkuParserTest缺外部样本）保留。
- **组合/未验**：Git已整合且编译通过，不等于组合行为通过；未跑本次护栏JUnit、真实非空迁移、28案SQL矩阵、行/DDL/Flyway/文件保留、MySQL启停、真实HTTP/设备。每案只比数据库行快照（排除Flyway），迁移前后另比旧表/DDL/Flyway/文件，不能混计。

### 当前限制与失败保留

- 初始化清理在二次wait期间再次InterruptedException时force后未再次等待确认；可能遗留未确认初始化进程。动态前需补强或明确检查/证据，不能把server=null且端口空闲写成进程必然退出。
- 旧启动器aea2c5d仍覆盖主异常/清理不足、9be99b8资源hash/marker阻断均保留为失败候选；568683c仅静态+作者编译消除已知主问题，未动态验证。
- CREATE_NEW资源/marker残留会阻断重跑；保留原文件与失败库，后续由总调度明确续接命名/环境，不删除或覆盖。phase/精确全链SHA证据、Flyway success JDBC编码、路径/权限与实际生命周期继续验。
- cross-scope矩阵只替换scope验证父FK，未覆盖object/set/verification全部跨scope组合；字节摘要内容一致性、对象kind、route、可信签名属writer/协议语义，DDL通过不替代。合成SCAN复用STOP对象仅结构代表；e2a.csv为合成导入文件，未宣称来源业务证据可信。
- 旧向量6beb4f6、桥接适配、字体SocketTimeout、工具502/503/429/空payload/Windows失联分别保留；工具故障不计产品测试失败。部分提交正文含字面\n、0ef7f29标题格式不符，保留历史不amend，后续用真实body文件纠正格式。
- 原身份/可信facts、异常回滚后另读、旧seed401/keyring/权限、真实HTTP、Android code20/Room17耐久、R05关键未验、R09/现场/审批均未被本轮消除。正常确认空理由、localDeletionAllowed=false保持。

### 环境、槽与独立test部署

- 19:21本机只读13316/19321–19329无监听；E2a唯一拟用127.0.0.1:19329 / acrylic_outbound_e2a_migrate_20260921 / e2a_migrate_20260921，migration树.local/mysql19329/data、private-files、signing；数据库槽未分配。
- 部署任务 `01a0c853-feef-7d40-a819-a8591b3278f7` 用户已明确授权test；原deploy_transfer及deploy_runtime均空Payload失联已停，现唯一部署执行者转为本总调度/root/launcher_finish；部署任务协调负责精确交接和验收，总调度中继，禁止再唤醒旧部署代理。全部远端部署/重型DB写权持续归该任务；19:16临借本机Gradle完成后19:20明确归还，E2a当前无重型槽。
- 部署业务固定 `386739cfbe450e0cb58937b0745c6a74bf4c7297` / API0.30.0 / V34，原sixth-wave-integration；三部署文件此前未提交，工具修复与业务SHA分列。排除E2a/V35、不合main、不动生产、不提前改PDA/APK清单。
- live目标114.67.203.242 / outbound-test.nbsmzwl.cn / acrylic-outbound-test及worker / acrylic_outbound_test：最近确认current=r05-sync-20260919-01、API PID2930319 active、worker inactive/PID0、V23/失败0、PDA code16；尚未切换。
- 副本唯一分配schema acrylic_outbound_v34_verify_20260922、账号outbound_v34_verify_20260922@localhost、回环18088、/var/lib/acrylic-outbound-v34-verify-20260922/{files,signing}、units acrylic-outbound-v34-verify-{api,worker}-20260922。执行者报告从不存在创建；旧R05副本/18087保留。
- **部署准备/独立核**：包/opt/acrylic-outbound-test/candidates/v34-386739c.zip大小506820083，SHA256 2A9904D98AB02C676A40AAF98888B9E4424D34728C32B5097FA62FE2B12779D5本远端一致；94hash通过，JAR34迁移/max34/noV35、业务diff386739c为空。Linux激活8/8；checkpoint原CRLF导致pipefail失败，临时LF副本7/7，已派独立-02修复包，不覆盖初版，修正候选结果待报。
- **部署副本**：作者报告恢复私有23文件/签名并V23→V34 apply11；部署主协调独立读v34-migration.stdout确认validate34/apply11/正常关闭。1..34全success为作者核；原列逐值/文件完整保留仍待核。下一副本API/Worker、Playwright1.55渲染、原WEB订单三格式真实HTTP与143启停；未切test。
- 部署证据根远端candidate/v34-386739c/deployment-evidence/，verify_backend_release、test_backend_activation、test_service_checkpoint及normalized stdout、v34-migration.stdout。Windows symlink权限/browser下载超时/429与传输中452MB hash不同阶段读数保留，最终包hash已匹配；移交先核旧子进程退出。

- **部署已执行补证**：launcher_finish实核副本101表、Flyway1..34全success1。官方Chromium140.0.7339.16/build1187、FFMPEG1011、Headless1187已装DATA/browsers；日志deployment-evidence/playwright-install-chromium.stdout SHA c34d65582b097577e8421a27c538d8c94726ddf3211c5d23225d8511546c50ac。
- **独立原值保留**：部署协调仅只读核备份/opt/backups/acrylic-outbound-test/20260921T201503Z的84表/66非空/782原行，逐原列BINARY null-safe各恰匹配1；missingOrChanged/nonUnique均空。仅Flyway+11、permission+4、role+3、role_permission+4，其余80原表行数不变；tar原3业务文件+2签名hash5/5。原工具chunk c267d4/60d75f在部署任务；历史restore唯一来源命令未找回，不能扩大为唯一来源链已证明。首次解析误假设显式列名被拒，补CREATE列映射后一次SQL通过，无新库/写数据。
- **恢复路径失败与修复**：原所谓23文件实为6顶层业务+错误整包套层；files/etc含旧敏感配置，files/var含套层业务。已保留字节移入DATA/quarantine-config-20260922，部署协调独立核0700 root:root、服务账号不能遍历、files仅6业务；不输出secret、不删除证据。首次移动hash比较因绝对路径变化失败保留，作者归一相对路径核验待最终报告。签名独立signing保持。副本units曾停为inactive/PID0，隔离后允许继续副本启动；live未切。
- **当前副本运行权**：只有/root/launcher_finish可写sixth-wave-integration与已分配test副本。原三部署文件未提交保留；独立-02候选尚待正式LF/权限/manifest修正。仅部署runtime可生成/etc/acrylic-outbound-v34-verify-20260922/runtime.env（私有）；仅无消费者副本账号可重设随机口令，live账户不动。副本端口18088、API/worker两个renderer变量、SuccessExitStatus143、worker2G/Tasks512。原WEB完成单/仓库已交接，三格式/143/正式切换尚未回报通过。
### 接续入口与边界

- W2专项docs/parallel/R06_E2A_MIGRATION/{README,ACCEPTANCE_STAGES}.md已更新整合边界；W1 MIGRATION_LAUNCHER_REVIEW.md、W3 V35_NEGATIVE_MATRIX.md定向读。旧文档章节不覆盖顶部。
- 正确构建入口gradlew -p server；本地字体foundation/report-rendering/build/generated/resources/NotoSansCJKsc-Regular.otf的hash为2c76254f6fc379fddfce0a7e84fb5385bb135d3e399294f6eeb6680d0365b74b，使用r08FontFile绝对路径，不绕检查。
- docs/out.xlsx原SHA256 C3BABA70BC900975503457C5468363DE91417F156F2441ED71A3B7BE5E8FB6C4保持。原资料/密钥/本机配置/数据库/构建产物不入Git。
- V1–V34不改，V35未在E2a动态；HTTP接收/writer/文件事务/K/七类生产handler/POST/决定/放行关闭。E2a不部署、不合main；独立test仅按已授权范围执行。下一会话沿本队列核实际Git/写权，不重做完成工作。
<!-- CURRENT_STATE_END -->
