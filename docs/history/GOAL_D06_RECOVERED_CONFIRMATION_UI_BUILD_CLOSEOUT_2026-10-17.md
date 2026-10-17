# E08 / D17 UI 隔离构建收口（业务范围待核，开发备份）

日期：2026-10-17（Asia/Shanghai）

**范围更正**：本页文件名及首个提交沿用了历史“D06 恢复确认”名称。现行D06为坏机未完成订单的后台撤回，D07为重新发布及新设备从头作业；换机进度承接、多跳、接收者确认/激活已取消。本页构建记录归入E08 / D17 UI WIP，不计D06验收。已有fixture访问`/api/v1/pda/handovers/{caseId}/recovered-confirmation`并导入开发接收机钥，是否属于仍有效的灾后可信恢复范围尚未核清；不得据本页自动恢复旧交接链。

本页只交付已存在WIP的默认关闭构建、隔离属性构建与lint事实，以及停机和D盘清理记录；未完成业务UI L1，不计D项整体、E06 L2、D18 L3或总目标。

## 精确输入与位置

- 工作树：`D:/acrylic-pda-outbound`
- 分支：`codex/development`
- 构建收口前HEAD、远端与最后推送输入：`78b448d17086c4e092a16116b8d9c3f21de84db3`。首个文档收口提交`979777f421bdcaefb9f741b38422dbd0875262fa`已推送；最终Git回执见交接快照及看板，不以本行作当前位置。
- 输入快照：`.local/goal-20261017-resume/resume-20261017-0849/input.json`，SHA-256 `42d8f5d3febab41ae9e3a1c4104b6d56d2416d0da6aa292f04f049b3a68dacfe`
- 收口快照：`.local/goal-20261017-resume/resume-20261017-closeout/`；`status.txt` SHA-256 `AC6678C43F854E02997535F57D27A180DE5CF2D3198B81713E774D21F259A123`；`dirty-hashes.json` SHA-256 `3C12591CB3FBE40C999D9E089C62C1694A13A55511CC04E02B5EDDCB20690FF2`
- 看板更新前快照：`docs/history/ORCHESTRATION_STATE_before_ui_closeout_20261017.md`

## 本切片已完成

1. run02 默认关闭属性构建：`assembleDebug`、`assembleDebugAndroidTest`、`lintDebug` 全部成功，`build.exit=0`。命令见 `.local/goal-20261017-resume/android-ui-run02/build-command.txt`，日志 SHA-256 `07CD2F7D8419E7B754219FA47EAFF4BABFE53369420C681EFB339DD41D3BC825`。
2. run03 `-PpdaIsolatedRecoveredHandover=true` 构建：同三项任务成功，`build.exit=0`。命令 SHA-256 `4AF5A29B4FD44DB9B3673D3177CBD71333B9CCEA55D57ADEACC3DFB02F8C77BA`，日志 SHA-256 `2FF099C765723F4949EBB530190B7FE69DA4392EEE980F731F92495EA117AED9`。
3. APK 已保留：`android/app/build/outputs/apk/debug/app-debug.apk` SHA-256 `4ACC1C8A3E60131824833BCD63A94B96E4BF3D7EB23A5D6C6DE50FC9516CE9AF`；测试 APK SHA-256 `6B0CF24219D2F370F5312DEF73D27AE2F768FFBA781A2A54DD59F5E705BB9507`。未使用正式签名，使用本机 Debug keystore `C:/Users/qq113/.android/debug.keystore`。
4. run02 独立AVD已boot后按已核命令行归属Force停止（emulator23964/qemu25908）；run03命名管道MySQL进程22920同样Force停止，进程消失且`5580/5581/19534/19535`零监听。此处停机认证指进程/监听已核清，不声称执行SQL优雅shutdown或库完整性验收。前后事实见`.local/goal-20261017-resume/stop-result.json`。
5. 清理前逐文件清单见 `.local/goal-20261017-resume/cleanup-20261017-pre.json`（SHA-256 `574B4BD7FCD77BDBC46318324EF5F18151EBCFB347625149E94B6C8D2029AC05`），清理后见 `cleanup-20261017-post.json`（SHA-256 `4B95520AE7EC1A8E674B2B25D67E5AD3F74BE712310C461F789207A7E0640461`）。清理了可重建 `android-ui-run02/avd/snapshots/default_boot`、已停机无消费者的 run03 `data`、派生 `private/seed.sql`；声明删除 `4,496,529,637` 字节，D 盘实际可用字节由 `52,180,439,040` 增至 `56,677,748,736`，实际增量 `4,497,309,696` 字节。清理脚本保留于 `.local/goal-20261017-resume/cleanup-run-20261017.ps1`。

## 未通过、未完成与失败指纹

- 尚未安装 APK、未运行 instrumentation、未执行页面正向/取消/丢响应/重试/登录切换/重启，也没有截图、Room/HTTP/MySQL 守恒或正式 UI L1 报告。
- run03 首次构建失败：`java.io.tmpdir` 指向不存在的 `D:/acrylic-pda-outbound/.local/t-ru03`；原件 `.local/goal-20261017-resume/android-ui-run03/build-failure01.log`，SHA-256 `975EDFD1704B210D808F3AFAA75E10032C9C1F9A30242571A478B567801EDC3F`。创建临时目录后重跑构建通过。
- run03 MySQL 首次启动失败：Windows 路径 quoting 导致 `Too many arguments (first extra is 'Files\\MySQL\\MySQL')`，原件 `recovered-confirmation-ui-run03/private/mysqld.log`，SHA-256 `60CC0434DBD8A9FBE67CD2F894F5CEBCC945BED9AAC0AE4FAA07AC4C102C37BC`。
- `--skip-grant-tables` 试验因 Windows 无 TCP/pipe 配置失败（port 0），原件 `mysqld-skip-grants.log` SHA-256 `2411331AD30EB47B525B502A56647E7D71DAF9074DFDA837C22ECA9D81E9A16D`；skip-pipe 进程随后已停止，未形成可用于 Android 验收的服务。上述失败只作运行器/夹具诊断，不能记产品通过。
- 未提交/未推送源码 WIP 保持原样，重点路径包括：`android/app/src/main/java/com/acrylic/outbound/pda/temporary/TemporaryRecoveredConfirmationWorker.kt`、`android/app/src/main/java/com/acrylic/outbound/pda/ui/RecoveredHandoverConfirmationDialog.kt`、`android/app/src/main/java/com/acrylic/outbound/pda/ui/RecoveredHandoverConfirmationDisplay.kt`、`android/app/src/androidTest/java/com/acrylic/outbound/pda/RecoveredConfirmationDeviceTest.kt`、`android/app/src/androidTest/java/com/acrylic/outbound/pda/RecoveredConfirmationUiFixture.kt`、`server/src/test/java/com/acrylic/outbound/e2averify/E2aRecoveredConfirmationAndroidBridgeMain.java`，以及既有 Handover/Sync/Temporary 相关改动。完整清单与哈希见收口快照。

## 交接判断

本次交付边界是 **E08 / D17 UI构建与环境收口**，仅构建/lint与资源停止、清理已完成，业务范围待核，UI L1仍未通过。首个文档提交中的D06命名不作为现行验收归属。源码与继承WIP保持；最终收口只更新受影响文档并停下，不再启动旧run03或下一验收。

接手第一步只读对照总目标第4/5节、R07政策、现行D06/D07与D13/D14，核对上述worker/dialog/fixture的业务适用范围并记录逐路径处置意见；不删除继承改动，也不预设继续旧接收确认。随后才从有效范围矩阵选择一个受影响组合；不得把这次范围核对当产品通过。
