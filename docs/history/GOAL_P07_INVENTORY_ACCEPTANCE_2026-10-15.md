# P07 原签全集封存 L1 验收

本批补轮换前签名全集及独立Python消费。只计离线工具L1开发备份，不计D13–D15全部、E07 L2、D18 L3或总目标。版本事实见看板生成事实块；显式恢复入口及外部保管/引用仍未完成。

## 输入、决定及影响范围

固定树`D:/acrylic-pda-outbound`、`codex/development`，输入HEAD/实际远端`d8bd20eb8dcfdcf5598c09b350b996f39f558758`。唯一写者为本聊天，未创建子代理。接续79项原字节、hash和旧看板见`.local/goal-20261015/p07-inventory01/`；其中78项继承改动保持原字节，不混入提交。上批[中止诊断L1](GOAL_P07_INTERRUPTION_ACCEPTANCE_2026-10-15.md)及失败原件继续保留。

自主决定见`p07-inventory01/decision.json`。问题是当前与原签备份同时缺少同一process时，剩余单条HMAC不能证明集合完整。采用持P3协调锁、核验原身份/所有写者/空租约并完整复制后，在首替换前以旧钥HMAC封存全集摘要；不新增运行期多钥，不改变单条格式。选择摘要而非在单JSON列全部记录，避免中等历史规模突破规范JSON上限。原记录数量/字节数和按路径排序的规范描述连续SHA-256均入清单，绑定原marker/固定身份/两代钥ID；成功回执另含清单SHA。

改动路径→影响层→必跑：

- `RecoveryLeaseRekey.java`、`RecoveryRekeyInventory.java`、`RecoveryRekeyCli.java`：离线轮换和回执，实际跨JVM正向、活动writer/lease拒绝、同请求并发/重放、尾替换回滚及真实强制终止必跑；无HTTP/schema/Android入口变化。
- `tools/recovery_rekey_verify.py`、`recovery_rekey_inspect.py`、`recovery_rekey_inspect_state.py`：实际Python供应回读和只读诊断，全集重算、双侧缺件、篡改、错绑定/计数、空process集合、旧无清单兼容及回执降级拒绝必跑。
- `RecoveryRekeyInventoryIT/InspectFixture/PythonIT/CrashIT`：真实产品文件、Python/JVM、合成历史和本测试子进程终止；不冒充现场身份/历史规模。
- `RECOVERY_REKEY_RUNBOOK.md`：规范、上限、保管及所有分类停写边界。

复用依据：前批2005项回归的非离线轮换运行期范围按`p07-inspect03/source-input.json`与本批`source-input.json`核对；启动/锁/default-off及直接轮换范围本批重新执行。无业务路由、迁移、网页、Android或MySQL变化，不机械重跑无关链，也不据此免除后续L2/L3。

## 实现与验证

新备份在首替换前写`rekey-inventory.json`，清单HMAC验证及全集摘要重算通过才报告`processSetAuthenticated=true`。旧无清单备份保持false，不回填历史证明；带清单SHA的成功回执丢失清单后不能被独立消费者降级接受。此证明只覆盖轮换持锁快照时实际保存的集合，不能证明快照之前已丢失历史、当前写者退出、数据库截点或异地保管。

首批`p07-inventory01`：

| 范围 | 结果 |
| --- | --- |
| 普通test | 33/33，0失败/错误/跳过；Rekey4、CLI17、ControlLock8、default-off配置4 |
| 实际专项IT | 21/21，0失败/错误/跳过；Inventory7、Inspect7、Startup2、Crash1、Python供应4 |
| bootJar | 成功，退出0 |

自审把回执清单检查提为有界函数并补实际Python“移走清单后消费原成功回执”的反例；只影响Python回读。`p07-inventory02`新执行Python供应IT **5/5**、0失败/错误/跳过。其余17项IT和33项普通test按两批2230项输入hash复用，Java产品及诊断实现未变；不是重复相加成更多功能。两批命令/退出码、原XML、日志与输入关系均保存。

实际强制终止留下79新签/436旧签，清单在首替换前已存在且旧HMAC通过；Python返回MIXED、全集认证true，同时`writerLivenessVerified=false`、`recoveryAllowed=false`、`keepWritersStopped=true`。旧绑定启动实际拒绝。合成515条不是现场历史或真实规模性能验收。

自审含输入路径/链接拒绝、有界读取、规范字节、秘密不输出、清单生成前不替换、原件不删除、旧备份不冒领认证、同JVM锁描述符及回执降级。作者/自审为当前助手，Python为独立实现消费者，不等于独立人员审查。历史失败/暂缓计数不清零。本批无产品或业务验收失败；产物复制初次误用JAR文件名而FileNotFound，未复制/覆盖，核实际libs后按明确路径补保全，记录在本批operational-notes.json。

## 保全、提交与剩余

两批runtime分别`.local/rtmp-p07inv01/`、`rtmp-p07inv02/`，1677/100文件长度与hash清单，原签备份、签名清单、pending、请求、合成秘密及原日志/XML保留，只在本机私有区。JAR为`.local/goal-20261015/p07-inventory01/server.jar`，280754473 bytes，SHA-256 `cc7c53f880978122e13f3e80c62008a2498d55b2a3922654b77f7ed6d414bfff`。未发布/部署。

结束无项目Java/Python/API/Vite/隔离库或设备任务；共享MySQL3306/33060 PID5412父4372未操作。C/D可用78,858,129,408 /20,269,182,976 bytes，见`p07-inventory02/{processes-after,listeners-after,disks-after}.json`。仅本任务子进程TEMP/TMP/Java临时目录指D盘，无全局配置变更。没有原库/数据库/AVD操作。

明确路径开发备份并推送`codex/development`，实际提交/远端、全改动、写权及index在`p07-inventory02/post-commit.json`等收尾文件；不自引用循环提交。七类生产决定、TEMP边界、`localDeletionAllowed=false`及main/发布边界不变。

下一步继续显式可信恢复/还原、外部历史钥引用与保管、跨截点和升级回退；旧无清单中止原件不能靠新工具补造证明。所有恢复结果仍需实际进程排空、原锁身份和业务事实核对。D/O/U、第8节、E06 L2及D18 L3保持未完成，总目标active。
