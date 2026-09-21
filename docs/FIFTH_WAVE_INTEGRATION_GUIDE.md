# 第五轮共同基础整合记录

2026-09-21，执行者 W3 `/root/foundation_protocol_verify`。独立工作树 `D:/acrylic-pda-worktrees/fifth-wave-integration`，分支 `codex/fifth-wave-integration`，管理起点 `b3e3fcd4987c426cf1086e8189bb981297fe408f`。本轮完成已审来源/协议基础与独立验收材料的组合验证；没有开放 TEMP 证据写、命令执行、决定或放行。

## 已审来源与保留历史整合

| 来源 | 最终固定提交 | 合并节点 / 文件 |
| --- | --- | --- |
| W1 生产基础、旧消费者与补测 | `04a679beb9b5e250d0c67101edb97fdf1c264fac` | `156890c6fe1bfd128da2b731ba43ac6ee6369f06` / 21 文件 |
| W2 非空升级、关系和负向验收 | `8bdf1b87c3b18110c2551bdc89ff9c1f7d6728b3` | `4ae430637cba94fb67f3caa615f90c33f440c2a1` / 20 文件 |
| W3 独立协议消费者与两方复审 | `1c5fca8148947d35b8169a04e03680ca33e790a6` | `c74d91cf2c15e394b29e209a71545dd2a41563aa` / 22 文件 |

三次 `merge --no-ff --no-commit` 自动合并无冲突，随后分别提交。每次暂存路径集合和逐文件 Git blob 与已审来源一致；63 文件原字节 SHA-256 和 Git blob 保存于 ignored `.local/wave5/{W1,W2,W3}-merge-manifest.json` 与 `combined-source-manifest.json`。生产 Java/V34 与首生产冻结 `0311dfaf4255671a605a17aa3defc145df9621a6` 完全相同，没有整合修码。

五份主调度管理文件相对起点逐项 Git blob 不变。本轮只新增本指南；W2 的绝对环境护栏、原 C0 和其他执行者原件均保持。W1 已独立预核三次历史、63 文件、生产一致性和本机组合护栏，最终候选仍须其固定 SHA 复审后，由主调度明确安排 development 快进。

作者与独立审查证据分别见 [W1 验证](parallel/R06_FOUNDATION/VALIDATION.md)、[W2 非空升级](parallel/R06_FOUNDATION_VERIFY/HANDOFF.md)、[W3 离线交接](parallel/R06_FOUNDATION_PROTOCOL/HANDOFF.md)、[W1 固定复审](parallel/R06_FOUNDATION_PROTOCOL/W1_REVIEW.md)、[W2 固定复审](parallel/R06_FOUNDATION_PROTOCOL/W2_REVIEW.md)。作者 56 单元/29 IT、W2 分阶段验收和 W3 作者 233 项均不计作下表组合新跑。

## 独立环境与保护

仅新建 MySQL 8.0.46 实例 `127.0.0.1:19325`，datadir 为本树 `.local/mysql/data`，新库 `acrylic_outbound_wave5_20260921`、新账号 `outbound_wave5_20260921`。创建前确认目标目录不存在、19325 及原 13316/19321/19322/19323/19324 无监听；创建和运行前精确核对端口、datadir、DATABASE()、CURRENT_USER()。应用账号只有本库权限及 performance_schema 的 data_locks/data_lock_waits/threads 三表 SELECT，不授予旧库权限。

凭据、3072 位 RSA 开发签名和 `.local/private-files` 均为本轮独立资料。原授权 sku 样本只复制到本树 ignored `docs/sku.txt` 并核摘要一致；数据库、配置、私钥、样本、JAR 和原始日志不进入 Git。本树自行构建报表依赖；字体实际下载后由原构建任务核对固定 SHA-256 `2c76254f6fc379fddfce0a7e84fb5385bb135d3e399294f6eeb6680d0365b74b`，没有复用旧树的报表构建产物。

组合进程清除所有 FOUNDATION_* 开关，环境清单为 `foundationVariables: []`。六类 W2 专用夹具全部在执行前禁用，共 13 项预期跳过；未改其 19324 绝对护栏，也未跑其种子、复制、非空升级或破坏性反例。19324 始终停止，`v33-original.json`、`seed-manifest.json`、`v34-preservation-verified.json` 在组合前、测试后、停机后 SHA-256 一致。纯环境单元仅验证字符串配置，不连接 W2 数据库。证据为 `combination-environment.json`、`disabled-fixtures.json`、`protected-{before,after-tests,after}.json` 和端口记录。

原 R05/R06/R09 库与恢复断点、AVD/M71、旧 B/C/D、HTTP/代理/现网均未操作。主目录仅原未跟踪 out.xlsx，摘要仍为 `C3BABA70BC900975503457C5468363DE91417F156F2441ED71A3B7BE5E8FB6C4`。

## 本轮实际组合结果

首次实际 Gradle 构建成功，6 分 52 秒，11 个 task 实际执行；没有重启构建或重复测试。原日志 `.local/wave5/server-first.log`，原始 XML 已独立复制至 `server-first-results/test` 和 `server-first-results/integrationTest`，各套件数量与 SHA-256 在 `test-summary.json`。

| 新跑范围 | 实际结果 |
| --- | --- |
| 后端单元 | 9 类、71 项通过，0 失败/错误/跳过 |
| W1 消费者与共同占用 | 来源基础 5、精确投影 2、两连接顺序 2、BarcodeSync 6、PcOutbound 9、TemporaryAvailability 5，共 29 项通过 |
| 七类既有 API | Continuation 12、Disposition 11、PC lifecycle 5、Recall 11、Submission 12、TEMP source evidence 5、TEMP submission 14，共 70 项通过 |
| 实际集成汇总 | 13 类、99 个不同 IT 通过，0 失败/错误；与作者成绩不累计 |
| W2 专用夹具 | 另 6 类、13 项按计划禁用；XML 共 112 条记录，不能称 112 项执行通过 |
| 独立生产协议消费者 | 233 项通过，0 失败/错误/跳过，2.860 秒；实际消费本组合树生产源码 |
| 正式接口契约 | OpenAPI 3.1、info.version 0.29.0、116 paths 验证通过 |
| 编译与构建 | Java/测试编译、报表依赖及 bootJar 成功 |

集成从本轮空库迁移至 V34，最终 101 表，V1–V34 的 34 条迁移全部成功；这不是原库升级或灾后恢复验收。W2 的非空升级/负向原证据继续独立保留。新 JAR 为 `server/build/libs/acrylic-outbound-server-0.1.0.jar`，274,537,619 字节，SHA-256 `04ce00067755a175a1442dcb826d51703bf5179c9d7ee8935d8e98d2819a1155`。

离线日志 `offline-first.log` 固定 188 个生产字节/结构、6 个跨语言 hash、38 个合成签名消费者和 1 个九源码清单检查。receipt/control 仍只有对象和域 hash 基础；合成签名不能证明真实可信操作者/权限、生产回执签发或台账写者完成。C0 未改未重跑，旧 153 项不计本轮。

Web 仅执行 `npm ci --ignore-scripts --no-audit --no-fund` 和 `npm run check:contract`，日志 `npm-ci.log`、`contract-first.log`；没有运行浏览器、Web 全量测试或设备。

## 本轮真实并发证据

`.local/evidence/source-concurrency/source-first-true.json` 和 `source-first-false.json` 均为新跑，摘要汇总在 `.local/wave5/concurrency-summary.json`。两份都是 READ-COMMITTED，连接 22 持有、连接 23 等待 `user_management_guard` 的 X,REC_NOT_GAP；这是 G 等待，不声称直接 K 行锁等待。

来源先提交时，正式服务返回冲突，唯一 K 保持 SOURCE_RESERVED、原 evidence set 不变；正式先提交时，合成来源写者返回冲突，唯一 K 保持 DISPATCHED、evidence set 为 null。两份终态 dispatch_cycle 均为 1。来源一方仍是合成窄 SQL fixture，不能冒充生产证据接收/完整 C09/C15。

精确投影断言按目标 baleId 找唯一条目，分别要求条码增量 RESERVED、正式 PreparationPayloads 的 available 为严格 JSON false；PC 候选/录入、TEMP 投影与库存重发保留原 K 同批通过。

## 中断、失败与环境收尾

模型服务曾返回 429 exceeded retry limit，发生于环境准备后、产品测试前。恢复时核对原 PID 17204、19325、原 datadir/账号和空库，原签名/配置仍在，直接续接；没有重新初始化、重复三次合并或重新生成种子。这是模型服务中断，不是产品失败。

字体下载较慢时，PowerShell 目录项显示 0 字节，原计划精确停止当前构建后使用已验字体参数。执行停止前的实际文件 stat 已显示 11,616,128 字节并继续增长，所以没有终止任何进程，也未修改参数；原构建自行下载并校验完成。观察及线程材料保留于 `font-download-observation.json`、`font-download-thread.txt`。没有将计划操作记成已执行。

本轮产品测试和构建无失败、错误；13 项专用禁用按上表单列。编译的既有 deprecated/JVM class-sharing 提示不算失败。准备读取时误查不存在的辅助路径属于只读命令错误，未影响源码或数据。W1 作者历史样本/DDL/fixture 失败、W2 编译/复制失败和原 seed GET 401 均按各自交付继续保留，不改写为组合首轮失败或全部历史通过。

2026-09-21 12:13（Asia/Shanghai）再次精确核对原进程、端口、datadir 和账号后，经 mysqladmin 正常停止19325。持久进程退出码 0，日志包含 Normal shutdown/Shutdown complete；所有上述旧端口及19325均无监听，数据库、私有文件、签名和证据保留。见 `process-before-shutdown.json`、`before-shutdown.json`、`shutdown.json`、`mysql-session-stop.json`。停止后受保护 W2 原证据摘要仍一致，其他实例未操作。

## 未完成与下一动作

本批只收口来源/共同 K/生产字节和关系基础。E2 历史 mapping/canonical 精确语义、STOP_PROOF kind、set/hash/verification 一致、K 的可选 order 与当时 canonical、resource/receipt id 绑定、真实可信权限和不可变写入仍待窄服务；C1 原键恢复/命令台账执行与原响应原子签发未开放。原 seed 真实重新鉴权及 GET、HTTP/设备、Android 耐久、LINK/处置/续作/最终成功和 R09 V34 恢复/fence 均单列待办。

普通正常确认空理由目标和 localDeletionAllowed=false 保持；Flyway V34、契约0.29/116路径、Android code20/Room17。当前仅提交推送整合候选，交 W1 最终独立复审，再由主调度明确派工 ff-only 回 development；不自行提前快进，不合 main、不打标签、不发布或部署。
