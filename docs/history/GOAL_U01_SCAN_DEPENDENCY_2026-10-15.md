# U01 共享扫码布局依赖开发备份（2026-10-15）

本切片补齐 `fa583628e9b94e3baf757b4d99213a4e903e3322` 遗漏的布局依赖，不改写该提交。仅收口可还原源码的主机验证；产品设备入口未验，不能计完整 L1、U01/U02、E06 L2、D18 L3 或总目标通过。

## 输入和影响映射

工作目录 `D:/acrylic-pda-outbound`，分支 `codex/development`；HEAD/实际远端均为上述 SHA。五路径暂存候选树 `7191302eee4d7d90105208f0b23cc82757ed4eeb` 导出到 `.local/ui-dependency-candidate01/` 验证，没有切分支或创建 Git worktree。

| 改动路径（Android 目录内） | 影响层及必跑范围 | 复用与边界 |
| --- | --- | --- |
| `app/src/main/res/layout/activity_work.xml` | 正式/TEMP 共享首屏、滚动、候选、次级入口；两类布局及窄屏/大字测试、构建/lint、对应客户端入口 | 上次工作树证据不可代替此候选 |
| `app/src/main/java/com/acrylic/outbound/pda/ui/WorkRenderer.kt` | 正式来源、保存反馈、暂缓状态；首屏与调整测试 | 引擎/权限/数据库源码未修改 |
| `app/src/main/java/com/acrylic/outbound/pda/ui/WorkActivity.kt` | 次级动作展开、暂缓/恢复、手输后回首屏；引擎回归和实际点击入口 | 设备查询阻断，点击未验 |
| `app/src/main/java/com/acrylic/outbound/pda/ui/TemporaryWorkActivity.kt` | 同类 TEMP 入口；TEMP 引擎/布局/调整回归 | 不运行已连续失败的草稿设备链 |
| `app/src/test/java/com/acrylic/outbound/pda/ui/ScanFirstScreenTest.kt` | 320×509 主机布局、队列阻断、保存反馈 | 合成输入不冒充 M71 现场 |

其他恢复确认、搜索/提交 UI、文档、工具和私有请求全部保留；未混入此五路径候选。完整 83 项原字节、SHA-256/长度、tracked patch、Git 状态、进程和磁盘保存在 `.local/goal-20261015/ui-dependency01/`；接续复核 83/83 未变，见 `resume-hash-check.json`。唯一写者仍是本聊天，记录见 `ownership-resume.json`。

## 实际验证

精确命令、退出码和输入分别见同目录 `command.json`、`result.json`、`candidate-tree.txt`、`candidate-hashes.json`、`fixture-inputs.json`。导出 852 个源码文件前后哈希一致；12 个既有服务端开发凭据向量作为 ignored 测试输入另行绑定。Git 导出与主树的 LF/CRLF 差异按 Git blob 内容核对。

- 根 wrapper 对导出 Android 执行 `testDebugUnitTest`、`assembleDebug`、`lintDebug`，offline/no-daemon，退出 0；原日志 `gradle.log` 和 `test-results/` 保留。
- 实际 23 tests、0 failures、0 errors、0 skipped：WorkAdjustment 8、TemporaryAdjustment 6、AdjustmentLayout 1、ScanFirstScreen 5、TemporaryWorkLayout 3。命令的 `*WorkDetailsLayoutTest` 无对应类，不计执行。
- Debug APK 保存在 `ui-dependency01/app-debug.apk`，SHA-256 `2992E7FDBE2816F90FCF67F20B79DFFEB3AC74262C5B86D2F697AF3EA2858F8D`。前次 26 项工作树 APK 另存，不能混作相同输入。
- 同候选追加 `WorkEngineTest` 3 项、`TemporaryEngineTest` 2 项，5/5 通过；覆盖离页立即拒收、暂缓释放前台锁、积压输入守恒、跨订单不误写和耐久输入恢复。命令/原 XML/结果见 `.local/goal-20261015/ui-engine03/`，852 文件哈希仍匹配。它验证引擎行为，不能代替 Activity 点击/广播设备入口。
- `ui-engine02/` 首次补跑在构建初始化阶段因旧 Gradle 缓存锁退出，业务测试执行数为 0；初始汇总误读了残留 XML，原解析输出保留为 `untrusted-initial-parser-result.json`，`result.json` 明确标为不可计数。新运行只收本次时间之后生成的两份 XML，未删除锁文件/停止旧 daemon。

## 自审、环境和未验

作者自审确认 binding 字段与两类 Activity/Renderer 一致，窄屏候选/阻断恢复父容器可见；没有独立人员审查。生产开关、业务删除和原资料无改动。

只读 `adb devices -l` 曾列出原模拟器和物理 M71；没有操作物理机。模拟器包名查询 15 秒超时；继承的自身只读查询链也卡住，仅停止核实命令后的自身客户端/父查询进程，保留共享 ADB、原 AVD、应用和身份，未装 APK、未跑 instrumentation、未重启设备。日志及终止范围见 `adb-packages.*`、`own-query*.json`。

构建后 C/D 可用分别为 77,333,651,456 / 11,189,850,112 bytes；接续复测与监听见 `disks-resume.json`、`netstat-resume.txt`。共享 MySQL 3306/33060 保留；项目 API/Vite/隔离库无监听，无在途重型任务。所有历史设备失败、暂缓计数和失败库保留。

下一步取得允许且可响应的设备入口后验证实际点击；同时继续不依赖设备/Chrome 阻断的 E07 运维缺口。总目标保持 active，生产默认关闭、`localDeletionAllowed=false` 不变。
