# 历史归档

- [R09 C2 B1-3 S5：启动守卫与初始化窗口](R09_C2_B1_3_S5_STARTUP_WINDOW_2026-09-29.md)：解决"开开关即起不来"——`RecoveryStartupGuard`（清理未禁用⇒`CONTROL_SESSION_CLEANUP_UNCOVERED`；应用 Bean 显式排到初始化窗口之后）＋`RecoveryInitializationWindow`（`INITIALIZATION` 根覆盖 bean 构造与启动 runner，就绪时退役）；未改启动消费者。现场（实例 19462，已删探针替身）带 admission **真实启动成功**且 S3/S4 判据全绿，同轮另证清理守卫启动失败且端口未占用；全量单测 2806 项 0 失败、恢复链 25 类 696 项 0 失败。未做：阶段 0 安全前置（第 8–12 行）、锁并发（第 13 行）、拒绝码 HTTP 映射、入口 IT 重基。开关仍默认 false，K1 仍未通过；另附 S5 开工前看板归档。

- [R09 C2 B1-3 S4：multipart 与错误分派判据](R09_C2_B1_3_S4_MULTIPART_2026-09-29.md)：**无需产品改动**——真实容器观测证明 multipart 解析/暂存、handler、Session 保存与错误分派都在 S3 的同一请求根内（解析期 `BOUND`、暂存 1 个文件、请求后容器清理、根数＝请求数、14 次保存全 `BOUND`、无泄漏）；全量单测 2801 项 0 失败、恢复链 25 类 696 项 0 失败。发现：multipart 超限部件为容器级拒绝（观测 500 而非应用 413 映射）。**B1-3 S1–S4 全部完成**；开关仍默认 false，K1 仍未通过；另附 S4 开工前看板归档。

- [R09 C2 B1-3 S3：请求根过滤器与真实 Session 覆盖](R09_C2_B1_3_S3_REQUEST_ROOT_SESSION_2026-09-29.md)：`RecoveryRequestAdmissionFilter`＋`RecoveryRequestRootListener`＋`registry.admit`（一请求一根、每次分派绑定、ASYNC/ERROR/嵌套重分派复用同根）；真实容器＋Spring Session JDBC 现场判据通过（session/error/async 三步、根数＝请求数、10 次 Session 保存全部在存活根内、轨迹 REQUEST→ERROR→ASYNC、无根泄漏）；全量单测 2800 项 0 失败、恢复链 25 类 696 项 0 失败。发现未修：启动期无根合法能力缺失（开关打开即 fail closed）、锁非嵌套致并发接纳受限、拒绝码 HTTP 映射未实现。**S4 未开工，开关仍默认 false，K1 仍未通过**；另附 S3 开工前看板归档。

- [R09 C2 B1-3 S2：请求根数据源](R09_C2_B1_3_S2_REQUEST_ROOT_2026-09-29.md)：`RecoveryRequestAdmittedDataSource` 成为唯一 `@Primary`（六码、永不回退旧连接层），旧类降级并由静态白名单强制；`RecoveryOperationRegistry/Lease/SubLease/Token/Kind/Phase/SubOperation` 提供根登记与分派绑定。两级验证：定向单测 47/0（旧类 17/17 仍绿）、全量单测 2790 项 0 失败（无白名单）、恢复链 IT 25 类 696 项 0 失败 / 0 跳过（全新实例 19453）。**S3/S4 未开工，开关仍默认 false，K1 仍未通过**；入口 IT live/unknown 检查待 S3 重基。另附 S2 开工前看板归档。

- [R09 C2 B1-2 跨进程围栏与真实 Authority](R09_C2_B1_2_FENCE_2026-09-29.md)：规则/实现/多 JVM 矩阵、真实效果与故障注入分离、W 与全未确认分支保留；默认关闭，K1 仍未通过。
- [B1-2 开工前看板归档](ORCHESTRATION_STATE_before_R09_C2_B1_2_2026-09-29.md)。

- [R09 C2 B1-1 对抗性覆盖复核](R09_C2_B1_1_ADVERSARIAL_2026-09-29.md)：三处漏口、门内修正、九项清单、真实反例及默认关闭、逐轮失败留存；另附[600 个接口签名表](R09_C2_B1_1_JDBC_SURFACE_2026-09-29.md)。K1 仍未通过，B1 整体未完成。
- [对抗性复核过程断点](ORCHESTRATION_STATE_before_R09_C2_B1_1_ADVERSARIAL_CLOSE_2026-09-29.md)：保留验证进行中与编译失败断点。
- [对抗性复核前看板原区](ORCHESTRATION_STATE_before_R09_C2_B1_1_ADVERSARIAL_2026-09-29.md)：原样保留前轮当前区。

- [R09 C2 B1-1 效果门第一切片](R09_C2_B1_1_EFFECT_GATE_2026-09-29.md)：独立有限授权；规则/实现/用例唯一映射、真实驱动对照、默认关闭与缺权威启动失败证明、隔离身份及逐轮失败保留。126 项定向单测、35 项真实库 IT 通过；MODEL 权威输入不代替跨进程事实。默认关闭、未接线、未部署、未开生产开关；K1 仍未通过，B1 整体未完成，不进入 B1-2。
- [B1-1 前看板原文](ORCHESTRATION_STATE_before_R09_C2_B1_1_2026-09-29.md)：保留交接态原文及原相对链接，仅历史快照。
- [交接前看板原文](ORCHESTRATION_STATE_before_R09_C2_HANDOFF_2026-09-29.md)：保留 DSH 收口前的看板当前区原文（**收口轮 2026-09-29：DSH 停止推进，写权移交 Codex**；K1 仍未通过；单一下一步＝B1-1 待用户明确授权）。
- [R09 C2 G0-K1-3 遗留卫生修正](R09_C2_G0_K1_3_HYGIENE_2026-09-29.md)（纯文档/测试卫生；**B1-1 未获授权、保持停止**）：①[验收记录](R09_C2_G0_ACCEPTANCE_2026-09-28.md) 中“真实 FileLock 轮”段落的旧“矛盾阻断”表述**已就地更正**并指向更正轮；②`G0K1WriterLeaseIdentityRealIT.v4_…` 对**未入库** `.local` 路径的硬断言改为**只记录存在性** ⇒ 干净检出可复现。验证：`runA-clean-sim`（父目录不含上一轮证据）exit 0 / IT 5 项 0 失败 / `previous_round_evidence_present=false`；`runB-normal` exit 0 / 5 项 0 失败 / `=true`；`runC-test` 定向单测 **60 项 0 失败**。未改 `src/main`、未开生产开关；K1 仍未通过、B1 仍待授权。
- [R09 C2 G0-K1-3 更正与闭合轮（锁文件删除/替换走产品路径复验）](R09_C2_G0_K1_3_CORRECTION_2026-09-29.md)（独立有限授权）：真实子JVM走**产品路径**（`RecoveryControlLock.locked`→`RecoveryWriterLease.acquire`→`RecoveryWriterProtocol.validate`原生身份校验），`integrationTest`**5项0失败**（16行TSV：REAL12/MODEL4；失败轮2次保留）。**V1a同名替换⇒`CONTROL_WRITER_LOCK_REPLACED`（3/3）**、**V1b缺失⇒`CONTROL_FILE_MISSING`（3/3）**、不自动重建、无第二持锁者 ⇒ `writers.lock`为**已缓解/已覆盖**，**上一轮“矛盾A/B（阻断）”定性撤回**（原因：`fileKey()`在Windows为空＋未走产品路径＋用例不断言）；`fenced.lock`转**B1承重需求**；保留**可用性威胁**。**K1仍未通过；B1待授权**。
- [R09 C2 恢复链 IT 分批可解读跑批](R09_C2_IT_BATCH_2026-09-29.md)：`tools/r09-recovery-it-batch.ps1`＋一次实跑 24 类/683 项/0 失败；IT-BATCH-PIN 已修（守卫契约化）并在全新专用实例复跑 25 类/696 项/0 失败（恢复链 26 类仅 C1 HTTP 类不在批内）。
- [R09 C2 B1-2 认证锚点补做](R09_C2_B1_2_ANCHOR_SPEC_2026-09-29.md)：方案 A 的记录锚点部分**已实施并验证**（H3 闭合、跨进程缺锚点即启动失败）；H3′（删除记录）亦已由锚定操作索引闭合；剩余为锚点秘密保管/轮换的运维政策。
- [R09 C2 B1-2 P5 认证锚点补充规格](R09_C2_B1_2_ANCHOR_SPEC_2026-09-29.md)：登记 H3／H3′（删除记录）两条已实测放行变体的闭合方案与决定项；**未获决定前不实施**。
- [R09 C2 B1-2 对抗性复核](R09_C2_B1_2_ADVERSARIAL_2026-09-29.md)（DSH 接手 Codex 被 403 中断的会话；同一会话实施＋自审）：H1 本机缓存未绑定文件身份、H2 按文件名跳过审计标记、H3 租约 owner 引用可改写为活属主 —— 前两处已修并验证（3/3、6/6 拒绝），H3 未解决并登记 `DEBT-R09-C2-LEASE-ANCHOR`。最终单测 2038/0、并集 IT 662/0/0；K1 仍未通过、未进入 B1-3。
- [R09 C2 G0-K1-3 真实跨进程 FileLock 验证](R09_C2_G0_K1_3_REAL_FILELOCK_REVERIFY_2026-09-29.md)（独立有限授权）：真实独立JVM子进程（Windows11/NTFS、Java21.0.11；stdout/stderr重定向到文件、无管道），10场景×3重复、`integrationTest` 0失败（52行TSV）。**通过**：F1′（跨进程共享共存＋排他必失败）、F3（`release()`/正常退出可再获取）、F5（`destroyForcibly`/`Runtime.halt(70)`后OS释放且残留文件≠持锁）、第三方无持锁者查询、同JVM重叠抛`OverlappingFileLockException`。**S8/S9的“矛盾阻断”定性已被更正轮按产品路径撤回**（文首有更正块）。
- [R09 C2 G0-K1-1 真实 JDBC 驱动特征验证](R09_C2_G0_K1_REAL_JDBC_REVERIFY_2026-09-29.md)（独立有限授权）：隔离 MySQL 8.0.46＋Connector/J（端口19438、独立datadir/账号/schema；系统3306未使用）；`test`60项0失败、`integrationTest`**13项0失败**、27条`G0K1REAL`记录；**`getAutoCommit()`非提交证据**；**DDL隐式提交**（⇒close期rollback假设列入B1待实施需求）。**K1仍未通过**。
- [R09 C2-G0 K1 授权包（B1 授权前证据封存）](R09_C2_G0_K1_AUTHORIZATION_PACKET_2026-09-28.md)：纯文档封存`K1 仍未通过`的边界、五类验证分类、证据清单、未验边界、B1范围与排除（B9分离）、技术性前置条件与影响/风险边界。**为授权准备，不代表B1获批或运行验收**。
- [R09 C2-G0 K1 模型最终复验](R09_C2_G0_K1_MODEL_FINAL_REVERIFY_2026-09-28.md)：围栏矩阵按**观察时状态**；时序三条轨迹严格断言；JDBC**规则行↔测试方法唯一映射**双向校验（20规则行/17方法/方法名唯一）；`run2` 60项0失败（G0K1 37方法，4组负控分存）。
- [R09 C2-G0 K1 模型更正轮](R09_C2_G0_K1_MODEL_CORRECTION_2026-09-28.md)：围栏矩阵改状态迁移模型、时序加显式调度点、JDBC补可执行信息流；登记第2次失败XML缺失（未补造）；证据改逐轮独立目录。
- [R09 C2-G0 K1 测试侧模型复验](R09_C2_G0_K1_MODEL_REVERIFY_2026-09-28.md)：四项反例（①②③现有代理实测、④文档静态矛盾）与W反例有效；**文首有更正块**。
- [R09 C2-G0 v5 定向收敛与设计复验](R09_C2_G0_V5_REVERIFY_2026-09-28.md)：以`d656ab9`为基线仅收敛K1（冻结JDBC效果入口、`fenced.lock`原子观察、F3改用`FileLock.release()`、六行失败矩阵、逐记录事件语义与命名窗口W）。
- [R09 C2-G0 设计定稿 v5](R09_C2_G0_DESIGN_2026-09-28.md)：经 K1 收敛、模型三轮、真实驱动轮、真实文件锁轮与更正轮修订后的定稿（§4.2 效果门冻结表＋真实驱动证据、§5.3–§5.5 观察时依据与失败矩阵＋真实跨进程证据、§6.5 INITIAL 关闭意图、§9 真实入口清单、§10.2 S13/S17–S21/S20-u）。零生产代码；**K1 未通过，B1 未获授权**。
- [R09 C2-G0 验收与阻断记录 v5](R09_C2_G0_ACCEPTANCE_2026-09-28.md)：**设计/规范验收，非运行验收**；含测试侧各轮执行表、**验证类别（五类必须分开）**、覆盖率映射、授权包轮／真实驱动轮／真实文件锁轮／更正轮声明。
- [R09 C2-G0 v4 定向补正与设计复验](R09_C2_G0_V4_REVERIFY_2026-09-28.md)：K1–K4 首轮（历史；K1 判定与章节检查表述已被取代/撤回）。
- [R09 C2-G0 残余阻断补正与设计复验](R09_C2_G0_RESIDUAL_BLOCKERS_2026-09-28.md)：R1–R5（历史）。
- [R09 C2-G0 定向纠错与设计复验](R09_C2_G0_CORRECTION_REVERIFY_2026-09-28.md)：B01–B06（历史）。
- [更正轮前看板原文](ORCHESTRATION_STATE_before_R09_C2_G0_K1_3_CORRECTION_2026-09-29.md)：保留真实文件锁轮断点原文。
- [真实文件锁轮前看板原文](ORCHESTRATION_STATE_before_R09_C2_G0_K1_3_REAL_FILELOCK_2026-09-29.md)：保留真实驱动轮断点原文。
- [真实驱动轮前看板原文](ORCHESTRATION_STATE_before_R09_C2_G0_K1_REAL_JDBC_2026-09-29.md)：保留 K1 授权包轮断点原文。
- [授权包轮前看板原文](ORCHESTRATION_STATE_before_R09_C2_G0_K1_AUTHORIZATION_2026-09-28.md)：保留 K1 模型最终复验轮断点原文。
- [K1模型最终复验前看板原文](ORCHESTRATION_STATE_before_R09_C2_G0_K1_MODEL_FINAL_2026-09-28.md)：保留 K1 模型更正轮断点原文。
- [K1模型更正前看板原文](ORCHESTRATION_STATE_before_R09_C2_G0_K1_MODEL_CORRECTION_2026-09-28.md)：保留 K1 模型复验轮断点原文。
- [K1模型轮前看板原文](ORCHESTRATION_STATE_before_R09_C2_G0_K1_MODEL_2026-09-28.md)：保留 G0 定稿 v5 断点原文。
- [G0 v5收敛前看板原文](ORCHESTRATION_STATE_before_R09_C2_G0_V5_2026-09-28.md)：保留 G0 定稿 v4 断点原文。
- [G0 v4补正前看板原文](ORCHESTRATION_STATE_before_R09_C2_G0_V4_2026-09-28.md)：保留 G0 定稿 v3 断点原文。
- [G0残余补正前看板原文](ORCHESTRATION_STATE_before_R09_C2_G0_RESIDUAL_2026-09-28.md)：保留 G0 定稿 v2 断点原文。
- [G0纠错前看板原文](ORCHESTRATION_STATE_before_R09_C2_G0_CORRECTION_2026-09-28.md)：保留 G0 定稿 v1 断点原文。
- [G0设计定稿前看板原文](ORCHESTRATION_STATE_before_R09_C2_G0_DESIGN_2026-09-28.md)：保留Codex交回DSH收尾断点原文。
- [R09 C2 G0 范围与验收提案](R09_C2_G0_SCOPE_PROPOSAL_2026-09-28.md)：**已由[设计定稿](R09_C2_G0_DESIGN_2026-09-28.md)取代**，保留原始现状判断与待决清单以可追溯。
- [Codex → DSH 会话交接](SESSION_HANDOFF_2026-09-28.md)：本轮只收尾；50个接续提交、各线状态、精确未验边界、单一接手建议与写权说明。
- [DSH → Codex 原交接](SESSION_HANDOFF_DSH_TO_CODEX_ORIGINAL_2026-09-28.md)：逐字保存旧交接，仅历史，不恢复D片3未做及PDF未读等旧结论。
- [DSH切换工作树清点](DSH_SWITCH_WORKTREE_AUDIT_2026-09-28.md)：34树坐标，6棵有改动树/138个文件及环境监听；只盘点保护，不清理旧树。
- [交回DSH前看板原文](ORCHESTRATION_STATE_before_DSH_RETURN_2026-09-28.md)：保留C1验收后的旧断点。
- [R09_C1_REVERIFY_2026-09-28.md](R09_C1_REVERIFY_2026-09-28.md)：07ddf93修复参数/同类扫描、冒烟2绿；559缺PROCESS红保留，560真HTTP/真MySQL与105连接回归通过，124表原事实守恒；列上下文重启/故障注入/现场等精确边界，C2待授权。
- [C1真实验收前看板原文](ORCHESTRATION_STATE_before_R09_C1_HTTP_ACCEPTANCE_2026-09-28.md)：保存冒烟已绿、尚未执行559的断点。
- [C1冒烟通过前看板原文](ORCHESTRATION_STATE_before_R09_C1_SMOKE_PASS_2026-09-28.md)：保存脚本修复已完成、尚未启动的断点。
- [C1脚本修复/复验前看板原文](ORCHESTRATION_STATE_before_R09_C1_REVERIFY_2026-09-28.md)：保留上轮准备失败的完整断点，不恢复历史指令。

- [R09_C1_SMOKE_BLOCKER_2026-09-28.md](R09_C1_SMOKE_BLOCKER_2026-09-28.md)：0bcf24c新增完整应用冒烟入口，编译通过；本机MySQL主机参数误解析导致准备失败，Spring/IT未执行，559未分配，按用户边界停止，原样错误及清理证据保留。
- [C1冒烟前看板原文](ORCHESTRATION_STATE_before_R09_C1_SMOKE_2026-09-28.md)：保留两批实现及557/558红的收尾断点，历史指令不恢复生效。

- [R09_C1_INITIAL_REGISTRATION_BATCH2_2026-09-28.md](R09_C1_INITIAL_REGISTRATION_BATCH2_2026-09-28.md)：c974188接入初次NORMAL双侧协调器/新存储；557/558重复HTTP mapping启动失败，修正探针后78项单测绿，C1真实验收未完成，红证据及接续条件完整保留。
- [R09_C1_INITIAL_PROTOCOL_BATCH1_2026-09-28.md](R09_C1_INITIAL_PROTOCOL_BATCH1_2026-09-28.md)：37beeaa仅新增独立库外模型/耐久存储与测试，22项绿；c146f5f记录协议、可信原件及第二批DB存储边界。
- [C1第二批收尾前看板原文](ORCHESTRATION_STATE_before_R09_C1_BATCH2_CLOSE_2026-09-28.md)：保存评估时断点；首批按只新增约束延后既有文档同步，历史指令不恢复生效。

- [R09_C1_SCOPE_ASSESSMENT_2026-09-28.md](R09_C1_SCOPE_ASSESSMENT_2026-09-28.md)：独立初次NORMAL范围判c（兼b）；既有组件仅覆盖关闭链后的登记，需新组件/生产适配，按用户边界停报；含双侧证据、文件清单、3–4轮估算及未验，无代码/IT。
- [C1范围评估前看板原文](ORCHESTRATION_STATE_before_R09_C1_ASSESSMENT_2026-09-28.md)：保留C2入口评估收尾断点，历史指令不恢复生效。

- [R09_C2_ENTRY_ASSESSMENT_2026-09-28.md](R09_C2_ENTRY_ASSESSMENT_2026-09-28.md)：逐入口源码/生产面/前置/最小验收，区分C1历史组件与未完成双侧证据；大块实现按入口分批，建议先公共依赖再HTTP/Session。仅评估、无代码/测试，实施待授权。
- [C2入口评估前看板原文](ORCHESTRATION_STATE_before_R09_C2_ASSESSMENT_2026-09-28.md)：保留A3收尾断点，旧推进指令不恢复生效。

- [A3_REGRESSION_CLOSURE_2026-09-28.md](A3_REGRESSION_CLOSURE_2026-09-28.md)：A3主体完成；待核69类中40类已覆盖（39全绿、526待专项），21类转发布前完整回归清单，停止本阶段profile配置与补跑。
- [A3收尾前看板原文](ORCHESTRATION_STATE_before_A3_CLOSURE_2026-09-28.md)：保留Ledger配置前停报断点，旧推进指令不恢复生效。

- [A3_LEDGER_11_PREFLIGHT_BLOCKER_2026-09-28.md](A3_LEDGER_11_PREFLIGHT_BLOCKER_2026-09-28.md)：11类配置前复核触发“需要改守卫即停报”；精确旧datadir非空，新隔离profile未实施，11类全部未执行、仍21类未跑；另10类前置列明，seed标记未删。
- [Ledger 11类配置前看板原文](ORCHESTRATION_STATE_before_A3_LEDGER_11_PREFLIGHT_2026-09-28.md)：保留环境评估断点，历史指令不恢复生效。

- [A3_SPECIAL_ENV_ASSESSMENT_2026-09-28.md](A3_SPECIAL_ENV_ASSESSMENT_2026-09-28.md)：剩余21类精确环境/阶段/原件要求与共用评估；值得分组，建议先Ledger作者2＋验证9类，配置超出换库名，3–5轮仅条件估算。本轮只读、未执行。
- [专用环境评估前看板原文](ORCHESTRATION_STATE_before_A3_SPECIAL_ENV_ASSESSMENT_2026-09-28.md)：保留两批A3回归收尾断点，历史指令不恢复生效。

- [A3_POST526_REGRESSION_2026-09-28.md](A3_POST526_REGRESSION_2026-09-28.md)：526另挂专项后两批接续回归；531–556共26类107项全绿，余21类专项环境/原输入待接续；529/530入口失败、545编排编码失败保留，真实MySQL+MockMvc。
- [本次第2批收尾前看板原文](ORCHESTRATION_STATE_before_A3_POST526_BATCH2_CLOSE_2026-09-28.md)：保留第1批断点，历史指令不恢复生效。

- [A3_526_LOCK_PENDING_SCOPE_2026-09-28.md](A3_526_LOCK_PENDING_SCOPE_2026-09-28.md)：用户将526按疑似缺陷挂待专项，列真实业务入口/执行计划/跳锁/竞争验收条件，不占其他A3回归轮次。

- [A3_526_LOCK_DIAGNOSIS_2026-09-28.md](A3_526_LOCK_DIAGNOSIS_2026-09-28.md)：527绿/528红原样重现，43份锁快照及线程栈确认SKIP LOCKED查询等待pending主键锁；原定性记录保留，后按用户决定改挂疑似缺陷待专项，执行计划与真实写入口未验。

- [A3_NON_E2A_WAVES_2026-09-28.md](A3_NON_E2A_WAVES_2026-09-28.md)：新第1批513–526执行14类，37项36通过/1失败；条码并发commit timeout待定性停报，后续批次未启动。
- [A3_NON_E2A_REMAINING_2026-09-28.md](A3_NON_E2A_REMAINING_2026-09-28.md)：原47类中本次26类绿、21类待跑的逐类目录；8类R08历史验收去重，A3-526疑似缺陷单列待专项，明确各族环境和输入条件。

- [R09_C2_PROJECT_SCOPE_2026-09-28.md](R09_C2_PROJECT_SCOPE_2026-09-28.md)：应用入口接线独立范围、C1依赖与分段验收建议；仅记录，实施待授权，无代码变化。

- [R06_JWT_CLASSIFICATION_FIX_2026-09-28.md](R06_JWT_CLASSIFICATION_FIX_2026-09-28.md)：JWT定性a、39d1b84最小映射修复；单测6项与501–505共39项绿，红记录及同类静态候选清单保留。

- [A3_NON_E2A_REGRESSION_2026-09-28.md](A3_NON_E2A_REGRESSION_2026-09-28.md)：496–500停报红保留；JWT修复后501复验及506–512余7类34项绿，原首批12类均有通过记录，后续未启动。

- [R07_3_HEADER_WITHDRAW_BLOCKER_2026-09-28.md](R07_3_HEADER_WITHDRAW_BLOCKER_2026-09-28.md)：合法零账抬头被WithdrawConfirm空账守卫拒绝；静态确认、未跑HTTP组合，R07独立待办，只记录不修。

- [R08_D_DELIVERY_ACCEPTANCE_2026-09-28.md](R08_D_DELIVERY_ACCEPTANCE_2026-09-28.md)：用户按已验范围接受D整块交付；片1/2/3、四项基本边界及三项修复的证据与范围外限制。

- [R08_D_HEADER_DESIGN_AND_FIX_2026-09-28.md](R08_D_HEADER_DESIGN_AND_FIX_2026-09-28.md)：B3定性A、68a43f8抬头修复及495/run31验收；凭据扫描发现R07抬头撤销空账静态阻断，已停报。

- [R08_D_WITHDRAW_DESIGN_AND_FIX_2026-09-28.md](R08_D_WITHDRAW_DESIGN_AND_FIX_2026-09-28.md)：撤销版本定性A及493修复验收；494抬头零明细凭据缺陷停工，包含全部失败与未验。

- [R08_D_BOUNDARIES_2026-09-28.md](R08_D_BOUNDARIES_2026-09-28.md)：四项基本样本通过证据索引（489/490/493/495）；历史红及组合未验保留，交付决定见D交付确认。

- [R09_JAVA_CONTROL_ACCEPTANCE_2026-09-26.md](R09_JAVA_CONTROL_ACCEPTANCE_2026-09-26.md)：外部可信checkpoint/完整链/严格路径与Python→Java原件消费，Windows/Linux各16项真实进程锁；失败和共享接入限制保留。

- [R06_UNSUBMITTED_SOURCE_ACCEPTANCE_2026-09-25.md](R06_UNSUBMITTED_SOURCE_ACCEPTANCE_2026-09-25.md)：2026-09-26完成其他未提交FORMAL/TEMP原事实核验，attempt67非空MySQL/真实HTTP、613单测通过/1条件skip，64/65失败保留。

- [R06_DRAFT_ARCHIVE_TRUST_ACCEPTANCE_2026-09-25.md](R06_DRAFT_ARCHIVE_TRUST_ACCEPTANCE_2026-09-25.md)：草稿快照历史归档可信核验，attempt63实际Android归档/换钥重启HTTP，62失败资料保留。

- [R06_NONZERO_CANDIDATE_ACCEPTANCE_2026-09-25.md](R06_NONZERO_CANDIDATE_ACCEPTANCE_2026-09-25.md)：21候选五页真实HTTP、独立DB投影与Python54项、非零短装批准/普通确认阻断；完整单测576通过/1条件跳过。

- [管理文档精简前原文（2026-09-25）](management-2026-09-25-before-refresh/README.md)：七份工作区原文按原字节保存，manifest逐项SHA-256校验；包含旧看板历史、派工指令、失败与未验。本次接续核对与全部未提交路径见[快照](MANAGEMENT_REFRESH_2026-09-25_WORKTREE_STATUS.md)，当前事实仍只读主看板。

- [R06_DRAFT_TRANSFER_ACCEPTANCE_2026-09-25.md](R06_DRAFT_TRANSFER_ACCEPTANCE_2026-09-25.md)：Room26原键/回执耐久恢复、Android424/APK/lint及attempt57；对应WORKTREE_STATUS及before_draft_transfer保存路径与旧当前区。

- [R06_DRAFT_INTAKE_ACCEPTANCE_2026-09-25.md](R06_DRAFT_INTAKE_ACCEPTANCE_2026-09-25.md)：V37独立原件接收/可信准备/原响应，服务端577、Android408、attempt56真实HTTP；对应WORKTREE_STATUS与before_draft_intake保存路径和旧当前区。

- [R06_DRAFT_SNAPSHOT_ACCEPTANCE_2026-09-25.md](R06_DRAFT_SNAPSHOT_ACCEPTANCE_2026-09-25.md)：原件根和全设备边界结构闭合、Android406/服务端549；对应WORKTREE_STATUS及before_draft_snapshot保路径和前当前区。

- [R06_DRAFT_SIGNATURE_ACCEPTANCE_2026-09-25.md](R06_DRAFT_SIGNATURE_ACCEPTANCE_2026-09-25.md)：Room25原机事务签名/旧原件不补签、Android400与服务端522全套；对应WORKTREE_STATUS和before_draft_signature保存路径与旧当前区。

- [R06_LOCAL_DRAFT_STOP_ACCEPTANCE_2026-09-25.md](R06_LOCAL_DRAFT_STOP_ACCEPTANCE_2026-09-25.md)：Room24未冻结/零包/未决本机停止原件、23定向/390全套及attempt54冻结HTTP兼容；对应WORKTREE_STATUS与before_local_draft_stop保存全路径和旧当前区。

- [R08_TEMPORARY_EXPORT_ACCEPTANCE_2026-09-25.md](R08_TEMPORARY_EXPORT_ACCEPTANCE_2026-09-25.md)：TEMP原确认导出、全套及attempt53真实HTTP/Chrome/12文件验证；对应WORKTREE_STATUS和before_temporary_export保存全路径及旧当前区。

- [R06_ANDROID_PRODUCT_SUCCESS_ACCEPTANCE_2026-09-25.md](R06_ANDROID_PRODUCT_SUCCESS_ACCEPTANCE_2026-09-25.md)：Android产品成功RAW消费/事务显示/重开复核，367项、APK/lint及attempt48双端1/1和6/6；对应WORKTREE_STATUS及before_android_product_success保存路径和原当前区。

- [R09_TEMPORARY_ARCHIVE_RECOVERY_ACCEPTANCE_2026-09-25.md](R09_TEMPORARY_ARCHIVE_RECOVERY_ACCEPTANCE_2026-09-25.md)：TEMP V2/FORMAL V1混合归档快照及原确认/短装引用恢复；attempt47 IT1/1、11故障及真实HTTP。对应WORKTREE_STATUS与before_temporary_archive_recovery保存全路径和原当前区。

- [R06_RECORDED_DECISION_CONSUMER_ACCEPTANCE_2026-09-25.md](R06_RECORDED_DECISION_CONSUMER_ACCEPTANCE_2026-09-25.md)：首轮零候选真实决定独立消费者，attempt43实库/HTTP/Python46通过；对应WORKTREE_STATUS及before_recorded_decision_consumer保存路径与原当前区。

- [R06_TEMPORARY_CANDIDATES_ACCEPTANCE_2026-09-25.md](R06_TEMPORARY_CANDIDATES_ACCEPTANCE_2026-09-25.md)：正式侧成功/待审TEMP反向提示、稳定回执入口、真实HTTP及Chrome组合；对应WORKTREE_STATUS保存全路径，before_temporary_candidates保存前当前区。

- [R06_TEMPORARY_ARCHIVE_ACCEPTANCE_2026-09-25.md](R06_TEMPORARY_ARCHIVE_ACCEPTANCE_2026-09-25.md)：Room23 TEMP归档V2及同一Android实际冻结至内部成功/归档组合；对应WORKTREE_STATUS保存全路径，before_temporary_archive保存前当前区。

- [R06_TEMPORARY_SUCCESS_ACCEPTANCE_2026-09-25.md](R06_TEMPORARY_SUCCESS_ACCEPTANCE_2026-09-25.md)：Room22 TEMP成功原件严格消费/耐久、非空迁移与独立MySQL/真实HTTP；对应WORKTREE_STATUS保存全路径，before_temporary_success保存原当前区。

- [R06_TEMPORARY_CONFIRMATION_ACCEPTANCE_2026-09-25.md](R06_TEMPORARY_CONFIRMATION_ACCEPTANCE_2026-09-25.md)：首轮正常/独立短装内部决定、原回执及当前效力、完整回滚/真实HTTP/五组MySQL竞争；对应WORKTREE_STATUS保存全路径，before_temporary_confirmation保存前当前区。

- [R06_TEMPORARY_ACCOUNTING_ACCEPTANCE_2026-09-25.md](R06_TEMPORARY_ACCOUNTING_ACCEPTANCE_2026-09-25.md)：TEMP共同记账内部原资格政策适配、失败回滚、真实HTTP与正式续作回归；对应WORKTREE_STATUS列全路径，before_temporary_accounting保存替换前看板。

- [R06_E2A_ABORT_ACCEPTANCE_2026-09-25.md](R06_E2A_ABORT_ACCEPTANCE_2026-09-25.md)：正文前/部分上传后ABORT独立证据、STOP来源绑定、真实HTTP/非空MySQL与失败原件；对应WORKTREE_STATUS保存全部路径，before_abort_evidence保存替换前当前区。

- [R06_E2A_ROOM20_ACCEPTANCE_2026-09-25.md](R06_E2A_ROOM20_ACCEPTANCE_2026-09-25.md)：原请求/原键/STOP签名耐久当次验收；两树路径见同目录 R06_E2A_ROOM20_WORKTREE_STATUS_2026-09-25.md，前当前区见 ORCHESTRATION_STATE_before_android_room20_2026-09-25.md。

- [R06_E2A_CROSS_SOURCE_ACCEPTANCE_2026-09-25.md](R06_E2A_CROSS_SOURCE_ACCEPTANCE_2026-09-25.md)：当前会话跨来源事实、VERIFY 事务修复及独立非空 MySQL/真实 HTTP 结果与限制。
- [R06_E2A_CROSS_SOURCE_WORKTREE_STATUS_2026-09-25.md](R06_E2A_CROSS_SOURCE_WORKTREE_STATUS_2026-09-25.md)：该子项收口两树未提交路径。
- [ORCHESTRATION_STATE_before_cross_source_acceptance_2026-09-25.md](ORCHESTRATION_STATE_before_cross_source_acceptance_2026-09-25.md)：跨来源验收前看板原文，包含旧红测与未验，不作当前事实。

本目录保存已从根目录协作规则移出的完整历史，供需要时定向核查，不作为当前状态入口。

- [ORCHESTRATION_STATE_before_session_handoff_2026-09-24.md](ORCHESTRATION_STATE_before_session_handoff_2026-09-24.md)：新会话交接前当前区原文；最新状态仍以看板顶部为准。
- [R06_E2A_SESSION_HANDOFF_STATUS_2026-09-24.md](R06_E2A_SESSION_HANDOFF_STATUS_2026-09-24.md)：两棵原工作树交接前未提交路径快照。

- [ORCHESTRATION_STATE_before_android_archive_snapshot_2026-09-24.md](ORCHESTRATION_STATE_before_android_archive_snapshot_2026-09-24.md)：原机签名归档快照前的当前区原文。

- [ORCHESTRATION_STATE_before_android_snapshot_2026-09-24.md](ORCHESTRATION_STATE_before_android_snapshot_2026-09-24.md)：Room19 原机全设备快照前的当前区原文。

- [ORCHESTRATION_STATE_before_room19_stop_2026-09-24.md](ORCHESTRATION_STATE_before_room19_stop_2026-09-24.md)：Room19 原机 STOP 同事务落盘前的当前区原文。
- [R06_E2A_ROOM19_STATUS_2026-09-24.md](R06_E2A_ROOM19_STATUS_2026-09-24.md)：本轮两树未提交路径快照与写权断点。

- [ORCHESTRATION_STATE_before_android_transfer_2026-09-24.md](ORCHESTRATION_STATE_before_android_transfer_2026-09-24.md)：E2a Android 增量转入 Room18 主树前的当前状态原文。

- [ORCHESTRATION_STATE_before_android_coverage_2026-09-24.md](ORCHESTRATION_STATE_before_android_coverage_2026-09-24.md)：全设备审计/捕获连续前缀门槛前的当前区原文。

- [ORCHESTRATION_STATE_before_android_wire_2026-09-24.md](ORCHESTRATION_STATE_before_android_wire_2026-09-24.md)：Android E2a 线协议及 STOP 签名原语前的当前区原文。
- [R06_E2A_ANDROID_WIRE_STATUS_2026-09-24.md](R06_E2A_ANDROID_WIRE_STATUS_2026-09-24.md)：两树 Android 线协议增量的未提交路径快照。

- [ORCHESTRATION_STATE_before_history_archive_2026-09-24.md](ORCHESTRATION_STATE_before_history_archive_2026-09-24.md)：历史命令/归档签名轮换兼容前的当前区与两树路径快照。
- [ORCHESTRATION_STATE_before_k_writer_2026-09-24.md](ORCHESTRATION_STATE_before_k_writer_2026-09-24.md)：逐包 K 窄写者前的看板原文与两树未提交路径快照。
- [ORCHESTRATION_STATE_before_keyring_2026-09-24.md](ORCHESTRATION_STATE_before_keyring_2026-09-24.md)：历史公钥轮换前的看板原文与两树未提交路径快照。
- [ORCHESTRATION_STATE_before_source_read_2026-09-24.md](ORCHESTRATION_STATE_before_source_read_2026-09-24.md)：本轮逐包原字节链与原键只读前的看板原文；不作为当前状态。
- [R06_E2A_WORKTREE_STATUS_2026-09-24.md](R06_E2A_WORKTREE_STATUS_2026-09-24.md)：两树本轮管理更新前的未提交路径快照。

- [ORCHESTRATION_STATE_before_archive_coverage_2026-09-24.md](ORCHESTRATION_STATE_before_archive_coverage_2026-09-24.md)：签名归档与审计 PAGE 覆盖增量前的看板当前区原文；不作为当前状态。

- [ORCHESTRATION_STATE_before_audit_endpoint_2026-09-24.md](ORCHESTRATION_STATE_before_audit_endpoint_2026-09-24.md)：AUDIT 原件/原授权及签名 STOP 审计端点写入前的看板当前区原文；不作为当前状态。

- [ORCHESTRATION_STATE_before_audit_prefix_2026-09-24.md](ORCHESTRATION_STATE_before_audit_prefix_2026-09-24.md)：全设备库内审计前缀增量写入前的看板当前区原文；不作为当前状态。

- [ORCHESTRATION_STATE_before_stop_key_writer_2026-09-24.md](ORCHESTRATION_STATE_before_stop_key_writer_2026-09-24.md)：STOP 内部写入公钥来源对齐前的看板当前区原文；不作为当前状态。

- [ORCHESTRATION_STATE_before_direct_key_2026-09-24.md](ORCHESTRATION_STATE_before_direct_key_2026-09-24.md)：直接登记当前公钥修正、attempt2 失败和 attempt3 通过写入前的看板当前区原文；不作为当前状态。

- [ORCHESTRATION_STATE_before_paired_key_2026-09-24.md](ORCHESTRATION_STATE_before_paired_key_2026-09-24.md)：STOP 已存 OPEN/服务器配对公钥重验前的看板当前区原文；不作为当前状态。
- [ORCHESTRATION_STATE_before_raw_closure_2026-09-24.md](ORCHESTRATION_STATE_before_raw_closure_2026-09-24.md)：PAGE 原始对象/文件核对更新前的看板当前区原文；不作为当前状态。
- [ORCHESTRATION_STATE_before_stop_preflight_2026-09-24.md](ORCHESTRATION_STATE_before_stop_preflight_2026-09-24.md)：STOP 隔离实库写者和只读前置门槛更新前的看板当前区原文；不作为当前状态。
- [ORCHESTRATION_STATE_before_page_closure_2026-09-24.md](ORCHESTRATION_STATE_before_page_closure_2026-09-24.md)：PAGE 根只读核对前的看板当前区原文；不作为当前状态。
- [ORCHESTRATION_STATE_before_stop_persistence_2026-09-24.md](ORCHESTRATION_STATE_before_stop_persistence_2026-09-24.md)：STOP 独立持久接收及本轮自审结果写入前看板当前区原文；不作为当前状态。
- [ORCHESTRATION_STATE_before_v36_dynamic_2026-09-24.md](ORCHESTRATION_STATE_before_v36_dynamic_2026-09-24.md)：V36 动态迁移/OPEN 外键自审前的看板当前区原文；不作为当前状态。
- [ORCHESTRATION_STATE_before_stop_parser_2026-09-24.md](ORCHESTRATION_STATE_before_stop_parser_2026-09-24.md)：STOP 纯解析和 attempt4 写者验证收录前的看板当前区原文；不作为当前状态。
- [ORCHESTRATION_STATE_before_stop_compat_2026-09-24.md](ORCHESTRATION_STATE_before_stop_compat_2026-09-24.md)：STOP 事件标识协议兼容复审前的看板当前区原文；不作为当前状态。

- [AGENTS_2026-09-22.md](AGENTS_2026-09-22.md)：2026-09-22 精简前 `AGENTS.md` 的原文完整字节副本。其 SHA-256 为 `C6F6F8F7BDA490DACCDC23F9AA5C44FC6D1F0A2A8598288D20AEAA1372EE7999`。
- [ORCHESTRATION_STATE_before_e2a_attempt6_2026-09-23.md](ORCHESTRATION_STATE_before_e2a_attempt6_2026-09-23.md)：E2a attempt6 结果写入前，总调度看板旧当前区的原文快照；不作为当前状态。
- [ORCHESTRATION_STATE_before_e2a_trigger_preflight_2026-09-23.md](ORCHESTRATION_STATE_before_e2a_trigger_preflight_2026-09-23.md)：E2a 触发器预检续作写入前，总调度看板旧当前区的原文快照；不作为当前状态。
- [ORCHESTRATION_STATE_before_e2a_trigger_decision_2026-09-23.md](ORCHESTRATION_STATE_before_e2a_trigger_decision_2026-09-23.md)：E2a 触发器授权决策卡写入前，总调度看板旧当前区的原文快照；不作为当前状态。
- [ORCHESTRATION_STATE_before_e2a_attempt8_2026-09-23.md](ORCHESTRATION_STATE_before_e2a_attempt8_2026-09-23.md)：单助手接管及 E2a attempt8 动态验收写入前，旧当前区的原文快照；不作为当前状态。
- [ORCHESTRATION_STATE_before_e2a_independent_review_2026-09-23.md](ORCHESTRATION_STATE_before_e2a_independent_review_2026-09-23.md)：attempt8 独立证据复核与原样本完整单测写入前的当前区快照；不作为当前状态。
- [ORCHESTRATION_STATE_before_e2a_intake_read_2026-09-23.md](ORCHESTRATION_STATE_before_e2a_intake_read_2026-09-23.md)：E2a intake 查询只读事务与本轮单测写入前的当前区快照；不作为当前状态。
- [ORCHESTRATION_STATE_before_e2a_intake_recovery_2026-09-23.md](ORCHESTRATION_STATE_before_e2a_intake_recovery_2026-09-23.md)：E2a intake 冲突分类、回滚后独立重查与本轮单测写入前的当前区快照；不作为当前状态。
- [ORCHESTRATION_STATE_before_e2a_open_2026-09-24.md](ORCHESTRATION_STATE_before_e2a_open_2026-09-24.md)：E2a OPEN 接收及分页协议校验续作前的当前区原文快照；不作为当前状态。
- [ORCHESTRATION_STATE_before_e2a_page_read_2026-09-24.md](ORCHESTRATION_STATE_before_e2a_page_read_2026-09-24.md)：PAGE 解析、私有文件存储与已存 OPEN 绑定读取续作前的当前区原文快照；不作为当前状态。
- [ORCHESTRATION_STATE_before_e2a_page_write_2026-09-24.md](ORCHESTRATION_STATE_before_e2a_page_write_2026-09-24.md)：PAGE 写者和原始对象文件接收续作前的当前区原文快照；不作为当前状态。
- [ORCHESTRATION_STATE_before_e2a_item_stage_2026-09-24.md](ORCHESTRATION_STATE_before_e2a_item_stage_2026-09-24.md)：非空 PAGE item 严格解析及 V36 跨页索引续作前的当前区原文快照；不作为当前状态。
- 当前 Git、执行者、证据、环境和下一步只读 [总调度看板](../ORCHESTRATION_STATUS.md) 顶部 `CURRENT_STATE_START/END` 区段。
- 长期规则和新会话接续方式只读根目录 [AGENTS.md](../../AGENTS.md)；仓库操作规则见 [Git 管理说明](../GIT_WORKFLOW.md)。

归档文件不应被改写来表示新状态。阶段收尾和接续断点写入当前状态区段；需要保留的旧证据按专项文档和提交 SHA 定向引用。

原文字节副本中的相对链接仍按仓库根目录解释；归档位置不会改变这些链接的基准，也不得为修正链接而改写副本。
# 2026-09-25 Room27 产品队列

[停止产品入口与持久补传验收](R06_DRAFT_PRODUCT_ACCEPTANCE_2026-09-25.md)：本批实现/自审/最终444项及attempt59；非现场、非上线。接管前原当前区见 [归档](ORCHESTRATION_STATE_before_product_review_2026-09-25.md)。
# 2026-09-26 接续增量

- [R09 Job恢复代次屏障验收](R09_JOB_FENCE_ACCEPTANCE_2026-09-26.md)：V38旧Job保全、实例启动代次/持锁事务；attempt72专项+23回归、attempt71真实导出/浏览器，完整服务端618pass/1skip。

- [R09库外控制本地模块验收](R09_LOCAL_CONTROL_ACCEPTANCE_2026-09-26.md)：追加历史/CAS/进程锁及崩溃恢复，29新测试通过，完整R09 103通过/11跳过；不是共享门禁或生产恢复完成。
- [进入库外控制前看板快照](ORCHESTRATION_STATE_before_recovery_control_2026-09-26.md)。
