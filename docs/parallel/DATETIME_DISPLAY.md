# 日期时间展示统一：独立修复记录

2026-09-20。本轮完成 Web/PDA 公共格式及非重叠既有页面的源码修复与专项验证，**不是全端整合完成**。R06 临时作业、R08 导出和设备实际页面验收仍待后续补齐。

## 工作树与范围

- 工作树：`D:\acrylic-pda-worktrees\datetime-display`；分支：`codex/datetime-display`。
- 创建基线：`daf40834b04eeb43e5976636f834725f29ca5bd0`，当时本地及远端 `codex/development` 一致；目标目录和同名分支原先不存在。
- 原开发目录只读检查，当时仅有未跟踪 `docs/out.xlsx`；未复制未提交内容、配置、数据或构建产物。
- R06/R07/R09 设计工作树存在各自 `docs/parallel/`；R08 工作树基于 `8f566e9`，正在修改服务端 Gradle、`web/src/orders/OrderDetailPage.vue`，并新增 `report-rendering/`、服务端导出、Web 导出及测试。本轮没有编辑这些工作树或文件。
- 改动留在本专项工作树，未提交、推送、合并、打标签、发布或部署。公共 `AGENTS.md`、路线、开发进度和版本号未改，整合时统一更新。

## 展示约定

固定 `Asia/Shanghai`，例如 `2026-09-20T04:37:05.987Z` 展示为 `2026-09-20 12:37:05`。年/月/日/时/分/秒补零，24 小时制，午夜为 `00:00:00`；毫秒及更细部分仅在显示时省略，不四舍五入到下一秒。浏览器/设备语言、系统时区和 12 小时偏好不参与业务显示。

公共字符串入口接受带秒及明确 `Z` 或 `±HH:mm` 偏移的时间，兼容 1～9 位小数；空值、非法日期、缺少时区或单独日期显示“暂无记录”。不会用当前时刻代替数据时间，也不把不带时区字符串猜成 UTC。PDA 另提供明确的 epoch 毫秒入口；已有“尚未完成更新检查”等业务空状态和零值哨兵由原页面继续处理。

只在最终显示位置调用格式函数。UTC 原文、存储精度、签名清单、授权截止判定、同步重试、排序、接口/schema、Room/服务端数据库结构保持原逻辑。页面上的“数据读取于”“上次检查”继续取原来记录该事件的时间来源。

## 本轮接入

| 范围 | 接入方式及覆盖 |
| --- | --- |
| Web 公共入口 | 新增 `web/src/shared/date-time.ts` 的 `formatDateTime`；使用固定时区、Gregorian 日历、拉丁数字和 `h23`，按字段拼出固定文本 |
| Web 旧入口兼容 | `orders/contracts.ts`、`inventory/contracts.ts` 的 `localTime` 直接转出公共函数，不改接口 schema；现有调用点无需更换导入 |
| Web 既有调用点 | 订单列表/详情/计划历史/操作时间线/发布历史，库存导入/详情/版本/变更历史，设备登记/最近联网/短码申请，人员历史、规则发布、待审提醒、后台连接最近请求、PC 录入/停止/处置/审核记录 |
| Web 新修复点 | 下载页发布时间、正式订单续作资格截止时间改用公共函数；下载清单仍先验签，响应与清单原文不变 |
| PDA 公共入口 | 新增 `display/DisplayTime.kt` 的 `formatDateTime(String?)`、`formatEpochMillis(Long?)`，固定 `ZoneId.of("Asia/Shanghai")` 与 `Locale.ROOT` |
| PDA 配对与正式订单 | `PairingActivity` 显示完整到期日期时间；`OrderCards` 中正式订单离线期限不再直接输出 ISO 原文 |
| PDA 更新 | `PdaUpdateActivity` 的历史检查、本次检查、版本发布时间统一；安装/清单校验逻辑不变 |
| PDA 同步状态 | `SyncScheduleState.message()` 中最近核对时间统一，覆盖设置页现有提示；持久化状态和重试到期判断不变 |

上述 Web 旧页面多数通过兼容入口自动生效，专项浏览器验证选取下表代表页面；没有将代表页面通过写成逐页完整业务验收。R08 正在修改的订单详情文件没有直接改动，其既有时间调用通过旧入口获得统一格式。

## R06 / R08 待接入清单

以下基于对相应工作树的只读检查，不作为已经合入或验证的声明。

| 待接位置 | 当前差异 | 整合动作和验证 |
| --- | --- | --- |
| R06 `TemporaryPreparationViewModel.kt` | 私有 `displayTime` 使用 `ZoneId.systemDefault()` | 换为 `DisplayTime.formatDateTime`，删除私有格式；核对原资格固定期限、到期提示与恢复逻辑保持 |
| R06-C 新临时草稿/计划/扫码及后续提交页 | 正在另一任务开发，未包含在本工作树基线 | 枚举最终页面的资格截止、创建、保存、扫码、提交等时间显示，接入公共函数；只格式化现有字段，不新增时间事实或改变全机防重/事实顺序 |
| R06 首页及正式/临时共用入口 | 后续可能修改 `MainActivity`、`OrderCards` 或同步提示 | 合并时保留双方业务入口，复核本轮正式订单期限及同步时间调用，避免用旧文件覆盖新增功能 |
| R08 `web/src/exports/OrderExports.vue` | 申请时间/保留期限使用旧 `orders/contracts.localTime` | 合并公共入口后自动获得格式；在 R08 实际导出列表验证两个时间与下载到期判定，不能只依据本轮静态检查验收 |
| R08 `OrderDetailPage.vue` | 另一任务新增导出入口 | 保留其功能改动，继续复用公共入口；实际合并后核对订单摘要、历史和导出面板 |
| R08 `reports/Values.java` | `time(Instant)` 用 `LocalDateTime.toString()`，整分钟可能省略秒，小数可能保留 | 在 R08 模块中建立固定秒级格式函数，覆盖 PDF 确认/更正/生成时间等调用，使用同组跨日/整分/毫秒测试 |
| R08 `ExcelRenderer.java` / `ExcelDetails.java` | 元信息含 UTC Instant；接收/保存/异常/调整时间部分直接输出原文 | 明确普通可读展示列，按上海时间与 `yyyy-mm-dd hh:mm:ss` 单元格格式或固定文本实现；保留原快照/技术溯源字段原文及精度 |
| R08 `PdfDetails.java` / `ReportText.java` | 接收/保存/异常部分输出原字符串；公共时间文本依赖 `Values.time` | 统一普通阅读区格式；重新渲染核对列宽、换行、分页；PDF 技术元数据 `PdfMetadata` 中 UTC 原值保持 |
| R08 `ScanRecordExcelRenderer.java` | F 列按此前扫码表模板使用 `m/d/yy h:mm`，底层上海时间日期值仍含毫秒 | 本轮不改既有模板。最终整合时核对本专项秒级显示要求与扫码表模板约定，明确采用的展示格式并更新模板测试/记录；不重排、改写原扫码时间或丢底层精度 |

纯日期（报表期间、原出库归属日）不是时间戳，不人为补成当天零点。本专项不更改上海日界查询、原出库归属、快照成员、签名或授权规则。

## 实际验证与证据

所有测试、构建、缓存项目输出位于本工作树。Web 使用本树 `dist/`、合成 HTTP 边界数据以及 `https://127.0.0.1:5197` 静态预览，拒绝复用已有监听且没有 API 代理。未启动业务后端、数据库或模拟器，未读取真实业务样本，未安装 PDA、操作 M71 或切换公网。

| 检查 | 本轮结果 |
| --- | --- |
| Web 构建 | `npm ci --no-audit --no-fund`、`npm run build` 通过，包含 `vue-tsc --noEmit`；专项配置/测试文件另经 strict `tsc --noEmit` 通过；Vite 提示既有大 chunk 警告 |
| Web 格式/兼容入口 | 21 个用例在 3 个项目配置中执行，共 63 次通过；涵盖 UTC/显式偏移、跨日/跨年/闰日、补零、午夜、细小数截去、空值/畸形/无时区；单元用例运行在 Node，并不声称浏览器时区设置改变了 Node 时区 |
| Web 实际页面 | 6 个场景 × UTC/英文、洛杉矶/阿拉伯语、上海/中文，共 18 项通过：可信下载发布、订单桌面/窄屏及顺序、库存导入、设备列表、待审、续作期限与响应原文 |
| Web 视觉 | 查看洛杉矶/阿拉伯语项目的订单桌面/390px 窄屏及下载 360px 页面截图，时间完整可读；自动核对订单/下载无页面横向溢出 |
| Android 专项测试 | 7 项通过，0失败/错误/跳过：5项纯格式测试、2项 API28 Robolectric 同步提示测试；检查设备默认时区/语言变化、毫秒、无效输入、持久化内容及30秒重试边界保持 |
| Android 构建/lint | `assembleDebug`、`lintDebug` 通过；0错误、136警告。APK仅生成在本树 `android/app/build/outputs/apk/debug/app-debug.apk`，未安装或交付 |
| 差异检查 | `git diff --check` 通过；生产时间格式搜索剩余系统时区实现为明确排除的 R06 临时准备页 |

首轮 Web 为77通过、4失败：3项待审用例的合成分页夹具缺少 `hasMore`，另1项续作页未及时取得预期响应。补齐分页及 PC 视图夹具后81项全部通过；随后将过长夹具函数按订单/列表拆分，再次81项全部通过，0失败/重试/跳过。首轮结果保存在 `.local/datetime-display/web-first-results.json` 和 `web-tests.log`；没有修改业务接口来迁就夹具。收尾核对5197已无监听，默认业务套件发现列表不包含本专项用例。

最终证据：`.local/datetime-display/web-build.log`、`web-final-tests.log`、`android.log`，`web/test-results/datetime-display/results.json` 及页面 PNG，`android/app/build/test-results/datetimeDisplayTest/` 的 JUnit XML 和 `android/app/build/reports/lint-results-debug.xml`。以上均被 Git 忽略，只保存源码/测试/本文。

复现 Web：在本工作树 `web/` 运行 `npm ci --no-audit --no-fund`、`npm run build`、`npx playwright test --config=datetime-display.config.ts`。需要 Chrome、Python cryptography；端口5197须空闲，预览随 Playwright 结束关闭。默认业务 Playwright 配置排除此专项目录，避免误用其他业务测试服务。

复现 PDA：从本工作树根目录，以已安装 Gradle 8.11.1 运行 `-p android -I ../tools/datetime-tests.gradle :app:datetimeDisplayTest :app:assembleDebug :app:lintDebug --no-daemon --console=plain`，设置本机 `ANDROID_HOME`。专项 Test 任务复用 Android 的编译输出、classpath/JVM 配置和资源，但只执行 `pda.display.*`，不需要也不伪造服务端签名夹具；原 `testDebugUnitTest` 及其夹具前置保持。

没有运行原213项完整 Android 业务回归、真实 API/数据库联调、Android 系统12/24小时设置切换、实际设备/模拟器页面、系统覆盖升级、R06/R08 整合或公网验收。代码未改版本/Room/契约；整合交付时统一确定版本及上述验证。下一步按待接入表合并 R06/R08 已完成的独立成果，再做组合回归和设备展示验收，之后才能判断全端范围是否收口。
