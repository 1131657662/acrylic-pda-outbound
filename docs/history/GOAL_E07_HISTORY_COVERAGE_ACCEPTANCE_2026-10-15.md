# E07/D14 历史覆盖与恢复后非空升级开发验收（2026-10-15）

本批在精确重建历史检查点后显式升级，再验证候选清单及真实历史损失窗口覆盖。只收口该开发切片；E07 L2、D14/D15 整体、E06 L2、D18 L3 和总目标仍未完成。历史开发来源、合成主管、开发签名及同机隔离恢复不代表原 PDA 或现场事实。

## 输入与影响

树 `D:/acrylic-pda-outbound`，分支 `codex/development`，运行输入 HEAD/实际远端均为 `68e9852a8691a63605b2a8709c86d4e12d706963`。唯一源码/数据库/看板/索引写者为本聊天 `01a13a57-8082-7dd2-a08f-987483dcda4b`；清理聊天 round3 仅拥有自身清理证据、独立报告及逐项协调的冷临时文件窗口，不写共享文件或数据库。

| 改动路径 | 影响层 | 必跑清单 | 复用依据 |
| --- | --- | --- | --- |
| `server/src/test/java/com/acrylic/outbound/e2averify/E2aReceivedCheckpointIT.java` | 指定历史覆盖方法 | 精确恢复核对后调用显式升级；新实例实际 HTTP/MySQL 正向和既有拒绝路径 | 只有 `verifiesOriginalHistoryCoverageForActualRecoveryLossWindow` 改变，原方法源码及前后 SHA 见 `e07-cross608/reuse-inputs.json` |
| 同目录 `E2aReceivedCheckpointUpgrade.java` | 测试用非空迁移断言 | 升级前后快照；原字段、文件和历史前缀守恒；精确权限/角色新增、revision 回填、新表空与必需表存在 | 原来源 `.local/e2a-cross-20260925-1233/`，393 文件 SHA 清单见 `e07-cross608/source-before.json` |
| `.local/goal-20261015/e07-cross608/` 消费者与保全 | 独立 Node/Python 回读 | 历史覆盖证明、137 表完整成员、原字节/回执、三项删改负例、JDBC/dump/storage、停机 hash | 原消费者副本、适配前后 SHA 见 `consumer-adaptation.json`；最终编码修正见 `release-consumer-encoding-diagnosis.json` |
| 本报告、索引、看板及旧结论归档 | 验收记录 | 引用、生成事实和全部改动/写权/进程/磁盘快照 | `finalize-input.json`、`finalize.patch`、`board-before-finalize.md`、`environment-finalize.json` |

本批证据根为 `D:/acrylic-pda-outbound/.local/goal-20261015/e07-cross608/`；本页仅写文件名的证据均相对该根，`e07-cross606/`、`e07-cross607/`、`e07-cross608/` 则相对其父目录。源码及以 `.local/` 开头的路径相对项目根。608 的 `impact-plan.json` 继承了 607 的编号文字；实际执行身份以 `input-before.json`、`correction-plan.json`、`run.ps1`、数据库身份和原 XML 为准，未把 607 失败改写为通过。33 个 worktree 核实无占用后分配 608；端口 19330/19434，datadir `.local/e2a-cross-20260925-608/data`。

## 实际验证与失败

1. **606 真实失败并保全**：恢复旧检查点后未显式升级即查询候选表，缺 `quarantine_origin`。未删去缺表清单或伪造空表；148 表/dump/storage 守恒，认证停机，363 文件 hash/长度不变。
2. **607 升级后测试断言失败并保全**：历史 TEMP submission 的 `assignment_id=null`；已有迁移通过 INNER JOIN 只回填有关联领取的记录，无关联记录保留默认 `operation_revision=1`。修正测试为分别核对默认值或对应 assignment.revision，未改产品/迁移。180 表/dump/storage 守恒，认证停机，390 文件 hash/长度不变。失败依据和计数留在 `e07-cross607/failure-diagnosis.json`。
3. **608 新实例实际业务通过**：运行 `powershell -NoProfile -File .local/goal-20261015/e07-cross608/run.ps1`，内部使用既有 `tools/r09-e2a-entry-rerun.ps1 -Run 608` 和精确方法选择。Gradle `BUILD SUCCESSFUL`，runner/test 退出均 0，原 XML `1/0/0/0` 且完成 marker 存在。恢复核对、真实 HTTP/MySQL 核对清单、实际 binlog 窗口覆盖、错误控制代次/来源摘要拒绝和全表不变均执行；普通写入、放行及本地删除始终 false。
4. **升级守恒**：原 146 张业务表的原字段、19 个私有文件及历史前缀不变；33 张新增业务表为空。四项预期权限的固定 UUID、名称和精确角色关联通过；`submitted_capture` 只追加 revision 列，逐行默认值/关联回填准确。版本事实只见看板生成块。
5. **独立消费者**：`node .../received-history-consumer.cjs 608 history-consumer` 实际消费 6 项输入通过。`node .../received-release-consumer.cjs 608 release-consumer02` 实际消费 11 项输入，通过 137 张声明表的精确成员/原行/digest、仓库绑定、固定公钥 RSA 回执和三项删改负例，`releaseAllowed=false`。首次 release-consumer 在最后读取 UTF-16LE BOM 的 PowerShell JSON 回执时失败；保留原目录和源码，仅补显式 BOM 解码后复验，未重启数据库或降低业务断言。
6. **独立 Python**：`C:/Python314/python.exe .../verify-independent.py` 回读原 XML、候选/来源文件、升级数据、消费者及三个实例的完整停机 hash。5,255 项候选输入和 393 个来源文件不变；606/607/608 各 363/390/405 文件不变。首个回读器误将规范化行摘要当作 TSV 文件字节摘要，原脚本/失败记录保留；改为独立计算排序行摘要并同时核 raw before/after 与行列数后通过。

作者与自审均为当前助手；Node/Python 是独立算法消费者，不等于独立人员。未修改产品 Java、Android、Web、Python、契约、迁移或生产配置。`reuse-inputs.json` 逐路径/前后 SHA 核实 2,354 个受影响依赖未变，唯一旧文件差异为指定 IT 方法；603 重建及 604/605 原归档/故障围栏按其正式报告和输入复用，未机械重跑。独立核验通过后仅按计划新增报告/归档、更新索引和看板；提交前全部输入差异、继承改动守恒及自审结果另存 `precommit-review.json`，不覆盖原核验结果。

## 保全、Git与后续

608 收尾 1 库、181 表，JDBC/dump/storage 守恒，认证 shutdown，19330/19434 零监听；dump SHA256 `f7a03e3dab7f838da0c0fb6cf5ae531ac922878789b4614c9f929f6592233146`。606 dump 为 `f047f75f5c6f84aaf3cf73a504ffe7b8404f8cb6720e112d9da8c9f1e175599a`，607 为 `824e4b2de3e835ad886a04a04a68a5c11524d80ca59a8ea74cbec7f62a91a847`；全部失败库/datadir/dump/binlog/原日志/XML/原件保留，不复用。

收尾磁盘 C/D 可用 `78,777,085,952 / 16,473,927,680` bytes，精确截点见 `environment-finalize.json`；无本聊天重型任务。实际进程/监听分别保存于 `processes-finalize.json`、`listeners-finalize.json`，共享 MySQL 只观察。全部继承 Android/UI/文档/工具/私有请求保持，明确路径提交推送开发分支，实际回执见 `e07-cross608/post-commit.json`，不循环自引用提交。

下一步在新隔离输入补原全局编号历史/重建对账的受影响范围；不恢复已取消的换机承接、多跳或激活。继续 E07 组合、E08 留存/UI、E09 和剩余 D/O/U；设备/Chrome审批、历史暂缓及外置介质/现场未验各自保留。七类生产决定、TEMP 生产归档边界和 `localDeletionAllowed=false` 不变。
