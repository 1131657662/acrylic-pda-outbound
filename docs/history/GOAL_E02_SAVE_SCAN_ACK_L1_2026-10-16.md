# E02 保存扫码分段接续与失败确认反馈 L1

本切片补验原模拟器的合法续备、页面保存计划至扫码的分段接续，并修复确认失败输入后旧提示残留。仅为 E02/U 高频页面的限定 L1 开发备份，不计 E02/U 整体、E06/E07 L2、D18 L3 或总目标通过。

## 输入与影响

工作树为 `D:/acrylic-pda-outbound`，分支 `codex/development`，构建输入 HEAD 为 `dac477f1b834d4bbffad71418e3588471275905e`，含继承改动。所有 APK 均来自完整脏树，不能视为本次部分提交的精确候选。版本事实见看板生成块。

证据根为 `.local/goal-20261016/e02-save-scan-combination01/`，以下文件名均相对此根。input10 保存构建前 5,309 项源码/文档输入，SHA `19934b1487509ccf461609bc29e018afd5446aa8d493beed509fc8eb282c33ad`；input11 保存接续时全量输入、全部改动、未跟踪原件、写权和环境，SHA `2b85e1fed29264d0d51a90428fbd770160d6114d3f2e136dd69e7a9e03dd984d`。`build08-source-recheck12.json` 核对这 5,309 项，仅看板断点变化，产品/测试输入未漂移。

本聊天为唯一写者，清理聊天实查 idle 且回执 complete-released；无子代理或并行项目写者。原草稿 `108f544b-07f6-482d-81ec-88a3014004af`、原账号/登记钥及原绑定保持，不修改时钟或资格期限。此处“原身份”指已有本机隔离模拟器身份，不代表物理 PDA 或现场事实。

| 改动路径 | 影响层 | 必跑与结果 | 复用依据 |
| --- | --- | --- | --- |
| `android/app/src/main/java/com/acrylic/outbound/pda/temporary/TemporaryEngine.kt` | TEMP 失败确认反馈/串行消费 | 两连续失败输入、最后确认反馈、扫码/调整回归；原订单实际重复拒绝与确认通过 | SHA `cd27c7779a93d5886f8fb3af993ae1d34c1314f655e113e29d4ee07224c53acc`；无 HTTP、模型、迁移或签名变化 |
| `android/app/src/test/java/com/acrylic/outbound/pda/temporary/TemporaryEngineTest.kt` | 队列与提示回归 | build08 Engine 3 项通过，后续失败可覆盖完成提示，原输入/确认记录保留 | SHA `e66ef65f6054fa5c37378d6f635035bf244b5f358f67e9bd868a613dafe77517` |
| `android/app/src/androidTest/java/com/acrylic/outbound/pda/TemporaryPreparationRenewalDeviceTest.kt` | 原身份真实 HTTP/MySQL 续备 | renewal660-01 通过，原资格保留，新准备与 ACK 生效 | SHA `9c35bf86e132e9f3b64356e8f5b7fda9f71ef7d14cdf3b18e621c0af89315c73`；consumer02 原签消费 |
| 同目录 `TemporaryPreparationViewportDeviceTest.kt` | 准备页真实手势/确认取消 | viewport659-touch01 通过，83 表无变化；不追认 659 原失败 | SHA `f617744bfc83f7cbfbc6588980cf918e8343a7eb59505e1f61b74a2fa104b99f` |
| 同目录 `TemporarySaveScanCombinationDeviceTest.kt` | 保存/扫码/原 pending 接续 | savescan660-01 失败保留；resume01 沿原订单/原输入确认、暂停、重复拒绝通过 | SHA `cdd9c7e528da4a9349841cba670afec50bea7faaef2fccd396fd0456a632145c`；非一次全程通过 |
| 同目录 `TemporaryFailureAcknowledgementDeviceTest.kt` | 改后产品实际入口 | ack-ui01 通过，截图核对完成反馈，原 captures 保留、有效仍 1、无 submission | SHA `216ed4418ee480a6a6c69b6de43c69f1de2f6173eac12e98218ba736873edd20` |

完整路径/长度/SHA 见 `l1-source-hashes12.json`；实现决定与必跑映射见 `ack-ui-impact.json`（清单中 Adjustment 类拼写以实际 build08 命令/XML 为准）。采用最小修复：确认事务成功后更新完成提示，再消费后续队列；下一失败仍显示其处理提示。未改失败原件、耐久保存、权限或资格语义。

## 实际执行与失败保留

1. **658/659**：首次滚动失败、第二次点击未打开确认框，均未请求新准备且设备 83 表不变。日志、tar、数据库、原 binlog 和 dump 保留；认证停机、端口清零，不复用。659 后续仅通过原手势定位/触摸确认框→取消诊断，未重签资格。续备测试据滚动位置证据增加稳定等待，不追认前两次通过。
2. **660 续备**：沿原账号与原登记钥连接隔离 HTTP/MySQL，`renewal660-01` JUnit `OK (1 test)`。原请求 `e80665c3-0085-43db-a340-5ba7211caf57` 保留，新请求 `8f6285c3-e519-4181-8074-f09a677122b2` 的 previousKey 指向原请求；新资格及五类分片、原签 ACK 完成。未补造身份、签名或原资格。
3. **保存扫码首轮失败**：`savescan660-01` 实际创建订单 `73a1391f-e140-41f2-9b8c-cf5ab6eaf42c`，页面搜索→添加 1→修改 2→保存 revision1→开工 revision2/SCANNING→手输 WAITING_CONFIRM。运行器误把 F1 当确认，实际 F1 为校验、Enter 为确认；这是测试错误，已发生业务写入。完整失败日志、页面、tar 和数据库事实保留，未新造替代订单。
4. **分段接续**：修正测试键位后，`savescan660-resume01` 使用 build07 测试包、复用原产品 APK，在服务停机下对原 WAITING_CONFIRM 输入发送 Enter。有效 1 包/300500g、原 capture `b98ce2e9-c926-4917-a1d5-9945bc4069c0` 转 COMMITTED；暂停拒收广播→恢复重复拒绝→确认处理结果通过，JUnit `OK (1 test)`。无 submission。仅称失败链分段接续成功。
5. **自审修复**：上述最后截图仍显示旧“请确认处理结果”，据此修复 Engine。build08 执行 `assembleDebug`、`assembleDebugAndroidTest`、`lintDebug`、`testDebugUnitTest --tests '*TemporaryEngineTest' --tests '*TemporaryScanningTest' --tests '*TemporaryAdjustmentTest' --offline --console=plain --no-daemon`，退出 0。XML 合计 18/0/0/0；lint 424 warning、0 error/fatal，原 XML 保留。
6. **改后原订单入口**：`android_ack01.py` 校验原 ranchu、binding、空 reverse 和旧 APK 后，以 `install -r` 安装 build08 产品/测试包，保留全部数据。ack-ui01 再次拒绝原包→真实页面确认→完成反馈通过，JUnit `OK (1 test)`。作者已查看 `duplicate-acknowledged.png`：有效数 1/计划 2、300.5kg、完成提示和不得放行说明可见，无提交。

作者完成源码/测试和截图自审；没有独立人员审查。Python 为同助手运行的独立实现消费者，未导入产品校验器，不等同于独立人员验收。

## 独立消费与取证补正

`consume_660.py` 的 consumer02 实际消费四份原 tar 的 SQLite/WAL 副本、两份 RS256 资格、manifest 原字节摘要、身份/请求上下文、48 小时期限、五类原分片及条数/摘要、原签 ACK 和 commandSHA。计划 canonical SHA 为 `f0a908f775d634b22f7f1e284c4a44a48d7ea7c1764e34919ad96de7dc62e55e`；原 pending 身份、序号、300500g、重复拒绝及无提交成立。

83 表逐阶段允许差异：续备仅增加 request1/preparation1/chunk5；保存阶段增加 draft1/line1/capture1/event6 并递增设备序号；分段确认阶段变更原 capture/draft 序号，增加重复 capture1/scan1/guard1/event5。原业务行保留；`work_device` 是明确允许递增的计数器。consumer02 的 `allOriginalRowsPreserved=true` 不能解释为计数器逐字节不变，原结果保留，本报告明确该限定。

consumer02 最初哈希清单 3,788 项未包括库外 binlog：REPO 父级少一层，glob 为空。保存 `consume_660-before-pathfix03.py` 后只修正路径，未覆写原结果或重跑业务。`consume_ack03.py` 的 consumer03 补证通过：

- consumer02 清单中 3,785 项字节未变；三项差异分别为尚未 flush 的原 stdout 日志、已保留旧原件的路径修复脚本、任务描述更新的快照运行器。consumer02 结果/业务原件未变。
- ack-ui01 前后 SQLite 完整性通过，83 表仅 work_device 和本单 nextScanSeq 按预期递增、新增 INVALID capture1/event3；旧业务行、原 captures/events/scan/guard 保留。三事件为 RECEIVED、INVALID、FAILURE_ACKNOWLEDGED；有效仍 1，无 submission，binding 原字节不变。
- 36 份关键原件、APK/XML、tar、dump、脚本和 660 原 binlog 双次哈希/长度一致；binlog.index 解析至同一绝对目录，UUID 与停机凭证核对。此为只读补证，不补算历史通过或重做续备。
- 服务端 180 表：续备前 393 行、后 402 行；续备后至最终 count/checksum 相同。恢复到迁移后仅迁移历史与权限种子变化，新表均空，旧业务守恒。

| 产物 | SHA-256 |
| --- | --- |
| build08 产品 APK | `bebabdf097f5768e5a8e7f4a992c85f0a901e5197e39a645ec15bd4c69fb0682` |
| build08 测试 APK | `ac0f61979d2a72d32124075316a12fd08ebad03d4e5651520fc1b00ad5773927` |
| 660 完整 dump | `c68fa77c1da44ae3b89ba4c96b50dbafb2e43fa731005310ce0ecbe312de3b32` |
| 660 原 binlog.000001 | `0e271fb52b90a37467e4be5ad15dc89af1a8e9ef7064b9947e57d717539599df` |

全部命令、JUnit/XML、原签、tar、dump、APK、PNG 和哈希留在 D 盘，不提交 Git；tar/本机输入可能含秘密，禁止上传。服务 JAR SHA `e130aa2b7844daf6380045e3f3e809672b9c15f5ff31c4a24adf4cdf6bdc92ba`，原签/迁移/HTTP 结果仅在上述输入范围复用。

## 环境与接续

660 为 `.local/goal-20261016/e02-save-scan-660/data` / `acrylic_e02_20261016_660` / UUID `1ae47637-c8ee-11f1-96dc-d8bbc13a2b36` / server-id10660；数据库19370、HTTP19462认证停机且零监听，不复用。原模拟器 TemporaryAdjustment1364/emulator-5564 保留，emulator12912/qemu12108、5564/5565；ADB8500/5037，reverse 恢复原空列表。共享 mysqld4632/5440 身份未明，仅观察，不访问。

input11 实测 C/D 余量 `83,108,249,600` / `46,166,671,360` bytes；本次无清理，全局环境未改。构建 TEMP/TMP/Java 临时目录仅作用于本任务子进程，预计增长与重型前磁盘见 `build08-command.json`。提交前后全量 Git、写权、进程/监听和磁盘快照接看板。

按本次六个代码/测试路径及本报告/索引/看板明确暂存、提交和推送开发备份；其余继承改动及 `server/physical-disposition-last-request.json` 保留。生产决定能力、TEMP 生产归档边界和 `localDeletionAllowed=false` 不变；不接原库/现网、不合 main、不发布部署、不用正式签名。

下一步依据已验输入推进 E08 的结单/恢复确认真实客户端入口与 U02 状态语义，按实际缺口组合 E07/E06，再核 E09/D18 候选。1540 Chrome 运行器审批阻断继续保留，不换包装绕过。上述任一局部通过不结束总目标。
