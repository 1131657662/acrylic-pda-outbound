> **无状态文档**：本文不含当前版本或进度事实。版本四元组与断点只看[看板](docs/ORCHESTRATION_STATUS.md)的生成事实块（由 `tools/version_facts.ps1` 生成）；下文出现的“当前/最新/现网为”等措辞都是写作时点的历史记录，不作为现行状态。

# 纤序·腈纶包离线出库管理系统

这是一个由 Web 后台、服务端和 Android PDA 组成的离线出库工程。产品范围、协作边界和证据分层分别见路线、政策和治理地图；任何版本、分支、当前完成度和运行环境都以看板实际核对结果为准。

首次接手请按 [`docs/PROJECT_DOCUMENT_MAP.md`](docs/PROJECT_DOCUMENT_MAP.md) 的顺序阅读，再读取 [`docs/ORCHESTRATION_STATUS.md`](docs/ORCHESTRATION_STATUS.md) 当前区。README 只提供静态导航，不承担当前状态、验收结论或接续指令。

历史 Demo、设计资料和操作指南继续保留在下方链接中；它们的日期和测试结论是各自资料的历史范围，不替代看板或专项报告。

## 先看这里

- [本机历史资料归档位置](docs/LOCAL_ARCHIVE_LOCATIONS.md)：旧工作区、快照及历史测试资料从 D 盘迁往 E 盘后的查找入口、校验与恢复说明。
- [项目文档地图与零记忆接手顺序](docs/PROJECT_DOCUMENT_MAP.md)：当前状态、路线、政策、验收索引、历史证据和继承改动的职责边界。

仓库保存源码及文档；原始业务样本、服务器资产清单、本机凭据、APK和原始测试产物不随Git上传。克隆后的资料准备见[本地资料说明](docs/GIT_WORKFLOW.md#克隆后的本地资料)。本文的 `outputs/` 和 `demo-artifacts/` 链接指本机构建产物，安装包另按测试版本规则发布。

- [仓库、分支与提交约定](docs/GIT_WORKFLOW.md)：开发分支、稳定分支、每次提交说明及测试版本发布规则。
- [后续开发路线与跨会话衔接](docs/DEVELOPMENT_ROADMAP.md)：已确认的十步范围、依赖和长期执行规则；当前接续位置只看[开发看板](docs/ORCHESTRATION_STATUS.md)。
- [十步完成度统计](docs/history/TEN_STEP_COMPLETION_AUDIT_2026-10-11.md)与[无人值守开发计划](docs/AUTONOMOUS_DEVELOPMENT_GOAL_2026-10-07.md)：按修订范围统计，方案自主决策，同根因重复失败保全暂缓，按依赖继续独立工作。
- E07/O05 历史说明：电脑工具的加密副本核验、回读和外置介质边界见 [专项报告](docs/history/GOAL_E07_RESTORE_COPY_ACCEPTANCE_2026-10-14.md)，不替代当前状态或最终候选验收。
- [PDA应用检查更新与安全升级](docs/PDA_APP_UPDATE_DESIGN.md)：历史设计、专项交付和安全边界入口；当前实现与验证以看板和对应历史报告为准。
- [安装与构建](docs/DEMO_INSTALL.md)：APK、设备连接、保留数据升级。
- [真机操作与测试](docs/DEMO_TEST_GUIDE.md)：扫码、实体按键、10 分钟压测、断电、内存、导出。
- [M71 真机验收记录](docs/M71_ACCEPTANCE_REPORT.md)：现场操作、连续负载、问题修复与剩余门槛。
- [Demo自动验收记录（历史）](docs/M71_AUTOMATED_ACCEPTANCE_REPORT.md)：B 模式性能调优、进程恢复、历史容量与保留数据核对；实体操作另列。
- [开发验证历史](docs/DEMO_REPORT.md)：0.1.0 自动验证与模拟器结果。
- [完整验收计划](docs/DEMO_PLAN.md)：全部门槛，只有真实完成后才能判定通过。
- [已确认业务规则](docs/OPEN_QUESTIONS.md)：Q01～Q16 的确认结果。

## 历史 Demo 资料（非当前正式状态）

以下下载、运行和目录内容是保留的 Demo/旧工程资料；正式工程当前状态、版本和可交付性不在本节判定。

## 下载与运行

历史 Demo APK 原产物不在当前工作树；名称、安装步骤和历史结论只见[自动验收记录](docs/M71_AUTOMATED_ACCEPTANCE_REPORT.md)及[安装说明](docs/DEMO_INSTALL.md)，不能按本节路径下载或把旧结果当作正式工程当前交付。

连接已开启 USB 调试并授权的 M71 后，在项目目录执行：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File tools/install_demo.ps1
```

扫码校验通过后自动保存；手输先按 F1 校验，再回车或点击大保存按钮。出错时暂停后续自动保存，点击大按钮或回车确认错误后恢复。按键通过诊断页的“全部按键验证向导”逐个采集，完整确认后自动应用。不要开启扫码头自动回车。

## 目录

| 目录 | 内容 |
| --- | --- |
| `web/` | Vue 3、TypeScript、Vite、Element Plus正式后台；库存与映射、订单草稿/发布、短装审批、云端实绩、政策及历史 |
| `server/` | Java 21、Spring Boot 3.5.16、JDBC、Flyway、MySQL正式后端 |
| `android/` | 独立正式PDA工程；Keystore、双身份登录、领取、资料验签、Room激活、B模式本地作业、召回与受控续作提交 |
| `outputs/pda/` | 正式PDA开发验证APK、SHA256与构建信息 |
| `contracts/` | 已实现接口的OpenAPI、字段、错误码与重试约定 |
| `demo/` | Kotlin、XML ViewBinding、Hilt、Room、SoundPool 验证程序及测试 |
| `demo/schemas/` | Room 版本 1、2、3 的结构快照；保留数据的索引/确认策略迁移 |
| `tools/` | 构建、安装、提取证据、JSON/Excel 核对、数据库检查工具 |
| `demo-artifacts/` | APK、校验和、当前测试证据；现场每轮证据放在 `runs/` |
| `docs/` | 原始业务资料、V2.3 方案、决策、Demo 计划及说明 |

所有正式功能仍属完整交付范围，不划分一期二期。正式开发已获授权，Demo三项现场验收现已完成；完整业务上线验收仍需真实证据。Git提交、分支和版本发布按[仓库管理约定](docs/GIT_WORKFLOW.md)执行。

0.1.7 在 B 规则下合并预校验与自动意图事务，保留原始接收与最终保存独立耐久。最新结果见 [B 模式记录](docs/M71_B_MODE_REPORT.md)，不将 0.1.6 或更早证据当成新版通过。

## 0.1.8：一次完成全部按键验证

0.1.8新增 [按键向导](docs/KEY_VALIDATION_GUIDE.md)：正面/左右扫描键、回车、K1、K2、返回共 7 键，各按两次；无倒计时、可中断续测、完成后确认并应用。用户已在0.1.19完成真实位置确认及应用，断电后配置保持。原 B 模式和音效保持。

0.1.8 开发、安装及未完成验证的证据见 [按键向导检查记录](docs/M71_KEY_WIZARD_REPORT.md)。
