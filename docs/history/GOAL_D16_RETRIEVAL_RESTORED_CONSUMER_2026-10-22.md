# D16 可取回归档与完整恢复副本消费者组合

输入提交 `9cb6b1c8a4460d093abd92b58c2ff1fc640d0e18`。本片只新增测试，将[前片可取回文件检查](GOAL_D16_ARCHIVE_RETRIEVABILITY_L1_2026-10-22.md)与原恢复 FORMAL、两代审计和完整原生触发器组合，实际 **4 tests / 0 failures / 0 errors / 0 skipped**，exit 0。测试与前片127项属于不同运行，不拼成同次131项。产品、Python消费者行为、契约、数据库结构和开关均未修改；版本事实见看板生成块。

## 判断与范围

再次核定 Q12、`WorkCorrections.kt`、`ScanDao.kt`、`BarcodeWorkGuard.kt`：正常 FROZEN 成功单没有既有合法 guard 退出入口；原 release 仅在 SCANNING 下的受控 VOID 中使用，云端库存状态是额外限制，不能释放本地 guard。保持原已定政策，不删 guard、不改状态制造清理资格。本片不声称解决正常带 guard 目标的整单清理。

前片简单合成目标已验证文件丢失，完整回归也执行过恢复正向；仍缺新文件前置检查在复杂恢复输入上的重新准备/消费和故障交互。本片使用原 `RestoredFormalCase` 工厂：独立 guard-free 合成目标 + 原两代审计依赖 + 历史恢复 FORMAL 正常单及其两个有效 guard，并完整安装原生触发器。全部来源都是隔离合成材料，不冒充原身份或同一现场数据库。决定见证据根 `design.md`。

## 实际验证

新增唯一代码路径：`tools/pda_retention/tests/test_restored_retrieval.py`。两个故障方法直接复用前片断言体，但由恢复组合工厂构造不同输入；没有复制/放宽消费逻辑。

| 方法 | 实际行为及断言 |
| --- | --- |
| 恢复备份后重新CLI消费 | 先消费原候选，完整备份恢复到新文件；重新绑定恢复文件 SHA 并通过真实 CLI prepare/consume，使用同两份原归档文件；候选差异相同、重放无写入，再次完整恢复核同 |
| 恢复后归档回执文件丢失 | SQLite 仍含完整原签回执，但外部文件不可读，新的 prepare 拒绝，不生成新 plan；恢复文件全表与原正常单保持 |
| 实际写锁等待时丢文件 | 真正等待 SQLite 写锁后拒绝；复杂候选所有事实保持，包括四条有效防重和两代审计 |
| 末DELETE后丢文件 | SQLite authorizer 在最后受控 DELETE 时使文件不可用；最终复核拒绝并全部回滚，原生触发器与防重保持 |

命令：`C:/Python314/python.exe -m unittest -v tools.pda_retention.tests.test_restored_retrieval`。原日志记 5.418s，外层进程计时 5.726867s；没有据此估算生产恢复耗时或 RPO/RTO。

证据根 `.local/goal-20261022-resume/d16-retrieval-restore01/`：

| 证据 | SHA-256 |
| --- | --- |
| `combination01/command.json`（完整参数/环境/源码和历史输入SHA） | `7e2ee2d78e0a5bd10f8df9501295995e181cd329a4cfda16237a83779b23cbff` |
| `combination01/exit.json` | `d42be0f02209a573086c18be89d32a0654b3bf5640b14965dc61d29356efb2a9` |
| `combination01/unittest.log` | `6274e264734c31da15aadcaf96ea98a23a8d462e3720f0ae3410d650c9fb34ce` |
| `audit-summary.json` | `7f18d282d3a018797e4cdc3e71418417f23bca1bc3dedfa0bcb64dba48d919a0` |

历史基础源、审计源、恢复 FORMAL 源和新夹具容器均沿前片原 SHA 核定，完整关系在 `command.json` 及 `derived-fixture-input.json`；没有覆盖4字节旧占位或重签原对象。运行前后全部工具 Python 源码及历史输入 SHA 相同，schema/原生触发器5依赖再次与前片 `dependency-binding.json` 核同；该绑定的先前采集局限仍按前片报告，不伪称新增更早快照。

另用 `audit-results.py` 只读比较实际5个有 plan 的 run：其中2次实际合成提交精确移除 selected，3个未提交候选保持全部事实；每个输入均有4条有效 guard 且保持，schema/trigger SQL 前后一致。3份完整恢复文件的全表和结构与原合成组合相同。首次内联算法已保存摘要，随后保存可复现脚本并重读核同，未再次调用删除消费者。作者/执行/自审同一助手，算法独立不代表独立人员验收。

## 保全与清理

`baseline.json/inherited-wip.zip/status.z/tracked.patch` 保存40条展开继承WIP；新增测试和两报告原先不存在。`docs-before.zip` 保存本片索引/看板原字节，旧叙述另见[切片前看板](ORCHESTRATION_STATUS_BEFORE_D16_RESTORE_CONSUMER_2026-10-22.md)。历史索引仅暂存本片入口，继承内容不纳入提交；看板本地维护。

运行结束后先核进程：发现其他项目 `D:/weiqi-coach` 的 Python/MI6 消费者，首轮保守清理前置返回9，没有清理；核其命令范围与本片完全分离后，仅压缩本片独占的19份已结束 SQLite 副本。未终止或操作其设备/进程。MySQL系统服务仅观察，未连接 SQL、未认证内部库身份。

无损 NTFS 压缩逻辑字节21,245,952，减少分配 **18,522,112 bytes**，删除 **0**，全部长度/SHA保持，19条 compact命令退出0。D余量 **38,783,909,888 → 38,802,411,520 bytes**，卷变化受其他活动影响，和文件分配减少分开记录。完整计划/命令/结果见 `compact-plan.json/compact.log/compact-result.json`，不与前片402,075,657字节重复累计。原资料/身份/Keystore/签回执、唯一证据、最终APK及全部继承改动保持，不重试既有删除审批拒绝。

## 未完成与接续

只证明同机显式合成归档文件、完整SQLite备份恢复及受控消费者的有界组合。没有云端/异地实时可取回证明，没有HTTP/MySQL、APK、设备、现场或实际最终候选验收；文件与SQLite无跨资源原子性保证。复杂目标仍保护拒绝，正常有效guard退出尚无合法产品路径。

下一步从原生生产者的归档/恢复输入契约和 D13–D15 跨入口矩阵，选取尚未覆盖且不依赖已取消链的独立消费者；补实际候选覆盖不能靠本片或历史局部拼接。D16整体、D18与第8节均未完成，总目标保持 active。`localDeletionAllowed=false`；七类生产决定/TEMP生产归档、正式签名、原库/现网、main、部署/Release关闭；换机承接、多跳、接收者确认/激活与旧run03不恢复。
