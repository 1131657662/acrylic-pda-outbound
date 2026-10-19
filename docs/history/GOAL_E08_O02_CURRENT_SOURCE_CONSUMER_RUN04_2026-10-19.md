# E08/O02 当前源只读消费者与副本往返 L1（2026-10-19）

本切片在重启后的当前工作树上重新消费已封存的合成 Android rescue 向量，核对电脑端只读验证、输出发布、加密副本创建与取回。它不把合成向量升级为真实 PDA、生产身份、外置介质或 O02/O05 L2/L3 证据。

## 输入和写权

- 树：`D:/acrylic-pda-outbound`，分支 `codex/development`，输入 HEAD `f02c5a03530558770ffbd4f833a7fa26982e5904`。
- 当前源码及向量逐文件 SHA、字节数、Python 路径/版本见 `.local/goal-20261019-resume/o02-current-consumer-run04/input-manifest.json`。该清单记录 120 个当前源码/封存输入文件；run04 自有向量副本位于同目录 `vectors/`。
- 封存输入未覆盖：合成包 SHA-256 `758B6713E08DBE3E2EA0F86C8B3A8A1732FEE48AD2ED3260D0B8F36CFB082C41`，包外信任清单 SHA-256 `6387094124B84E8DBFEEBE5F82B6877A52A4A68E81473FEBB55DCF4737120CC6`。
- 所有运行输出、临时目录和 relay 测试根均在 `.local/goal-20261019-resume/o02-current-consumer-run04/`；未使用服务端、MySQL、ADB、模拟器或正式 Keystore。

## 定向测试

首轮 `o02-current-consumer-run01` 只绑定了闭包向量，导致 32 个环境变量/向量加载错误；没有进入产品或数据库，日志和退出码保存在该目录。`o02-current-consumer-run02` 补齐部分变量后仍把 `test_verify` 的历史变体向量当作输入，3 个缺失的 frozen/empty/disposed 文件错误保留。`o02-current-consumer-run03` 将三类向量复制到新根，55 项中 54 项通过，唯一错误是选择了缺失的原始 disposition 变体文件；未修改源文件。

最终 run04 将 synthetic、closure、independent 三类输入复制到隔离根，并只运行当前可取得的测试集合：

```text
python -m unittest tools.pda_rescue.test_copy tools.pda_rescue.test_independent_closure tools.pda_rescue.test_independent_review tools.pda_rescue.test_independent tools.pda_rescue.test_relay <selected test_verify cases> tools.pda_rescue.test_xlsx
```

结果：`54 tests`, `0 failures`, `0 errors`, `0 skipped`，退出码 0。完整命令输出 SHA-256 为 `D50B26EB6DB45D242E95841257703153FF9E023E64118CA9CD21A560192A6594`，日志为 `.local/goal-20261019-resume/o02-current-consumer-run04/python-tests.log`。测试覆盖：包外信任、签名/清单和语义守恒、长序号/危险文本、原件篡改与重复 ZIP 成员拒绝、独立来源范围/VOID/凭据/结束签名、relay 丢响应重试、副本清单和取回完整性、转换失败 partial 保全以及 XLSX 文本保护。

## 直接消费结果

对 run04 自有 synthetic 向量执行一次只读直接消费，命令摘要、退出码和输入清单在 `direct-consumer-command.txt`、`direct-consumer-exit.txt` 与 `direct-consumer.log`。结果文件为 `consumer-result.json`，SHA-256 `E75D1CB02F163A02F1E770F04B745A0F8BC2E79886FB7B78A416869E285F2A35`。

- 包外信任为 `true`，业务回执核验为 `false`；4 captures、2 scans、3 events、1 ACTIVE、1 VOID、1 pending、1 failed，重量 `123456` 克。
- 13 个只读输出文件发布到 `verified-output/`，发布前完整生成并可读；既有输出不存在时才创建，未覆盖历史目录。
- `encrypted-copy/original.pdarescue` 与输入包同为 3706 字节、SHA-256 `758B6713E08DBE3E2EA0F86C8B3A8A1732FEE48AD2ED3260D0B8F36CFB082C41`。
- 副本清单 SHA-256 `15D84AB6C803C8DF5F199B2B27AE8DB9E9BB5AEA06B9D872B33C1F5F01608CE4`，取回报告 SHA-256 `392788EB8EDB5673E5EEACAC5DBF4B8EA624599E3FCBE0885259CF51D8D453D3`；取回完整性和来源信任为 `true`。
- `externalStorageVerified=false`、`businessReceiptVerified=false`、`localDeletionAllowed=false`、`protectsExportedSnapshotOnly=true` 均由直接结果和取回报告确认。
- 输入包及信任文件 SHA 前后一致；输出仅为本地只读解密资料和加密副本，不含提交、放行、库存释放、删除或新作业权限。

## 边界和保全

本轮没有连接原库/现网，没有启动 HTTP/MySQL/Gradle 业务服务，没有操作 Android 设备，没有使用正式签名，也没有构建或覆盖 APK。run01–run04 的日志、退出码、输入清单及 run04 成功输出均保留。run03/run04 自有合成向量副本中的 `pda-rescue-partial-*`、`verified-pc-copy` 和 `xlsx-test-*` 临时测试输出已识别，但清理命令被执行策略拒绝，未删除任何文件；拒绝输出未被覆盖。历史向量目录中因 run02 环境绑定产生的测试输出未删除，源包字节未变。

这是当前源电脑消费者的 L1 开发证据，不关闭 O02/O05、D11/D12/D14/D15/D16/D17、E06/E07/E08 L2、D18 L3 或第 8 节闸门。真实 PDA/原身份、实际 Android 文件通道、MTP/OTG/外置介质保管、原生 Excel、业务回执、现场人工核对和最终候选组合仍未验；生产默认、`localDeletionAllowed=false`、正式签名、部署、Release、`main` 合并和已取消换机链继续关闭。

## 证据索引

- `.local/goal-20261019-resume/o02-current-consumer-run04/input-manifest.json`：输入 HEAD、三类向量、当前 rescue 源逐文件 SHA。
- `.local/goal-20261019-resume/o02-current-consumer-run04/python-tests.log`：54/0/0/0 定向测试。
- `.local/goal-20261019-resume/o02-current-consumer-run04/consumer-result.json`：直接消费、输出和边界摘要。
- `.local/goal-20261019-resume/o02-current-consumer-run04/retrieval-report.json`：副本取回结果。
- 清理：尝试的目标路径、命令和策略拒绝记录在会话工具输出中；未产生 `cleanup-before.json`/`cleanup-after.json`，因为未获准执行删除，也没有释放空间。
- `.local/goal-20261019-resume/o02-current-consumer-run04/`：失败轮、命令、退出码、输出和原件哈希。
