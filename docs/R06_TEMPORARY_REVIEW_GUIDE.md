# R06-D TEMP 独立待确认审阅

日期：2026-09-20。本批是 R06-D M2a，接续 [临时订单冻结、上传与原批次恢复](R06_TEMPORARY_SUBMISSION_GUIDE.md)，提供 TEMP 提交的独立只读审阅入口。当前仍不能确认出库、扣账、放行或生成成功回执；M2b 的写确认及实物处置留给后续批次。

## 实现范围

- 服务端新增 `GET /api/v1/temporary-reviews`、`GET /api/v1/temporary-reviews/{id}` 和 `GET /api/v1/temporary-reviews/{id}/scans`。列表和原扫码明细按真实游标分页，三个接口均要求当前仓库授权及 `temporary_order:confirm`。
- 详情核对原 TEMP 来源、原资格、原人员/设备、恢复代次、固定计划、正文摘要、实绩、逐包记录、上传时问题、当前资格/权限/恢复/库存/出库周期/包占用问题。动态变化只生成待核对原因，不自动拒绝、释放占用或改变作业事实。
- 所有审阅查询使用只读事务；响应明确 `writeConfirmationAvailable=false`、`releaseAllowed=false`。本批不写确认状态、不扣库存、不释放占用、不生成成功回执、不创建正式关联或拒绝处置记录。
- 超时提醒增加 `sourceType`，按 FORMAL/TEMP 分流。正式审核权限不能读取 TEMP，TEMP 权限不能读取正式提醒；同时拥有两类权限的人员可分别进入两类入口。
- Web 增加临时待确认列表、详情和原扫码明细分页页，直接访问按权限保护，详情保留返回列表游标。正式订单详情遇到 TEMP 时隐藏正式审批动作并提供独立审阅入口。

## 权限与只读边界

TEMP 入口独立使用 `temporary_order:confirm`，不借用 `submission:review`。仓库范围仍由会话授权校验。资格到期、权限版本变化、恢复代次变化、短装、库存冲突、出库周期冲突或包占用冲突只能显示原因并保持待核对状态；这些条件不能通过本入口绕过确认屏障。

短装批准、重复正式单人工核对、受控正式关联、拒绝后的保留/卸货/隔离、最终记账、签名成功回执和归档均未在本批开放。正式审批入口不会把 TEMP 当作正式单处理。

## 契约与版本

OpenAPI 更新为 `0.26.0`，共 106 条路径；新增 `contracts/temporary-review-paths.yaml`、`contracts/temporary-review-schemas.yaml`，并更新提醒契约和 `contracts/IMPLEMENTED_API.md`。Flyway 保持 V33，Android 保持 0.15.0-r06-submission/code20/Room17；本批不新增数据库迁移，不改变 PDA 版本。

## 实际验证

| 范围 | 结果 |
| --- | --- |
| 服务端专项集成 | 7 个 IT 类共 63 项通过，0 失败/错误/跳过：TEMP 审阅、权限四象限、列表/扫码分页、动态库存冲突、只读事实保护、提醒查询/Worker 回归、TEMP 提交回归 |
| 服务端单元/构建 | 32 项单元通过，0 失败/错误/跳过；`bootJar` 成功。专项日志/XML 为 `final.log`、`final-results/`，单元/JAR 为 `unit-second.log`、`unit-second-results/` |
| Web 构建 | `npm run build` 通过 |
| Web 专项 | `npm run test:temporary-review` 7 项通过，覆盖导航/直接访问权限、真实后端契约样本和只读字段保护 |
| OpenAPI | `npm run check:contract` 通过，报告 `OpenAPI 3.1.0: 106 paths validated.` |
| 差异检查 | `git diff --check` 通过；`.local/` 凭据、私钥、数据库和原始证据未进入 Git |

失败记录：首轮 51 项服务端专项有 1 项失败，测试夹具直接修改资格时间，破坏固定 48 小时约束；改为整体平移 `issued_at`/`expires_at` 并保持期限后通过，未放宽业务约束。首轮 Web 构建因列表的 `remember` 未绑定到入口而被类型检查拦截，补齐入口绑定后通过。单元首轮 32 项有 1 项因隔离工作树缺少被忽略的 `docs/sku.txt` 而失败；从主目录复制已授权同一样本并核对 SHA-256 一致后，32 项通过。各轮日志/XML均保留在 `.local/r06-review-20260920/`，样本不入 Git。

## 未验证边界

本批未启动真实 HTTP 监听或浏览器端到端测试，未连接设备、Wi-Fi 后台、M71、异地网络或现网；MockMvc、真实 MySQL 和前端契约样本不能替代这些验收。未部署、未安装 APK、未合入 `main`，现有 R05/R06 数据库、恢复断点、AVD 和生产环境保持不变。

## 接续关系

本批使用专用合成库 `acrylic_outbound_r06_review_20260920`、工作区 MySQL `127.0.0.1:13316` 及 `.local/r06-review-20260920/private-files/`。测试进程已退出，共享 MySQL、专用库、凭据和证据保留。未启动新的 HTTP/API/Web/Worker 服务。

本分支 `codex/r06-confirmation` 从 `6d814f8` 隔离；提交推送仅保存本批，由总调度另行安排审查和组合整合。共享整合点为 OpenAPI 版本/路径索引、已实现接口记录、Web `package.json`、导航/布局/路由和正式提交显示。合入时须保留 R08/R09 同期入口，不能用整文件覆盖。

下一项为 M2b：在本批审阅基础上接独立写确认、短装审批与库存/周期冲突保护、重复正式单人工核对及 `temporary_order:link` 受控关联、拒绝后的实物保留/卸货/隔离，最后接共享记账、签名成功回执和归档。M2b 必须继续保留 TEMP 与正式审批的权限隔离，并在独立工作树完成验证后再由主线整合。本批不启动 M2b。
