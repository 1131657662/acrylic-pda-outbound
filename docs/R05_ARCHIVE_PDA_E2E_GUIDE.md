# R05 本机 V28 与 PDA 归档端到端验证

日期：2026-09-19。用户选择先完成本机 V28 归档/PDA 端到端，最终确认使用现有 `Pixel_6` AVD、运行既有 `PreparationEndToEndTest`。本轮已完成该范围；公网仍为0.18/V23，未切换后台，M71未操作，R05整体仍在开发。

后续已用同一保留身份完成[跨进程/跨恢复代次恢复](R05_ARCHIVE_RECOVERY_E2E_GUIDE.md)6个原生阶段；当前本机库为代次2/NORMAL、PDA为代次2/RECONCILING。本页记录原代次1的历史流程，不再是可重复执行的空白夹具。

## 环境和保护边界

| 项目 | 实际值 |
| --- | --- |
| 分支 | `codex/development`，HEAD `963c8bc5c4b540a1d7dad9c1cc6adf61c7fa29ca`；工作区含既有未提交改动 |
| 设备 | 现有 `Pixel_6`，仅本轮启动为 `emulator-5586`，API37；没有新建AVD或清除原AVD数据 |
| 本轮包名 | `com.acrylic.outbound.pda.debug.r05archive`，由ignored Gradle init脚本为debug添加独立后缀；安装前不存在，未替换原debug/staging应用 |
| 应用构建 | `0.11.0-r05-sync` / code15 / Room14 的本机debug测试构建；不是原已发布code15，也不是公开code16 |
| APK SHA-256 | `358af2b1953673c8b08b77162036fa854c0628876deb2bd9dd8ee53fc7f0fa81`，已核对实际安装APK相同 |
| 测试APK SHA-256 | `9039421e6c452967a9988e866b153fc8710ee0ef6d0bf9c97820a3f717004230` |
| API | 本机 `127.0.0.1:18088`，复用已验证的0.20/V28候选JAR，SHA-256 `82efcedcb85e20fea6bfb6a260eb21d7bf1ca82ebdd56bba1a52070bfe203759` |
| 数据库 | 工作区MySQL13316中的全新独立库 `acrylic_outbound_r05_archive_20260919`；新账号权限只限本库 |
| 文件与签名 | `.local/r05-archive-e2e/private-files`，新建本机测试签名密钥；未读取现网私钥作为本机签名配置 |

本机服务只监听回环地址；Android通过标准模拟器主机地址 `10.0.2.2:18088` 连接。既有 `SubmissionProxy` 仅在测试进程内转发本机API，用来模拟提交响应丢失；没有设置模拟器系统代理，没有改变HTTPS证书校验或公网更新源。该debug本机HTTP路径不能作为公网HTTPS或已发布staging包验收。

## 本轮执行和结果

1. 在上述全新库上重新执行 `PdaAndroidFixtureIT`，1项通过，0失败/错误/跳过；Flyway实际执行至V28。夹具创建专用测试人员、仓库、两包库存及READY订单，不操作真实业务订单。
2. 构建 `assembleDebug`、`assembleDebugAndroidTest` 成功，用明确设备序号安装到独立包名；未卸载或清除原应用。
3. 运行既有 `PreparationEndToEndTest#registeredDeviceLogsInClaimsDownloadsAndRecoversLocalActivation`，添加显式参数 `verifyArchives=true`；**1项Android原生端到端通过**，16.001秒，0失败/跳过。归档断言是这1项内部流程，不另计为多项测试。
4. 测试真实完成设备公钥登记、Keystore挑战签名登录、订单领取、准备资料下载/确认、实际Activity显示、广播扫码/手工输入、错误确认、冻结提交、丢提交响应后的原请求恢复、短装主管批准及原成功回执验签。此测试复用既有管理员登记路径，不替代另有证据的短码配对流程。
5. 随后成功提交归档与审计归档都调用真实V28 API，使用真实数据库和文件，不模拟服务端凭据。每类归档第一次收到成功结果后，由测试客户端在写入Room前抛出IOException，模拟已提交但本地尚未保存响应；新的repository重试原请求，核对服务端返回同一份完整签名结果。
6. Room关闭重开后重新读取并验签归档记录，确认原冻结正文、成功回执、采集/扫码/事件未变。这是数据库重开验证，不把它写成系统断电、进程被杀或跨恢复代次重建通过。

| 事实 | 核对结果 |
| --- | --- |
| 采集/扫码/审计 | 4条采集、2条有效扫码、15条审计；全部保留 |
| 出库 | 1个成功提交、1张订单、2条逐包出库明细，合计601500克；重试没有重复出库 |
| 成功提交归档 | Room和MySQL各1条，完整确认内容一致，真实签名验证通过 |
| 审计归档 | Room和MySQL各1段，连续序号1～15（afterSeq0、throughSeq15），完整签名证明一致 |
| 私有文件 | 提交正文、成功回执、审计段共3个内容寻址文件，SHA-256和字节数均与保存凭据一致 |
| 本地数据库 | Room14，`PRAGMA integrity_check`为ok；重开后凭据再次验签通过 |
| 删除许可 | 两种凭据均为 `localDeletionAllowed=false`；未删除本地业务事实 |

本轮未修改业务主源码、API契约、迁移或公开APK配置。只给既有端到端测试加了可选归档阶段，并新增 `ArchiveEndToEndScenario`。后端247个主源码文件摘要仍与上轮候选一致，因此未重复运行上轮32/336全套回归；那组结果仍归[V28恢复副本演练](R05_V28_REHEARSAL_GUIDE.md)，不冒充本轮新测试数。

## 失败、更正和证据

Android端到端本次首轮通过。后续证据采集脚本首次误把 `dispatch_record` 按订单计数，预期1条而实际2条；核对schema和原结果后确认该表按包记账，改为同时断言2条明细、1张订单、601500克，再次核对通过，没有改变业务数据。更正保存于 `evidence-collection-correction.json`，不能省略为所有检查首轮全过。

证据均位于ignored目录 `.local/r05-archive-e2e/`：

- `fixture-build.log`：夹具真实MySQL测试；`android-build.log`：两种APK构建。
- `preparation-archive-e2e.log`：原生1项通过；`e2e-evidence.json`：完整准备/作业/提交结果。
- `archive-e2e-evidence.json`：两类归档丢响应重试、原事实保留和Room重开复验。
- `verification.json`：Room/MySQL/3个文件核对、实际安装APK摘要及测试计数。
- `outbound-pda.db`及相关WAL/SHM：测试应用保留的数据库副本；`app.apk`、`test.apk`：本轮产物。
- `live-unchanged.json`：本轮只读核对公网PID、JAR、current、签名清单和备份timer均保持。
- `cleanup.json`：专用本机API停止、Pixel_6正常关闭请求和保留数据记录。

`runtime.json`、`e2e-fixture.json`、签名密钥及数据库/文件只保留于ignored本机目录，不提交。测试结束后只停止本轮启动的本机API及 `emulator-5586`；保留AVD及测试应用、独立库、私有文件和证据。既有测试结束会关闭其临时转发端口，因此不得直接重跑原一次性夹具覆盖已登记设备；后续先读取保存断点，按原身份准备明确的恢复阶段。

## 未完成与下一动作

本轮补上了V28/PDA普通归档、响应丢失重试和Room持久保存的真实本机联调。下一项可独立推进的是本机PDA跨进程重开及跨恢复代次的归档恢复/屏障核对，应使用专用夹具并保留原证明，不能直接修改现网恢复代次。

Linux unit的143退出码修正运行复验仍受上轮自动审批拒绝影响，仅语法检查通过；本轮没有重试该远端动作。App内升级独立渠道、第二台PDA、Android9/M71/异地、长时/断电及真实业务提醒现场均待补；整单90天事实清理仍依赖R09备份恢复和引用核对。用户保持现网不切换的决定继续有效。

本轮没有提交、推送、合并、打标签、GitHub发布或生产部署，不能据此标记R05全部完成。
