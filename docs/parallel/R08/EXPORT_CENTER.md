# R08 原确认汇总与导出中心

## 2026-09-24 原键、授权和状态反馈定向修复

现有干净工作树 `codex/r08-export-rendering` 从 `e874349` 仅快进到已提交开发基线 `23ae8c492acf47e89875852d2573392fcf379f80`，未覆盖主树脏改。先核对原有单订单和中心原键恢复、服务端授权竞争、失败重生成、文件 SHA-256/长度校验及真实浏览器下载覆盖，不重做已有功能。

- 中心申请对 `EXPORT_QUEUE_FULL`、范围/格式/上限不合法、不可重生成及快照/模板不可用等明确未受理结果清除待恢复原键；403/404、401、服务异常、网络丢响应等结果仍不确定时保留原键、代次、范围/格式或重生成目标。不会自动重放或暗换新键；用户可核对任务列表，授权恢复后手动重试原申请，或经二次确认显式放弃并承担可能重复生成的风险。
- 下载沿 `server_state` 共享锁→当前用户行锁→会话/权限/仓库及任务归属复核的顺序，在同一事务内、读取文件字节和写下载成功审计前完成。新增会话、角色、仓库在控制器预检查后撤销的真实双连接测试，拒绝时无文件附件、无 `REPORT_EXPORT_DOWNLOADED` 事件。
- 任务状态或列表查询短暂失败只显示查询错误，保留已受理任务的在途状态，10 秒后继续轮询并在成功时清除错误；401/403/404 不自动轮询，需重新鉴权或手动核对。只有服务端实际返回 `FAILED` 才显示生成失败。

本轮定向验证：`npm run typecheck`、`npm run build` 通过（既有大 chunk 提示）；隔离 MySQL `127.0.0.1:23308/acrylic_r08_it` 上 `integrationTest --tests 'com.acrylic.outbound.inventory.R08*IT'` 共47项、46通过/0失败/1个预览种子按设计跳过；完整浏览器8项在最后时序调整前通过，均使用 R08 专属预览库、API、Worker、Web 与合成数据；调整后原键/权限/显式放弃3项定向复跑通过，单订单2项也通过。原有服务端文件核验和浏览器下载回归通过；未重新做独立文件内容逐格回读、真实业务资料、现场设备/打印、生产规模、共享主线组合或部署。

最后调整成功响应后先检查页面仍在再清理原键；调整前的完整浏览器8项通过，调整后的原键/权限/显式放弃3项定向复跑通过，其他逻辑未改变。收尾时仅停止本轮启动的 R08 MySQL 23308、API 18108、Worker、Web 5178，保留专用库目录、生成文件及忽略目录日志；未碰共享3306。验证时工作树仅本批7个 R08 文件有未提交改动，HEAD 为 `23ae8c492acf47e89875852d2573392fcf379f80`；验证时尚未提交或推送，未合并或部署，`git diff --check` 通过。

失败证据不抹除：先误用不存在的 `compileIntegrationTestJava` 任务；首次 Java 测试编译的断言方法不匹配，已修后通过；首次47项回归因本地 `report-rendering/build/install` 仍为旧包而有1项英文扫码人渲染失败，执行 `:report-rendering:installDist` 更新忽略的本机安装产物后单项及47项回归通过，未修改渲染器源码。专用测试和预览运行结果不替代正式 OpenAPI、主线组合、现网或原库验收。

2026-09-20，总调度首轮 W2 已接管原暂停工作区，原B会话保持暂停。独立分支 `codex/r08-export-rendering`，基于 `effba8f` 完成本批源码与本机验证：40项后端集成、35项单元、bootJar/Web构建、最终5项真实浏览器和7份文件回读通过。实际失败、环境状态及交付提交以 [PROGRESS](PROGRESS.md) 最新记录为准，未声明完整R08或现场验收完成。

## 已实现边界

导出中心按当前仓库查询本人任务，包含单订单文件及跨订单汇总文件。汇总限定已完成正式订单的 `ORIGINAL_CONFIRMED` 首版结果，支持通用 Excel 和 PDF。扫码模板继续从单订单申请。产品按原代码、名称、型号分组，重量以整数字符串计算，Web 使用 BigInt 显示精确 kg。

查询范围采用北京时间原确认日，包含开始日及结束日；SQL 使用上海零点对应的 UTC 半开区间。按原确认时刻、订单 UUID 升序固定成员；单内采用持久化原扫码序号。最多31天、100单、Excel5000包、PDF1000包，超限明确拒绝，不截断。空范围可以查询并生成空文件。

汇总查询是查询时的当前结果；申请导出在独立一致性事务内重新固定完整范围。因此查询与申请之间新确认的订单可以进入新文件，页面明确提示。已接受申请固定快照、成员、原修订及结果版本。丢响应后使用本机保留的原键、原日期、格式和恢复代次回放，不能改为一次新申请。

任务列表每页20项，使用 `created_at DESC, id DESC` 及最后一项 UUID 游标。格式与有效状态筛选在数据库分页前执行，代次变化优先显示 `INVALIDATED`，过期显示 `EXPIRED`。Web翻页沿用上次实际查询的筛选条件，编辑筛选而未点击查询不会混入另一范围。状态变化可能使动态筛选下的任务移出或移入；刷新重新读取最新第一页，不声称任务列表是不可变全量快照。

失败、已成功或过期任务可按原快照重生成：新任务 ID、新有效期，复用原摘要、快照 ID、成员、格式及模板；原任务和文件保持。原模板不可用、快照损坏/缺失、恢复代次不同则拒绝。尚未到期的在途任务不能重复生成。每次原请求回放和重生成均重新检查当前账号、仓库授权及本人归属。

创建/重生成事务先取得server_state共享锁和当前用户行锁，再重新鉴权，之后才读取原任务或建立汇总一致性快照；控制器的先行检查不代替锁后检查。会话、角色或仓库授权在先行检查后被撤回时，事务必须拒绝且不生成任务。6项真实MySQL双连接测试和独立只读复审已覆盖这一并发窗口。

## 待主线整合的正式接口

以下接口已在本分支实现，尚未进入正式 OpenAPI。使用现有 ApiEnvelope、会话、CSRF、恢复代次写屏障。

| 方法与路径 | 输入与结果 |
| --- | --- |
| `GET /api/v1/exports/summary` | `warehouseId, fromDate, throughDate`；返回 basis、range、readAt、orderCount、actualCount、weightGrams、orders、products |
| `GET /api/v1/exports` | `warehouseId`，可选 before、format、status；返回 items、nextCursor |
| `POST /api/v1/exports` | `warehouseId`；Idempotency-Key、X-Server-Recovery-Epoch；正文 format=`XLSX/PDF`、basis=`ORIGINAL_CONFIRMED`、range={fromDate,throughDate}；返回任务 View |
| `POST /api/v1/exports/{id}/retry` | `warehouseId`、上述两个头；不接受新范围/格式；返回新的任务 View，原键重复调用返回同一任务 |

现有任务 View 新增：可空 `orderId`、可空 `range`、非负 `orderCount`、可空 `retryOf`。跨订单 revision=0，单订单保持原正数修订。旧持久任务 payload 缺少新增字段时按单订单处理，不需要数据迁移；新 API 与新 Web 应一起启用，旧 API 不提供新 View 字段。

错误码新增/复用：`EXPORT_RANGE_INVALID`、`EXPORT_FORMAT_UNSUPPORTED`、`EXPORT_LIMIT_EXCEEDED`、`EXPORT_STATUS_INVALID`、`EXPORT_NOT_RETRYABLE`、`EXPORT_GENERATION_CHANGED`、`EXPORT_TEMPLATE_UNAVAILABLE`、`EXPORT_SNAPSHOT_UNAVAILABLE`、`IDEMPOTENCY_CONFLICT`、`EXPORT_QUEUE_FULL`。未知任务和非本人任务统一404。

## 共享文件与整合顺序

1. 先消费 `effba8f` 的 report-rendering、server exports、`server/build.gradle`/`settings.gradle` 依赖，以及原订单导出页。再消费本批中心源码与测试；本批不修改 report-rendering。
2. 本批共享界面接入仅 `web/src/navigation.ts`、`web/src/router.ts`；与主线TEMP页面的导航/路由变更逐项保留。服务端只改 exports 模块，不改认证、记账、提交、Job/Worker或恢复核心。
3. 测试公共支撑 `PcFixture` 增加可选择跳过重复政策初始化的构造重载，既有构造保持原行为；仅新增同仓多单测试使用该重载。
4. 主线负责正式 OpenAPI 索引、接口版本、应用版本和组合验证。本批无 Flyway、Room 或应用版本变化，不抢占迁移编号。

## 明确保留的待办

TEMP、更正净结果、多设备来源，等待各自稳定模型；正式契约整合及当前主线组合回归另列。任务筛选仍扫描 JSON 字段，未新增索引/专用元数据表，生产规模压测待做。文件保留7天不等于自动删除，清理、配额、恢复引用和异地备份与R09衔接。现场Excel/打印机、异地HTTPS、公网部署均不在本批完成声明内。

专用库23308、preview种子、原签名材料及证据保留。真实业务库、PDA、主线设备、现网和历史审批阻断不操作。

当前API18108/Worker使用本任务`.local/r08/integration/center-final-server.jar`固定副本，摘要及PID见PROGRESS和`preview-pids.json`。完整浏览器复跑曾因原build JAR运行中被后续bootJar替换而5项登录超时；仅重启本任务进程后5项全过，原失败日志保留。浏览器筛选用例覆盖格式筛选；未应用筛选后再跨页组合未作浏览器验收，分页及有效状态筛选的4项后端测试结果单列。
