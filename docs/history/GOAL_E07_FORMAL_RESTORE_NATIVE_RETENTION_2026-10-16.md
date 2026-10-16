# E07 正式原批次恢复与 D16 原生触发器边界开发备份

665 正式产品激活、扫码、冻结、真实 HTTP/MySQL 成功归档及原 Room/SQL/私有文件恢复通过；留存工具补齐产品原生保护触发器的精确识别，101 项实际 CLI/SQLite 回归通过。正常目标的两个有效 guard 完整保留，实际 CLI 正确拒绝删除。本单元仅组合开发备份，不计 E07 整体 L2、D16 整体、D13–D15 整体或 D18 L3。

## 输入、决定和影响

- 固定树 `D:/acrylic-pda-outbound`、分支 `codex/development`；输入 HEAD/远端 `604b98ebc2ec9b675ab9cfbd7f26f16ed5ac1d7e`。本聊天唯一写者，清理聊天 complete-released 且实际 idle，进度聊天未加载；无子代理。87 项继承改动原样保全。
- 证据根 `.local/goal-20261016/e07-formal-retention01/`。input01 全量5364项，input03 共5369项、SHA `b48884717474ee4c8c63795815df4df1dc33b91daeb58ef158152cc4b19e2de9` 绑定665；input08/09 共5372项、SHA `21fe8cdedd2833ac5b2179fc527a4bf7b377e7ae7a3cf17840fb333b8617b4e5` 绑定最终工具回归，09同时保存本次接续的全部改动/原件/旧看板/写权/进程监听/磁盘。
- `design01.md`、`design02-native-triggers.md` 记录自主默认：Q12 有效防重不因日龄释放，不删 guard 制造正向；产品原生副本显式指定 `PRODUCT_NATIVE`，只接受固定清单中完整精确的保护逻辑，不从待删输入动态学习信任。
- 新隔离实例 `.local/e2a-cross-20260925-665/`，数据库 `acrylic_e2a_cross_20260925_665` 及空伴随恢复 schema；DB19375、HTTP19434及内部适配端口19435/19436运行前核未占用，不复用失败库或1538。密钥/凭据只在 `.local/`。

| 改动路径 → 影响层 | 本切片必跑 | 复用依据（路径+SHA） |
| --- | --- | --- |
| `FormalAndroidPreparation.java`、`E2aFormalAndroidClient.java`、`E2aFormalArchiveRestore.java`（server测试目录） → 合成准备/顺序Android驱动/服务恢复 | 非空升级、实际源/恢复两阶段、完整SQL/文件/配置恢复与全表守恒 | `reused-input-sha01.json` 逐路径绑定137项；Java/Kotlin五路径均与input03一致；既有663/664只按原报告范围复用 |
| `FormalArchiveWork.kt`、`FormalArchiveRestoreHttpTest.kt`（Android retention测试目录） → 产品激活/扫码/冻结/原签/归档/Room | 原生SQLite源事实、真实HTTP成功与归档、第二Android JVM消费原Room并核原签/guard/全表 | 产品依赖逐路径SHA同上；未执行PdaSession/SubmissionRepository，不把测试上传适配器写成完整客户端提交入口 |
| `tools/pda_retention/{product_triggers.py,product-triggers.json,catalog.py,consumer.py,references.py}` → 原生结构识别/事务内复核 | 完整集合精确匹配、source/manifest漂移、缺失/增加/篡改/view/profile反例；CLI正向/default-off/并发/锁后撤权/末DELETE回滚/恢复 | input08/09及101项新运行；137项复用清单中4个留存旧路径为本批预期变化，其他133项无漂移，不虚称全清单无漂移 |
| `tests/test_product_triggers.py`、工具README、报告/索引 → 定向验收与可追溯性 | 合成无目标guard正向；665正常有效guard拒绝；所有原件与失败保全、自审 | tests03实际执行，665仅补受影响Python消费，未重跑源业务；旧权限/事务/扩展链只按各正式报告原输入复用 |

## 实际业务与独立消费

1. Java build02 和 Android compile02 均退出0；compile01可空父目录警告已最小修正，最终编译通过。直接Java驱动内两次Android Gradle顺序执行，单次一个构建，源与恢复各新Android JVM。服务端为关闭再启动Spring context，不声称服务独立JVM或原PDA/Keystore。
2. `run665-result.json` 退出0；source/restored XML各1 test、0 failures/errors/skipped。合成准备身份通过测试装配/MockMvc取得正式签名准备包；产品 LocalActivation、WorkProcessor、SubmissionFreeze 实际产生两包事实。测试适配器调用公开真实HTTP，ReceiptVerifier核原签，产品DAO保存成功，再由 SubmissionArchiveRepository 经真实HTTP取得归档。未执行 PdaSession/SubmissionRepository。
3. 从非空旧结构升级，源成功后恢复完整SQL、7个私有文件和签名配置到新schema；恢复HTTP查询前后179业务表223行及DDL/迁移历史完全一致。dump665286 bytes，SHA `0257e08798821540b71243f41e13a542cd38e9ffccf792b63f131be9f1dfb91d`。源/恢复Room均83表26行、两个有效目标guard，原正文/原成功/原归档/全部表保持。
4. `consumer01/result.json` 独立Python只读核SQL、SQLite integrity/FK、逐表事实/私有文件、原RSA成功及归档和独立公钥固定。原payload SHA `cc01c8ebf34bad12bf91e4391ace4ef9ce1ceeae2281c16dc17aa3baaf7cc72f`，公钥SHA `868123dbfe6f20b77f5bb194dd2e3f3d3664a02fb67c2f17dcf879b3e14aa5c3`。591个原件260259262 bytes多次路径/长度/SHA相同，原清单SHA `2a8572301f77c93521065a268dc298c35b96f462207445f70f4fb1a1bcdadc2a`。

## 原生留存工具及失败保全

- 首轮 `retention01/` 实际prepare安全拒绝全部触发器，尚未到达有效guard判定；无删除。原脚本/完整副本/stdout/失败指纹保留，不追认为guard验证通过。真实新Room安装了47项独立采集保护trigger，工具旧catalog仅支持无trigger合成结构。
- 新清单逐字固定 name/table/sql 和各SQL哈希，并固定四个Kotlin保护源码SHA；工具固定清单LF规范化SHA `8ef30d0a17ce896e0309828e573067777695512c8d4693ca4b0fc219bb0d0db5`。只规范化checkout的CRLF/LF，SQL字符串仍逐字精确比较。默认 `SYNTHETIC_SCHEMA_ONLY` 须无trigger；`PRODUCT_NATIVE` 须完整精确47项，未知profile、view、缺失/额外/篡改、清单或源码漂移均拒绝。计划及实际锁等待后事务内重新核验，保护trigger及guard原样保留。
- tests01实际99项通过。自审增加换行/源码漂移反例后，tests02实际101项出现19个原生夹具setUp错误：规范化清单却仍使用旧raw CRLF哈希。按原件计算新哈希后修正常量，未放宽校验。其他case有真实合成写入，因此整轮数据/结果全部保全，并独立审计，不走无写入快速通道或清零历史计数。
- tests03实际101项、80.988秒，0 failures/errors/skipped，退出0。包括实际CLI删除/重放、default-off有无许可、异文、同键并发、真实锁等待后撤权、末DELETE失败全回滚、完整恢复、原审计及依赖保留、原生trigger集合保护、有效目标guard拒绝。
- `consume_retention02.py` 在新的 `retention02/` 停止单文件副本显式 `PRODUCT_NATIVE` 实际prepare，退出1且精确原因为 `Effective guard protects target order`，属预期拒绝。没有plan/permit；source/backup/candidate/独立恢复全表和47项trigger相同，两个guard未删；665原591件再次不变。不将正常guard拒绝写成取得整单删除资格，正向仍是无目标guard合成输入。
- `evidence-audit02.json` 独立核三轮301case、30次实际合成提交和2500证据，SHA `ef9260938b7cd5379c86953b95309b9d071c99ba1ef19a02c2bea6f6bef04f74`；每次提交backup/source/候选差异精确、guard不变、适用恢复回到原事实。`review-result02.json` 再核所有已提交原生case和retention02的trigger逐字保持、全部证据SHA不变、五个JVM代码路径无漂移、5364继承输入仅4个本批工具路径变化。
- 作者自审与独立Python算法分列，不冒充独立人员评审。首轮只读review因误写清单文件名而FileNotFoundError，旧脚本及 `failure-review01.json` 保留；修正路径后只读复核通过，未重跑业务。新代码文件≤200行、Python函数体≤30行，JVM函数逐项人工检查。

## 环境、限制和接续

665 connected/shutdown身份字节一致，认证停机；19375/19434–19436零监听，无本批Java/Gradle/MySQL残留。共享mysqld4632/5440身份未核清、仅观察；原TEMP AVD12912/qemu12108在5564/5565，ADB8500/5037，未操作设备。input09采样C/D余量 `83,009,773,568 / 23,650,549,760` bytes，后续精确状态见本根新快照；证据、DB、dump/binlog、临时目录均D盘，无清理。

本切片未运行Chrome、模拟器/物理PDA、APK、全普通回归、原Keystore/真实外置介质或D18最终全链；相关必跑组合继续保留。未改产品schema/迁移/网页，不新增Chrome通过记录。生产默认、七类决定、TEMP归档边界及 `localDeletionAllowed=false` 保持；不合main、不发布部署、不恢复取消链。

接续复杂引用及D13–D15组合、E07/E08剩余组合与E09实际候选；正常有效guard仍保护，D16整体退出标准未满足。总目标第8节未全满足，保持active。
