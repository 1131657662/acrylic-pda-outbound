# D15 任务监控工具 L1 验收

本批只收口身份绑定任务探针与本地监控入口，属于开发备份；不计 D15 全部、E07 L2、D18 L3 或总目标完成。版本事实见看板生成事实块。

## 输入、写权与设计

固定树 `D:/acrylic-pda-outbound`，分支 `codex/development`，输入 HEAD/实际远端均为 `ff2e4dfb1a011955ec088273f64cf56b7919d060`。唯一开发写者为本聊天 `01a13a57-8082-7dd2-a08f-987483dcda4b`；清理聊天明确释放写权，见 `.local/disk-cleanup-20261015/task-complete.json` 与 [独立清理报告](D_DISK_CLEANUP_2026-10-15.md)。本批未发送跨聊天消息。

证据根 `.local/goal-20261015/d15-jobs01/`：`ownership-input.json`、`dirty-hashes-before.json`、`dirty-files/`保存80项接续输入；其中清理报告已更新，其余来源按前批哈希核对。`board-before.md`先保存旧看板；本批结束除自主更新看板外，其余79项原字节均保持，见 `inherited-check.json`。未清理、还原或混入其他 Android/UI/文档/工具和 `server/physical-disposition-last-request.json`。

按总目标自主设计授权选择显式配置和独立列级SELECT账号：比整表只读减少载荷读取能力；复用旧聚合与阈值，避免改变旧实验库身份边界。结构检查替代陈旧版本数字白名单，但只证明必要表/列及无失败迁移，不证明完整业务schema。部署、生产授权和外部通知仍独立。

改动路径→影响层→必跑清单及复用依据见 `l1-mapping.json`。产品路径为 `docs/parallel/R09/ops/{jobs_credentials,jobs_connection,jobs_probe,monitor,cli}.py`；新增配置示例、凭据/配置测试及 `tools/d15_jobs_*`隔离夹具/保全/实际消费者。无应用API、迁移、Android或网页行为变化；原应用默认及删除边界保持，不要求无关 Chrome/设备链机械重跑。

## 实现行为

- `jobs-monitor --config`与总`monitor`的可选`jobs`均实际消费 MySQL；健康退出0，超阈值/错误退出2，只输出计数、超标名或固定码。
- 连接限定回环且同时核对端口、datadir、数据库、CURRENT_USER、server_uuid；禁止隐式option文件、local-infile、多语句、重连和自动重试。
- 密码只来自显式绝对路径私有文件，不进命令行、环境、配置JSON或日志；拒绝额外选项/宽松ACL。账号仅准许两表指定列SELECT，拒绝整库读取、角色、额外表/列及写权。
- 身份/授权后设置查询上限，开启只读事务，结束回滚；检查InnoDB基础表、必要列类型/可空性及非空且无失败迁移历史。缺表/错结构不变成0。
- 不自动处理FAILED、释放租约、清业务数据、发外部通知或启用timer；旧lab jobs保持原身份边界。操作说明见 [监控文档](../parallel/R09/MONITORING.md)。

## 实际验证

跨33个工作树及监听核对后分别分配新实例1543/19343、1544/19344，未复用1538或旧失败库；路径 `.local/d15-monitor-1543/`、`.local/d15-monitor-1544/`。执行仓库真实Flyway迁移，随后生成合成任务、分例复制表和受限账号，不接原库/现网。迁移日志、请求配置、原stdout/stderr、全表前后事实、dump/binlog和失败原件均保留。

| 运行 | 实际命令/结果 | 证据 |
| --- | --- | --- |
| 首夹具 | `C:/Python314/python.exe tools/d15_jobs_fixture.py <manifest>`，退出1，导入PyMySQL前失败，迁移/业务写入0项；转用既有`.local/r09-control-venv/Scripts/python.exe` | `fixture.log`、`fixture-runtime02.log` |
| 首真实入口 | `tools/d15_jobs_probe_it.py`，退出0，11/11通过、0失败/错误/跳过 | `it.log`、1543各`.stdout`/`-conservation.json` |
| 首R09回归 | `-m unittest discover -s docs/parallel/R09/tests -p test_*.py`，退出0，共150：139通过、11既有显式跳过；跳过不计通过 | `r09-tests.log` |
| 权限补测首次 | `tools/d15_jobs_security_it.py`，退出1，3项中2通过、1错误，发现宽ACL误通过 | `security-it.log`、`failure-fingerprint.json` |
| 修复后新实例真实入口 | `-m unittest discover -s tools -p d15_jobs_*it.py -v`，退出0，15/15、0失败/错误/跳过 | `it1544.log`、1544原输出及逐次全表守恒 |
| 最终配置/ACL | `-m unittest test_jobs_config test_jobs_credentials -v`，退出0，4/4、0失败/错误/跳过 | `config-acl-tests02.log` |

15项实际覆盖：完整迁移库非空成功；备份/磁盘/任务组合成功；101条到期积压和最老等待失败；过期RUNNING、FAILED、RETRY指标；21条重试超阈值；错误datadir/UUID/账号/数据库；整库SELECT和无权拒绝；缺表/错误列类型/失败迁移拒绝；真实列级账号拒绝载荷SELECT、DELETE、UPDATE和多语句；宽ACL拒绝；额外隐式选项拒绝；错误密码经总入口固定码脱敏。所有探针前后读取目标库全部表，行数及内容hash一致。夹具的合成任务/错误迁移输入不冒充可信业务或现场事实。

作者为当前助手，含自审；Python CLI是实际产品入口消费者，非独立人员审查。未改变业务写路径，因此业务末SQL回滚、同键提交等不属于本切片新增覆盖，不以只读测试代替原业务/L2/L3。未运行HTTP/Android/Chrome、APK/JAR构建、设备或部署。

## 失败、自审与复验边界

首次权限补测真实发现Windows PowerShell继承不兼容PSModulePath，Get-Acl自动加载失败但脚本继续，空ACL被误判私有。保留Everyone SID及原误通过输出；修复为子进程移除该继承路径、任何错误立即退出、stderr非空即拒绝。最终在新1544输入完整复验，另补纯边界回归。旧失败计数1保留，不将初次11项算修复后15项。

`test02-final-input-hashes.json`绑定最终产品及测试源码；验证后仅保全工具变更，`self-review.json`/`result.json`说明输入关系。R09旧回归中直接改动的配置测试已按最终输入复跑；其余未受影响用路径hash复用，未复制旧输出当新执行。单文件/函数检查满足本批职责约束。

1544首次停机后的hash清单遇MySQL尚在退出造成的瞬态文件消失；dump和事实检查已完成且认证停机，原失败日志保留。保全工具改为等待明确PID退出并验证端口不可连后生成清单；首次Finalize因不存在PID导致PowerShell退出码1再被拒绝，修复该运行器返回语义后完成，未重启或重跑业务。证据 `preserve1544.log`、`preserve1544-finalize02.log`、`preserve1544-finalize03.log`。这两个不同运行器诊断不掩盖原失败、不重置产品ACL失败计数。

## 保全、Git与未完成

1543保留13个合成数据库、dump和510文件hash；1544保留14个合成数据库、dump和517文件hash；全部清单在结束再次逐项校验，输入文件长度/hash不变。两实例认证停机，19343/19344零监听；未操作共享3306/33060 MySQL。无项目API/预览/Java/ADB/emulator/qemu在运行；详见本批`processes-after.json`、`listeners-after.json`。

收尾C/D可用为78,896,844,800 / 19,392,180,224 bytes，见`disks-after.json`；本批输入预计≤1.5GB，两实例、dump/binlog和原日志继续保留。没有清理任何历史材料。仅明确路径提交/推送开发分支，实际Git回执写本批`post-commit.json`，不自引用循环提交；清理报告仅作为独立归档纳入。

Linux/systemd、timer启用、外部告警、真实规模成本/阈值和生产RPO/RTO未验。D15完整恢复/回退/资源限额、E07组合、E06 L2、D18 L3、其余D/O/U及总目标第8节仍未完成；设备/Chrome阻断、同根因暂缓和原件限制继续保留。下一项核对P07突发中止/部分替换的停写与恢复入口，再推进跨截点/回退可独立验证范围；不恢复已取消的换机承接。
