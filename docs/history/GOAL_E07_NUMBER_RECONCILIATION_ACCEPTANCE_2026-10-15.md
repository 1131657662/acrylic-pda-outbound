# E07/D14 全局编号历史与只读恢复对账开发验收（2026-10-15）

本批完成历史检查点精确重建、显式非空升级后的全局编号历史及只读业务对账。仅收口该开发切片；E07 L2、D14/D15 整体、E06 L2、D18 L3 和总目标仍未完成。历史开发来源、合成主管、开发签名及同机隔离恢复不代表原 PDA、现场实物或外部保管事实。

## 输入、决定与影响

工作树 `D:/acrylic-pda-outbound`，分支 `codex/development`；运行输入 HEAD 和远端均为 `ee98d4cd5aeb34b877aefdfad5c5b0e78e5da2ea`。本聊天 `01a13a57-8082-7dd2-a08f-987483dcda4b` 为源码、隔离库、看板及索引唯一写者。清理聊天 round3 已 `complete-released`，无在途任务或共享写权；回执见 `.local/disk-cleanup-20261015-round3/completion-receipt.json`，独立报告见 [D盘第三轮清理](D_DISK_ROUND3_CLEANUP_2026-10-15.md)。全部继承 Android/UI/文档/工具及私有请求改动保留，不随本批暂存。

证据根 `D:/acrylic-pda-outbound/.local/goal-20261015/e07-cross610/`；下文未写完整路径的证据名均相对该根。新库 `.local/e2a-cross-20260925-610/`，schema `acrylic_e2a_cross_20260925_610`、MySQL 19330、HTTP 19434；33 worktree 定向检查无同号目录、端口无监听后分配。原来源 `.local/e2a-cross-20260925-1233/` 的 393 文件独立固定，不复用失败库。

| 改动路径 | 影响层 | 必跑清单 | 复用依据 |
| --- | --- | --- | --- |
| `server/src/main/java/com/acrylic/outbound/recovery/RecoveryReleaseSchemaCompatibility.java` | 闭合恢复只读对账 | 精确历史 schema 兼容、未知/非空/原字节变化拒绝、真实非空升级和业务对账 | `impact-plan.json`；未改迁移、契约或权限入口 |
| 同目录 `RecoveryReleaseScopeSemantics.java` | 对账全部调用者 | 兼容单测、真实 HTTP/MySQL 编号/对账、语义负例、跨仓尾事务回滚、相关恢复回归 | `reuse-inputs.json` 逐路径/前后 SHA；旧原表守恒及未知业务拒绝逻辑保持 |
| `server/src/test/java/com/acrylic/outbound/e2averify/E2aReceivedCheckpointIT.java` | 指定编号历史方法 | 精确历史恢复核对后显式升级；全局编号/错 pin/逆序/跨仓变化及完整保全 | 仅 `reconcilesGlobalNumberHistoryAcrossOriginalCheckpoint` 新增升级调用；升级断言沿 [608报告](GOAL_E07_HISTORY_COVERAGE_ACCEPTANCE_2026-10-15.md)和输入 SHA 复用 |
| `server/src/test/java/com/acrylic/outbound/recovery/RecoveryReleaseSchemaCompatibilityTest.java` | 兼容行为 | 17 项实际执行及相关 54 项回归 | 原 XML、`unit-result.json`、`regression/` |
| 本机独立 Node/Python 消费者 | 原件、成员、摘要、停机事实 | 实际消费、独立重算、删改负例、输入前后 SHA | 消费者源码/结果及输入清单保留在证据根 |
| 报告、看板及索引 | 断点和验收入口 | 旧结论归档、版本事实、全部改动/hash/写权/进程/磁盘及暂存核对 | `finalize-input.json`、`finalize.patch`、`environment-finalize.json`、`precommit-review.json` |

自主决定：只接受七张明确历史缺表在精确列顺序且为空时的升级效果；旧 `submitted_capture` 仅接受追加 `operation_revision`，值由独立固定的原 submission/assignment 推导，无 assignment 时为原迁移默认值，有则精确取原 revision。原快照不改，缺失/重复引用、未知列、非空新表和原字节变化继续拒绝。这样可消费真实非空旧检查点，同时保持不从候选声明推断原事实；不新增 API，不开放生产能力。

## 实际验证与原失败

1. **609 产品失败**：`RECOVERY_SCOPE_COLUMNS_CHANGED_quarantine_origin`，同根因计数 1；精确恢复、升级、故障围栏已产生隔离写入，按完整保全处理，未走无业务写入的快速通道。原 XML、日志、datadir/binlog、404 文件 hash 和诊断保留于 `e07-cross609/`；1 库/181 表、dump/storage 守恒，认证停机且端口零监听。修复后新分配 610，未复用 609。
2. **610 实际业务通过**：`powershell -NoProfile -File .local/goal-20261015/e07-cross610/run.ps1`，原 XML `1/0/0/0`、完成 marker 存在，runner/test 退出均 0。精确原检查点恢复→显式非空升级→主管故障围栏→全局编号历史→只读对账实际执行；`allocatedInWindow="1"`。错误 counter、错误 pin、逆序和跨仓订单变化拒绝，12 项语义负例和 2 项跨仓末尾事务回滚通过；原 146 表字段、19 私有文件、迁移历史前缀不变，33 新增业务表空，四项预期权限及精确角色、capture revision 回填准确。版本事实只见看板生成块。
3. **定向及回归**：兼容单测 17/17；`run-regression.ps1` 实际执行 9 个测试类共 54 项，失败/错误/跳过均 0，Gradle 退出 0，含兼容 17 项，不相加重复计数。范围为 `RecoveryRelease*`、`RecoveryRecoveredTemporaryArchiveTest` 及恢复确认命令/配置；命令、原日志/XML、退出码见 `regression/`。
4. **Node 消费**：`node .local/goal-20261015/e07-cross610/received-release-consumer.cjs 610 release-consumer`，11 输入、137 表精确成员/仓库/原行及固定公钥 RSA 回执通过，3 项删改负例通过。`release-consumer/inputs.json` 与消费者原件保留。
5. **Python 原件/保全消费**：`verify-independent.py` 实际核对 5,262 候选文件、393 来源文件 SHA/长度不变，非空升级精确效果、原 XML、dump/storage及609/610停机文件通过。独立编号/对账消费者 `independent-number-reconciliation.py` 实际消费双固定 pin 原快照、原权限 begin/end/binlog、规范 JSON/摘要、全仓订单/137 原始表，重算全部绑定；3 项改数/pin/实物状态负例通过。结果 `number-reconciliation-consumer-result.json` 同时绑定 54 项回归原 XML。首次消费者多余断言误将 snapshot.schema 定义集合当成库名，保留 `number-consumer-attempt1/` 后改为校验实际定义集合；未改产品、重启库或降低摘要断言。
6. **运行器预检原件**：610 脚本初次编号替换误改日期；原错误脚本/输入均封存在 `preflight-attempt1-wrong-date/`。修正后重新核实实际 `20260925-610` 路径、33 worktree 和端口，正确证据为 `input-before.json`、`source-before.json`、`preflight-environment-corrected.json`。原 `preflight-environment.json` 是错误预检截点，不作正确路径证明。

独立重算的编号历史 SHA256 `7d28d8dca9b5c3aa822796729fa2687869a18caeb9bfbb3e44b47c4fa9bca9b5`，清单 SHA256 `f7e74fa77a220745cf2538a51dcacb540bbf3d1506846088cccd5b76b3a87ca5`；对账原行 SHA256 `3b08c79273e65e28ca877879a86988654509cbc224c78b2a3446741e5c7a914f`，全仓 2 订单摘要 `13bf801512b33f53bab0373afb644d59f9c8d5a0124953cc068eaef5a7b88884`。普通写入、放行与本地删除始终 false，实物状态仍 `UNCHECKED`，`PHYSICAL_REVIEW_REQUIRED` 保留。

作者与自审为同一助手，Node/Python 是独立算法消费者而非独立人员。`reuse-inputs.json` 对 608 输入的 3,718 个产品/测试/工具路径核对 SHA 未变，旧路径仅两处有影响差异，另新增兼容器及其测试。Android、Web、迁移与 Python 产品未改，本切片无新增网页/Android入口，按路径+SHA复用相关既有报告；不机械重跑无关客户端/Chrome链。已复核严格列允许集、原引用唯一性、规范行序、重复行保留及未知业务拒绝，无生产开关变化。

## 保全、开发备份与继续

610 为 1 库/181 表，完整 JDBC/dump/storage 前后守恒，认证停机、19330/19434 零监听，408 停机文件 SHA/长度一致；dump SHA256 `259ce0557c23c2fd0bc08d3d3f6a13a7e580c9e80552bf6dcd2fc1f3bd3f0dea`。609 dump SHA256 `92da50d29f415c704fb586436b1351374510bc8b9b7838b1874a3859eaa02caa`。全部失败库、原日志/XML、dump/binlog与原件继续保留，不复用。

收尾 C/D 可用 `78,771,994,624 / 67,907,416,064` bytes；精确进程/监听/磁盘截点见 `environment-finalize.json`，无项目 Java/Gradle/隔离 MySQL 在途，共享 3306 只观察。旧看板结论归档为 `ORCHESTRATION_STATE_before_e07_number_report_2026-10-15.md`，此前两个诊断归档继续保留。明确路径暂存自审后按开发备份提交推送 `codex/development`，实际回执见本机 `post-commit.json`，不以循环自引用制造提交。

下一步继续 E07 受影响恢复组合，随后 E08 留存/UI、E09 和剩余 D/O/U。实物/主管/运维资格链不在本切片内，已取消的接收者/换机承接/多跳/激活不恢复；外置介质、设备/Chrome审批和全部历史暂缓按原范围保留。七类生产决定、TEMP 生产归档边界与 `localDeletionAllowed=false` 保持关闭；没有据此记 E07 L2、D18 L3 或总目标通过。
