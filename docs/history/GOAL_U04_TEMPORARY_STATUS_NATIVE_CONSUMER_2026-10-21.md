# U04/D17 临时明细状态筛选：隔离原生消费者

## 结论与范围

用户明确批准“全部 / 有效 / 已撤销 / 主管保留”，默认全部，只改变原记录可见范围，不写入记录。本片将已有六路径实现与主机测试接到全新隔离 Android 原生界面消费者；四档字号各一项、两项既有兼容回归各一项，六次仪器运行均为 `OK (1 test)`。四档 Room 内容指纹相同，普通与主管保留输入的独立 SQLite 核对均覆盖83表且逐值/类型一致，会话及整单/产品汇总保持。

这是合成输入、有界只读 L1 证据，不是 U04/D17 整体、真机/现场/原身份、实际最终候选或总目标第8节通过。主管保留的**非空正向只在100%兼容消费者覆盖**，四档消费者的普通输入中该分类为空；不得将二者合并声称非空保留四字号均已验。

## 输入、写权与源保全

- 输入 HEAD：`883381f7d16093d8a2989993c0bdd4cbf2a91ed3`；固定主树 `D:/acrylic-pda-outbound`、`codex/development`。本聊天独占写权，无子代理；接续实查另一同项目聊天 idle，无本片重型任务或模拟器在途。版本事实见看板生成块。
- 证据根：`.local/goal-20261021-resume/u04-temporary-status-native01/`。`baseline.json`/`wip-before.zip` 保存开工42展开脏文件；`resume-closeout01/wip.json/zip` 保存文档收口前45展开文件，新增三个仪器测试改动，其他继承路径原字节保护。
- 六个筛选源码/主机测试 SHA 与先前 `u04-temporary-status-filter-run02/README.md` 全部核同，见 `host-reuse-source-check.json`。本片没有重新实现该六路径，也没有重跑其历史8项主机结果（reader7项/model1项）；只作明确输入绑定的历史复用。
- 新增 `TemporaryStatusConsumerDeviceTest.kt`；两个既有仪器测试仅同步批准的标签与普通输入“主管保留为空”期待，不修改 fixture、业务链或开关。修改前原件见 `compatibility-tests-before.zip/json`。
- `build-source-inputs.zip/json` 保存首次1038源文件，`build-source-delta.json` 记录三个测试差异，`final-source-inputs.zip/json` 保存最终1038源文件。APK来自含其他继承改动的实际脏树；仅本片路径的开发备份提交不能被解释为干净树最终候选。
- 文档原字节：`docs-before-closeout.zip/json`、`resume-closeout01/wip.zip`；旧看板当前区保全后再刷新，不改写历史 GOAL 报告。

### 保留普通合成输入

来自 `.local/goal-20261016-resume/temporary-details01/host04-input/`，仅复制到新隔离 debug 包：

| 输入 | 字节/SHA-256 |
| --- | --- |
| synthetic-temporary.db | 1187840；`8d8469a0a98aea02983ea699feb1b3b23370c16e23c6efb7d946fcb6b6c9b5fc` |
| synthetic-session.json | `76c136dd828563bb300cea652b560e0d7a2d5ec8dcd91025c0a521878042c43d` |
| identity.json | `d0e9b51e0ecaf269830ac550997ea6e4d8961e82bcc9d932f5ddddfdef71de6d` |

### 保留主管保留合成输入

来自同历史根 `host04-prefix-input/`，只读消费既有 blocked 前驱原件，不运行其旧生成、续作或激活命令：

| 输入 | 字节/SHA-256 |
| --- | --- |
| synthetic-temporary-prefix.db | 1122304；`8daa7596449ff603d24afbff34e188ac80628963e5a49825aa4e886b2c9643f6` |
| synthetic-session.json | `d50dbb0ced82896bf0118f330fd4e7092b097c96c4d90960c2bbeb38067baa36` |
| identity.json | `b5992d6b2d4df9af88778b0d41eabdce4a30ee02797c415caafb058e36363f33` |

两套历史 DB/session/identity 收口 SHA 核同；原资料、原身份和 Keystore 未触碰。

## APK、构建与失败

| 用途 | 产品 APK SHA-256 | 仪器 APK SHA-256 |
| --- | --- | --- |
| 四档筛选消费者，apk/ | `347d70306e0e849208c0e103a9dd2954da53425488a84799f68aeb680310737b` | `090d80e680228bb57b151baa502e2023550801488a983b40a22289ec8c5798ba` |
| 两项兼容回归，apk03/ | 同上，逐字核同 | `7c3f924adac951a0cb64ced7f502415a738c3e47a9d4e31802ce936399e26f27` |

产品 APK 14291018字节，保留在本片明确路径。四档没有在 apk03 仪器 APK 上重跑；build03 仅同步两个兼容测试期待，新消费者源与 build02 相同。全部为 debug 签名，三个取消链 DEVELOPMENT 开关在生成配置中均 false，不使用正式签名。

- build01：新测试对 `TextView.text` 的 CharSequence 直接调用 substringAfter 导致 Kotlin 编译失败；改为 `.text.toString()`。`build01/gradle.log`、`build01/failure.json` 及 `TemporaryStatusConsumerDeviceTest-before-fix01.kt` 原件保留。实际进程退出码未捕获，记录 null，不编造非零码；该轮未运行仪器。
- build02：实际 exit0；build03：实际 exit0，分别见 `result.json`。offline/no-daemon/max-workers1；本片 Gradle build、project-cache、TEMP/TMP/Java临时目录均在 D 盘本片目录，共享依赖复用。
- 不以构建通过替代业务验收，不覆盖已有项目 APK。

## 原生运行与内容守恒

新建隔离 `U04TemporaryStatus01`（5684/5685）和 `U04TemporaryPrefix01`（5686/5687），仅新配置与既有合成 DB/session 输入；未使用原设备身份。WorkManager 在基线前取消/暂停，消费者本身只读取界面与原记录，不调用业务写入链。

| 运行 | 实际结果 | 输入及覆盖 |
| --- | --- | --- |
| font100-first | 1项通过，scale1.0 | 普通50条原记录 |
| font130-first | 1项通过，scale1.3 | 同输入 |
| font150-first | 1项通过，scale1.5 | 同输入 |
| font200-first | 1项通过，scale2.0 | 同输入 |
| compat100-first | 1项通过，scale1.0 | 普通既有界面/键盘/身份变化/辅助页返回兼容 |
| prefix01/prefix100-first | 1项通过，scale1.0 | 非空主管保留原件、定位、身份变化拒绝 |

四档逐次验证：

- 实际 Spinner 默认“全部”，50条按原序号分页20/20/10；下一页、上一页、首页和 Activity 重建。
- “有效”仅原序号46；“已撤销”仅1–45并分页；普通输入“主管保留”诚实为空。筛选及翻页不改变整单/产品汇总。
- Activity 重建保留筛选与页；完整条码定位恢复“全部”，显示46–50的原状态，包括未决记录。
- 原生 viewport 检查字号、系统栏与文本横向不截断；100%/200%截图人工查看，页面可滚动，不声称全部内容首屏可见。
- 每档83表逐表内容指纹守恒；相同规范化内容 SHA：`3eef812bbbfd43a2263a418b1c636dfb7db189104744b37d246f8df3e5b94d1d`。会话不变，`localDeletionAllowed=false`。

`native-results.json`、每档 `*-command.json`/日志/结果/after.tar 保留精确参数与 APK 绑定；`device-member-manifest.json` 保存230成员哈希。`compat-conservation.json` 覆盖83表303行，`prefix01/conservation.json` 覆盖83表65行，独立 SQLite 比较逐值/类型相同。以上为内容守恒，不把 SQLite DB/WAL 物理布局及运行元数据字节变化误写成业务写入。

## 清理、环境与边界

本片两新模拟器已正常 `adb emu kill`，`emulator-final-check.json` processes/listeners 均空；接续实查5684–5687无监听，无本片构建或仪器在途。M71仅枚举、不操作。系统3306/33060身份不可读，不连接、不停机；共享 Gradle/Kotlin 保留。

两份可重建 `default_boot/ram.img` 共8590065664逻辑字节，先保存每文件 SHA、精确路径及重建配置，见 `snapshot-cleanup-plan.json`/`snapshot-rebuild-configs.zip`。明确 PowerShell 删除命令被自动审批策略在执行前拒绝；**未删除、未绕行，释放0字节**，见 `snapshot-cleanup-blocked.json`。早前被拒绝的 D13/datadir 目标不重试。两套 userdata、原身份、原件、APK、日志、tar和失败证据全部保留。磁盘余量按最终环境快照，不将逻辑文件大小冒充实获空间。

本片没有连接原库/现网、操作正式签名、main、部署或 Release；不恢复换机承接、多跳、接收者确认/激活链，不启动旧 run03。七类生产决定/TEMP生产归档继续 fail-closed。

## 接续

仅本片9个筛选/测试路径与本报告、两索引按明确清单开发备份；看板在提交后刷新事实并保留本地。其他继承改动逐 SHA 守恒，提交/治理/清理不记验收进展。先看最新看板及本片 `postcommit-wip.json/zip`，再按进度索引查实际缺口；不重跑本片已通过消费者，不为补正向数据执行已取消链。U04/D17整体及D/O/U真实组合、可信原材料、实际最终候选与第8节闸门仍未满足，总目标保持 active。
