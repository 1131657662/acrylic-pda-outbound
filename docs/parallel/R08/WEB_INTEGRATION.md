# R08 正式订单原确认文件接入

本子项接续用户2026-09-20明确授权：R05任务内核就绪后，推进Web点击导出、后台处理、下载正式订单文件。工作树 `D:\acrylic-pda-worktrees\r08-export`，分支 `codex/r08-export-rendering`；原始起点 `2369392076083a5a4f59d8ea3d6edebe5067a03f`，已核对并快进到R05提交 `8f566e9b33cafa41269787dc7d9b4e2788355daa`。主工作区R06未提交改动未带入。

## 范围与口径

- 订单详情“原确认文件”页签提供通用Excel、[六列扫码记录Excel](SCAN_RECORDS.md)和PDF生成、持久状态列表、刷新、失败后重新生成和下载；浏览器丢响应后以原幂等键恢复请求。
- 仅 `WEB` 来源、`COMPLETED` 状态的正式订单，固定首版 `result_version=1` / `ORIGINAL_CONFIRMED`。原确认文件不含之后更正，不冒充最新净结果。日归属仍按原确认时刻的上海日期。
- 原始事实按 `dispatch_record.order_scan_seq` 递增，不使用设备局部序号、当前人员/主数据或客户端时钟重排。当前适配拒绝尚未支持的PDA跨设备首版来源；后续交接模型单列。
- 模板 `r08-general-2` 将“当前净结果”改为“本版本净结果”，Excel页签改为“版本有效明细”；文件标明原确认V1不含后续更正。模板版本变更不修改应用版本。
- 独立生成器继续支持明确标注的R06/R07提案样例。此Web入口不接受 `CURRENT_EFFECTIVE`、临时订单或客户端提交的报表快照。

## 数据链路

接收请求时在一致性事务内读取：原计划修订、首版结果和产品汇总、原dispatch、原PC/PDA成功回执和冻结正文。PC核对正文摘要与回执；PDA通过既有 `SubmissionFiles` 核对持久正文摘要，再核对回执、原归属与高水位。逐包匹配身份、重量、序号、周期及有效集合；短装的计划零实绩产品也必须匹配。撤销、无效输入、卸货/隔离等原处置证据单列。

PDA冻结正文只有原人员ID，无完整姓名快照，通用Excel/PDF明确写“原人员ID:...”；扫码记录Excel按用户要求在申请时读取人员资料姓名并固定（中英文均可，仅排除缺失、人员ID、账号等占位值），来源写入私有快照，不冒充历史姓名。PC使用冻结的operatorName和createdAt。原车柜号和备注来自原order_revision，首版header_snapshot实为回执，不能当作完整抬头。

固定JSON先持久化到 `outbound.storage/exports/snapshots/<sha256>.json`。小型任务payload保存范围、恢复代次、快照摘要/ID、修订、原出库日、格式、模板和到期时间。相同创建人及幂等键串行复核，已完成任务回放不再重新生成快照。

`REPORT_RENDER_V1` handler由原R05 Worker自动发现；不修改共享任务调度。每次租约使用 `<jobUUID>/<leaseTokenUUID>/report.xlsx|pdf` 私有路径。Java子进程生成文件，定期续租，硬超时/中断/失租终止子进程；完成后摘要和字节数经原R05事务栅栏发布。旧租约或发布回滚产生的孤立文件没有可下载引用。服务器恢复代次不一致时拒绝发布和下载。

## 本轮接入接口

以下是本分支新增接入契约，**尚未并入正式OpenAPI**。正常JSON响应沿用ApiEnvelope，写请求沿用CSRF和恢复代次头。

| 请求 | 参数 / 结果 |
| --- | --- |
| `POST /api/v1/orders/{id}/exports?warehouseId=UUID` | `Idempotency-Key: UUID`、`X-Server-Recovery-Epoch`；正文format为`XLSX`、`SCAN_XLSX`或`PDF`，basis为`ORIGINAL_CONFIRMED`；返回任务View |
| `GET /api/v1/orders/{id}/exports?warehouseId=UUID&before=UUID` | 20项游标页，`items` 和可空 `nextCursor`；只列本人任务 |
| `GET /api/v1/exports/{id}?warehouseId=UUID` | 任务View |
| `GET /api/v1/exports/{id}/file?warehouseId=UUID` | 验证后附件字节；私有/no-store、nosniff；安全固定下载文件名 |

View字段：`id,orderNo,format,basis,resultVersion,revision,status,attempts,errorCode,createdAt,expiresAt,originalDay,snapshotId,sha256,bytes,downloadable`。状态为 `PENDING/RUNNING/RETRY/SUCCEEDED/FAILED/EXPIRED/INVALIDATED`。尚无文件时摘要和字节数为null，不返回存储路径、快照正文或回执。失败只返回稳定错误码。

每次查询/下载均复用 `order:read`、当前账号状态/授权版本及仓库权限；另限定创建人，不增设角色或授权表。撤权、错仓、他人任务拒绝。下载重新核对文件摘要和大小；浏览器也核对响应摘要后保存。过期或代次变更须新建任务。

## 构建与配置

共享构建改动只有：`server/settings.gradle`增加composite build，`server/build.gradle`引用独立生成器并排除其log4j-to-jul桥接。合并时同时带入report-rendering目录；不修改数据库迁移、正式OpenAPI、任务内核或版本号。

1. Java21、Gradle8.11.1构建独立模块的 `test installChromium installDist`；本任务的字体、依赖缓存和浏览器目录见原模块README。生成器不依赖服务器数据库。
2. 构建 `server` 的 `bootJar`；构建 `web` 的 `npm ci` / `npm run build`。API和原独立Worker使用同一服务器JAR及同一私有存储。Worker按既有 `--spring.profiles.active=worker` 启动。
3. API与Worker配置 `outbound.exports.renderer-home` 指向 `build/install/acrylic-report-rendering` 的绝对路径，`outbound.exports.browsers`指向固定Chromium目录。未配置时POST返回503，不假装已经接受。
4. `outbound.exports.timeout-seconds`默认180、范围10至600；子进程堆512MiB，临时目录在本租约目录内。CPU/整个进程树资源限额由部署环境提供，不能把Java堆上限当作完整系统限额。
5. `outbound.exports.sample-data=true`仅隔离合成验证时开启，输出TEST_SAMPLE；默认false表示服务器提取的SOURCE_SNAPSHOT。不能将合成库配置为真实业务来源。

当前集成限制：Excel最多5000个原确认包，PDF最多1000包；文件/快照读取上限64MiB；每人最多5个活动任务；最多3次尝试；下载有效期7天。独立生成器的大数据能力不等同于API的集成上限。长异常清单还受生成器行数和单元格可读性限制，超限失败、不静默截断。

## 验证与保留

本机仅使用专用MySQL `127.0.0.1:23308`、账号 `outbound_r08`、库 `acrylic_r08_it` / `acrylic_r08_preview`。API18108、Web5178；配置、密钥、数据库、日志、下载和截图均在 `.local/r08/integration/`，不入Git。合成订单通过现有正式订单/结单API创建，未使用真实业务数据或PDA。

测试入口：服务器 `R08ExportIT`、`R08ExportFenceIT`、`ExportRendererProcessTest`；一次性预览种子 `R08PreviewSeedIT` 仅允许专用库且要求 `R08_PREVIEW_SEED=true`，已存在preview.json时拒绝覆盖。Web `npx playwright test --config r08-export.config.ts` 使用该预览库/API及真正的独立Worker。实际测试结果、失败修正和最终产物核验见[进度](PROGRESS.md)。

## 后续事项

- R07更正凭据、逐包净贡献及最新版本快照；R06临时来源；交接后的跨设备证据。
- 跨订单汇总查询、完整导出中心和正式OpenAPI整合；当前只有单订单原确认文件闭环。
- 高并发/生产负载及Linux进程监管验收。本轮任务列表按JSON范围筛选，未增加索引/迁移；大规模前须评估专用导出元数据表。
- 过期和孤立文件目前保留但不可下载；定期清理、存储配额、异地备份和恢复与R09另行整合。不得直接套用共享清理任务删除业务证据。
- 真实业务数据、现场Excel/打印机、异地HTTPS与公网部署均未验收。本轮没有提交、推送、合并、部署或PDA操作。
