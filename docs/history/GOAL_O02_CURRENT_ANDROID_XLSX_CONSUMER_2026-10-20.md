# O02 Android主机原产物的电脑Excel转换消费者

## 有界结论

本轮在主树 D:/acrylic-pda-outbound、codex/development、输入HEAD 9d165a2d5cf27dd2ee0f9ea37f9e7968cd626514 加清单保护WIP上，复用[完整rescue主机回归](GOAL_O02_NATIVE_FULL_HOST_REGRESSION_2026-10-20.md)的合成Android原ZIP，以及[本轮CLI消费者](GOAL_O02_CURRENT_ANDROID_PC_CLI_CONSUMER_2026-10-20.md)已保全的PC加密外壳。产品CLI的 --xlsx 实际执行5次，全部exit0；5份最终Excel、25张表的139396个数据单元格逐值核对一致，63个原包成员逐字节不变。作者和自审均为本助手，工具消费者不等于独立人员审查。

没有重跑Android生产者或旧用例，没有修改产品源码、原包、已发布旧副本或继承改动。五份输入全部是先前PC夹具加密外壳，不能把本轮转换称为Android加密导出/文件通道验收。没有提供可信登记：sourceTrusted=false、businessReceiptVerified=false；localDeletionAllowed=false。版本事实见看板生成块。

证据根：[o02-current-xlsx-consumer01](../../.local/goal-20261020-resume/o02-current-xlsx-consumer01/)。本地证据未上传Git，不虚称已异地备份。

## 标准、命令与独立核对

执行前的[完成标准](../../.local/goal-20261020-resume/o02-current-xlsx-consumer01/acceptance.md)要求新目录发布、全表值/类型核对、原件守恒和最终Excel预览；原字节、输入清单、完整脏路径和补丁分别保存于 input-manifest.json、dirty-before-bytes.zip、status-before-raw.bin、tracked-before.patch。清单绑定11个生成物、42个电脑消费者输入、1022个Android生产者输入、10个fixture、31项继承保护及10项选定包/原报告。

通过已安装bundled Python/Node/Artifact Tool运行原 tools.pda_rescue.cli --out <新目录> --xlsx。命令、运行库及runner哈希在 run-command.json 和五份 *.command.json；各原始stdout/stderr分别保全。子进程仅把合成getpass口令边界接到标准输入，经runpy执行原CLI；argparse、解密、验证、转换和发布使用产品路径。这不证明真实终端交互。临时目录只在本任务子进程指向D盘。

最终Excel由另一读取器openpyxl回读；期望直接取先前逐SHA固定的原 verified.json/facts，不把产品生成的 expected.json 当唯一判断。核对原顺序、全部标识/条码/时间的文本类型、超过安全整数范围的原序号、危险公式前缀作为文本、控制字符转义、每产品计划/有效/撤销/处置/差额/整数克、整单汇总、未决失败输入和原审计；没有公式或错误单元格。所有原成员SHA、旧 verified.json、表名/行数/冻结表头/隐藏网格线均符合预期。

## 五组实际结果

| 输入 | CLI退出码 | 扫码行 | 核对数据单元格 | 保持的原成员 | 说明 |
| --- | --- | --- | --- | --- | --- |
| FORMAL | 0 | 1 | 88 | 13 | 1有效、1未决、300500克；保留零贡献计划产品 |
| TEMP | 0 | 1 | 96 | 13 | 1有效、1未决、300500克；原审计不混同FORMAL |
| synthetic-disposed | 0 | 2 | 85 | 12 | 1原ACTIVE有QUARANTINE、1VOID；有效数/克为0；原行保持 |
| synthetic-empty | 0 | 0 | 34 | 12 | 空数据保持空，汇总为0，不补造扫码记录 |
| large | 0 | 12639 | 139093 | 13 | 11376有效、1263VOID、3418488000克；所有行均核对 |

大包CLI耗时约56.859秒，仅为本机该次样本，不是性能承诺或现场规模结论。五份最终Excel的SHA-256：

| published目录 | rescue.xlsx SHA-256 |
| --- | --- |
| formal | 3f7a59811772c0274e98f7525abb5bc3182d0ec0207ffd983bef917ad2b5d2db |
| temporary | 61b4e11ca6d04c3638da6ffcf7ddfb08be99021a63cb5d88b4d6cc1167290594 |
| synthetic-disposed | 8aa9d585bc0f183f39ed8db19cfe0c5190be13911f57c43e1e114a3529349af6 |
| synthetic-empty | f88688d92379eb76b034acf4a753be03303df022e4503245d923526eefc80744 |
| large | 15393a4b858bef6da16c37a4e24f258af4d8f7eb41c7e9091006e66e9c01cc36 |

## 可见检查、失败与保全

从最终Excel读取的25份 verified.png 已组成五张接触图，逐组查看全部表。标题、表头、数据及整数克可读；长序号完整、危险文本按转义显示，空表无合成记录。预览按产品说明只显示前16行并可能截长文本，完整值以Excel/原件及全量逐格核对为准；未运行原生Excel或打印。

执行前一次快照工具调用因传输含NUL被拒绝、未创建进程，随后按原始NUL分隔status的 bytes([0]) 解析保存；另有执行前JS转义错误未进入主机，均未运行产品。转换本身五次无失败。外层PowerShell汇总 run.log 有230个替换字符，属于控制台解码损失；原run.log保留，不改写或重跑。原始CLI stdout/stderr和直接写出的UTF-8各组结果JSON保持，可读摘要另存 readable-summary.md，不能代替原日志。

执行后逐SHA复核所有输入、31项继承保护与前轮217项CLI输出均无漂移。只见另一聊天拥有的看板、历史索引和test演练报告变化；本聊天未写这些路径。本轮不使用test、USB M71、模拟器、数据库或业务HTTP，不构建APK；共享文档和HEAD等对方通知释放后再接续。所有旧失败原件、APK/tar、原身份/Keystore、回执/签名与继承WIP保留。

本轮任务tmp为0文件/0字节，无遗留partial目录；五份实际转换产物及输入原件属于本轮证据，不按缓存删除。本轮释放0字节，先前9.44GiB不重复计增。

## 证据哈希与未验

| 文件（本证据根内） | SHA-256 |
| --- | --- |
| input-manifest.json | 7c92e2c70815519042e8d3a3ac9b5595983d47f5217454e2eb1cfd66ac7da038 |
| run_conversion.py | 4305996a91aef60bd7f6b923c2ed11b03a6d51fb0167a567ce3e09e43a6f22d6 |
| result.json | 54ec1bfff1a5f7b23c8ed0734d48c2a573af2484b178176323acd0f0489139f3 |
| verification.json | 7ae256409033808f4bfe3b777a07a335a656bb3e67f2e3cc1ec0ae72431d00af |
| output-manifest.json | 98727f473b4458dca29d72a193517d366c213fa07e46a5fce65b0b119ee727d8 |
| run.log（原乱码保留） | 4b346d43d8d3560f948de370d139fa97f43f5d49079bde00abeaa52d951bbdf4 |

只收口上述合成文件转换范围。真实来源登记/原身份、业务回执与现时净值、原机完整导出/业务文件通道、原生Excel/打印、外置副本可取回和统一最终候选仍未验。不能因此关闭完整O02/O05、D18或总目标第8节；目标active、未完成。原库/现网、正式签名、main、部署和Release不操作；生产决定保持fail-closed，不恢复取消链或旧run03。
