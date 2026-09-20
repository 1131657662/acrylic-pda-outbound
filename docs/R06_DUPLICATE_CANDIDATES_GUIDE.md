# R06 M2b-1 只读重复候选与计划对照

日期：2026-09-20。基线 `90581375128ee426fcf979a76eb44f4bb7734fef`，独立分支 `codex/r06-duplicate-candidates`，工作树 `D:/acrylic-pda-worktrees/r06-duplicate-candidates`。本批依据 [M2b方案](R06_M2B_IMPLEMENTATION_PLAN.md)，只补临时审阅中的疑似重复核对信息。没有确认、排除、关联、拒绝、记账或放行操作。

## 查询行为

新增 `GET /api/v1/temporary-reviews/{id}/duplicate-candidates`，每次核验当前 `temporary_order:confirm` 及仓库授权。对象须为同仓且已完整接收正文的 TEMP 批次；正文前 ABORT、正式来源或其他仓对象返回 404。具备临时审阅权但没有 `order:read` 的人员可在此核对同仓正式候选，不因此取得正式订单其他接口的权限。历史批次可读，但不再待确认的来源显示不可关联原因。

候选限定同仓 `WEB` 正式订单，以服务器建单 `createdAt` 按北京时间首尾日期检索；车号精确相同或产品 ID 有交集时提示疑似重复。两个信号是人工核对线索，不自动按车牌或产品合并。不同车号但同产品也可能出现，因此不得把列表当作已判定重复的订单清单。

不传首尾日期时，默认以原批次服务器完整接收 `receivedAt` 所在日期前后各 7 天查询。日期可调整，一次最多 366 天，是查询技术边界，不是临时作业 48 小时资格规则，也不是放行阈值。原 PDA `closedAt` 只保留为原申报证据，不充当可信时间锚点。日期缺半、逆序、非法或超过技术范围返回错误，不显示成无候选；范围外仍可能存在疑似重复。

响应同时保存用于本次对照的原来源、提交批次、正文/计划摘要、轮次、current 指针、canonical 记录、原计划/实绩、恢复代次及审阅权限版本。每个候选返回正式号、车柜号、服务器时间、状态、计划修订/记录版本、库存/映射版本及完整产品计划。

## 分页与不变性

每页实际在数据库使用 `created_at DESC, id DESC` 游标分页，支持 1～200 条，Web 固定 20 条。`total` 是当前完整查询范围的候选数，页面明确已应用日期、本页数、总数和读取时刻。多个匹配产品通过 `EXISTS` 判断，不放大订单行数。

一次请求使用只读 `REPEATABLE_READ` 事务，核对原上下文、按相同条件遍历完整候选头计算摘要及总数，再查询当前页和批量读取其计划。没有候选数量静默截断；完整候选摘要需要遍历范围内候选头，大规模查询性能尚未压测。

游标绑定原批次上下文、仓库/日期范围及候选集版本摘要，边界时刻/订单还须确实属于该范围。新增正式单、发布/修订、已有作业标志或原来源状态变化使旧游标失效，返回 `409 TEMPORARY_DUPLICATE_CONTEXT_CHANGED` 并要求重新查询；伪造/损坏游标返回 `400 INVALID_CURSOR`。这不是长期数据库快照或已持久化的人工核对决定，后续写服务仍必须重新授权并在共同锁下重新核对。

查询不写正文、current/slot、决定、占用、出库结果、记账、回执或业务审计。候选 `linkAvailable` 与响应 `writeAvailable`、`releaseAllowed` 均固定为 `false`，前端契约拒绝将这些字段改成 `true` 的响应。

## 当前对照与 Web

正式目标非 READY、有任意 PDA 领取历史、PC 作业、提交控制头/批次或出库结果时分别显示原因。产品、名称/型号、计划包数及库存版本差异也显示待核对；成功事实不能通过关联迁移或重记。这里的 `blockers` 只是本批已检查条件，空集合不证明全部关联条件满足。

临时详情加入独立候选对照组件及日期检索。日期尚未应用时禁翻页，页面保留已应用范围说明；查询失败清除旧对照，避免显示成无候选。切换批次/仓库后迟到响应不覆盖当前结果。原计划仍在详情展示，候选卡片展示正式计划和版本；只有查询与分页按钮。

## 实际验证与失败记录

最终作者证据位于 ignored `.local/r06-duplicates-20260920/`。业务、测试及契约 19 文件的原始内容指纹为 `62675dec0f4f0d14451f48934251de98eec7b07785bbac1cfc48edc23369d063`，在独立复审期间和最终验证后保持一致；专项指南不计入该指纹。

| 验证层 | 当前结果 |
| --- | --- |
| MySQL / MockMvc | 最终 59 项通过，0 失败/错误/跳过：28 项新候选测试及 31 项既有临时审阅回归；含游标锚点归属保护。见 `final.log`、`final-results/` |
| Web 构建 | 第二轮 `npm run build` 通过；保留既有大 chunk 提示 |
| Web 契约 | 最终 11 项新候选及 7 项原临时审阅检查通过，使用本次真实 MySQL/MockMvc 响应，检查原上下文、只读常量及日期边界 |
| Vue 状态 | 4 项通过，以 Node 内 Vue renderer 与 HTTP 边界替身验证日期应用、旧仓响应、409 重查和无效输入；不是浏览器验收。与上项共 22 项见 `web-final.log` |
| OpenAPI 片段 | 在内存中与原 112 路径组合为 113 路径验证通过，仓库 OpenAPI 总索引未修改 |
| 后端单元 / JAR | 35 项单元通过，0 失败/错误/跳过，`bootJar` 构建成功。见 `unit-final.log`、`unit-final-results/` |

首轮 Gradle 同时发现 `Timestamp` 通配导入冲突及报表依赖字体网络读取超时；改为明确导入，第二次下载等待主动终止后，复用首轮整合已校验 SHA-256 的 Noto 字体，通过既有 `r08FontFile` 参数构建，没有改依赖或跳过字体校验。首个实际 IT 轮 57 项有 2 项失败：测试夹具复用 TEMP 与 FORMAL 同一提交 ID，触发既有“来源不可改变”保护；修正为独立正式批次后第四轮 58 项通过，未放宽服务端来源规则。

Web 首轮类型检查因复用契约的 `.ts` 扩展导入不符合项目配置而失败，改用现有无扩展写法后通过。Node 专项仅在测试解析器中补扩展，不修改生产 tsconfig。各次日志和 XML 保留，数据库/凭据、私钥、字体、原样本、构建产物及响应样本均不入 Git。

前端定向命令（在 `web/`）：`node --experimental-strip-types --test tools/temporary-review.test.mjs tools/temporary-duplicates.test.mjs tools/temporary-duplicate-state.test.mjs`。先在配置好的新合成库运行候选与临时审阅 IT，生成本次 `server/build/temporary-duplicate-contract.json` 和 `temporary-review-contract.json`，不能把旧响应样本冒充本次后端证据。临时 113 路径组合仅存在内存，正式索引仍交给整合执行者。

## 版本、共享接入与未完成

Flyway V33、Android code20/Room17 和现有正式 OpenAPI `0.27.0` 总索引保持。本批只提供 [路径片段](../contracts/temporary-duplicate-paths.yaml) 与 [响应片段](../contracts/temporary-duplicate-schemas.yaml)，由整合执行者统一挂载 `/temporary-reviews/{id}/duplicate-candidates` 并决定契约版本；尚未挂索引不代表该接口没有源码。

只使用新专用合成库 `acrylic_outbound_r06_duplicates_20260920`、限定该库的新账号、独立签名及文件目录；MySQL 为获授权的共享 `127.0.0.1:13316`。授权 `sku.txt` 样本从主目录复制后确认摘要相同，不作业务输出。没有启动真实 HTTP/API/Web/Worker 监听、浏览器或设备；未进行实际页面/真机、Wi-Fi、异地、长时、断电或现网部署验证。

R05/R06 原库、恢复断点、旧工作树、AVD/M71 及现网保持。本批没有迁移、Android、共享导航/路由、提交/记账核心或管理五文档改动。写决定前仍须补齐 guard/外键/会话完整锁图与并发证据；成功 TEMP 后正式订单侧的反向提示、候选排除、受控关联、拒绝实物处置和最终签名成功恢复均属后续批次，未因只读候选完成而宣称实现。
