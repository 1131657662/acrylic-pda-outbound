# PC 手工出库实施与验证记录

日期：2026-09-18。分支：`codex/development`。实施提交 `41a295c` 已推送。状态：源码和本机隔离环境闭环已完成；未部署、未合入 `main`，不表示现场或生产验收完成。

本轮按 [PC 手工出库设计](PC_MANUAL_OUTBOUND_DESIGN.md)实施订单内 PC 采集渠道。订单详情在有权限且状态允许时显示“PC 手工出库”或“继续PC录入”，进入 `/orders/:id/pc-outbound`。页面固定本单仓库，左侧提供条码候选和本单记录，右侧显示每个发货代码的计划、有效录入数和重量；返回订单详情时保留订单列表的仓库、筛选、页码和页大小上下文。

后台新增 Flyway V15、V16 和 `pc` 服务包。`pc_work` 固定订单发布、库存快照、政策、操作员和恢复代次；`pc_entry` 保存可信重量、产品快照、录入序号和撤销历史；`pc_submission` 冻结本轮正文摘要；`pc_submission_entry` 防止冻结后的有效包被普通撤销；`pc_review`、`pc_command_receipt` 保存审批和原请求回执。PC 占用使用 `bale_commitment.state=PC_RESERVED`，确认出库后复用全局 `DISPATCHED`、`dispatch_record`、结果版本和可选库存账务约束。

页面和服务端约束如下：

- 查询仅限本单固定发货代码和库存快照。完整 11 位条码精确匹配，4～10 位片段只在前三位代码之后连续包含，尾部匹配优先；保留前导零和大小写归一化，通配符、过短输入和跨代码拼接拒绝。全部代码返回真实匹配总数，各代码独立 5 条分页；单用户一分钟最多 120 次候选查询。
- 选中候选不写入；完整条码唯一可录入时只预选。确认计入要求后台再次读取库存、轮次、产品快照、占用和计划上限，重量来自库存，客户端重量不会被信任。`physicalConfirmed=true` 是计包和结单的显式确认条件。
- 同一订单的 PC 和 PDA 共用订单锁；不同 PC 订单争用同一包时最多一个得到占用。写入窗口使用随机 `writerId`、递增 fence 和作业版本，原人员在新窗口显式“在本窗口继续”后旧窗口只读。关闭页面、刷新和租约失效不会释放订单或包占用。
- 每次写请求在浏览器发送前写入按账号、仓库、订单隔离的 localStorage 恢复记录。结果未知时停止下一笔写入；刷新先查询原回执，再按原幂等键和原正文重试。401/403、网络、5xx、回执查询失败或回执关联不符都会保留原记录。回执查询只需 `order:read`，不能借此重新写入。
- 撤销需要实物确认和原因，保留原行、序号及审计，只释放当前 PC 占用；已进入冻结提交的有效行不能普通撤销。0 包、超计划和无原因短装拒绝；短装提交进入主管审核，审批再次复核原操作员权限、仓库和实时库存。退回保留占用，主管只能凭实物记录恢复原人员和全部原有效包，开启新轮次；旧冻结正文不可变。

## 实际验证

验证使用本机已有 MySQL 8（端口 13316）新建的隔离数据库 `acrylic_outbound_pc_test`，不重置原开发库、设备身份或业务数据。后端脚本为 `.local/verify-pc.ps1`，浏览器预览为端口 18084 / 5175；随机测试账号、fixture、截图和查询性能文件均在 `.local/` 或 `server/build/`，不进入 Git。

- 后端完整回归：24 项单元测试、215 项真实 MySQL 集成测试，共 239 项，零失败、零跳过；包含原 PDA/订单/配对链路以及 PC 的权限、跨仓库、并发抢包、PC/PDA 抢单、幂等、回执恢复、窗口 fencing、撤销留痕、短装审批、主管恢复、冻结行保护和 PDA/PC 互斥。录入记录接口支持 20/50/100 条页大小；浏览器实际验证 24 条记录按每页 20 条分为 20/4 条，筛选后总数由服务端返回。
- 候选查询性能取自 12639 包、145 产品的本机真实 MySQL 单用户 MockMvc 样本，最新完整回归中 5 次查询约 94.2～97.8ms；这是查询边界和限流验证，不是生产规模负载承诺。完整记录在 `server/build/pc-search-performance.json`（ignored）；此前 87.9～90.4ms 为前一次运行结果。
- Web 类型检查、Vite 构建和 OpenAPI 校验通过；契约为 0.13.0，共 77 条路径。构建仍有 Element Plus 主包体积提示，不影响本机功能检查。
- PC 浏览器最终 11 项场景全部通过，零失败、零跳过。覆盖草稿发布并开始、三代码分组候选、Enter 只查询、明确选择后计入、短装主管审批、只读查看、窗口切换、撤销历史、拒绝后原人员恢复、丢响应刷新恢复、错误回执关联、回执查询失败、浏览器存储失败、登录过期后恢复，以及真实服务器记录分页。结果见 `.local/pc-browser-final.json` 和 `.local/pc-browser-final.log`。补充结单截图等待表格加载完成后，定向复测 1 项通过，记录在 `.local/pc-browser-screenshot-check.json`；此项包含在原 11 项中，不重复计数。该测试调整后 Web 类型检查再次通过。
- 既有 Web 29 项回归在当前 PC 后端与同一隔离库上完成：首轮 21 通过、8 失败；其中 5 项使用了隔离库中不存在的旧账号，3 项配对测试仍访问旧端口。仅修正本机测试配置（`OUTBOUND_UI_FIXTURE=1` 和 ignored 配对 fixture 的 `baseUrl`）后，8 项分两次定向复测全部通过，无跳过；合并证据覆盖 29 个独立场景，没有把它写成单次 29 项全绿。首轮及两次复测分别见 `.local/pc-browser-regression.json`、`.local/pc-browser-regression-retry.json`、`.local/pc-browser-regression-retry-extra.json`。
- 代表页截图保存在 `.local/pc-outbound-1366x768.png`、`.local/pc-outbound-1440x900.png`、`.local/pc-outbound-1024x768.png`、`.local/pc-outbound-1280x500.png`、`.local/pc-outbound-390x844.png`、`.local/pc-outbound-200-percent.png` 和 `.local/pc-result-completed.png`。布局证据在 `.local/pc-layout-evidence.json`、`.local/pc-zoom-evidence.json`：确认按钮在各视口均保持至少 36px 高并可滚动到视口内，200% 原生浏览器缩放已实测。

实施期间出现过编译缺少 `SubmissionJson.parse(String)` 重载、CSS 导入顺序提示，均已修复。PC 浏览器首轮因定位隐藏的 Element Plus 输入控件超时而中止，改为点击可见标签并断言选中；下一轮 9 通过、1 失败为分页按钮使用了英文定位，改为中文后最终 11 项通过。失败日志保留，不用最终通过结果掩盖首轮问题。汇总证据在 `.local/pc-outbound-verification.json`；后端完整运行日志和统计分别为 `.local/pc-server-final-all.log`、`.local/pc-server-final-results.json`。

## 未完成和兼容边界

PC 业务尚未在真实现场、异地 HTTPS、多实例限流、生产数据规模或真机环境验收；M71、PDA 页面全面改造、PC 换人/卸货/隔离处置、灾后重建、通用报表和连续同步仍待后续任务。本轮没有重新构建 Android 或执行 Android 主机/真机测试，既有 68 项主机通过为前序记录。V15～V16 必须与新 Web 一起迁移；原 V1～V14 校验和、Android 0.7.0/versionCode 7、Room6、PDA 设备身份、签名证据和既有订单数据保持。不要把源码构建、后端集成通过或本机浏览器截图写成已上线或整个系统已验收。
