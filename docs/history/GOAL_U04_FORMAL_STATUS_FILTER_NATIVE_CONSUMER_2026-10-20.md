# U04 正式订单原记录状态筛选：四字号原生消费者

## 有界结论

用户批准的“全部 / 有效 / 已撤销 / 主管保留”筛选，在全新隔离AVD的同一组22条合成正式订单原记录上，实际100%/130%/150%/200%字号各执行1项原生产品页面消费者，均 `OK (1 test)`。一次性产品writer造数另执行1项通过；不将其计作页面消费者。四轮前后83张Room表的逐表行数/内容摘要均与造数后基线逐字一致，每轮会话内容一致。仅新增两条AndroidTest源码，产品源码和产品APK没有改变。

该结论限正式订单的已签名合成测试夹具及隔离Android L1。主管保留只验证空结果，没有非空可信RETAINED输入；没有点击调整写入操作。没有在四轮运行前采集session.enc密文SHA，不能声称密文跨四轮字节守恒。不是原身份、完整UI、U04/D17整体、L2/L3或总目标第8节通过。版本事实见看板生成块。

证据根：`D:/acrylic-pda-outbound/.local/goal-20261020-resume/u04-formal-status-consumer01/`。

## 输入与实现范围

- 输入HEAD `580d7570a7a0c31d56cb4a3c15fd714768f728e5`，固定树 `D:/acrylic-pda-outbound`、分支 `codex/development`。fresh远端开发分支一致；main为 `c6e9e0c238c657baecbf699ba6fdbada5d979b86`。本聊天唯一active写者，同树其他聊天notLoaded，未创建子代理。初始32条脏路径/SHA见 `takeover-manifest.json`，接续复核见 `resume-recheck.json`。
- `FormalStatusDeviceFixture.kt` 只服务本专项：DEBUG、ranchu、明确synthetic-only参数、契约SHA及取消链开关保护；新身份/空prepared_order检查，证据目录已存在则拒绝覆盖。`FormalStatusConsumerDeviceTest.kt` 分开一次性造数和只读消费者，原消费证据不重写；不能直接整类重跑造数方法。
- 正式订单合成夹具来自既有 `PdaPreparationIT.exportSignedPreparationForAndroidContractVerification` 的实际输出。原来源XML为1 test/0 failure/0 error，仅作来源复用，不是本次重跑服务端IT。原件复制至 `private/contract.json`，来源、原字节SHA及生产者源码/XML SHA见 `input-provenance.json`。该“已签名”指测试契约签名，不是正式发布签名。
- 新AVD `U04FormalStatus01` 使用独立ini/datadir/data、端口5682，启动前目标应用未安装；没有复制旧userdata/身份或wipe。所有专项设备命令明确指定 `emulator-5682`；USB M71没有业务命令。没有数据库连接或服务端HTTP业务调用。
- 使用产品 `LocalActivation` 的授权/清单验签与摘要校验，`WorkStore`、`WorkProcessor`接收/确认/保存及 `voidSelected` 形成1–20 VOID、21–22 ACTIVE原记录。没有直接scan插入、continuation/handover/disposition激活。新合成session首次创建时使用签名issuedAt+1秒的测试时间anchor；没有修改既有身份或旧anchor。
- 完整构建输入1029项逐条重算无漂移；产品APK复用前轮保留原件，本次只独立构建测试APK。原产品lint结果仅按未变输入复用，没有新增lint/产品构建或全套回归结论。

## 实际运行

| 执行 | 实际结果 | 证据 |
| --- | --- | --- |
| `:app:assembleDebugAndroidTest` | 退出0，BUILD SUCCESSFUL，独立输出目录 | `build01/execution-plan.json`、`exit-code.txt`、`gradle.log` |
| 一次性 `createSyntheticFormalOriginalsOnceThroughProductWriters` | adb退出0，JUnit 1项通过；22条原记录 | `seed01-command.json`、`seed01.log`、设备seed01摘要 |
| `font100-first` | 实际fontScale=1.0，adb退出0，JUnit 1项通过 | 对应command/log/exit、设备result.json |
| `font130-first` | 实际fontScale=1.3，adb退出0，JUnit 1项通过 | 同上 |
| `font150-first` | 实际fontScale=1.5，adb退出0，JUnit 1项通过 | 同上 |
| `font200-first` | 实际fontScale=2.0，adb退出0，JUnit 1项通过 | 同上 |
| 设备tar收集与只读复核 | 退出0，四轮83表与seed摘要逐字一致 | `collect-receipt.json`、`consumer-results.json`、`self-review.json` |

消费者点击实际Spinner四选项、筛选/清除/上下页，检查原分页、20条VOID精确满页不出现空下一页、两条ACTIVE原序号、主管保留空结果、状态+产品+原序号组合、合法/非法条件跨Activity重建。实际产品阻断原因为空，ACTIVE行启用、VOID行禁用；没有点击行进入调整或调用保存/冻结/上传writer。

四张 `formal-filter-final.png` 已目视核对：各档筛选/清除可见，200%文字换行；截图只代表当时滚动位置，边缘裁切不证明整页无障碍通过。工具消费者另检查文本不横溢/省略、系统栏padding并经安全区滚动点击；实体键、完整键盘流程及真机仍未验。

## 哈希与可取回入口

| 产物 | SHA-256 |
| --- | --- |
| `private/contract.json` | `befdf17ae9df7c85d514312ee4c2733106dc5c8db9888c0dd0fe8f85f71fac84` |
| `build-input-files.json` | `9fba47e167b900953348187b410196d67cb4ef102fbb4cb71b3418ab9862c9d2` |
| 前轮保留的产品 `app-debug.apk` | `8b9c162fda5af7aff4e23622d92c0069540c4a09808b884d3e6dced5fd279dd7` |
| 本轮 `apk/app-debug-androidTest.apk` | `1a103ba04bfa436672a68f49262d0825d6b74e0121d25ef8aee26f13246cdb86` |
| `private/device-final.tar` | `27ef607d8a830bd84e44f882ede733555512e178a6e34f8537e10bc01caa955c` |
| `consumer-results.json` | `555739423309ff171aaa3bdfc534885df1a3b502d3bdeaf3ff6c470b37487bf7` |
| 四轮与seed的83表逻辑摘要 | `3094c13cd9d9d68d7947d4b026b8e18a204aad26c3a13991ab917ce06b25d619` |

设备tar原字节、逐成员哈希、原始日志、命令/退出码、截图及APKs保留。最终session.enc SHA仅为最终采样，见 `identity-evidence.json`，不冒充前后比较。索引/看板原字节及哈希见 `pre-doc-bytes/`、`preserved-document-bytes.json`。输出总清单、最终提交/远端与全部WIP入口见 `output-manifest.json`、`final-manifest.json`、`closeout.md`；本机材料没有因此获得异地备份证明。

## 自审、停机与清理

作者/自审是同一助手；Espresso/Python是独立工具消费者，不是独立人员复核。两新测试无硬编码凭据、私钥或生产库连接，95/107行；源码SHA、五次JUnit日志核对、diff-check及1029项输入一致性见 `self-review.json`。31条受保护继承路径逐项SHA未变，看板单独授权刷新，不混入测试提交。

正常停止专项模拟器后，实际进程/监听复核无Java/Gradle/emulator/qemu及5680–5683。原Pixel_6的8镜像与前TEMP隔离AVD的6镜像均SHA不变，Debug Keystore未变。项目MySQL/系统MySQL/ADB只观察，没有连接数据库或操作USB业务；系统MySQL datadir身份读取受限，不臆断。

本轮2437项可重建build/cache/tmp文件共39,535,681逻辑字节已逐文件哈希；重建命令、输入与既有离线依赖条件保留。此前递归删除被自动操作审查以 `blocked by policy` 拒绝，本轮不换方法重试，实际删除/释放0字节。新AVD/身份、tar、原日志/回执与APKs保留，见 `cleanup-disposition.json`；不是治理累计清理数的新增长。

## 未完成与下一项

本轮补齐同一合成正式输入、正常测试授权条件下的四字号状态筛选消费者；前轮TEMP重启围栏结果仍按原报告独立适用。非空可信主管保留、全UI/实体键/真机、原身份/原资料/现场回执、真实HTTP/MySQL/Chrome、统一实际最终候选L2/L3及第8节闸门仍未满足。O02 SQLite CANTOPEN暂缓不变，无新runner假设不重跑。

下一项从未完成的独立页面/文件消费者缺口中选取，先核已有覆盖及实际可信输入；不得直接造RETAINED或恢复已取消链。总目标保持active、未完成，七类生产决定、原库/现网、正式签名、main合并、部署与Release关闭，`localDeletionAllowed=false`。本报告与测试仅作开发备份，提交动作不计新增验收。
