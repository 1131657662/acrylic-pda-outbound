> **无状态文档**：本文不含当前版本或进度事实。版本四元组与断点只看[看板](ORCHESTRATION_STATUS.md)的生成事实块（由 `tools/version_facts.ps1` 生成）；下文出现的“当前/最新/现网为”等措辞都是写作时点的历史记录，不作为现行状态。

# 开发验收索引

<!-- CURRENT_ENTRY_START -->
唯一当前状态、Git/写权、最新测试和下一断点见[看板当前区](ORCHESTRATION_STATUS.md)。本页只索引已有验收，不复制另一份“最新状态”；接续范围见[路线](DEVELOPMENT_ROADMAP.md)及[剩余计划](REMAINING_DEVELOPMENT_PLAN.md)。
<!-- CURRENT_ENTRY_END -->

## 使用口径

有实现、作者验证、独立消费者、组合验证、现场验收及部署是不同结论。下列记录各自固定到对应输入/版本，不能拼成当前全仓通过。R01–R05 已完成的开发范围按历史复用，关键未验继续保留；R06–R09 局部通过不代表整体完成。

## 近期验收入口

| R09 应用入口接线第一批（DataSource 与 JdbcTemplate/事务） | [验收](history/R09_APPLICATION_ENTRY_ACCEPTANCE_2026-09-26.md)：`RecoveryJdbcObjectHandler.invoke` 未同步判为**缺口**并修复（子对象调用并入同一监视器；去掉共享监视器后 17 项中 2 项失败、按字节还原）；新增 opt-in `RecoveryAdmissionConfiguration`（admitted `@Primary` DataSource 供 JdbcTemplate/事务管理器，原始池只以 `@FlywayDataSource` 给迁移，默认关闭、权威缺失启动即失败）。隔离副本全量 714 tests=713 pass/1 skip、bootJar `faae1cbc…`；独立非空 MySQL attempt105 IT 1/1 无 skip：关闭竞争（close 等待 2513ms）、关闭意图拒新接纳、提交期连接 KILL 的未知提交、丢失提交回执均保持 fence，退出后连接/事务 0/0、耐久原意图完成、认证 shutdown。未接 HTTP/Session/Worker/文件入口与 NORMAL |
| R09 运行时只读门禁（在制） | [验收](history/R09_NORMAL_ADMISSION_ACCEPTANCE_2026-09-26.md#追加运行时只读门禁本批新增真实验证未完成)：`RecoveryAdmissionEvidence`/`RecoveryAdmissionQueries`/`RecoveryAdmissionVerifier` 逐项比对库外快照、耐久意图、库内登记与投影、`server_state`，任一侧缺失即 `admitted=false`，不写库/不登记/不初始化；V40 增列 `authority_revision`。定向 8 项、全量 708 tests=707 pass/1 skip、0failure。**未完成**：加列后的真实 MySQL 复验未绿（attempt97 两次分别为浏览器登录超时与导出 `RETRY_SCHEDULED`），绿色基线仍为 attempt90m |
| R09 初次 NORMAL 登记 | [验收](history/R09_NORMAL_ADMISSION_ACCEPTANCE_2026-09-26.md)：V40 `recovery_normal_admission` + 不可改删触发器（迁移不插行）；`RecoveryNormalIntent`/`RecoveryNormalRegistration`/`RecoveryNormalRows`，唯一可置回 NORMAL 的组件，要求 RELEASE_READY 终态 + 双侧一致 + 外部写者租约，幂等且 `ordinaryWritesAllowed=false`。定向 75/75、全量 700 tests=699 pass/1 skip、0failure；**隔离 attempt90 通过**（V34→V40、IT 1/1 无 skip、连接/事务排空 0、认证 shutdown；四类拒绝无半条记录且保持 RECOVERY_PENDING）。未接 API/Session/Worker/文件入口 |
| R09 连接适配器并发 seam/可信反证 | [验收](history/R09_ADMISSION_PROBE_ACCEPTANCE_2026-09-26.md)：新增包内 `RecoveryAdmissionProbe`（默认 `NOOP`）与 `admit(probe)`，probe 点位于 `synchronized invoke` 监视器内；新增 `probeForcedOverlapKeepsAConcurrentCloseOutOfTheInFlightDelegateCall`（14→15）。**反证成立**：仅去掉 `invoke` 的 `synchronized` 后 `gradle exit=1`、failures=1 且失败用例即该断言，备份按字节还原；全量 `test bootJar --rerun-tasks` 691 tests=690 pass/1 既有符号链接条件 skip、0failure/0error。未跑 MySQL/HTTP/APK |
| R09 连接适配器并发补强 | [验收](history/R09_ADAPTER_CONCURRENCY_ACCEPTANCE_2026-09-26.md)：补 Statement 代理路径的并发执行/关闭与 08006 反例，`RecoveryAdmittedDataSourceTest` 12→14；`test bootJar --rerun-tasks` 全量 690 tests=689 pass/1 既有符号链接条件 skip、0failure/0error；去同步反证当时未成立（监视器无法外部抢占），只声明当前实现下断言确定性通过；主代码零改动、未跑 MySQL/HTTP/APK |
| R09 连接生命周期 adapter（attempt89） | [当批证据](../outputs/r06-product-20260925-01a0d8fa/admission-attempt89/manifest.sha256)：服务端 `RecoveryAdmittedDataSource`/JDBC 代理/租约毒化修复；全量 688 tests=687 pass/1 既有符号链接条件 skip，bootJar 成功；独立非空 MySQL V34→V39、真实 HTTP、JVM 退出后连接/INNODB_TRX=0/0；未注册 Spring/NORMAL，全入口和生产写权限仍未开放 |

| R07-1 交接命令层（REQUEST/APPROVE/PREPARE/ACTIVATE + R07 专用激活路径） | [状态归档](history/R07_1_COMMAND_LAYER_STATUS_2026-09-27.md)：**核心逻辑**已实现，**仅完成编译 + 全量单测（718 项 0 失败 / 1 既有 skip）**；**端到端未验**——14 条断言（8 反例 + 3 幂等 + 3 并发 + 原子性两模式）**全部未跑**，outbox 接线未落代码。控制器 + 契约 0.35.0（paths 130）属**辅助逻辑**，已达"编译 + 全量单测"粒度。提交 `ec58801`、`49e3b0f`。**不得视为 R07-1 完成** |
| R07 FORMAL 来源夹具（**验证基础设施**类目，未启动） | [阻塞记录](history/R07_FORMAL_FIXTURE_BLOCKER_2026-09-27.md)：累计 **5 轮未成**（attempt 138/139 编译与运行期、140/141/142 三轮推进、143/144 方向 2 自建设备资格）。根因：复用 TEMP 夹具的设备侧采集序号与审计批次（`CAPTURE_CONTENT_CONFLICT`、`冻结审计批次错误`）；方向 2（自建 device/app_user/session/assignment）亦未成立（401→404）。**不是"多修几处"能解决**，需**独立时间盒（长于普通子项）+ 独立验收标准**；现在不启动，待 R07 其他部分或 R06 完成后再专门排期 |

| 范围 | 证据及适用边界 |
| --- | --- |
| R09 耐久关闭意图/接纳排空 | [验收](history/R09_CLOSING_INTENT_ACCEPTANCE_2026-09-26.md)：协议v2原意图永久保留、新写者拒绝；Windows/Linux各31无skip、Java675pass/1skip、Python114pass/11skip；attempt82实库事务/投影/HTTP。全应用接线与NORMAL仍未完 |
| R09 关闭阶段数据库投影 | [验收](history/R09_CLOSED_PROJECTION_ACCEPTANCE_2026-09-26.md)：V39非空迁移、原事实字节不变、四阶段及并发/回退/错库反例；670单测通过/1skip，attempt80实库+HTTP/浏览器。仍未接共享入口或NORMAL |
| R09 真实事务生命周期租约 | [验收](history/R09_LEASED_TRANSACTION_ACCEPTANCE_2026-09-26.md)：提交/回滚/UNKNOWN/内层事务反例、670单测通过/1skip，attempt78独立非空MySQL及原HTTP/浏览器回归，JVM退出后连接/事务0；API共享接入和DB投影仍未完 |
| R09 本机跨进程写者租约 | [验收](history/R09_WRITER_LEASE_ACCEPTANCE_2026-09-26.md)：Java41项、Python33专项、Windows/Linux各25真实跨进程；原锁身份/引用计数/旧资料只读；服务端665通过/1skip。事务及完整共享门禁继续 |
| R09 导出进程容器 | [验收](history/R09_PROCESS_CONTAINMENT_ACCEPTANCE_2026-09-26.md)：Windows真实Job父孙进程/握手9项、服务端659通过/1条件skip、report124通过；attempt74真实HTTP/浏览器；Linux独立用户cgroup2项通过，未部署项目服务；完整共享门禁继续 |
| R09 已追踪导出进程排空 | [验收](history/R09_PROCESS_DRAIN_ACCEPTANCE_2026-09-26.md)：真实父子进程退出、5定向；655单测通过/1条件skip、JAR；attempt73非空MySQL/真实HTTP+浏览器各1通过。孤儿/完整树与共享租约仍待接 |
| R09 Java库外原件消费者 | [验收](history/R09_JAVA_CONTROL_ACCEPTANCE_2026-09-26.md)：Java35定向、服务端653通过/1条件skip、Python103通过/11专用环境skip、Windows/Linux各16真实跨进程及独立原件消费；共享API/DB/文件排空继续开发 |
| R09 Job共享事务/代次屏障 | [验收](history/R09_JOB_FENCE_ACCEPTANCE_2026-09-26.md)：V38非空迁移原Job保留、实例/Job双代次、恢复锁竞争与HTTP；attempt72 1+23集成测试无skip，attempt71导出/浏览器通过；库外共享接入待续 |
| R09 库外控制本地模块 | [验收](history/R09_LOCAL_CONTROL_ACCEPTANCE_2026-09-26.md)：原命令/CAS/OS锁/进程崩溃恢复29/29；完整R09为103通过/11专用环境未验；普通写始终关闭，共享门禁与释放未接入 |
| R06 M2b-2b 受控关联闸门＋指针切换 | [闸门验收](history/R06_CONTROLLED_LINK_ACCEPTANCE_2026-09-26.md)、[切换验收](history/R06_CONTROLLED_LINK_SWITCH_ACCEPTANCE_2026-09-26.md)：`LINK` 放行＋锁内候选摘要逐字比对、0/多候选失败关闭、锁内目标重验；同一事务写映射 v+1（`CONTROLLED_LINK`）/scope canonical/slot 改指/被取代单 `LINKED`（V41 约束扩展）。attempt113 独立非空 V34→V41 实库 IT 1/1 无 skip；attempt114 按字节去守卫后同一 IT 真实失败并原字节还原。**关联后确认/成功链（M2b-4b）、排除台账、锁图并发未实现**，生产入口仍无 CommandOperation bean |
| R06 其他未提交来源事实 | [验收](history/R06_UNSUBMITTED_SOURCE_ACCEPTANCE_2026-09-25.md)：2026-09-26 attempt67非空MySQL/真实HTTP，原授权/库存/审计状态核验；其他来源不误占K，613单测通过/1条件跳过，64/65失败保留；其他TEMP计划和跨来源续作限制单列 |
| R06 草稿历史归档信任 | [验收](history/R06_DRAFT_ARCHIVE_TRUST_ACCEPTANCE_2026-09-25.md)：配置历史钥/已存原凭据/文件/逐事件一致；attempt63实际Android归档+换钥重启HTTP，62失败及修复保留；其他来源业务事实仍未完 |
| R06 非零候选独立消费 | [验收](history/R06_NONZERO_CANDIDATE_ACCEPTANCE_2026-09-25.md)：attempt61实库/HTTP/Python54通过，完整候选/上下文/游标独立重建；非零短装批准不授普通确认，关联/排除仍未完 |
| R06 Room27 产品停止/持久补传队列 | [验收](history/R06_DRAFT_PRODUCT_ACCEPTANCE_2026-09-25.md)：最终Android440通过/4HTTP跳过、APK/lint及attempt59产品写者→真实HTTP；坏资料拒绝、身份切换持久暂停、非空26→27原件保留；不授业务能力 |
| R06 Room26 原键/回执仓储恢复 | [验收](history/R06_DRAFT_TRANSFER_ACCEPTANCE_2026-09-25.md)：Android420通过/4HTTP跳过、APK/lint及attempt57真实HTTP；产品停止/补传接线由后续批次验收 |
| R06 V37 草稿原件接收 | [验收](history/R06_DRAFT_INTAKE_ACCEPTANCE_2026-09-25.md)：可信准备、原响应及attempt56；ORIGINALS_STORED不等于业务事实已评估 |
| R06 原件结构闭合 | [验收](history/R06_DRAFT_SNAPSHOT_ACCEPTANCE_2026-09-25.md)：原件根/数量/全设备截点，不能替代其他来源业务事实 |
| R06 Room25 原机签名 | [验收](history/R06_DRAFT_SIGNATURE_ACCEPTANCE_2026-09-25.md)：停止事务签名，旧无签名原件不补签；非真实PDA Keystore证明 |
| R06 Room24 本机停止 | [验收](history/R06_LOCAL_DRAFT_STOP_ACCEPTANCE_2026-09-25.md)：未冻结/零包/未决原件；attempt54只证明旧冻结链兼容 |
| 来源事实、确认记账、独立短装、Room20–23、R08 TEMP导出、R09混合归档 | [专项历史索引](history/README.md)：逐项读取原验收、失败和未验，不以原确认导出替代净结果、不以混合归档替代完整恢复 |
| R01–R05及更早版本 | [原进度完整归档](history/management-2026-09-25-before-refresh/docs/DEVELOPMENT_PROGRESS.md)：按章节定位；包括code20/Room17及现场、升级、长时等未验 |

<a id="2026-09-18开发阶段提交"></a>

## 历史阶段提交入口

原2026-09-18开发阶段提交及679d5e4记录见[原进度完整归档](history/management-2026-09-25-before-refresh/docs/DEVELOPMENT_PROGRESS.md)。保留此锚点供旧交付文档跳转；该提交不代表当前工作区版本。

## 证据维护

每次子项验收保存：输入树/精确SHA及未提交差异、实现与自审、当次实际测试、独立/组合关系、原始证据路径、失败和限制、下一依赖。当前断点只更新看板；本页在增加新的验收记录时增加入口。

2026-09-25管理整理只归档原文、核对测试报告并更新入口，没有新增业务验收。原文与SHA校验见[归档说明](history/management-2026-09-25-before-refresh/README.md)。

| R06 发布前必须解决项：全量服务端 IT **从未成功跑过**（未知状态） | attempt 168 首次全量跑批（R02 构造器，隔离 MySQL）：`classes=172 tests=767 failures=722 errors=0 skipped=45`；失败由 `One-shot attempt already used`(28) 与 `ApplicationContext failure threshold exceeded`（级联，占绝大多数）主导 → **不可解读为产品缺陷，也不能视为通过**。发布前必须改为分批覆盖并取得一次可解读的全量结果。 |
| `ExportRendererProcessTest` pid 握手 flake 修复（`9480a21`） | 根因＝fixture 用 `Files.writeString` 发布 `attempt/pid`（该 API 先建**空文件**再写），测试旧逻辑只判 `Files.exists` 就 `Long.parseLong(Files.readString(...))` → 负载下命中空窗，`NumberFormatException: For input string: ""`；非 stdout 时机、非并行共享资源（全仓无 JUnit 并行配置）。修法：原子发布（`.tmp` + `ATOMIC_MOVE`）＋等「非空可解析」内容。验证：隔离单类 3/0/0/0、12 路 CPU 负载单类 3/0/0/0（15.6s→26.3s）、全量 718/0/0/1 既有条件 skip；机制 A/B 反证 12998→0。**未复现原始失败本身**（概率性）。详见[欠债清单](history/R06_DEBT_LIST_2026-09-27.md)。 |