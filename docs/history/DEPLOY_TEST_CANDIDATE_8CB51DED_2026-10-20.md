# test 候选包部署与基本功能验证

日期：2026-10-20。所有执行时间均为 UTC。此报告记录用户授权的 test 后端部署，不代表生产发布、Android 客户端交付或真实数据预检通过。

## 结论

候选提交 `8cb51ded3ef2cd6cd45eaca09d8947ce068e89cd` 已部署至 test 并保留。服务启动、外部 API 健康检查 200、授权账号登录和基本只读 API、后台 worker 实际执行、XLSX/PDF renderer 报表生成及下载完整性检查均通过。部署迁移基于 test 现有合成数据，另在隔离 schema 验证了本次备份的恢复与迁移。

这只能证明候选包在当前 test 环境及现有合成数据上能启动和跑通本报告覆盖的基本功能。**真实业务数据升级、恢复及其 API/worker 验证没有完成，依用户决定继续列为 C 档现场条件阻断。** 不写成真实数据验收、完整预检通过或可部署生产。

test 最终保留新候选包。若验证失败原计划会恢复原数据库、私有文件、配置和发布指针；本次验证成功，故没有执行回滚。回滚脚本已准备，但本次回滚路径未实际验证。

## 输入与边界

- 候选 Git SHA：`8cb51ded3ef2cd6cd45eaca09d8947ce068e89cd`；未修改候选源码。
- 候选包：`preflight-8cb51ded-20261020-01.tar.gz`，SHA-256 `8539177d1274864b94772c9941a1ff3dfab9735dda545b4fca988f3232c37ad0`。
- 包内 manifest SHA-256：`3a0102f95c4c4ea44ba2200f9105e74e1f0fbba1e7a36c3d48d07574274425bb`；source manifest SHA-256：`ea431b20a4288a3d59204b3f7374beb6c0490275e3e74ffa2267536ff70e5c72`；server JAR SHA-256：`6c41d8358b402c1e2594d630f71eeb7b848fdcb2181428dd54edb83dfe3d16f8`。
- 部署前后候选 verifier 均校验 manifest、运行时与 JAR 自洽、647 个 artifact hash 和 5856 个 source hash；激活后再次核对 release 路径。候选归档字节未变。
- 仅操作授权的 test 主机 `lavm-z54ysvgz9n`、test schema `acrylic_outbound_test` 和本次任务的隔离 schema；没有连接生产、原库或其他未授权数据库，也没有操作 main、Release、正式签名或生产开关。
- 这是后端部署。Web 发布目录和 Android APK 未更换；不得据此声称客户端已升级。`localDeletionAllowed=false` 及生产关闭边界保持。

## 备份与维护窗口

维护窗口先记录原发布指针、manifest、systemd 服务状态和 Nginx 配置；快照 PDA03 后写临时维护围栏，要求连续三次 HTTP 503 才认为围栏稳定。之后停止 test API、worker 和备份 timer，再备份数据库、私有文件、配置及旧发布状态。

- 维护开始检查点：`2026-10-20T13:37:52.770207Z`；原发布为 `/opt/acrylic-outbound-test/releases/pda-orderflow-20260923-01`。
- PDA03 围栏前快照：`13:37:53.887380Z`；围栏连续三次 503 就绪：`13:38:09.895388Z`；围栏内快照：`13:38:11.012658Z`。
- 备份完成：`13:40:08.128549Z`。远端受限目录为 `/opt/acrylic-outbound-test/deployment-evidence/candidate-8cb51ded-20261020-01/before`，权限 `0700`。备份含完整 MySQL dump（含 routines、events、triggers）、数据库建库语句、row facts、完整私有文件目录、Nginx/systemd 配置和发布目录/指针；逐项 SHA256SUMS 核验通过。
- 数据库 dump `database.sql.gz` SHA-256：`8a4a9a64b317195bfc233249a0ab27bd88ee89a27cdf9ce8f2bca3db0f011105`。
- 私有文件归档 `private-files.tar.gz` SHA-256：`b8c1796f930a51cce560f00924d56fde700e0de5cc52f46e0cdfa336e30a49e2`；文件事实摘要 `file-facts.json` SHA-256：`0553c6536dded693cce8506fcef1fee13762782238c5f15d16667abf387bbcc0`，覆盖 220 个文件、6,828,098 字节。
- 配置归档 `configuration.tar.gz` SHA-256：`a210a2668139beab3e2d29064334deb7f9039af924d3eb2466dbe255df078edc`；发布状态归档 `release-state.tar.gz` SHA-256：`8ebbb51b4c597d09d874e8bdbfb67aadfc3a293055b6a018ae2b453e56a916b7`。这些归档只留在服务器 root-only 备份目录，不进入 Git 或随安全证据包下载。
- Nginx 原配置 SHA-256 `a48ff1d79163a3495f768dce8224ef8bd3b9caaf6162542039d18bee5adba463`；撤除围栏后文件与原配置字节一致。

受限证据包 `evidence-safe.tar.gz` 已下载到 `.local/test-candidate-8cb51ded-20261020/run01/`，服务器及本地 SHA-256 均为 `7680634d60ff6a9068a6c0a63d41a06e34b05c4c2df0ea3d97f4e4d0787585a7`。包内保留运维回执、日志、健康检查、验证 JSON、PDA 快照和脱敏前仅适合本机访问的 HTTP 回执；该目录属于 `.local`，不得提交。私密配置、凭据、cookies、备份 dump 和原始发布归档不在其中。

## 预部署 Linux 运行检查

由于包内 Linux Java 实际运行是本次部署前需要确认的事项，先使用候选同一 JAR 和合成隔离数据验证启动及健康检查：

1. 隔离尝试 1 使用受限 schema 用户，MySQL 在共享 binlog 策略下拒绝建 trigger（1419/SUPER privilege 与 `log_bin_trust_function_creators=0`）。未改全局变量或扩大常规运行账号权限。
2. 隔离尝试 2 尝试独立 mysqld datadir，遭系统 AppArmor 拒绝。保留 DENIED 证据，没有修改策略或改路径绕过。
3. 隔离尝试 3 使用只对新隔离 schema 执行迁移的本地 DBA 凭据，再以受限 schema 用户运行应用；候选 Java 实际启动，HTTP health 返回 200。检查后主动停止隔离进程。transient systemd unit 将预期 SIGTERM 143 记作 failed，因此 unit 最终状态不作通过证据，启动日志与停止前 health 200 是判据。

隔离数据库及每次日志均保留。尝试 1/2 的失败未被后续结果覆盖。

## 恢复演练与部署

先把备份完整恢复到允许清单中的合成隔离 schema，并按 schema hash 与 row hash 逐项比较：101 张表、129,644 行完全相同。这个结果只证明 test 合成备份在该隔离路径可还原，不满足真实数据闸门。

随后用候选包迁移隔离副本，成功后迁移 test schema，再切换 test 的 current 指针并启动服务。test 数据迁移日志显示本候选所需迁移均成功。迁移阶段因共享 MySQL 的 trigger/binlog 权限要求，使用服务器已有本地 DBA 凭据，仅对本次允许的 schema 执行候选迁移；应用仍使用受限运行账号，没有修改 MySQL 全局变量、授权或常规运行配置，凭据只在进程内读取且不进入日志。主要时间点：

- 隔离合成副本恢复核对：`13:40:28.845290Z`。
- release 包完整性核对：`13:40:41.693202Z`。
- 隔离合成副本迁移：`13:40:59.561365Z`。
- test schema 迁移：`13:41:17.236557Z`。
- API/worker 激活：`13:41:48.486237Z`。
- 基本功能验证完成：`13:43:00.301722Z`。
- PDA03 围栏内状态比对：`13:43:01.483819Z`；Nginx 恢复：`13:43:01.699415Z`。

激活脚本在切换后轮询内部端口，前十次快速探测连接被拒绝，随后服务就绪。脚本 stderr 中的失败探测已保留；它们是启动等待过程，最终有界探测健康检查通过，没有用它们冒充服务失败或删除其日志。

## 功能验证

通过标准 CSRF 与登录流程访问 test API，使用授权 admin 身份及现有已完成合成订单，不批量灌入数据、不修改产品代码。就绪检查使用产品既有 `/api/v1/auth/csrf`（HTTP 200），另验证登录、当前身份、订单列表、订单详情和商品列表请求均成功；没有把该入口当作全依赖诊断接口。

worker 真实执行两项 export job，均第一次尝试成功，且 worker journal 中存在对应 `SUCCEEDED` 记录：

| 格式 | Job ID | 结果字节 | 下载 SHA-256 |
| --- | --- | ---: | --- |
| XLSX | `01a1598c-0475-76ae-9952-7d41903d355d` | 23,022 | `a698953eb8feb95068ff0f7e14388b6fe1088cc3f09d1fe4d6e2f420c74ded5c` |
| PDF | `01a1598c-7f85-72e7-9da6-373a81b2958c` | 381,416 | `e1d0eb2ea8c1a7c709e6b9560cb9217ae782c8539a743ce05c6bbfeecd36926a` |

下载的文件长度及 SHA 与 API job 记录相符；XLSX ZIP/workbook/worksheet 结构有效，PDF 有效 PDF header 和 EOF 标记。没有做 PDF 视觉排版审阅，故不声称 renderer 的视觉验收。Linux Java renderer 和 Chromium 在实际报表任务中运行。

部署结束后重新通过 test 公共 HTTPS 域名检查既有 API 就绪入口，返回 HTTP 200。API、worker 和 backup timer 均 active；API/worker `NRestarts=0`。最终 release 为 `/opt/acrylic-outbound-test/releases/preflight-8cb51ded-20261020-01`，manifest SHA 与候选一致，JAR SHA 与部署前包一致。

## PDA03 与维护收口

在受保护窗口内维护前后订单和设备记录相等。围栏撤销并恢复同步入口后，`last_seen_at` 前进，说明设备再次联机；之后订单记录仍与维护前相等。没有新增、删除或撤销 PDA session；唯一差异是现有 session 的 access token 到期时间正常续期。

**首次维护演练留下的 PDA03 快照正文丢失，历史完整性缺口仍存在。** 本报告仅记录这次新窗口成功采集和比对的数据，不倒推或补造首次快照，也不声称首次演练有逐订单核对。

维护围栏已移除，Nginx 原配置字节恢复，PDA03 允许同步，test API/worker/backup timer 正常运行。其他 test 使用者依赖主机服务的情况不在本验证范围内；本次维护依据用户授权执行。

## 仍未通过或未验证

- 真实数据来源、升级、备份还原及真实数据上的 API/worker：**C 档阻断，未尝试，以合成恢复结果替代不成立。**
- 固定候选十九项跳过测试计通过 0；环境跳过 2 项，条件未启用 17 项，逐项原因见[分类报告](DEPLOY_FIXED_CANDIDATE_SKIPPED_TESTS_2026-10-20.md)。
- Android 固定候选编译另有 10 项 unresolved references 失败；本次没有修复或部署 Android。
- PDF 只检查文件结构和 hash，未做视觉复核。回滚执行路径未实跑。
- 早期脚本 staging 误判包的根目录布局，修正的是本机暂存目录结构，没有改候选包。
- 本机 Linux 隔离启动早期失败原因及判定见本报告“预部署 Linux 运行检查”；不隐藏 MySQL 权限边界和 AppArmor 拒绝。
- 收口时一次探测误用了未配置的域名，curl exit 6、状态 000；这是本助手命令输入错误，不是 test 服务故障。保留失败回执，按配置中的正确 test 域名检查返回 200。一次本地误调用 `transport.main` 产生 AttributeError，未发起 SSH，随后按该脚本命令行入口调用成功。
- 远端部署 transient unit 已正常退出，`Result=success / ExecMainStatus=0`，没有部署或备份 writer 残留；本机等待该已退出 unit 的 SSH 会话未自动结束，核实后仅中断本机等待，未停止 test API/worker。此中断不是远端部署失败，原等待输出仍保留。

## 证据位置

- 服务器受限完整备份和部署回执：`/opt/acrylic-outbound-test/deployment-evidence/candidate-8cb51ded-20261020-01/`；其中备份子目录权限 `0700`。
- 本机受限回执、证据安全包、校验脚本和本窗口后设备快照：`.local/test-candidate-8cb51ded-20261020/run01/`。
- 固定候选 19 项 skip 分类：[DEPLOY_FIXED_CANDIDATE_SKIPPED_TESTS_2026-10-20.md](DEPLOY_FIXED_CANDIDATE_SKIPPED_TESTS_2026-10-20.md)。
- 前序维护窗口根因与完整性限制：[window04 报告](DEPLOY_MAINTENANCE_RETRY_WINDOW04_2026-10-20.md)。
- 此阶段仅用于候选 test 基本运行验证，不计 D/O/U 整体完成，不打开生产能力。
