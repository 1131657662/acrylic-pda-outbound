# O02 Android主机产物：电脑CLI只读消费者

## 有界结论

本轮输入为主树 D:/acrylic-pda-outbound、codex/development、9d165a2d5cf27dd2ee0f9ea37f9e7968cd626514 加保留WIP。复用[rescue完整主机回归](GOAL_O02_NATIVE_FULL_HOST_REGRESSION_2026-10-20.md)实际生成且逐SHA未变的FORMAL/TEMP/scale文件，由产品 tools.pda_rescue 只读解析及CLI实际消费；没有重跑Android主机集合。31项检查全部符合预期：7项原ZIP校验、10次CLI成功、14次CLI预期拒绝。作者与自审为同一助手；Python/CLI是工具消费者，不等于独立人员审查。

仅证明该组合成输入的包完整性、离线文件核对和合成登记范围行为。包外登记为同一夹具另行生成的合成登记文件；没有从包内复制公钥自证，也没有证明真实来源信任、业务回执、现时业务状态、外置介质可取回或O02/O05整体通过。localDeletionAllowed=false。版本事实见看板生成块。

证据根：

- [consumer01](../../.local/goal-20261020-resume/o02-current-cli-consumer01/)：首轮失败，原件保留。
- [consumer02](../../.local/goal-20261020-resume/o02-current-cli-consumer02/)：有效执行。
- [closeout01](../../.local/goal-20261020-resume/o02-current-cli-closeout01/)：只读复核与收口。
- 源生产者的[输入清单](../../.local/goal-20261020-resume/o02-native-full-host01/input-manifest.json)和[输出清单](../../.local/goal-20261020-resume/o02-native-full-host01/final-manifest.json)绑定本轮复用文件；本地证据不等于Git异地备份。

## 执行与输入关系

- 首轮直接运行 Python CLI，通过管道提供合成口令。7项原ZIP先完成；第一次CLI在45秒后TimeoutExpired，未得到有效CLI结论。Windows控制台getpass不按本运行器预期读取管道；原runner、failure.json及失败输出清单保留，不改写为通过。
- 第二轮使用新的证据根；只在子进程把getpass交互口令边界接到标准输入，经runpy运行原 tools.pda_rescue.cli。argparse、解密、验签、事实校验、信任判断及发布仍为产品代码，未改产品或验证断言；不把这次边界替换称为真实终端交互验收。完整命令、stdout/stderr、逐次退出码见各 command.json。
- 七份原ZIP仅由PC夹具加合成AES-GCM外壳，Android原ZIP字节不变。另两次直接消费Android实际生成的 synthetic.pdarescue 与 room-generated.pdarescue，避免将PC外壳等同Android加密导出。
- Python及依赖版本、42项电脑消费者输入、11项生成物、1022项Android生产者输入、10项fixture、31项继承保护和2项资源摘要均由清单绑定。执行后及本次只读复核无漂移；不以HEAD相同替代WIP输入核对。

## 实际检查

| 范围 | 实际结果 | 证据边界 |
| --- | --- | --- |
| synthetic-disposed、synthetic-empty、synthetic-frozen、formal-frozen、formal、temporary、large原ZIP | 7项完整性与事实校验符合预期；原序号有序且不重复 | 合成资料；文件名含frozen不保证顶层状态为FROZEN，逐份source/state见raw摘要 |
| 七个PC夹具外壳：CLI核验及新目录发布 | 7次exit0；94个原成员逐字发布，verified.json中的摘要/原事实核对通过 | 无登记时sourceTrusted=false、businessReceiptVerified=false |
| 无包外登记而要求可信来源 | 7次exit1；拒绝输出目录均不存在 | 完整性不替代来源登记 |
| 两个Android加密原包 | 2次exit0 | 未做网络、设备、USB/MTP/OTG或介质往返 |
| 合成包外登记匹配 | 1次exit0，sourceTrusted=true；businessReceiptVerified仍false | 同夹具旁路登记，仅合成信任测试 |
| 登记人/仓库/公钥不符及无设备登记 | 4次exit1；无拒绝输出目录 | 不从包内自建信任根 |
| 错口令、密文损坏、已存在输出 | 3次exit1；原输出全成员哈希不变 | 原包/已发布资料不覆盖 |

大包含12639条scans，11376有效、1263已撤销，有效重量3418488000克；仅用于本轮合成规模文件核对，不冒充真实库存或现场规模验收。所有成功输出均不代表出库、放行或可删除。

## 原件与失败保护

本次收口只读复核了217项既有输出清单、24份命令及stdout/stderr摘要、94个发布原成员和七份verified.json；未重跑CLI或旧用例。11项生产者输出、42项消费者输入、1022项Android输入、10项fixture、31项继承保护及2项资源摘要无变化。重核结果见[只读审计](../../.local/goal-20261020-resume/o02-current-cli-closeout01/evidence-audit.json)，其SHA-256为54e737d7d025982bc19076b4ba00bfd05e6d70dc7e8595d5501680bba3cedaf2。

首次收口快照误对整个porcelain文本strip，导致首条AGENTS.md路径被截短遗漏；原快照保留，随后NUL分隔原始status重新采样并补齐32项，见 dirty-before-corrected.json、dirty-before-bytes-corrected.zip 和 status-before-raw.bin。修正只涉及本地快照，不改源码。

有效执行关键输入/输出：

| 文件 | SHA-256 |
| --- | --- |
| consumer02/result.json | e059e668470e190ba0ac2131eeb88440854e66c1b483f13646cf44182de18aed |
| consumer02/input-manifest.json | 438bad3f4749d3a0dfa47e3e1787d0e0245a4cd31f500cf94783ca80be613b3a |
| consumer02/verification.json | fdb51b7d5a1875065670be15299123293b1a1b3689a88b0daf11b55e9aba4ce0 |
| consumer02/output-manifest.json | 4123b30edc19e39a0b88906dc79f3cb7739f9bcbdb3efba735c8fed35dafb511 |
| consumer02/run_consumer.py | 1e3ef4ac64f9a60a287045b5d07239d45dbe13b86571035d342c908c440a6ba9 |

首轮 failure.json SHA-256为2578a4080a4238d3a95ad1dde108cdaf07119f75392156c6820d6f3530bf9f2e，失败输出清单为04b69c31b7c35b206994f9a52c7f6c48757affa29d6333bf8563faa1db002102；失败和成功根不互相覆盖。

## 环境、协调与未验

本聊天没有test服务器消费者，没有使用或计划在此收口阶段使用USB M71/模拟器，不启动重型构建、业务HTTP或MySQL，不操作另一聊天的test演练/隔离部署树或进程。共享看板与历史索引遵守写前保存原字节及比对；收到test维护协调时先只写此报告与专属证据根，具体协调在 coordination-20261020.json。

本轮清理释放0字节。证据、APK、tar、原身份、原回执/签名及继承改动全部保留；不把先前9.44GiB重复计作本次释放。

仍未验：真实来源登记/原身份、业务回执及现时状态、原机完整导出/电脑消费/外置副本可取回、完整O02/O05及统一最终候选的L2/L3、第8节。实际test演练属于另一聊天的明确范围，本聊天不据其授权操作test。总目标active、未完成；不恢复取消链/旧run03，不接原库/现网、不用正式签名、不合main、不部署或Release，生产决定保持fail-closed。
