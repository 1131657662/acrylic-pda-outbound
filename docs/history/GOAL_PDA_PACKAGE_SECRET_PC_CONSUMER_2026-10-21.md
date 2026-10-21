# O02 独立解密材料：电脑离线消费者开发备份

## 结论与适用范围

电脑离线核验命令增加显式 `--secret-file`，消费 Android 生成的独立解密材料。材料仅提供解密口令；仍须通过原包完整性、原事实和包外登记公钥核验，再核对材料的原订单字节摘要及来源/本地订单/设备/操作员/仓库绑定，最后复读材料，全部满足后才输出成功或发布核验副本。未指定该参数时保留终端隐藏口令输入。没有接入中转、回执或网络写入链。

这是合成输入的有界 Android 主机→电脑文件消费者证据。Android 生产者实际 1/0/0/0；电脑 `cli-consumer07` 同次执行 26 项，24 通过、2 跳过、0 失败/错误，exit0。新增 Windows junction 专项另行实际执行 1 项、零跳过、exit0；不能把两个轮次拼成一次 27 项全过。普通符号链接两项仍因 Windows 权限未执行。

不代表完整 O02/O05、D17/D18、原身份/实际 Keystore、真机外置文件通道、物理保管或最终候选及总目标第8节通过。构建、开发备份、索引和清理均不提升这些结论。

## 输入、范围及保全

- 固定主树 `D:/acrylic-pda-outbound`、`codex/development`，输入 HEAD/实查远端 `2fc5483e83691c3a3cd9fe6fe3336aaf112c8fd8`。版本事实只见看板生成块。
- 证据根 `R = .local/goal-20261021-resume/package-secret-cli01/`。原有 `design.md`、`preimage.zip/json`、`wip-before.zip/json`、`scope-final-plan.json` 保留设计、原字节和明确路径。
- 本片产品源码仅 `tools/pda_rescue/cli.py`、`secret_file.py`；新增两类 Python 测试及 Android `RescuePackageSecretVectorTest.kt` 文件生产者，工具说明和报告/索引随片收口。Android 产品、Room、接口和加密格式无改动。
- `closeout01/wip-before-docs.zip/json` 保存本次收口前43个展开脏文件，逐成员哈希回读核同；另保存进度索引原字节。ZIP SHA-256 `75b1f33dbc4ce07f6b9d028d0d049b4590976c029b6d7084c57cd1ee3ae50481`。
- 33项继承保护基准逐 SHA 核同，见 `protected-before.json` 和 `closeout01/protected-check.json`。另一个清理聊天结束并转 idle 后才补 junction 测试和编辑本片文档；其报告、清理脚本和看板新增记录保留。
- `version_facts.ps1` 的 `dirty_entries` 使用 Git 默认折叠目录口径；42条记录与 `--untracked-files=all` 展开的43文件来自归档目录折叠，不是漏记一份文件。最终快照仍保存两种口径。

### 原文件链

| Android 生产者原文件 | 字节 | SHA-256 |
| --- | ---: | --- |
| `android-producer03/vector/android-room.pdarescue` | 7928 | `5045fdbafe7258f9c552d9f509b5a14d4444d006d48f661270f18623dd4e5421` |
| `android-producer03/vector/external-trust.json` | 304 | `302483be0a3526f216aa9d80862082da3c76feb3c0523b24d43078244c907a1c` |
| `android-producer03/vector/package-secret.json` | 775 | `3c06a214de9c9641ea3d413048044f5aa9370dc03af0b85f30d1fbdeca73a768` |

解密材料含合成敏感口令，仅保留在本机私有证据目录，不提交材料或解密原件；登记材料也是合成测试信任输入，不冒充真实登记。原三文件在 CLI 前后及此次收口复核时均核同。

## 实际执行与失败分列

| 轮次 | 实际结果与依据 |
| --- | --- |
| `android-producer01` | 发现另一个获用户授权的构建在途后中断自己的进程；无 XML/向量，不计测试通过或产品失败。见 `interruption.json`。 |
| `android-producer02` | exit1，1项 SQLiteCantOpenDatabaseException，未产生向量；原日志/XML保留。 |
| `android-producer03` | 缩短任务子进程 TEMP/Java 临时路径，新隔离源/输出；原 XML 为1项、0失败/错误/跳过，exit0，产生上表原文件。6011源/辅助成员与清单核同，缺失/多余/不符均0。 |
| `cli-consumer02` | exit1，编码相关异常，原 stderr/结果保留，不记通过。 |
| `cli-consumer03` | exit0、26项，含2项普通符号链接跳过；原输入前后核同。 |
| `cli-consumer04` | 实际 CLI `--secret-file --trust --require-trusted --out` exit0；14个核验输出成员均未包含口令，`order.json` 为363字节，原摘要核同。 |
| `cli-consumer05` | exit1，缺少 `PDA_RESCUE_SECRET_VECTOR_DIR` 导致10个 setUp 错误，2跳过；输入/源码不变，不作业务结论。 |
| `cli-consumer06` | exit1，子进程控制台编码与 UTF-8 解码不一致导致11个错误，2跳过；输入/源码不变，不把运行器问题归为产品拒绝。 |
| `cli-consumer07` | 显式 UTF-8 子进程环境，26项中24通过/2跳过，exit0，原输入及43个 Python 源文件前后核同。 |
| `junction-review01` | 新夹具误用 PowerShell `New-Item -LiteralPath`，错误输出解码异常，实际1项跳过；exit0不等于通过。原测试字节、stderr和结果均保留。 |
| `junction-review02` | 改用环境参数传入路径、`New-Item -Path`、原始字节捕获；Windows 上夹具创建失败改为断言失败。实际1项通过、0跳过、exit0，原生产读取器无需修改。 |

Android XML SHA-256 `34896eb783869b0dfe000ace66b419c3cbd94afda90110face9e30c52f3a1f02`。独立 CLI 输出指明 `integrityVerified=true`、合成登记下 `sourceTrusted=true`、`businessReceiptVerified=false`；原包含1条有效扫描、300500整数克，不能据此确认出库或放行。

命令、环境、退出码见各轮 `command*.json`/`execution.json`/`result.json`；`closeout01/evidence-manifest.json` 保存24个关键结果/原日志/XML的哈希，清单 SHA-256 `6461b0d31ded46fb7313b66a6d731345bf08dd47aedb613ca21f507ef2be490f`。本片没有重新运行既有设置用例或已通过的筛选消费者。

## 行为与安全核对

- 输入最多4096字节且必须单链接普通文件；拒绝路径及祖先符号链接/reparse point、目录、硬链接，读取前后核文件身份/大小/时间及材料原字节。junction 正向夹具确认拒绝；普通符号链接权限缺口如实保留。
- 严格 UTF-8/重复键/字段集合/类型、用途、随机口令形状、原订单摘要、UUID和范围校验。材料不供给登记公钥、不充当救援身份或业务权限。
- 真实 CLI 覆盖非空包、每个范围不符、错摘要/口令、缺/错包外信任、核验期间材料变化、发布前拒绝、重复目标防覆盖、成功副本排除材料/口令及旧隐藏输入兼容。
- `cli.py`、`secret_file.py` 和 CLI 测试与 run07 输入逐字相同；材料测试仅增加 import/夹具和 junction 方法，原17个方法的 AST 逐一核同，见 `closeout01/python-source-reuse.json` 与 `test-ast-reuse.json`。不将新增方法之后的文件冒充 run07 原始字节。
- 原生产者主机测试文件 SHA-256 `30171f167fa3d0ebcc5413e93004503dad07239166aa55533418975dc37bb665`；本片未改该文件或其他 Android 源码。没有重新构建、安装或覆盖 APK。
- 自审由当前作者完成，Python 文件消费者是独立运行时消费者，不宣称独立人员审查。

## 环境、清理与接续

只枚举 M71 设备；没有运行模拟器、Gradle或业务服务。系统 MySQL 监听的 datadir 身份未连接核实，继续只观察、不连接/停止；仅对既有 debug Keystore 核哈希，正式签名不读。子进程临时路径在 D 盘任务目录，未改全局环境。

本次收口仅新增小型测试/文本与保护快照，临时合成文件由测试清理；未对旧缓存/镜像/失败目录发删除命令，清理释放按0登记。并行清理聊天的压缩及空间增量只引用其独立报告，不计入本片。原失败日志/XML、原材料、APK和唯一证据保留；不绕过已有自动审批拒绝。

本片开发备份范围为明确9路径；历史索引只包含本片新增条目，继承的 M71 诊断条目保留在工作区。本报告此前预写了尚不存在的 `commit01/` 与 `postcommit01/` 回执路径，收口时按实际 Git 结果补齐本地回执并更新后置WIP快照。提交不计验收进展。

下一步先核本片报告/哈希及看板：凭据交互仍缺分用途复用/真实外部保管及设备通道、实际最终候选组合；按现有政策选一个独立开发缺口，不重跑本片已有通过结果。M71 领取预览问题继续按看板优先级和单独证据接续，不从客户端猜仓库标识，不操作其原订单/会话。

总目标保持 active、未完成。`localDeletionAllowed=false`、七类生产决定/TEMP生产归档 fail-closed；换机承接、多跳、接收者确认/激活链及旧 run03不恢复。原库/现网、正式签名、main、部署、Release及产物发布继续关闭。
