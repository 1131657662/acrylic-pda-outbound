# 腈纶包离线出库系统

完整系统以 [V2.3 设计](docs/系统设计方案与开发计划-V2.3.md) 为准。**2026-09-16 Demo已按Q17约定范围验收完成：按键映射、B模式现场复验、真实断电恢复全部通过。**实际操作及取下电池重启后均为12包、3610kg、待处理0，明细、输入、审计与按键配置完整保留，见[现场验收记录](docs/M71_ACCEPTANCE_REPORT.md)和[按键记录](docs/M71_KEY_WIZARD_REPORT.md)。继续已获授权的正式开发，不追加Demo测试。

正式工程当前可运行流程：**Web登录 → 仓库权限 → sku.txt上传预检 → 库存发布 → 多产品订单草稿 → 配置出库政策 → 发布为待领取 → 正式PDA设备/人员登录 → 独占领取 → 下载验签 → Room原子激活 → 下载确认 → 本地扫码保存 → 结单冻结/上传 → 短装审批 → 云端出库/按政策扣账 → PDA验签回执**，并支持未领取订单修订重发/作废、库存和映射管理、历史追溯。各产品独立填写计划包数，订单合计不设116包上限。已接固定48小时离线签名及丢失响应恢复；登录/授权过期、设备停用和下载失败不自动放锁。正式订单首轮提交已接入；召回续作、临时单、更正、增量同步和文件生成仍在开发计划内，完整系统尚未验收上线。见[正式开发进度](docs/DEVELOPMENT_PROGRESS.md)、[本机运行说明](server/README.md)及[PDA协议](contracts/PDA_API.md)。

正式PDA首轮提交版APK：[`outputs/pda/Acrylic-PDA-0.3.0-debug.apk`](outputs/pda/Acrylic-PDA-0.3.0-debug.apk)，构建及首次设备登记见[Android说明](android/README.md)。这是独立正式工程的开发验证包，已通过主机测试及真实后端模拟器联调，尚未在M71上验收。已包含结单/上传与成功回执验签；待上传、待复核或验签失败均不可放行。

## 先看这里

仓库保存源码及文档；原始业务样本、服务器资产清单、本机凭据、APK和原始测试产物不随Git上传。克隆后的资料准备见[本地资料说明](docs/GIT_WORKFLOW.md#克隆后的本地资料)。本文的 `outputs/` 和 `demo-artifacts/` 链接指本机构建产物，安装包另按测试版本规则发布。

- [仓库、分支与提交约定](docs/GIT_WORKFLOW.md)：开发分支、稳定分支、每次提交说明及测试版本发布规则。
- [安装与构建](docs/DEMO_INSTALL.md)：APK、设备连接、保留数据升级。
- [真机操作与测试](docs/DEMO_TEST_GUIDE.md)：扫码、实体按键、10 分钟压测、断电、内存、导出。
- [M71 真机验收记录](docs/M71_ACCEPTANCE_REPORT.md)：现场操作、连续负载、问题修复与剩余门槛。
- [最新自动验收记录](docs/M71_AUTOMATED_ACCEPTANCE_REPORT.md)：B 模式性能调优、进程恢复、历史容量与保留数据核对；实体操作另列。
- [开发验证历史](docs/DEMO_REPORT.md)：0.1.0 自动验证与模拟器结果。
- [完整验收计划](docs/DEMO_PLAN.md)：全部门槛，只有真实完成后才能判定通过。
- [已确认业务规则](docs/OPEN_QUESTIONS.md)：Q01～Q16 的确认结果。

## 下载与运行

APK：[`demo-artifacts/M71-Demo-0.1.19-debug.apk`](demo-artifacts/M71-Demo-0.1.19-debug.apk)。这是隔离的测试包，名称“M71 扫码验证”，包名`com.acrylic.m71demo.debug`，与设备上的同名release测试包数据独立。本轮使用debug包。不连接生产库存，不提供出库放行凭据。执行 B：扫码校验通过自动保存，手输仍 F1 后人工确认；Room 3 保留旧记录的人工策略及全部事实，PASS/FAIL 音频不变。0.1.19把状态和计数更新限定为必要字段，保留0.1.17的键盘恢复；64页回写试验已撤回。91项主机回归及构建通过，软件证据见[自动验收记录](docs/M71_AUTOMATED_ACCEPTANCE_REPORT.md)。Demo按约定范围验收完成；历史失败和初始时延目标未达证据保留。

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
| `android/` | 独立正式PDA工程；Keystore、双身份登录、领取、资料验签、Room激活、B模式本地作业与首轮提交 |
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
