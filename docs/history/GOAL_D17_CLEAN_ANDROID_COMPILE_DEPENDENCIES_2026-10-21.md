# D17 开发备份提交的独立 Android 编译依赖修复

## 实际发现与结论

对已推送开发提交 `6020db4979a06a7abc8f82c913b3347904a95e3e` 只用 `git archive` 提取 Android/Gradle 源码，1026文件 SHA 绑定，排除所有继承 WIP；首次实际 `assembleDebug/assembleDebugAndroidTest` 在 `compileDebugKotlin` 失败，exit1。此前含继承改动的工作树构建通过，不能证明该提交独立可构建。

首次编译明确缺少 `workVehicle/workRecentDetails/workCandidateDetails/content` 绑定，以及已提交读者对未提交 `TemporaryHandoverActionGate` 的引用。本片只补三处必要依赖；隔离快照除三路径外原1026文件均未变。修复后产品/仪器构建 exit0；四个明确主机类共八项，首轮七通过、一项 NATIVE SQLite CANTOPEN，短路径只复验该项后1/0/0/0；不是八项同次全绿。新产品 APK 在原合成隔离数据上100%原生筛选1项通过，83表303行逐值/类型与会话密文守恒。lint exit0，0错误/508警告，不称警告已解决。

仅修复开发备份的可构建性、必要绑定与关闭边界。没有统一最终候选、全套回归或 D17/D18/第8节通过；构建不代替业务验收，前片四字号不转记本片新APK已验。版本事实见看板生成块。

## 修改与保护

- `activity_independent_work.xml`、`activity_temporary_details.xml`：提交已保全且已有消费者实际使用的继承布局原字节，补齐已提交 Kotlin 所需资源。独立首屏/大字布局不重新设计；两文件相对本片开工 WIP SHA 不变。
- `TemporaryHandoverCardReader.kt`：将同一 `BuildConfig.DEBUG && TEMPORARY_HANDOVER_DEVELOPMENT` 谓词放回该读者，移除对被用户排除的未提交 ActionGate 的编译依赖；既有行为/默认关闭语义不变。没有提交或运行接收者确认、激活、多跳或换机承接链。
- 新 `TemporaryClosedCardReadTest.kt`：只检查缺本地身份的读者异常分支，返回 TEMP_DISABLED/actionAvailable=false，前后全部业务表行数一致、session仍空，并断言三个 DEVELOPMENT 开关 false。使用 NATIVE Room，没有阶段/确认/激活写者。该行数断言不被扩大为所有非空表内容守恒。
- 其他继承文件原字节/哈希在 `baseline.json`、`wip-before.zip`，修改前读者/两布局的 HEAD及WIP字节在 `dependency-preimage.zip/json`。取消链ActionGate及相关测试、身份/Keystore、原资料、原回执、失败证据和所有旧APK/tar/log保持。

## 输入和命令

证据根 `.local/goal-20261021-resume/clean-android-head01/`。`head-source.zip` SHA `6e7da95c4db2696bb7c3cfa7269b4b27bdd2fcc8b97e8f7abb6e68a998d31c16`；`baseline.json`记录1026源文件与36展开脏文件。不是新工作树，不改变主树HEAD/index或原身份；全部新产物在 D 盘。

- `build01/execution-plan.json`：原提交源码独立 `:app:assembleDebug :app:assembleDebugAndroidTest --offline --no-daemon --max-workers=1`，专有 build/cache/TEMP，实际 exit1及原编译日志保留。
- `dependency-decision.md`/`dependency-patch.json`：三路径明确裁定与输入 SHA，不采用提交取消链、回退已有UI或修改业务围栏的方案。
- `build02/execution-plan.json`：同隔离源码加三路径修复，重用本片 build/cache，独立日志，实际 exit0。初轮编译阶段未产出APK，未覆盖旧交付APK。正式签名properties在隔离输入中不存在；debug、服务地址空、三个取消链开关生成值均 false。
- `auxiliary-test-inputs.json`：12份配置所需本机fixture/测试资源显式保全；两optional success/archive fixture不存在，不补造。选定四类不执行旧业务链或使用这些材料生成新回执。首次工具按系统默认编码读UTF8失败，未复制输入；改显式UTF8后完成，失败说明单列。

## 分层验证与失败

| 运行 | 实际结果 | 边界 |
| --- | --- | --- |
| build01 | exit1，原提交Kotlin编译失败 | 两布局绑定与ActionGate依赖缺失 |
| build02 | exit0，产品/仪器APK生成 | 同HEAD+三必要补丁，无其他WIP |
| host01 | 8 tests，1 failure，0 errors，0 skipped，exit1 | IndependentFirstScreen4、IndependentLargeFont2、状态模型1通过；关闭读者1项CANTOPEN |
| host02 | 1 test，0 failure/error/skip，exit0 | 只复验原失败关闭读者；同源、NATIVE、原断言 |
| lint01 | exit0，0错误/508警告 | 非全套业务验收 |
| clean100-first | OK (1 test) | 新APK/普通合成50条原件/100%，不称四字号 |

`host01/xml`保留完整四类原XML。按已证实的短路径诊断，将子进程TEMP改到 `.local/t21ca01`，并把同一 Android 资源绝对路径置于专有测试classpath，见 `host02/short-path.init.gradle`/execution-plan；不改测试、SQLite模式、Room产品或断言，不重跑七个通过方法。`host02/xml-results.json`记录原失败方法通过。失败在数据库打开，不能伪称已执行关闭分支；短路径复验才实际执行该断言。

新增测试第一次工具传输字符串语法失败发生于写入前，随后apply_patch正常写入，见new-test-before。原生结束后的独立审计首次因相对Path.as_uri失败，未查询SQLite；partial抽取保持，另新 `native-audit02`用绝对只读连接完成，不重跑仪器。

### 新APK原生消费者

复用前片 `U04TemporaryStatus01` 的合成隔离身份，不wipe/不换Keystore。fresh进程/端口确认后启动；安装前 `device-before-install.tar` SHA `ef0107c4764ca87a5bb220e675e35d02d13b94f6beace863ac60288ac358ad05` 与前片普通兼容最终tar逐字核同。只替换产品/仪器APK，原安装资料先保全，新目录/日志/证据run名称独立。

| APK | 字节 | SHA-256 |
| --- | ---: | --- |
| app-debug.apk | 14276818 | `266aa47be893729c558b430ace39c4f3a704db513f1fd9bb1ed202212d988e91` |
| app-debug-androidTest.apk | 2822972 | `5a9b3c670748b5031aee6344c6f849a39b1e3bb3de0d122c4705daee42386763` |

`apk-manifest.json`/native-prepared记录设备实际APK哈希核同。`clean100-first-command.json`保留确切命令；测试实际四选项、50原记录分页、有效/VOID/保留空结果、重建、定位恢复全部及汇总不变。`native-conservation.json`独立 SQLite 比较83表303行逐值/类型一致，会话密文SHA相同；`native-members.json`保留逐成员摘要。after.tar SHA `8136074a2769dab99a62f5ba8998da7884ddab77caa15dcb397b0b143dd15ed8`，内容规范摘要 `16f11dbf8651c85eeb6b561f1c3ce0706700259ec5b75936facffb1a53667d0c`（本轮序列化方案，不跨报告直接比较摘要）。主管保留正向不在该普通输入中，不造新业务事实。

## 停机、清理与接续

原生后明确 `adb emu kill` exit0，实际进程/监听与C/D/E余量见environment-final；无业务数据库连接、原设备操作或真实HTTP。共享系统3306/33060不连接不停。

本片可重建构建/cache/tmp属于持续清理范围；同类明确PowerShell清理此前被自动审批拒绝，本片不换工具或包装重试。删除/释放0字节；所有原证据/失败XML、两套APK、源归档和继承字节保留。新增源快照只为证明提交可构建，不视为异地备份或新增长期开发树。

仅三依赖路径、新关闭测试、本报告及两索引明确开发备份。提交后核相同source blob及fresh远端，其他33份非看板继承路径保持原字节，index空；看板保持本地并刷新事实。总目标active、未完成；原库/现网、正式签名、main、部署/Release关闭，localDeletionAllowed=false。后续继续实际受影响候选组合，不能以本片构建或提交替代D/O/U业务验收。
