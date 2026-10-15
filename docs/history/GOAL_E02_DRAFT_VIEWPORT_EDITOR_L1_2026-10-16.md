# E02 草稿视口与页面编辑 L1 开发备份

## 范围与输入

本切片修复临时草稿页面在系统栏 inset 下的滚动裁切，并验证搜索、数量编辑、移除确认、Activity 重建和明确丢弃页面修改。属于 E02/U 高频页面的限定 L1，不包含保存计划→开始扫码完整链、E02/U 整体、E06/E07 L2、D18 L3 或总目标通过。

- 树：`D:/acrylic-pda-outbound`；分支：`codex/development`；构建输入 HEAD：`18f07cf2e36806686aef67c7efca241049067758`，含继承脏树。构建 APK 不能被称为仅本次部分提交的精确候选。
- 唯一写者为本聊天；清理聊天已回执 `complete-released`，接续时仍 idle；无子代理。修改前快照、完整 binary diff、未跟踪原件和 5,304 项输入保存在 `.local/goal-20261016/resume-20261016-e02-l1-precommit/`。
- 该目录 `all-input-hashes-utf8.json` 与 `build10-source-recheck.json` 复核构建结束后 5,304 项输入零变化。首个快照因 Git 引号转义六个中文文档名产生假缺口，原结果保留，修正只读枚举后零差异。
- 版本事实见看板生成块；无模型、迁移、服务端、签名或权限变更。原身份/Keystore、现场设备和可信资格均未补造。

## 决定与影响映射

采用最小结构修复：外层 FrameLayout 接收既有系统栏 padding，内层 `draftScroll` 拥有真实可见高度。保留已有搜索、永久标签、dirty 提示和能力文案；有效新输入清除旧错误。理由是旧 ScrollView 同时承接 inset 与滚动视口，导致 Espresso 认为已揭示的控件仍被裁切；不扩大至全局窗口行为。

| 改动路径 | 影响层 | 必跑与结果 | 复用依据 |
| --- | --- | --- | --- |
| `android/app/src/main/res/layout/activity_temporary_draft.xml` | 实际布局/滚动/键盘 | build08/10 布局测试；device10 原完整输入顺序及 device11 手势触摸通过 | layout SHA `C1F9A27E2DB5F9391EA89793EEE6FE5E1C1FE16880730CCD03096B316F5A31C6`，build08→10 未变 |
| `android/app/src/main/java/com/acrylic/outbound/pda/ui/TemporaryDraftActivity.kt` | 页面输入/数量/移除/返回 | editor02 实际页面正向、错误恢复、重建、丢弃及重开通过 | SHA `EF73FC80CE4F755DB65F7C47B60C9A802815A28606264125042E62E34C7B27A0`；build08→10 产品仅本文件清除旧 error 的两处变化 |
| `android/app/src/main/java/com/acrylic/outbound/pda/ui/TemporaryProductSearch.kt` 及同名测试 | 搜索过滤 | 代码/型号/品名、空/无匹配及原目录不变；设备搜索实际消费 | 产品 SHA `664293A1C267327D6052A095FCE65EECA69AAC6053EDFF342E4097CF58E9C58A` |
| `android/app/src/test/java/com/acrylic/outbound/pda/temporary/TemporaryWorkLayoutTest.kt` | 小屏/字体/视口 | 1/1.3/1.5/2 字号，外层上下 inset、内层高度/padding及可滚动通过 | SHA `FEA3837FC31B6EDA36B5CE78AB12A49670443A25863307283A97D2C884E373F5` |
| `android/app/src/androidTest/java/com/acrylic/outbound/pda/{DraftDiagnosticDatabaseSnapshot,TemporaryDraftTouchDiagnosticDeviceTest,TemporaryDraftEditorDeviceTest}.kt` | 设备输入及只读取证 | JUnit真实结果、原草稿和全表摘要守恒 | 全部路径/长度/SHA 见证据根 `l1-source-hashes.json` |

证据根统一为 `.local/goal-20261016/e02-touch-diagnostic01/`。无 HTTP/Python 消费者、数据库持久逻辑或迁移改动，本片不重跑无关服务链；最终候选和后续保存/扫码受影响组合仍须实际验证。

## 实际执行与自审

1. **build08**：`assembleDebug`、`assembleDebugAndroidTest`、`lintDebug` 与三个定向类通过；原输入一致。旧失败序列复现于 device09：72px 搜索框仅36px可见，未发生触摸或新增行，83表守恒。根因有几何和截图依据。
2. **device10**：原车号→原因→搜索→swipe→数量→添加完整序列通过，真实 DOWN/UP 两事件、1行、数量清空，未调用 `performClick`，83表和原草稿守恒。device11 手势定位/点击亦通过；其输入准备曾直接定位，仅补充手势证据，不称完整链。
3. **editor01**：编辑、移除和重建均走到返回后，立即断言 DESTROYED 得 STARTED。保留 JUnit 失败、截图、输入和 tar；83表守恒。归因为测试未等待异步退出。同时截图暴露搜索框旧 error 未清除，产品补最小修复。
4. **build10**：`build10-command.json` 记录真实命令、JDK与本任务 TEMP/Java 临时目录。`assembleDebug`、`assembleDebugAndroidTest`、`lintDebug`、`testDebugUnitTest --tests '*TemporaryDraftTest' --tests '*TemporaryProductSearchTest' --tests '*TemporaryWorkLayoutTest'` 成功；XML 为 7+1+3=11 项，失败/错误/skip 均0；lint 424 warning、0 error，原 XML 保留。构建中 `sourceDifferences=0`，安装两 APK 均 Success。
5. **editor02**：`run-editor.ps1 -Run editor02`，JUnit `OK (1 test)`、ADB退出0。无匹配/数量0拒绝→搜索003添加2包→数量清空与旧error消失→0拒绝/3包成功→移除保留/确认→再添2包→重建保留→返回继续编辑/明确丢弃→重开原草稿0行且dirty隐藏通过。finally 核对原草稿及83表逐行类型/长度/字节摘要完全相等。
6. 作者自审检查实际数量更新、重建、重开 PNG，未见旧错误图标；无独立人员审查。测试使用既有隔离模拟器与原本机草稿，未保存计划、未扫码、未变更时钟或身份，不代表原 PDA 现场事实。

`build08-to-build10-differences.json` 比较917项既有 Android 输入，产品仅 DraftActivity 的错误清除变化，另增 editor 测试。故 device10/11 的视口/触摸证据按 XML 与诊断源码 SHA 复用；editor02 对改后产品入口再次实际执行。原三次添加失败计数与 device09 次级取证错误保留，不追认为通过。

## 产物与证据绑定

| 产物 | SHA-256 |
| --- | --- |
| `build10-product.apk` | `D4C1C885C7A56018FB2764A29DF19BA1FDC065A468880FF391029D7CD01CA829` |
| `build10-test.apk` | `98481C3E1769B77A7C53F09EA8B2A6083B60EB8877FA5C946EE3A023AE661910` |
| `device10-evidence.tar` | `0B8F49A821B977B400DCFA815C433A227DB20E3A7D12E03DD2AF583F70489C82` |
| `device11-evidence.tar` | `CFE6FBD3949B8304F2745B5C06643164312350C01D38BFAA638F835BEEA3A16E` |
| `editor01-evidence.tar`（失败） | `1D772AF5C8F4462CC5BDCCA1E0CA2FE59FD40BD5031AADB5E9DAD05BEC9BD0AD` |
| `editor02-evidence.tar` | `60B9FE3737AA270BDA1B7D73E3574D3EE09921A3AA4560A7EECFE92F6A180D95` |

原日志、命令、源码快照、XML、PNG、逐表摘要、APK和原AVD16文件备份全部留在D盘，不上传Git。`editor02-summary.json` 为83表/原草稿守恒；`build10-result.json` 为构建退出0及输入无变化，未用ADB退出0代替JUnit通过。

## 环境、Git与下一动作

提交前快照实测C/D余量 `83,188,764,672` / `47,475,564,544` bytes。原 AVD `TemporaryAdjustment1364`/emulator-5564 保留，emulator12912/qemu12108 与5564/5565；ADB8500/5037。无项目Gradle/Java或隔离MySQL重型任务。共享mysqld4632/5440身份未明，只观察，不访问/复用。只改本任务子进程临时目录，无全局环境修改或清理。

按明确路径提交/推送开发备份；推送回执与提交后全量快照见看板。其余继承Android/server/文档/工具及 `server/physical-disposition-last-request.json` 均保留。生产开关、TEMP归档和 `localDeletionAllowed=false` 边界未变。

下一步核对实际资格与已有失败，再以新合法隔离输入补保存→扫码受影响组合；不改时钟、不造原身份。1540 Chrome运行器审批阻断继续保留，不换包装绕过。继续 E07 受影响组合、E08/UI、E09 与剩余 D/O/U；本L1不结束总目标。
