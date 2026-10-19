# U04 独立作业原生键盘避让与保留输入确认 L1

本批为 E08/D17、U01/U04 的有界开发证据。独立作业页在原生键盘出现后按实际 IME 与系统栏留出底部空间，手输校验后隐藏键盘，候选仍须显式确认。四档字号均有手输、待确认、保存和原记录回读证据；130% 与 200% 使用原失败任务补验，不能称四档同轮全通过。U04/D17 整体、第8节及总目标仍未完成。

固定树 `D:/acrylic-pda-outbound`、分支 `codex/development`，输入 HEAD/origin 开发分支 `47f58615842a15aa076e2631e94c9fbb33e002ae`，origin/main 只读 `c6e9e0c238c657baecbf699ba6fdbada5d979b86`。版本事实见看板生成块。作者、自审与工具消费者均为本聊天，不冒充独立人员审查。

## 改动、验收设计与输入

证据根：`.local/u04-independent-native-input-run01/`。运行前标准见 `acceptance.md`、产品修复依据见 `acceptance-product-fix.md`，诊断选择见 `diagnostic-decisions.md`。已有用户自主设计授权适用，未改变既定业务政策。

| 路径 | 影响层与直接验证 |
| --- | --- |
| `android/app/src/main/java/com/acrylic/outbound/pda/ui/IndependentWorkActivity.kt` | 唯一产品改动：显式 edge-to-edge、bottom padding 取系统栏与 IME 的较大值。实际 Activity/IME 四字号触屏消费者、Room 回读、定向主机回归及 lint 分别留证。 |
| `android/app/src/androidTest/java/com/acrylic/outbound/pda/IndependentSkuDeviceEvidence.kt` | 跳过 `!isShown` 子树，避免隐藏面板零宽控件误报；仍检查实际显示文字行宽。 |
| 同目录 `IndependentWorkNativePreparationTest.kt`、`IndependentWorkNativeFixture.kt`、`IndependentWorkNativeInputTest.kt` | 新原生身份准备与手输消费者；账号/SKU/任务为合成输入，不注入私钥、session、grant、可信时间。 |
| 同目录 `IndependentWorkNativeDiagnosticTest.kt`、`IndependentWorkNativeScroll.kt`、`IndependentWorkRetainedSavedNativeTest.kt` | 保留原任务的按键/授权诊断；按真实 IME 后视口滚动且要求控件可见；等原已保存条目发布到最近保存后只读回读。 |
| `server/src/test/java/com/acrylic/outbound/inventory/IndependentWorkUiFixtureIT.java` | 新隔离库只准备合成账号，强校验库名、端口、datadir 及空设备/凭据表；Android 自行原生登记并经真实 HTTP 取得开发凭据。 |

没有引擎、确认策略、Room/schema、服务端产品或网络行为改动。最终 `input10-manifest.json` 中 3,448 项 Android/server/report-rendering 源码逐项 SHA 无漂移，检查在 `final-room-audit.json`。构建配置、依赖、运行器与各轮原输入分别绑定 `input*-manifest.json`，不把历史通过迁移到不同输入。

## 实际执行与结果

新隔离 MySQL `19398/acrylic_u04_native_input01`、datadir 为证据根 `database/data`、UUID `426ab5fd-cba2-11f1-a196-d8bbc13a2b36`；HTTP `19498` 使用本根冻结 JAR。完整 AVD 副本 `U04NativeInput01/emulator-5596` 位于本根 `avd-home/`，480×800px、density 240、SDK37；不计 Android 9/M71 兼容。源 `Pixel_6.avd` 的 27 个输入文件运行前后 SHA 相同，见 `avd-source-recheck02.json`。

`run_fixture.ps1` 实际 `integrationTest --tests *IndependentWorkUiFixtureIT` 为 1/0/0/0、退出0；Android `IndependentWorkNativePreparationTest` 为 `OK (1 test)`。原生生成 Keystore，真实 HTTP 登记、登录、签发开发凭据并验证可信期限，再断开副本网络；准备只运行一次。合成账号/SKU/开发信任根不代表原人、原机、原资料 SAF 或现场资格。

修复后产品 APK `apk04` 至 `apk10` 同 SHA-256：`141cb294a4fcdd18dae231b920d66a808914ad8277bda0ad793e3d902f5e08c8`。最终 test APK `apk10/app-debug-androidTest.apk`：`3fc177233467c0f0a0c3b7fc83ae0b2ea40bffc14826c0527f4df4bb7ff33e03`。所有旧 APK 保留。Debug keystore SHA `0539b3d0b80ab4619a08db639d433ec2f3ac0a8d7b2fbdc32865d5cfc6b0b6cb`，没有正式签名。

| 字号 | 有效证据与限制 |
| --- | --- |
| 100% | `retry02-font-1` 完整原生消费者 `OK (1 test)`；IME 可见时 F1 零采集，实际触屏校验后 IME 隐藏、待确认零保存、触屏确认恰好一条及最近保存。 |
| 130% | `retry02-font-2` 原生手输/IME/待确认通过，原注入 Enter 未保存。`diagnostic-key-2` 打开同一原任务，外部 `adb shell input keyevent --longpress KEYCODE_ENTER` 收到首次按下后 `OK (1 test)`；`readback-font-2` 再等同一 capture 出现在最近保存，`OK (1 test)`，未新增输入。 |
| 150% | `retry03-font-3` 完整原生消费者 `OK (1 test)`；真实触屏校验与确认，恰好一条保存。 |
| 200% | `retry03-font-4` 旧测试滚动器未计 IME padding，校验前失败且零新采集。`retry04-font-4` 改用实际剩余视口后真实触屏校验、IME 隐藏、原输入待确认通过；test 内部 shell 长按仍缺首次按下。`diagnostic-key-3` 在同一原任务用外部 ADB 首次按下合法确认，`OK (1 test)`；`readback-font-4` 等同一原记录出现在最近保存，`OK (1 test)`。 |

内部注入缺首事件时 Activity 只收到 repeat=1 的 down 和 up；产品正确忽略重复按下，不改 WorkKeys 或确认规则。外部 ADB 记录首次 repeat=0 并恰好保存一条。平台注入不计 M71 实体键、扫码枪或 TalkBack 验收。诊断 immediate after-enter 为引擎发布前中间帧，不当作最终界面；后续只读消费者专门等待计数1/队列0/无候选/最近保存原条码。

原待确认任务导致另一任务授权被正确拒绝：`blocked` 明确“另一来源或独立任务仍有未处理输入”。没有丢弃、改状态或删防重来跑后续任务。原130%任务 `87430cb5-8814-4763-945a-27ad911fcbc1`、capture `013374e1-3162-458d-94e3-5964c4333d46`；原200%任务 `f148405f-43a0-4ad1-ada1-816427e7e883`、capture `3004a760-70c3-4313-9fba-317b6dad7cf3` 均保留。

`run-test.ps1 -Phase after` 在新 `host-regression01/` 执行：面板7项/首屏4项通过；Activity6项全部在数据库创建阶段 `SQLiteCantOpenDatabaseException`，总 Gradle 退出1。XML、输入、日志及 SHA 全保留；不能称17项通过，不无新证据重跑既有 SQLite 根因。APK assemble 与 lintDebug 实际退出0；lint01日志 SHA `dbc0f5226a7c38784364cca1b15eee66728ddcfa6bd7c316590fe8c2e9d8855f`、原XML SHA `74cbfd3c26f881cbf5c562f83ba53b306db2ea172e4d703013d94b72c85edc57`。构建、lint、真实 instrumentation 与主机结果分别记录，构建不冒充业务验收。

## 只读证据消费与可见核对

`audit_retained_native.py` 对原 tar 只读，复制 DB/WAL/SHM 后以 sqlite `mode=ro/query_only/trusted_schema=OFF` 回读；原 tar 前后 SHA 相同。首次审计误假设存在 `independent_scan_state`，失败副本/指纹在 `audit-reader-failure01.json`，后续按实际表与 `COMMITTED` 状态消费，不改库求通过；无效 `audit_native.py` 未执行。

最终 `saved-readback-final.tar` 回读：9任务、4 capture、4 scan、4 guard、0 VOID；每条保留原条码/顺序1/手输来源、各1有效包和15501克，全部 COMMITTED。完整性 ok、外键无错。与准备快照比较83张表，只变10张独立作业状态表；其余表、SKU原件/目录及 FORMAL/TEMP/提交/取消链表守恒。相较原 `audit-key-success` 的6任务/2capture/2scan/2guard及原处理状态逐项完全不变。

关键输出 SHA-256：

- `final-room-audit.json`：`57bd957234fc0994f0e3519177a8275c7aab378506d68b1be08e533dd54e5460`。
- `audit-saved-readback-final/audit.json`：`a9488c324619c09b23a418d42d416ad3d82a9caa72294fd0254cfbc493f06bbb`。
- `evidence-manifest01.json`（410项阶段输入/证据路径、大小与 SHA）：`e27a9f3026f7328eaaad927246ca6ce6fefe5ed9cf3ef5abd3c52fbbc81a893f`；后续新增证据另由收尾清单覆盖，不改此清单。

截图取自原 tar，`visual-review01/02/` 原图与 contact-sheet 已实际打开核对。四档无横向文字溢出；200% 保存后原条码和计数可读，但部分内容/主操作需要滚动，待确认首屏也不能保证所有关键操作同时可见。因此没有验收“大字首屏所有关键动作可见”、整套 UI 重设计或设备效率。

## 保留失败、环境与下一步

保留首轮 GONE 取证误报、build02 PowerShell 参数展开、retry01 原生 IME 遮挡反例、130%/200% 注入缺首事件、未决输入保护、200%旧滚动器、审计读表错误和本轮6项主机 SQLite 失败。build10 脚本复制误改文档日期的 preflight 退出1发生在 Gradle 前，记录在 `build10-preflight-failure.json`；修正后独立 APK 构建退出0。旧运行日志不改写为通过。

接管完整 tracked/untracked 路径/SHA 与 patch 在 `takeover.json`、`reconnect02-snapshot.json/patch`。后者226项WIP、218项继承仅看板变化，暂存为空；后续新增文件见最终快照。fresh list_threads确认本聊天为唯一项目active写者，其他项目聊天notLoaded；未创建代理。源/继承改动按 SHA 保护。

`stop_slice.ps1` fresh 校验本根 DB/HTTP/AVD 命令行和监听后执行 MySQL 认证 shutdown、AVD `emu kill`（均退出0），HTTP为精确Java PID `Stop-Process`，不称HTTP优雅停机。`stop-result01.json` 中19398/19498/5596/5597监听均空。原身份AVD、签名原件、所有APK/tar/日志和库事实保留；系统MySQL3306/33060只观察，未连接/停止；M71仅枚举。本任务 Gradle daemon经fresh命令行核实后停止；收尾无Java/Gradle/模拟器进程，ADB仅枚举M71。完整监听、全部进程和C/D/E余量见 `environment-closeout01.json`。

下一可执行项是独立作业大字首屏的候选确认与最近保存信息密度：先用本批200%截图/几何作为反例，记录当前任务/计数/候选/确认动作的实际首屏验收标准，再做最小布局调整和定向验证。既有 SQLite/内部注入根因不无新证据重复，原身份/Android9/实体键/原资料及统一最终候选的必需组合继续单列。

保持 `localDeletionAllowed=false`、七类生产决定和TEMP归档关闭；不恢复换机进度承接、多跳或接收者激活，不启动旧run03，不连接原库/现网，不使用正式签名，不部署/Release/合main。仅本切片的有界原生 L1 证据，含明确未验与失败；不能计 U04/D17/E06完整L2、D18 L3 或总目标完成。


收尾清理只含本轮可重建的编译中间文件和专用构建缓存，未触旧策略拒绝目录；每文件哈希/输入SHA/命令/依赖/无消费者核对在 `cleanup-compilation-before01.json`（SHA `cb90c60bd2add6760467ab0de2123c7e2d0fa408582fe536fb2a7bd0df355db4`）。删除32,017个文件、逻辑669,119,190字节；D余量52,024,352,768→52,742,045,696字节，卷观测增加717,692,928字节，二者不混称。APK/tar/log/XML/JSON/身份/原库资料及继承源码全部排除。第一次扩展名保护、第二次PowerShell5长路径失败均在删除前退出，随后同一明确目标用PowerShell7完成，非自动审批拒绝或绕过。

阶段manifest之后的131份APK/tar/log逐项SHA未变；3个当时仍在运行的模拟器/MySQL日志在认证停机时追加，前缀哈希一致、原字节仍保留，见 `protected-evidence-recheck01.json`、`stop-log-append-audit01.json`。原始“全部哈希不变”的初次断言失败单列，不隐去日志自然追加。保留含原生身份的AVD、对应签名/回执与本轮库，供身份来源及保管核对，未作为可重建缓存删除。所有本机被忽略材料未声称异地备份。
