# R09 C2-G0 验收与阻断记录（2026-09-28）

> **这是设计验收，不是运行验收。**验收对象是[G0 设计定稿](R09_C2_G0_DESIGN_2026-09-28.md)；本轮零代码变更，未编译、未跑单测/IT/HTTP/数据库，未启动服务。B1 尚未实施。

## 1. 验收结论

| 项 | 结果 |
| --- | --- |
| G0 六项必做设计是否全部定案 | **是**（设计 §3–§7）：初次 NORMAL 与租约衔接、接纳原子性与证据连接隔离、根/子租约与关闭后收尾、UNKNOWN 与多进程语义、启动与框架边界、B1 范围收紧 |
| 是否仍有交回用户的技术选型 | **否**。类名、接口签名、连接来源、租约传递方式、拒绝码、过滤器顺序、框架路径的纳入/不纳入均已定案，落在设计 §4–§9。 |
| 设计阻断 | **无阻断**。唯一保留的**授权性**事项：§6.4“无任何耐久标记”分支需要生产释放/外部强制隔离权限，本轮不实现、不开放（见 §4）。 |
| 结论口径 | **G0 设计完成；B1 尚未实施，待独立授权。**不得写成 C2 已实现、B1 已完成或运行验收通过。 |
| 新发现真实产品缺陷 | 无。本轮未做动态验证，设计中的接线风险均为源码级判断，不得写成已复现故障。 |

## 2. 本轮实际执行与结果（设计验收的依据）

| # | 检查 | 命令/方法 | 结果 | 原始证据 |
| --- | --- | --- | --- | --- |
| 1 | 基线与远端 | `git rev-parse`、`git status --porcelain --untracked-files=all`、`git rev-list --left-right --count HEAD...origin/codex/development` | 分支 `codex/development`；HEAD `27cc85a`；工作区干净；`0 0` | `01-git-*.txt` |
| 2 | 差异自检 | `git diff --check` | 退出 0（无空白错误） | `01-git-diff-check.txt` |
| 3 | 写者核对 | `.git/index.lock` 存在性；`Get-CimInstance Win32_Process` 过滤 `acrylic-pda-outbound`/`r06-e2a-migration`；最近 60 分钟被修改的受控源码文件 | 无 index.lock；除本会话自身外无进程引用本树；受控源码 0 个近期改动 | `01-writer-checks.txt`、`04-writers.txt` |
| 4 | 监听/进程 | `Get-NetTCPConnection -State Listen`；进程清单 | 19330/19434/19437/23308/8080/8081/9090 无监听；19387=DSH；3306=系统 mysqld；Gradle daemon 保留 | `05-listeners.txt`、`05-processes.txt` |
| 5 | 共享树只读保护 | `git -C D:/acrylic-pda-worktrees/r06-e2a-migration rev-parse HEAD`、`status --porcelain --untracked-files=all` | detached `ea4d166`；30 修改 + 67 未跟踪 = 97；状态列表 SHA-256 `C76522C509FFCBA1D66436E290D6146E553D6761CC278D6022445FFD327BD9AC`（与本轮前一批快照相同） | `03-shared-tree-*.txt` |
| 6 | 共享树内容哈希比对 | 对 `dsh-handoff-20260928-ec4be3b/dirty-file-hashes-initial.json` 的 138 条逐文件重算 SHA-256 | 138/138 一致；mismatch 0；missing 0（6 棵树） | `02-shared-tree-hash-verify.txt` |
| 7 | 源码坐标核对 | 逐文件阅读 `recovery/*`、`auth/*`、`common/*`、`jobs/BackgroundWorkerProcess`、`exports/ExportRendererProcess`、`application.yml`、`server/build.gradle`、R09 控制协议（Python `control_store.py`/`control_admission.py`） | 设计 §3–§7 的每条源码坐标均已核对（见 `06-source-coordinates.md`） | `06-source-coordinates.md` |
| 8 | 历史引用核对 | 核对交接/C1 复验/C2 范围与入口评估中的引用与结论 | 一致；C1 已按限定范围通过、C2 未实施的口径不变 | `07-reference-check.txt` |
| 9 | 文档链接检查 | 正则提取本地 Markdown 链接并逐一 `Test-Path` | 全通过（计数见证据文件） | `08-doc-links.txt` |
| 10 | 事实块刷新与校验 | `powershell -NoProfile -File tools/version_facts.ps1 -Write` 然后 `-Check` | `-Write` 更新生成块；`-Check` 通过（代码派生事实一致；看板当前区在 100 行/8000 字符内） | `09-facts-write.txt`、`09-facts-check.txt` |
| 11 | 授权范围核对 | 暂存前后 `git status`/`git diff --cached --name-status` | 仅授权文档变更（本记录、设计定稿、看板、欠债、进度、历史索引、原提案导航） | `10-staged.txt` |

**命令模板（未来 B1，本轮未执行）**与本表分开，见设计 §9.4。本节列出的都是本轮**实际运行**的只读/文档级命令。

## 3. 场景推演验收

九场景（正常接纳、关闭竞争、缺侧证据、协调锁重入、身份失效、子令牌滥用、UNKNOWN、跨进程重启、初始化未就绪）已逐条推演并给出唯一预期结果与判定依据，见设计 §10 表。推演为**源码级**（含 `RecoveryControlProcess` 既有 nested/threaded 证据与 `RecoveryTransactionFixture`/Python 控制工具的既有能力），**未做动态复现**。

## 4. 设计阻断与保留事项

| 项 | 状态 | 说明 |
| --- | --- | --- |
| §6.4“写 `pending` 失败→无任何耐久标记”分支 | **已定案但存在残余缺口**：进程 fail-stop，但新进程无法自动得知，必须外部强制隔离。清除/解除属生产释放能力，**未授权、本轮不实现** | 不是设计未完成，而是需要生产权限的范围性事项 |
| Python 侧毒化语义同步（§3.5 第 2 条） | 已列入 B1 实施清单，需 Java+Python 同批双侧测试 | 未实现 |
| C1 未验边界 | 首次插入提交丢回执、独立进程重启、真实网络故障、断电、Linux 持久性、现场原资料组合 | 继续保留，未被本设计覆盖 |
| C2 动态验证 | 全部未做 | B1 及后续入口均未实施 |

## 5. 未运行的验证（明确记录）

编译、单测、IT、真实 HTTP、数据库读写、完整应用冒烟、跨进程、断电/Linux 持久性、现场组合：**本轮全部未运行**。因此本记录不能作为任何动态行为已通过的证据；设计 §9 的用例与命令模板在 B1 实施前不得被引用为“已执行”。

## 6. 后续 B1 验收要求（承接设计 §9）

1. 先过完整应用冒烟（证明准入组件实际启用、过滤器顺序正确、Session 走预期连接路径），再进 IT。
2. S4a（正常收尾成功）与 S4b（故障后安全拒绝）必须是两个独立用例，各自唯一判据，不得合并为“完成或毒化都算通过”。
3. 拒绝类用例必须断言**拒绝前零业务写**。
4. C1（`C1InitialNormalHttpIT`）与 105（`RecoveryAdmissionEntryChecks`）既有断言全绿是 B1 新用例的前置。
5. B1 只做 HTTP/Spring Session 外层与必要租约衔接；`jobs/*`、Worker、各文件入口、渲染子进程、Flyway/Bootstrap 接线不在本轮授权内。

## 7. 本机证据清单

目录：`.local/r09-c2-g0-design-20260928/`（本机忽略目录，不提交；不含凭据、数据库或业务原件）。

| 文件 | 内容 |
| --- | --- |
| `00-batch-dir.txt` | 本批次目录名（若旧批次存在则新建不覆盖的新批次） |
| `01-git-*.txt` | 分支、HEAD、远端领先/落后、status、最近提交、`diff --check` |
| `01-writer-checks.txt` | index.lock、diff 退出码等写者核对 |
| `02-shared-tree-hash-verify.txt` | 138 条脏文件哈希逐项比对结果 |
| `03-shared-tree-*.txt` | 共享树 HEAD、97 条状态列表、计数与列表哈希 |
| `04-writers.txt` | 引用本树的进程清单与近期受控文件改动 |
| `05-listeners.txt`、`05-processes.txt` | 端口监听与相关进程 |
| `06-source-coordinates.md` | 设计所依据的源码坐标与行号表 |
| `07-reference-check.txt` | 历史文档引用核对 |
| `08-doc-links.txt` | 本地链接检查结果 |
| `09-facts-write.txt`、`09-facts-check.txt` | 事实块刷新与校验输出 |
| `11-kanban-current-region.md` | 写入看板当前区的正文快照（拼接前原文） |
| `12-scenarios.md` | 九场景推演表（源码级，含残余缺口说明） |
| `10-staged.txt` | 暂存/提交清单与最终推送回执 |
