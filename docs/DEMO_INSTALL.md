# M71 Demo 安装、构建与取证

日期：2026-09-16｜应用版本：0.1.19-demo｜Room：3｜包名：`com.acrylic.m71demo.debug`

## 1. 现在可以做什么

当前按用户 Q16 选择执行扫码自动保存，手输 F1 后人工确认。自动/人工策略逐条持久保存；旧未确认记录不自动升级。按键向导已集成；0.1.9～0.1.17 的性能调优、自动故障检查和未完成项目见 [最新自动验收记录](M71_AUTOMATED_ACCEPTANCE_REPORT.md)。新版本独立验收，旧版性能缺陷和证据保留。

当前0.1.19完成91项主机回归、debug/release构建和Lint。它只更新必要的处理状态及累计字段，原始扫码上下文保持；0.1.18的64页回写试验未见收益，已撤回。真机核心负载结果见最新自动验收记录。用户提出Demo范围过大，当前集中收尾核心验证，尚未将原计划的未测项目改为通过。

**2026-09-16验收结果：**0.1.19已完成Q17约定的按键映射、B模式现场复验和真实断电恢复，Demo验收结束，见[真机验收记录](M71_ACCEPTANCE_REPORT.md)。本轮包名为`com.acrylic.m71demo.debug`；设备另有同名`com.acrylic.m71demo`测试包，数据彼此独立，打开另一包不会显示本轮12包。旧包0.1.5的四档负载、P95未达等记录及[原验证报告](DEMO_REPORT.md)中的模拟器历史结果保留，不混同当前现场结论。

| 文件 | 用途 |
| --- | --- |
| `demo-artifacts/M71-Demo-0.1.19-debug.apk` | 使用自定义音效、B模式和全部按键向导的调试签名测试包 |
| `demo-artifacts/SHA256SUMS.txt` | APK SHA-256，传输后核对是否一致 |
| `demo-artifacts/APK_INFO.txt` | 包名、版本、SDK、签名证书摘要 |
| `tools/install_demo.ps1` | 保留应用数据的安装/更新，不卸载、不清库 |

## 2. 不接电脑也可以安装

把 APK 传到 M71，在设备文件管理器中打开，按系统提示允许本次安装来源。启动后等待内置库存首次导入完成。首次导入读取 `docs/sku.txt` 的原样副本，12,639 条、GBK 固定 57 字节记录，进行 SHA-256 校验；实际导入时间需在 M71 记录。

演示订单默认产品 `003:80,571:100`。更多 → 新建测试订单，可以输入各产品计划，例如 `003:180,571:180`。不设 116 包上限。内置样本中的包在某张测试单保存后持续防重，其他测试单不能再保存；软件压测另造独立合成条码。

## 3. USB 安装

1. M71 开启开发者选项和 USB 调试，使用可传数据的 USB 线连接电脑。
2. 在 M71 上确认电脑的调试授权。仅远程操作电脑无法代替设备上的授权。
3. 从项目根目录打开 PowerShell，运行：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File tools/install_demo.ps1
```

脚本会要求恰好一台授权设备。多设备时明确选择序列号：

```powershell
& "$env:LOCALAPPDATA/Android/Sdk/platform-tools/adb.exe" devices -l
powershell -NoProfile -ExecutionPolicy Bypass -File tools/install_demo.ps1 -Serial '实际设备序列号'
```

ADB 在其他目录时传入 `-Adb '完整路径/adb.exe'`。`unauthorized` 要在设备确认；找不到设备先检查线缆、驱动和 USB 模式。若签名不同导致覆盖安装失败，保留原包及数据，先取证再处理，不以卸载解决。

## 4. 设备配置

- 记录扫码头 E3250/E3200 型号、固件、Android 构建号、屏幕 density/fontScale、电池与可用空间。
- 优先将厂家扫码设置为仅广播，关闭键盘输入和自动 Enter/结束符。Action 为 `com.android.decodewedge.decode_action`，字符串 Key 为 `com.android.decode.intentwedge.barcode_string`。
- 备用 `barcode_data` 只记录实际类型，不擅自猜编码。只收到备用字段时先查看厂家设置/SDK。
- 更多 → 诊断 / 设置 → 全部按键验证向导；7 个实体键各按两次，最终确认后应用，见 [操作指南](KEY_VALIDATION_GUIDE.md)。Enter 用于手输/旧人工候选保存或确认错误；屏幕提供等价大按钮，K1 对应 F1 手输校验，K2 对应 F2 辅助。扫描键交给厂家服务，不随意改映射。
- 屏幕失去前台时暂停接收；手输或弹窗期间扫描会提示错误并记录拒收。只在扫码主屏测连续吞吐。

## 5. 构建环境与命令

已使用本开发电脑的 Java 21.0.11、Gradle 8.11.1、Android SDK 36、Build Tools 36.1.0 构建。Java/Kotlin 字节码目标 17，minSdk 28（Android 9）、targetSdk 35。

| 组件 | 版本 |
| --- | --- |
| Android Gradle Plugin | 8.10.1 |
| Kotlin / KSP | 2.1.20 / 2.1.20-1.0.32 |
| Room | 2.7.1 |
| SQLite Framework | 2.5.0，固定版本用于连接池 FULL 配置桥接 |
| Hilt | 2.56.2 |
| Coroutines | 1.10.2 |
| AndroidX Core / Activity | 1.16.0 / 1.10.1 |
| Lifecycle | 2.9.0 |
| JUnit / Robolectric | 4.13.2 / 4.14.1 |

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File tools/build_demo.ps1
```

脚本优先使用用户缓存中的 Gradle 8.11.1，找不到时使用仓库 Wrapper；只在测试、打包和 Lint 全部成功后更新交付 APK/校验和。可传 `-GradleExe`、`-Sdk`。`local.properties` 是本机 SDK 配置，不纳入 Git。

通用命令：

```powershell
./gradlew.bat :demo:testDebugUnitTest :demo:assembleDebug :demo:lintDebug --console=plain
```

本机测试时 Java 直连官方 Gradle 分发服务器出现超时，已安装的同版本 Gradle 构建成功。Wrapper 固定官方 SHA-256 并将超时设为 60 秒；换电脑需具备可访问官方分发及 Maven 仓库的网络，不能把本机缓存命中等同于已验证首次网络安装。

## 6. 导出和核对

1. 先停止软件压测、等候队列排空，处理所有候选/失败提示。
2. 更多 → 导出核对文件。每次生成独立 UUID 目录，包含 `captures.ndjson`、`scans.ndjson`、`audit.ndjson`、`manifest.json`；不会覆盖旧导出。
3. 按订单冻结后，重试冻结返回原目录；冻结只表示本地测试正文固定，没有真实上传/出库确认。
4. 运行：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File tools/pull_evidence.ps1 -Serial '实际设备序列号'
```

结果位于 `demo-artifacts/runs/日期时间/evidence/订单UUID/导出UUID/`。软件压测的独立期望序列位于 `evidence/expected-订单UUID.ndjson`，诊断事件位于 `evidence/events-时间.ndjson`。

使用 Python 3.11+（数据库 tar 检查建议 3.12+）核对，替换示例路径：

```powershell
python tools/verify_export.py '导出目录' --expected 'expected-订单UUID.ndjson' --events 'events-时间.ndjson' --output '核对结果.json'
```

普通人工扫码轮次没有自动期望文件：在扫描前另备已知顺序清单；无外部基准时只能确认数据库内部一致，不能声称没有漏收。脚本返回 0 表示导出内部一致，1 表示失败；`hardwarePassed` 始终为 false，由真实测试记录决定硬件验收。

### 无需现场按键的自动检查工具

当前调试包包含非导出、仅同应用UID可调用的自动入口。电脑通过已授权ADB的run-as调用；仅合成测试单可以注入条码、确认和故障。所有结果标明软件测试，不替代真实扫描头、按键或突然断电。发布清单不包含此入口。

| 工具 | 用途 |
| --- | --- |
| `demo_control.py` | 一次命令一个UUID；只重读该回执，不在超时后自动重发变更 |
| `run_benchmark_check.py` | 1～4包/秒完整负载，独立期望→完整广播→持久输入→保存→音效逐条核对 |
| `run_crash_checks.py` / `run_freeze_checks.py` | 指定阶段实际终止应用进程，保留前/后DB与WAL；不是物理断电 |
| `run_reboot_check.py` | 普通Android重启，核对原确认上下文与跨启动授权复核；不是突然断电 |
| `run_mixed_check.py` / `mixed_evidence.py` | 重复、超量、手输、错误暂停、撤销、存储故障恢复、备注；核对每包声音请求与保存先后 |
| `run_history_check.py` / `verify_history.py` | 增加36万条独立合成历史和108万审计；全库核对及分页导出样本 |
| `verify_retention.py` | 比较旧业务行，允许设备计数器递增，检查按键草稿保持 |
| `monitor_memory.py` | 每2秒执行系统侧`dumpsys meminfo --local`，保存含图形的PSS原始样本；采样峰值不等于瞬时上界 |

每次使用新的证据目录，同一设备只运行一项工作负载。例如：

```powershell
python tools/run_benchmark_check.py demo-artifacts/runs/my-new-4ps-run --rate 4 --seconds 600 --cold
python tools/run_mixed_check.py demo-artifacts/runs/my-new-mixed-run --seconds 1800
```

`--cold`指终止并重启应用进程，不是操作系统冷启动。脚本保留不完整和失败结果；失败后先检查原订单、故障见证与回执，不能换目录盲目重做变更或删除失败样本。历史夹具会真实增加本地测试数据，不生成真实业务出库。最终结论以逐项检查报告为准，不能仅看脚本退出码或截图数量。

M71固件的应用内memtrack接口可能漏记图形内存，不能只看应用内采样。普通主机`dumpsys meminfo`回调应用收集详情会影响时延；当前工具改用设备支持的`--local`，仍需注明采样条件并保留无额外采样对照。可提前运行`python tools/monitor_memory.py 新采样目录 --seconds 650 --interval 2`，负载结束后在该目录创建`STOP`文件结束采样；工具保留失败样本，不以缺失值当0。

### 真机软键盘兼容记录

本机原QQ输入法在强制显示硬件键盘设备上的软键盘时出现全高空白窗口。0.1.17已通过应用恢复路径验证：前台手输内容连续250ms不可见时收起异常键盘，保留输入并提示“使用实体键 / 选择输入法”；不自动校验或保存，不更改系统偏好。这不是修复QQ内部问题。设备已有Android键盘`com.android.inputmethod.latin/.LatinIME`在标准和1.3倍字体下完成手输、F1校验和确认保存检查。远程检查后已恢复原QQ输入法、`show_ime_with_hard_keyboard=0`及原默认字体；正式现场验收时确认实际使用的输入法。证据见自动验收报告，不需要重新安装输入法。

### Excel

`tools/export_xlsx.mjs` 是开发电脑上的核对工具，使用本机已配置的 `@oai/artifact-tool`，不在低端 PDA 上安装表格库。当前电脑 `tools/node_modules` 为指向 Codex 工具运行库的本地 junction，不纳入 Git；换电脑需重新配置运行库。正式后台 Excel 服务将按 V2.3 独立实现，不依赖该工具。

```powershell
& "$env:USERPROFILE/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/bin/node.exe" tools/export_xlsx.mjs '导出目录' '结果.xlsx'
python tools/verify_xlsx.py '导出目录' '结果.xlsx'
```

包含当前有效扫码、原始事实（含撤销）和审计。明细始终按 `orderScanSeq` 升序，保留原设备、设备序号、周期和原始小数；北京时间只是显示字段。数字重量用整数克核对，Excel 净重显示有效小数，产品/条码保留前导零。验证器从 XLSX XML 独立读回，逐格核对字符、序号、周期和重量。

## 7. 保存完整数据库证据

先停扫、记录故障点和当前画面，再执行下列命令。它只停止本 Demo 进程并将数据库、WAL、SHM、偏好一起保存，不删除数据：

```powershell
python tools/capture_database.py '实际设备序列号' '本轮数据库.tar' --adb "$env:LOCALAPPDATA/Android/Sdk/platform-tools/adb.exe"
python tools/inspect_backup.py '本轮数据库.tar'
```

检查工具在临时目录打开副本，做 SQLite 完整性/外键检查。不能运行时单独复制 `.db`；不能将 `force-stop` 记录写成硬断电。Keystore 测试签名密钥不在备份包中，因此该 tar 是诊断证据，不能当正式换机授权包。

## 8. 数据与范围

- 调试签名只供本次 Demo 安装测试，正式签名及设备注册另行管理。
- 不请求互联网权限，不连接服务器，不含生产账号或令牌。
- 本地授权用于 48 小时/重启/时间异常边界模拟；不是可在正式系统中自行签发的授权。
- 不自动清理数据。先保留故障、未决输入、冻结文件和审计，再做任何清理决定。
- 结构 1 → 2 使用保留数据的索引迁移，2 → 3 在输入和明细增加 confirmationPolicy，所有旧行默认为 MANUAL，保留旧确认语义；没有破坏性重建 fallback。后续模型改变必须增加迁移，不能仅修改旧结构快照。
- `FullSyncOpenHelper` 使用 Android 9 已公开的 `SQLiteDatabase.OpenParams` 配置 WAL/FULL，连接池后续新建连接也保持该设置；`DatabaseDiagnostics` 在事务内检查实际写连接。不是仅对某一次 PRAGMA 返回值进行推断。
- Java 桥接使用固定版本 AndroidX SQLite 的适配类（JVM 构造器公开、Kotlin 标为 internal），升级 SQLite/Room 依赖时必须重跑连接、迁移和启动验证。没有使用 Android 隐藏 API 或反射改写连接池。
- 数据库损坏时停止打开并保留文件，不调用默认的损坏后删除策略；需要提取原始文件诊断。

0.1.7 在 B 规则下合并预校验与自动意图事务，保留原始接收与最终保存独立耐久。最新结果见 [B 模式记录](M71_B_MODE_REPORT.md)，不将 0.1.6 或更早证据当成新版通过。

## 0.1.8 按键向导

完整操作见 [KEY_VALIDATION_GUIDE](KEY_VALIDATION_GUIDE.md)。升级保持 Room 3、原数据、原音效及旧映射；新增私有配置文件。自动检查不代替操作者按实体键。私有文件从本版起纳入数据库备份工具，外部报告另拉取。
