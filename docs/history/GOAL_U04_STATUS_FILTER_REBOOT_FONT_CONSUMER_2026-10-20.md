# U04 原记录状态筛选：重启围栏下130%/150%原生消费者

## 结论与边界

用户已批准“全部 / 有效 / 已撤销 / 主管保留”，默认全部，仅改变可见范围。本次复用同一隔离AVD和原22条合成TEMP记录，在实际1.3/1.5字号各执行1项产品页面消费者，均 `OK (1 test)`。重启后授权时间围栏保持生效，全部调整行禁用；筛选、原序号、分页、清除、组合条件及Activity重建继续可用。两次83张Room表摘要、会话内容均守恒，且与前次证据一致。

仅修改一条Android instrumentation测试路径；产品源码、产品APK、会话授权、契约、迁移和生产边界没有修改。前次100%/200%是重启前合成授权条件；本次130%/150%是重启围栏条件，不能拼成同一授权条件下的四字号全UI验收。不记U04/D17整体、L2/L3或总目标第8节通过。版本事实见看板生成块。

证据根：`D:/acrylic-pda-outbound/.local/goal-20261020-resume/u04-filter-ui-consumer02/`。

## 输入、改动及复用

- 固定树及分支为 `D:/acrylic-pda-outbound`、`codex/development`；输入HEAD与fresh远端开发分支为 `6e087154f883688335e61dfac4566e1ccb6dad34`，main为 `c6e9e0c238c657baecbf699ba6fdbada5d979b86`。唯一写者是本聊天，未创建子代理；初始32条脏路径和SHA见 `takeover-manifest.json`。上轮缺失的 `u04-filter-ui-consumer01/final-manifest.json` 已补齐，原看板先保全再更新，事实Write/Check退出0。
- 复用前次 `U04StatusConsumer01` 独立ini/datadir/data；没有wipe、清库、重新造数或重装产品APK。原22条记录仍为1–20 VOID、21–22 ACTIVE，未制造RETAINED记录。全部设备命令明确指定 `emulator-5680`；USB M71没有业务命令。没有连接MySQL。
- 上轮完整Android源码/schema/配置输入逐条重算，只有 `android/app/src/androidTest/java/com/acrylic/outbound/pda/AdjustmentStatusConsumerDeviceTest.kt` 改变；清单见 `build-input-files.json`、`build-input-delta.json`。原测试134行以内，方法小于30行；本次只增加明确允许的重启阻断预期、产品阻断原因核对、所有调整行禁用断言及结果字段。默认空预期保留原测试契约，未用宽泛接受错误掩盖失败。
- 同一产品APK SHA-256始终为 `8b9c162fda5af7aff4e23622d92c0069540c4a09808b884d3e6dced5fd279dd7`。前次lint的0 errors / 508 warnings只按未变产品源码复用，本次没有重跑lint或声称新增lint结果。测试APK独立重建并仅替换测试包；旧APK和测试APK仍保留。

## 失败、诊断和复验

| 执行 | 实际结果与处理 |
| --- | --- |
| 原测试 `font130-first` | adb退出0，但JUnit为1 test / 1 failure。实际页面显示“设备已重启，请联网校验授权时间”，旧测试预期空结果文案；不是业务通过。原日志及 `private/device-font130-failure.tar` 保留 |
| 失败事实核对 | `failure-facts.json` 证明83表前后及前次摘要相同，session.enc密文SHA不变；页面已读到正确原序号。TrustedTime在冷启动后拒绝旧anchor是正确围栏，没有修改anchor、boot计数、会话或产品逻辑 |
| 独立 `:app:assembleDebugAndroidTest` | `build01/execution-plan.json` 明确参数与环境；退出0，BUILD SUCCESSFUL；没有assembleRelease或产品APK安装 |
| `font130-rebootguard` | adb退出0，JUnit `OK (1 test)`，实际fontScale=1.3；首个主机包装器因过度转义的成功匹配表达式退出1，原回执保留。随后只读按原日志 `OK (1 test)`、status code 0及无失败标志复核，并从设备JSON交叉确认，没有重跑 instrumentation |
| `font150-rebootguard` | adb及主机包装器均退出0，JUnit `OK (1 test)`，实际fontScale=1.5 |
| `collect-evidence.py` | 退出0；原始tar逐成员哈希、两次83表逐字摘要、会话/身份和37个旧证据/原输入成员均核对不变 |

原生消费者继续点击实际产品Spinner、筛选/清除/上下页，检查20条VOID精确满页后没有多余下一页、两条ACTIVE原序号、主管保留空结果、状态+产品+序号组合、合法/非法条件跨重建。重启围栏条件另外要求每个调整按钮均禁用。没有调用调整writer、旧造数方法、接收者确认/激活链或旧run03。

准备阶段有一次PowerShell数组连接优先级错误，在模拟器启动前退出；另有一次工具封装解析失败未启动shell。首次boot探测尚未完成，随后采样才确认启动。以上均保留/说明，不计业务失败或通过；未因此清理任何证据。

## 哈希与保全

| 产物 | SHA-256 |
| --- | --- |
| `private/device-font130-failure.tar` | `1ef49aaae2f5a0f9cd275461f1bbc142e41be23f02d146e70fcc15f52f20abc1` |
| `private/device-final.tar` | `3a8fa330a85cf89d323b40e99e93eae36034887e7dad487922a1391af1f3f5d4` |
| `consumer-results.json` | `54d08ff84bbe767180e4359aeb0b12bfaa269fbaf64b8ab1c10bf2d1c67abd2d` |
| 新 `apk/app-debug-androidTest.apk` | `471d93ab6c4910771c4b74dca4ef1a713353853b5699b58fa1ef9f42ea1126e8` |
| 两次及前次83表逻辑摘要 | `2ecd343fcfbe33bf9e19933145a7ec41e12915c63e870b042c27074071f89cc4` |
| `no_backup/session.enc` | `7b8b911dcc52e5358d4699528fdeae8ebb7fade2c7e3550a5797b45e6fc91a28` |

原日志、命令/退出码、截图、tar、输入清单、逐成员哈希及APK均保留。新旧索引/看板修改前原字节和SHA见 `preserved-document-bytes.json`；测试原字节另有副本。截图只覆盖当时滚动位置：已目视核对130%筛选区和150%标题/说明区，不能用裁切截图证明整页无障碍通过；控件触达由Espresso安全区滚动/点击记录补充。

## 停机、自审与仍未验

`emulator-stop-check.json`确认emulator/qemu/Java/Gradle进程和5680/5681监听均为空，原Pixel_6的8个镜像SHA未变；Debug Keystore未变，正式签名未使用。项目/系统MySQL及ADB只观察，没有数据库连接或USB业务操作。31条继承路径除授权更新看板外SHA逐项不变，暂存/提交不混入继承改动。

作者/自审为同一助手；Espresso和Python是工具消费者，不是独立人员审查。新建构建目录按清理规则保存逐文件哈希、重建输入和依赖；上轮递归清理被自动操作审查拒绝，本轮没有换方式重试，释放0字节。AVD/身份/原始失败证据及APK保留，详见切片closeout。

仍未验：正式订单、非空可信主管保留输入、130%/150%正常有效授权条件的完整走查、全UI/实体键/真机、原身份/原资料/现场回执、真实HTTP/MySQL/Chrome、统一实际最终候选L2/L3及第8节闸门。O02 SQLite CANTOPEN暂缓不变，没有重跑。总目标保持active、未完成；生产决定、原库/现网、正式签名、main、部署/Release关闭，`localDeletionAllowed=false`。

本次仅按明确路径提交测试、此报告和两个索引的开发备份；精确提交/远端、最终WIP和环境断点见证据根 `closeout.md`、`final-manifest.json` 及看板。
