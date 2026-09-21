# 第六轮 C1b 台账与协议整合记录

2026-09-21，执行者 W3 `/root/foundation_protocol_verify`。整合树为 `D:/acrylic-pda-worktrees/sixth-wave-integration`，分支 `codex/sixth-wave-integration`，组合候选基于 development 管理基线 `19ef9a7`，保留三次历史合并节点 `d61b845`、`3cb7920`、`127c9d7`。本指南只记录已固定的生产、独立验收和新组合证据；没有开放生产决定 handler、POST、证据写入、放行或部署。

## 固定来源与整合边界

| 来源 | 固定提交 | 本轮用途 |
| --- | --- | --- |
| W1 C1b 生产及护栏 | `04890362cd8ebeaaf18c7782f6e145447d0ff92d`（生产父版本 `05ed1d3c4f121d9462d0077bd607c130f64cf562`） | 生产源码、作者测试、边界测试和环境护栏 |
| W2 台账独立验收 | `b3f426546ef5555375213c212ffe578613ce575c`（生产接入 `896320e91faf6897a23fff56026b8556ab24e9c6`） | 原键回放、故障、并发、权限和历史读取验收 |
| W3 协议独立消费者 | `fec06b0` | 固定生产编译、协议向量、可信 facts 消费和交叉复审 |

整合只接入已审来源和本指南，没有改写生产 Java、测试、迁移或五份管理文件。测试合并点为 `127c9d7`；指南固定提交另见 Git 及看板，须 W1 独立终审后由主协调安排 ff-only，当前未推送或合回 `codex/development`。

## 独立环境与保护

组合仅使用新 MySQL `127.0.0.1:19328`、数据库 `acrylic_outbound_wave6_20260921`、账号 `outbound_wave6_20260921@127.0.0.1`、本树 `.local/mysql19328/data`、`.local/private-files` 和 `.local/signing/{private.der,public.der}`。19328 的端口、datadir、数据库和账号均按精确护栏核对；W2 的 19327、原 13316/19321–19327、旧库、设备和现网均未操作。组合完成后 `.local/wave6/stop-proof.json` 记录 `remainingListeners: 0`、`processPresent: false`；数据库、文件、签名和日志仍保留。

本树原始环境资料及日志属于 ignored 证据，不进入 Git。报表字体使用已核 SHA-256 `2c76254f6fc379fddfce0a7e84fb5385bb135d3e399294f6eeb6680d0365b74b` 的 `NotoSansCJKsc-Regular.otf`；`unit-summary.json` 记录的路径来自第五轮整合树，属于可复核字体来源记录，不应解释为重新下载或组合树内生成路径。17+25 单元 XML 齐全；首构建完整 stdout 缺失。停机前状态仍保留。

## 组合实际结果

证据位置为 `.local/wave6/result-summary.json`、各结果目录 XML 及同目录日志。下表按用途分列，不能把不同来源或重复复跑相加：

| 范围 | 实际结果 |
| --- | --- |
| 组合单元：`CommandReceiptCodecTest` | 17 个通过，XML 齐全 |
| 组合单元：`LedgerRuntimeEnvironmentTest` | 25 个通过，XML 齐全 |
| 组合协议单元：`TemporaryProtocolTest` | 13 个通过，计入组合单元合计 |
| 组合作者/边界 IT | 11 个通过：`LedgerAuthorIT` 9、`LedgerProductionBoundaryIT` 2 |
| 七类既有 API | Continuation 12、Disposition 11、Recall 11、Submission 12、TemporaryReview 10、TemporarySourceEvidence 5、TemporarySubmission 14，共 75 项；各套件单列 |
| 组合单元合计口径 | 17（`CommandReceiptCodecTest`）+25（`LedgerRuntimeEnvironmentTest`）+13（`TemporaryProtocolTest`）=55 |
| 组合 IT 合计口径 | 作者/边界 11 + 旧 API 75 = 86 |
| 组合协议消费者 | 68 项通过，0 失败/错误/跳过；shape、密码学和负向子场景已包含在 68 内，独立原始工具证据另列 |
| W2 独立台账 IT（不计组合） | 50 个不同 IT 通过，0 失败/错误/跳过；准备护栏与补测不计入该 50 |
| 构建与契约 | `bootJar` 成功；`npm ci --ignore-scripts --no-audit --no-fund` 后 `check:contract` 成功，OpenAPI 3.1 共 120 paths |

组合证据中的 JAR、120 路径和结果 XML 均来自本次固定生产源码；不把 W2 独立 50 项台账证据、W2 非空数据库证据或第五轮历史 233 项离线结果冒称为组合 86 IT 或组合单元。

68 项的原始工具结果来自旧 W3 线程 `01a0c2ea-8c11-7cb1-92f9-7a3065bd2b8d` 的执行 `exec-8f846229-82ac-42b9-bf39-fcbd03e249d4`，工作目录为本组合树，命令为 `python -W error::ResourceWarning -m unittest discover -s docs/parallel/R06_LEDGER_PROTOCOL -v`，退出码 0，输出为 `Ran 68 tests in 2.840s` / `OK`。该线程输出是组合运行的原始证据；本树没有另存完整 stdout 日志，因此不虚构缺失的逐测试日志，也不重跑该验收。

## 交叉复审与保留失败

W3 已只读核对 W1 生产护栏和 W2 最终提交，未发现进入本组合的范围阻断。W2 首矩阵 40 项中的三项夹具失败（缺 `resource_id`、非法 `RECOVERING`、fraction 在 canonical 前置校验被拒）及两次准备 IT 失败均保留原 XML/日志；修正后的对应场景另有通过证据，不能把修前失败删除或改写为组合失败。W2 首次字体下载超时和首次未显式迁移导致的 0 表失败库同样保留。

W1 首 9 个 IT 失败（8 个夹具缺 `CONFIRMER`，1 个 JDBC `DATETIME` 类型读取）及同库修后追加复跑均保留；最终作者 XML 只作为作者交付证据。协议首轮的依赖安装记录也保留：首次缺少 Node 依赖时未把未运行状态写成失败，执行 `npm ci` 后契约检查通过。

W2 动态覆盖了故障注入、同键竞争、锁后第二查询、身份竞态、四类 GET 权限、损坏关系、历史读取和合法 RSA 错语义重签。唯一明确未覆盖的组合是：唯一异常完成回滚、事务已退出后，再由另一只读支路动态读取既有原记录；锁后回放与竞争测试分别覆盖相关边界，不能拼接成该场景已验证。旧 seed 的真实重新鉴权/HTTP GET 仍因令牌自然过期且旧工厂未保留 EC 私钥而返回 401，服务 DTO 或新 fixture API 不替代它。

## 能力边界与后续限制

协议消费者证明固定生产源码的原始 UTF-8、hash、严格 shape、RS256/kid/issuer/audience 和独立可信 facts 拒绝逻辑；合成回执均为 `INTERNAL_TEST_ONLY`、`businessDecision=null`、所有能力 `false`。这不证明 MySQL 非空迁移、真实数据库可信事实闭环、真实 HTTP 丢响应、Android 耐久、E2a 证据写、七类业务决定、最终记账/放行、R09 V34 恢复/fence 或现场设备验收。历史 key ring、完整 Web 历史权限表及旧 seed 401 继续单列。

普通 `NORMAL_CONFIRM` 空理由、`localDeletionAllowed=false`、Flyway V34、Android code20/Room17 保持。19328 已正常停止；本批不合 `main`、不部署、不恢复旧 BCD，也不改变现网、原库、设备或审批边界。
