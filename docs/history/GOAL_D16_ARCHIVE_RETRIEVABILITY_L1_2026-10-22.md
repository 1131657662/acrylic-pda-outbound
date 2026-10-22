# D16 归档原件可取回前置检查（有界 L1）

## 结论与适用边界

在输入提交 `7273af246da070359b133bdcf572018082e4400c` 上补强既有隔离 Python 整单消费者：消费时必须实际读取显式保存的归档正文及完整原签回执，核原字节、签名绑定摘要及长度。只对同机 `.local/` 合成文件和 SQLite 副本成立，不代表云端实时健康、异地备份、真实原身份、Android 产品删除链或 D16 整体验收。版本事实见看板生成块。

定向 `targeted01` 实际 **14 tests / 0 failures / 0 errors / 0 skipped**；完整工具回归 `full01` 实际 **127 / 0 / 0 / 0**，两次进程 exit 0。127 已包含这 14 项，不累加为 141。作者、执行和自审为同一助手；另用不调用删除消费者的只读算法核对 15 个实际合成提交 run 及 8 份恢复副本，算法独立不等于独立人员验收。

总目标保持 active、未完成；此片不关闭 D16、D18 或第 8 节。`localDeletionAllowed=false`，有效目标 `work_guard` 仍拒绝清理，不能删 guard、改状态或只凭日龄制造正向资格。普通构建不替代业务验收。

## 问题、实现及自主决定

既有消费者验历史归档签名和完整 SQLite 备份，但未强制重读独立保存的正文及完整原签回执。历史确认不能证明当次文件可取回。依总目标第 3/3.1 节既有自主授权，选择有界补强，决定与排除方案保存在证据根 `design.md`。

- spec 新增必填 `archiveFiles`，仅允许 `payload` / `receipt` 两个键，必须为两个不同的项目 `.local/` 绝对路径；复用原路径、大小、链接/reparse/hardlink限制。
- `retrieval.py` 实际读取原文件，逐字节比较原正文及 canonical 完整原签回执，同时核验已验签归档所绑定的 SHA 和字节数。
- `proof.py` 在 prepare 引用检查中调用该检查；`consumer.py` 在实际取得 SQLite 写锁之后、最后 DELETE 之后 COMMIT 之前再验。重放也要求当次原文件可读。
- 缺文件、字段、路径或字节漂移拒绝；事务内失败全部回滚。旧 spec 缺字段拒绝，须新建 spec/run，不改旧计划追认。
- 测试工厂每 case 创建独占合成文件，新增显式带 SHA 的测试夹具入口；没有新增产品 handler、URL、凭据或生产资格。

最后一次读取之后的外部变化仍不受 SQLite 事务控制；不声称文件与数据库跨资源原子性或未来可取回性。正常成功订单的有效防重尚无本片合法退出方案，保持 fail-closed。

## 输入来源与字节绑定

证据根：`.local/goal-20261022-resume/d16-retrievability01/`。命令、参数、进程、环境、完整 Python 源文件 SHA、退出码及原日志按 `targeted01/`、`full01/` 分开保存。运行器 `run-tests.py` 将所有新增 case 和 TEMP/TMP 放在本片 D 盘目录；没有重跑旧业务 IT。Python/SQLite/cryptography 的实际运行版本及 schema/原生触发器来源见 `dependency-binding.json`。

主树 `server/build/pda-archive-fixture.json` 实际为 4 字节占位，SHA `e3566b3a06430868d71e9287dfd6c6c520a3da027aabea01951d407ee131dc2f`，未覆盖。由历史成功合成 SQLite 及其旧 manifest 核定原 body/receipt/archive/key 后，保持原签对象另封装新 JSON 并验签：

| 输入 | SHA-256 |
| --- | --- |
| `.local/goal-20261016/d16-whole-order01/tests04/case-7b67eea9/synthetic-source.sqlite` | `5f08c6d7b03543b0e7149693f907c606f0bce5a07bf0112a9d8140dd5ac37774` |
| `.local/goal-20261016/d16-whole-order01/evidence-manifest01.json` | `0bb2113f2db511df37533b5c227d37248c9cac10bfe65d2985a9ac004ef3c366` |
| 本片 `derived-archive-fixture.json` | `c7f277314eaf9b3df2a85d241d4f980a15d10d485f980bad8576fecc36987c23` |
| 固定公钥对象 canonical JSON | `719de17ecfb23a34408313dfb6216d38b54bcf1de52fc83e53bf682c398cf73b` |
| `.local/goal-20261016/d16-audit-reference01/input02/audit-source.sqlite` | `9f6e2546288ff6a9b106c4590f3d9eabe0b432ca36abf9a070fd16c1d890667d` |
| 审计固定公钥对象 | `9402b43dbfa3d72e7bdfb78c18cc1cf69bbe514fe96b1ff0d671c79d52604077` |
| `.local/goal-20261016/e07-formal-retention01/retention02/synthetic-source.sqlite` | `d39bdce4bf3b5b8bdd4936375ac9bc836600007532083a1b08ff96dd08cbef4b` |

完整关系见 `derived-fixture-input.json` 及两次 `command.json`。新 JSON 容器不同于历史 fixture，不声称历史 fixture 原字节相同；没有重新签发原对象或声称原 PDA 身份。审计/恢复源按各自原 pin 核定，逐 case 新副本；历史源未修改。

## 验证结果与输出

实际命令及隔离环境以两次 `command.json` 为准；分别执行 `python -m unittest -v tools.pda_retention.tests.test_retrieval` 和 `python -m unittest discover -s tools/pda_retention/tests -t . -v`。原日志记 unittest 用时分别 10.100s、105.634s；进程外层计时见 `exit.json`，不可混用。

| 运行 | 实际结果 | unittest.log SHA-256 |
| --- | --- | --- |
| targeted01 | 14/0/0/0，exit 0 | `fee52217554aec1db8d12feb0032953f039330a9bc7ef4a3c4fb3079504dce2c` |
| full01 | 127/0/0/0，exit 0 | `cb503688b547a5755b575c11278fa200d36d240976a4e02c852e69d2ab89bdda` |

新增反例覆盖缺字段/文件、非法角色/类型/相对或外部路径、同路径、正文或回执空白漂移、prepare 后漂移、真实写锁等待时丢文件、重放丢文件、有效目标 guard 拒绝。末 DELETE 时由 SQLite authorizer 使文件不可用，最终复核拒绝并核所有原事实回滚。完整回归还覆盖 default-off、许可撤回、期限、schema/原生触发器、防重/审计引用、并发、末 SQL 失败及恢复组合。

只读 `audit-results.py` 对实际结果文件定位 15 次 `COMMITTED_SYNTHETIC_ONLY`：完整 backup 与 plan.before 相同，候选全表差异恰为 selected 移除，`work_guard` 全部不变；8 份实际恢复副本与备份全表相同。`result-summary.json` SHA `ef131d55ace01a3fec6e2eb1d8cd0d4e895135484af9fd91b1b0853a6ef7748e`。这不是新增 15/8 个 unittest，也不是原身份验收。

源码运行前后 SHA 核同，最终待提交源码再次核同运行输入；`code-review.json` 记录 5 个 Python 文件的大小/函数边界和安全自审。schema 与四个原生触发器依赖没有单独列入运行器 sourceFiles：由切片前不可变完整提交导出、baseline 无相关脏路径、单写者及运行后规范化换行核对绑定，不能声称存在更早的逐依赖运行时快照。

## 保全、偏差及磁盘

`baseline.json/inherited-wip.zip/status.z/tracked.patch/staged.patch` 保存起点 40 条展开继承 WIP。`pre-edit.zip/preimages.json` 保存已列改前字节和新文件不存在状态；初始列表漏列 `proof.py`，未伪称完整：其旧 CRLF 提交导出仍在 `.local/s22s/tools/pda_retention/proof.py`，SHA `4a2bce14b64f3973528c8dca3872d4dcd2e4ab5e02895878e62733c537012585`；撤回两处新增行所得 LF 重构 SHA `96fe21398c2883b9f700121e2e3a7c51edbf275461014ad9f94622369a5b4b69`，仅规范化换行后相同，不是原字节相同。说明见 `preimage-proof-note.json/proof-original-archive-binding.json`。

原字节审计一次因 CRLF 断言失败，修正只读比较说明；shell/Python 转义、收口脚本纯语法错误及 README patch 定位失败均未形成产品写入，纠正后继续，没有据此重跑业务。收口补充命令曾因 NUL 被执行器拒绝，尚未创建进程，修正为 bytes([0]) 后保全成功；换行传输错误改用 chr(10) 后完成断点写入。失败/旧日志保持。

停机后 fresh 核无本片消费者，仅对本片 targeted01/full01 的 419 份 SQLite 副本做无损 NTFS 压缩：逻辑字节 445,329,417，减少分配 **402,075,657 bytes**，删除 **0**，全部长度/SHA 不变；419 条 compact 命令 exit 0。D 盘余量 **38,409,457,664 → 38,811,156,480 bytes**，卷余量受其他活动影响，不当作精确文件释放值。计划/命令/结果见 `compact-plan.json/compact.log/compact-result.json`，结果 SHA `ab5f0e20d7ddaa479b973f2b6c0f116e0ad686e633beee0ece56f87fc5a07bf3`；不与前片压缩重复累计，不重试既有删除审批拒绝。

## 未验与接续

没有新增 HTTP/MySQL、APK、设备或现场验收；MySQL80 仅观察进程/监听，未 SQL 连接，内部库身份未认证。ADB 仅枚举，未操作设备、AVD 或 Keystore；另一清理聊天不承担源码写权。继承源码、原资料/身份/签回执、唯一证据及最终 APK 保持。

下一步定向核 Q12 下正常有效 guard 的合法长期保护/退出语义及复杂目标引用，不以删 guard 造资格；再补 D13–D15 的可取回和恢复消费者组合，按实际输入独立留证。D18 及其余矩阵缺口不由本片覆盖。四状态只读筛选已实现并验证，本片不重复。main、生产开关、七类生产决定/TEMP归档、正式签名、原库/现网、部署和 Release 继续关闭；换机承接、多跳、接收者确认/激活和旧 run03 不恢复。

提交/推送回执及最终 tracked/untracked 全 SHA、继承守恒、环境/监听/CDE 断点保存在本片 `closeout/`；看板只保留短断点，不把开发备份计为验收进展。
