# R08 文件生成及原确认文件接入交付记录

最新日期：2026-09-20。**总调度已将 R08 交给执行子代理 W2 接管，原 B 会话仍暂停。原确认跨订单汇总、导出中心和原快照重生成本批已完成源码及本机隔离验证；下方暂停记录是历史断点。** 仅本子项完成，不代表完整 R08、真实业务或现场验收完成。

## 2026-09-22 扫码人姓名口径放宽（扫码记录 Excel）

用户反馈 test 环境导出 `SCAN_XLSX` 报“扫码人缺少中文姓名，请完善人员资料后重新申请导出（请求 5052f470-…）”，并确认英文姓名应可用。核实：该限制是本格式自加的“必须含汉字”校验（`ExportOperatorNames` / `ScanRecordExcelRenderer`），人员资料录入本身只要求 `@NotBlank @Size(max=100)`，允许英文名；姓名来源为申请时读取的 `app_user.display_name`。

改动：新增 `report-rendering` 的 `OperatorNames.usable(name, reserved…)` 统一判定——姓名非空且不是占位值（拒绝空/纯空白、`原人员ID:` 前缀、人员ID本身含无连字符写法、账号名，账号比较忽略大小写与首尾空格）；**不再要求汉字，英文与其他文字姓名同样接受**。服务端 `ExportOperatorNames` 同时读取 `username` 参与判定，渲染前 `ScanRecordExcelRenderer.validate` 复用同一判定；错误码 `EXPORT_OPERATOR_NAME_UNAVAILABLE` 保留，提示改为“扫码人姓名缺失或无效，请完善人员资料后重新申请导出”。

本轮实际验证：渲染模块 `ScanRecordExcelRendererTest` 10 项（含新占位值拒绝/英文姓名接受用例）与新增 `OperatorNamesTest` 15 项全部通过；`gradlew -p server compileTestJava` BUILD SUCCESSFUL（exit 0）。同模块本轮 116 项中 2 项 PDF 测试因本机缺 Playwright `chromium_headless_shell-1187`（本机仅有 1228/1243）失败，属环境缺件，与本改动无关，不计入本次通过。

未做/未验：`R08ExportIT` 与 `R08ExportFenceIT` 未运行（需 R08 专用库 23308 或 wave1 合成库，当前均未运行，`.local/r08` 环境已不在）。

部署与真实验证（2026-09-22 23:25，用户授权）：以 overlay 方式发布 `/opt/acrylic-outbound-test/releases/v34-386739c-r08name-01`——复制原 `v34-386739c-02` 后仅替换 `server.jar`（`5767ea05bcec…`）与 `report-rendering/acrylic-report-rendering/lib/acrylic-report-rendering.jar`（`684ee0170d7b…`），重写 manifest.json/source-sha256.json 并通过发布自带 `verify_backend_release.py`；`activate_backend.sh` EXIT=0，API/worker 于 23:25:26/23:25:41 active，18085 监听、csrf 200。真实 HTTPS 三格式本轮全部 OK/SUCCEEDED（XLSX 13493B、PDF 360560B、SCAN_XLSX 4314B，sha256 `467ef76699ca…`）；导出的扫码记录工作簿列 E 实测为 `test_pda01`（英文姓名已可导出，旧阻塞消除）。Web/APK 发布物与数据库（仍 V34，无迁移）未动；旧发布保留，回滚=对 `v34-386739c-02` 重跑同一激活脚本。证据 `deployment-evidence/r08name-20260922/`。代码与文档改动**尚未提交、未推送**。

本文档 2026-09-20 小节中的“中文扫码人/缺失中文名拒绝”为当时口径，已被本节取代。

## 总调度 W2 接管与本批收口（2026-09-20，当前接续）

沿用 `D:/acrylic-pda-worktrees/r08-export` / `codex/r08-export-rendering`，从 `effba8f` 及原未提交断点继续。当前完成原确认上海日期汇总、固定成员和版本的 Excel/PDF、本人任务中心、真实游标及格式/有效状态筛选、丢响应原键恢复、原快照重生成和旧单订单 payload 兼容。范围严格限定正式订单 `ORIGINAL_CONFIRMED` V1；接口、约束与整合清单见 [EXPORT_CENTER](EXPORT_CENTER.md)。

独立审查发现导出申请和重生成在等待用户行锁期间可能使用撤权前的检查结果。已在 `server_state` 共享锁、`app_user FOR UPDATE` 后且首次一致性读前重新执行 `Access.require`；会话失效、角色撤回、仓库撤回分别覆盖申请与重生成，共6项真实MySQL双连接测试通过。W1只读复审确认原P1关闭，未发现新的阻断问题；当前26个代码/测试文件的内容指纹为 `ca205d5d683f9132a369831b23c6a47b7d64df8cfd7112e1d61fecb8f9b8ea33`（排序后逐行path、TAB、原始文件SHA256、LF；不含本文档）。

| 本批实际验证 | 最终结果与证据 |
| --- | --- |
| 后端集成 | **40项通过，0失败/错误/跳过**：AuthorizationRace 6、CenterPagination 4、ExportFence 4、Export 13、Retry 7、Summary 6。`.local/r08/center-authorization-final.log` 与 `server/build/test-results/integrationTest/`；覆盖原确认日界、精确汇总、固定快照、分页、越权、原键重试和旧payload兼容 |
| 后端单元与构建 | 同一最终命令的 `test` **35项通过**（含3项子进程），`bootJar`通过。未将模块旧101项冒充本批重跑；本批未修改report-rendering |
| Web | 类型检查和生产构建通过，`.local/r08/center-web-build.log`；既有大chunk提示保留 |
| 真实浏览器 | 最终 **5项全部通过，52.4秒**：中心Excel/PDF及原快照重生成、中心丢响应刷新恢复、格式筛选、既有单订单3格式下载、既有单订单丢响应恢复。`.local/r08/center-browser-final-restart.log`；本轮不声称浏览器跨页/未应用筛选组合已验，分页4项为后端集成证据 |
| 文件回读 | `.local/r08/integration/verify-center-files.py` 最终通过7份文件核对：冻结/重试各3包901.503kg；跨订单6包1803.006kg、4页PDF；空Excel；浏览器24包7212.024kg、7页PDF。核对快照成员、原顺序、文本条码、精确重量、无公式/链接、嵌入字体及原确认/TEST_SAMPLE口径。报告`center-file-verification.json`，执行日志`.local/r08/center-files-final.log` |
| 视觉与差异 | 已检查跨订单PDF的Poppler 4页渲染及桌面/390px截图；最后浏览器重跑的新桌面/手机截图再次目视核对，未观察到上部页面横向溢出。`git diff --check`通过 |

首次失败和修复均保留：

- 初次32项IT中11项失败：多订单重复初始化政策，以及夹具直接设FAILED但未满足尝试次数约束；只修正夹具，第二轮32项通过，后续增加授权并发和旧payload/并发重试至最终40项。见`center-it-first.log`、`center-it-second.log`。
- 首轮浏览器4通过/1失败，Element Plus占位层截获输入框点击；改为点击select容器，定向1项通过。该用例最终名称与断言只覆盖格式筛选，未把原标题提及的分页算作已验。见`center-browser-first.log`、`center-browser-filter-final.log`。
- 最终完整复跑首次5项均在登录超时：API/Worker于17:18启动，原build JAR随后被17:33的最终bootJar覆盖；API出现`NoClassDefFoundError: ThrowableProxy`。只重启已核身的R08进程，改用经SHA256核对的固定副本，之后完整5项通过；未改源码、重跑Gradle或重置原种子。失败日志`center-browser-final.log`、`integration/center-api-error.log`保留。文件核对初次使用系统Python缺少openpyxl，改用Codex已安装运行时后通过，未安装或修改依赖。

当前预览环境：API18108 **PID26576**、Worker **PID16292**、Web5178 **PID22608**、MySQL23308 **PID26004**保留运行，均已核对所属R08路径。API/Worker固定读取 `.local/r08/integration/center-final-server.jar`，SHA256 `D10AA00F0815F236A656285DD95C3A3D43D88AD7E3013CC47B5E4B09A0304CE6`。进程与摘要登记在`integration/preview-pids.json`，新日志为`center-final-api*.log`/`center-final-worker*.log`；原库、preview种子、签名资料、私有文件及证据保留。今后构建不得覆盖运行进程的JAR，先固定副本再启用。

本批按授权仅提交并推送独立分支，具体SHA由Git及交付回报核验。下一步由总调度安排正式OpenAPI/版本和当前主线组合整合；共享点为Web导航/路由、基线server复合构建依赖和既有订单导出入口，`PcFixture`仅增加可选政策初始化重载。未合入development/main、打标签、发布、部署公网或操作PDA。

未完成：TEMP来源、更正净结果、多设备来源、正式契约和主线组合回归、生产规模查询性能/索引、文件自动清理/配额与R09引用和异地备份，以及现场Excel/打印机、异地HTTPS。当前数据库仍用本R08已验证V28模型，无新迁移、Room或应用版本变化；不将本批汇总视为更正后的净结果。

## 历史：用户要求暂停（2026-09-20，已由上述W2接管续接）

用户要求B/C/D任务立即暂停，等待后续明确恢复。本任务已停止新增开发、测试和后续推进，仅记录断点；此次暂停不新增提交、推送或合并。没有子代理或本任务自动续跑安排。

- **分支/已保存成果**：工作树 `D:/acrylic-pda-worktrees/r08-export`，分支 `codex/r08-export-rendering`。既有Excel/PDF、模板扫码Excel及单订单下载闭环已在暂停指令前通过 `effba8f` 提交并推送，当前HEAD与上游一致。该批本轮验证为模块101项、服务端35项单元、17项MySQL/MockMvc、Web构建及2项真实浏览器通过；首次失败和重跑见下一节。
- **未提交开发断点**：原确认跨订单日期汇总、导出中心API/Web、原快照重生成已写入工作树但未完成功能验收。修改现有 `ExportModels/ExportProducer/ExportJobQueries/ExportSnapshotReader` 及Web导出契约、导航、路由；新增 `ExportRangeReader/ExportSummary/ExportSummaryQuery/ExportRetry/ExportCenterController` 与Web中心页面、汇总页和请求/任务状态模块。保留全部已修改及未跟踪文件，不清理、不重置。
- **新增范围实际验证**：暂停前服务端 `compileJava`、Web `npm run typecheck` 通过。之后对ExportRetry的过期重试条件有一处修改，尚未重新编译；新增范围未运行单元/集成/浏览器/文件回读或视觉验证，不算完成。旧101/35/17/2项结果属于保存前基线，不能作为新增范围通过证据。
- **已知运行不匹配**：本机API/Worker仍是保存前旧JAR，未重启接入新API；Web5178为Vite，会读取当前未提交源码。新Web导出契约增加字段，而旧API没有这些字段，因此当前浏览器预览可能报响应异常；暂停期间不修补或重启。
- **运行环境**：本轮未启动长期服务。先前保留的专用MySQL `127.0.0.1:23308` PID26004、API18108 PID23468、独立Worker PID29816、Web5178 PID22608在暂停核对时仍运行，均属于R08原隔离环境；未停止。API/Worker使用preview库，IT使用 `acrylic_r08_it`。进程登记 `.local/r08/integration/preview-pids.json`；专用库、签名材料、快照/文件、日志和证据全部保留。本轮构建/测试命令均已退出，无在途本轮长操作。
- **恢复后下一步**：先核对Git和上述环境，审查新增汇总/重试/分页及Web请求恢复逻辑；补越权、上海日界、固定成员/版本、幂等、分页、状态筛选和原快照重试测试，再完成独立环境API/Worker/Web及文件验证。正式OpenAPI及共享文件整合清单尚待整理；共享接入点目前为server构建两处、订单页、Web导航/路由，正式契约索引、版本、迁移编号由主线协调。临时来源、更正净结果、多设备来源分别等待对应已提交契约。

主工作树和其他任务未修改，未改提交/记账核心，未合并development/main、打标签、发布、部署公网或操作PDA。等待用户明确恢复，不自行继续。

## 本次接续：先保存既有成果（2026-09-20）

依据主项目 `docs/REMAINING_DEVELOPMENT_PLAN.md` 和本次明确指令，先保存本工作树既有Excel/PDF、模板扫码Excel和单订单闭环，随后推进原确认跨订单汇总与导出中心。依赖仍为已提交的 `8f566e9`；主线在途R06、V33/Room17及其他工作树不带入。正式契约索引、版本和总进度由主线整合，本任务维护此目录。

保存前本次复验：模块101项、服务器35项单元（含3项子进程）、17项R08 MySQL/MockMvc、Web构建和2项真实浏览器测试通过；没有重新进行Excel客户端、PDF视觉或现场验收。首次后端全量单测因本工作树缺少原 `docs/sku.txt` 失败，复制主项目同一获授权样本至忽略路径后重跑35项通过；样本不提交。首次并行模块/服务器构建发生共享输出争用，改为串行后101项通过。另两次Gradle命令路径/复合构建任务定位错误在执行测试前退出，均保留日志。既有Web大chunk提示保留。

本批按明确路径提交并推送至 `codex/r08-export-rendering`，实际提交号以Git为准；不合并开发分支/main，不打标签、不发布、不部署。凭据、私钥、专用库、文件、日志和原始证据不入Git。后续新增汇总及中心单独成批验证和保存，不将本次基线通过冒充新增功能验证。

## 最新补充：模板扫码记录 Excel（2026-09-20）

用户指定 `docs/out.xlsx` 后，新增 `SCAN_XLSX` / `r08-scan-records-1`：单表 `out`，序号/型号/条码/kg重量/扫码人（人员资料姓名）/中国时间六列，无标题、表头和合计。列宽逐列与参考文件一致，默认及单元格字体宋体11；长文本保持列宽换行。原顺序按持久化订单序号，显示序号从1连续编排。独立模块、CLI、Web按钮、固定快照、后台任务及受权下载已接入，仅本扫码记录子项完成。详细字段及限制见 [扫码记录接入说明](SCAN_RECORDS.md)。

PDA姓名是申请导出时读取并固定的人员资料姓名，私有快照保留原人员ID及来源；不是扫码时历史姓名。PC使用原冻结姓名。姓名缺失或为占位值（人员ID/账号）时拒绝申请，中英文姓名均可（口径放宽见下节 2026-09-22）；后续改名不改变原幂等请求。扫码时刻转Asia/Shanghai，模板显示到分钟，底层保留毫秒；不校正设备时钟。仍只支持单个正式订单原确认V1，不包含R07更正净结果和临时订单。

本轮最终实际验证：

| 范围 | 结果 |
| --- | --- |
| 模块 | 101项全部通过，含新增10项；空表、型号/多产品、原序号间隔、时间倒序、上海跨日、中文空格、前导零、200位条码、精确重量、恶意文本、50000行；`samples verifySamples installDist --offline`通过 |
| 服务端 | `R08ExportIT`13项、`R08ExportFenceIT`4项通过，真实专用MySQL；新增PDA姓名固定/改名幂等、PC冻结姓名、无中文名拒绝。子进程3项本轮通过；最终bootJar通过 |
| 浏览器 | 最终2项通过（28.7秒）；Web点击通用Excel/扫码记录Excel/PDF，各自等待本次任务完成、下载；刷新与丢响应幂等恢复。桌面/390px手机截图已检查 |
| 文件 | openpyxl独立逐行比对已接受快照：模块3行901.503kg、PDA2行601.5kg、PC3行901.503kg、浏览器24行7212.024kg；核对六列宽、文本类型、型号/姓名/扫码顺序、日期/精度，无公式/链接。只读比对原out.txt与out.xlsx全部81行一致，未导入业务库 |
| 本机显示 | WPS 12.1.0.28505通过Excel兼容COM只读打开合成样例，条码Text保留前导零；100%缩放导出检查PDF，Poppler渲染后已目视检查。未声称Microsoft Excel或实际打印机验收 |
| Web/工作区 | Web类型检查和生产构建通过，既有大chunk提示保留；`git diff --check`通过。未修改公共路线/总进度、正式OpenAPI、迁移、授权/调度内核或应用版本 |

回读证据：`.local/r08/integration/scan-file-verification.json`、`files-it/verified-pda-scan.xlsx`、`files-it/verified-pc-scan.xlsx`、`browser-evidence/web-scan-records.xlsx`、`scan-records-native.pdf/png`。浏览器文件SHA256为 `8ad377eb4bb8e047bf7762fea2f4e0878711b3b90fd5a8bd14d63f783e533d84`。可查看合成样例 `outputs/r08/scan-records-TEST-SAMPLE.xlsx`，可复现输入 `report-rendering/examples/scan-records-test-snapshot.json`。真实参考数据不入Git，输出与证据留在本任务忽略目录。

修正及限制：Artifact预览器会把全数字文本条码显示为去零数字，文件实际文本经POI/openpyxl/WPS交叉确认正确。新增Normal样式和模板默认字体，解决新格式的默认样式兼容提示；首次使用完整CTCellStyles类编译失败，改用已有XMLBeans结构化游标后重跑通过，无新依赖。WPS自动化将Zoom=false导出为过度缩小，检查脚本改为100%后显示通过，未为此改变业务文件列宽。回读首次遇到测试有意篡改的快照，改为只选择摘要匹配的源快照后通过。超长文本仍受Excel409磅行高上限约束，完整内容可在编辑栏查看。

专用MySQL23308、API18108、Worker和Web5178保留运行；最新PID登记在`.local/r08/integration/preview-pids.json`。未重置预览种子，不使用其他任务服务。分支仍为`codex/r08-export-rendering`，HEAD仍为`8f566e9b33cafa41269787dc7d9b4e2788355daa`；本轮未提交、推送、合并、部署公网或操作PDA。后续继续限定R08；更正模型、完整导出中心及生产/现场验证保持待办。

## 当前接入工作（2026-09-20）

用户明确继续推进已就绪依赖上的 Web 发起、后台生成、下载正式订单文件。本工作树经核对后快进到开发分支已提交的 `8f566e9b33cafa41269787dc7d9b4e2788355daa`，原始起点 `2369392076083a5a4f59d8ea3d6edebe5067a03f` 不变；保留已有 R08 未提交文件，未带入主工作区 R06 改动。

本次接入限定单个已完成正式订单的 `ORIGINAL_CONFIRMED` / 结果版本1，复用 R05 持久任务、租约和发布栅栏。复用当前 `order:read` 与仓库授权，仅创建人可查询和下载自己的任务。新增订单详情入口，不接入更正净结果、临时订单或全局导出中心。本次新增接口先在本目录登记，不修改正式 OpenAPI、迁移、任务内核或应用版本。

已完成数据库首版提取、并发幂等、私有快照、子进程生成、租约发布、受权下载和订单页。接入契约、配置、映射及后续事项见[接入说明](WEB_INTEGRATION.md)。所有验证数据为合成数据，文件标记TEST_SAMPLE；未部署、未操作PDA、未提交或推送。

### 上一轮接入验证（本轮新增格式见上节）

| 项目 | 结果 |
| --- | --- |
| 独立模块 | `test samples verifySamples installDist --offline`通过；91项测试，0失败/跳过；重新生成空数据、多产品、长条码/文本、恶意单元格文本、调整提案和50000行Excel；PDF样例1/3/13/4页 |
| 服务器 | `R08ExportIT`10项、`R08ExportFenceIT`4项全部通过；真实MySQL覆盖PC/PDA提取、零实绩产品、异常、固定快照、并发幂等、撤权/错仓/他人拒绝、篡改/缺失/过期、租约失效、发布回滚和恢复代次阻断 |
| 子进程 | `ExportRendererProcessTest`3项通过；实际Java测试进程在失租、超时、中断后退出，无结果文件 |
| 预览夹具 | 独立preview库通过既有正式业务API创建24包PC订单和含2项失败输入的PDA订单；一次性种子1项通过，数据保留 |
| 浏览器 | Playwright最终2项通过（21.1秒）；真实API18108、独立Worker、Web5178；点击Excel/PDF、等待本次任务ID、下载、刷新恢复、POST已接受但丢响应后以原键回放 |
| 文件回读 | openpyxl核对24包/7212.024kg及3包/901.503kg，序号、前导零、文本类型、精确kg，无公式/链接；pypdf核对7页PC和2页PDA PDF、字体嵌入、页码、合计、测试标记、原出库日口径及模板2 |
| 视觉检查 | Poppler实际渲染，核对中文、重复表头、原序号、换行、合计与签字；桌面1440×1000、手机390×844页面截图已检查，无横向溢出 |
| 构建 | 最新`bootJar`和Web `npm run build`通过，`git diff --check`通过；Web既有大chunk提示仍存在 |

证据位于本worktree `.local/r08/integration/`：`file-verification.json`、`files-it/verified-pc.xlsx`、`files-it/verified-pda.pdf`、`browser-evidence/web-original.xlsx|pdf`、`pdf-final-*.png`、`pda-final-*.png`、桌面/手机截图。Java测试XML在对应`build/test-results`，独立模块样例/回读报告在`report-rendering/build/samples/`。均为本机合成验证资料，不入Git。

最终浏览器样例SHA256：Excel `3609e3a783638cd9c93cf49243c93046170228adab94db6734ead8fbbb3ae7b6`；PDF `07778ca68f44b56ddaf9e26d552c422fdfbba9a151997e6c1f2555e3a5cc87ab`。

### 失败修正与边界

首轮8项集成中6项生成失败，原因是Windows不允许classpath通配符作为Path片段，修复后重跑通过。首轮浏览器丢响应注入发现未决提示在请求进行中提前出现，已调整并等待路由处理结束；一次启动阶段API未就绪引起登录失败，测试准备已增加就绪检查。下载测试绑定本次任务ID，避免重复运行选到旧文件。

实际PDF检查发现“当前净结果”会误导原确认文件读者，已改成“本版本净结果”，标明“不含后续更正”，模板升级为`r08-general-2`后重跑模块、集成和浏览器验证。文件核验脚本初次使用了错误元数据键/标题及页码假设，已按实际结构修正；pypdf把字体中的“页”提取为同形部首，核验兼容该编码，实际字形已目视检查。openpyxl有无默认Normal样式的提示，单元格值和样式可读取；未声称实际Microsoft Excel客户端验收。

环境仅为任务MySQL23308、账号`outbound_r08`、库`acrylic_r08_it`/`acrylic_r08_preview`。API18108、Web5178、独立Worker保留运行供本机查看：`http://127.0.0.1:5178/`；合成账号/订单在忽略文件`.local/r08/integration/files-preview/preview.json`，进程在`preview-pids.json`。凭据、密钥、数据库、产物均留在本任务目录，未使用其他任务服务。

集成上限：Excel5000包、PDF1000包、每人5个活动任务、3次尝试、7天下载有效期。过期/孤立文件保留但不可下载，自动清理与配额尚未接入。JSON任务查询未增加专用索引；未做生产并发压测、Linux进程监管、真实业务、Excel客户端、现场打印或异地HTTPS验收。

R06临时来源、R07交接/更正与最新净结果、跨订单汇总、完整导出中心、正式OpenAPI整合、R09清理备份继续待办。共享构建仅改server两处引用，既有Web订单页仅增加入口；未改公共路线/进度、迁移、共享任务内核、授权模型、同步、记账或应用版本。主工作区R06继续独立，未混入；本任务没有提交、推送、合并、部署或PDA操作。

以下为独立模块阶段历史记录，“未接入”描述以当时范围为准。

## 2026-09-20：已有依赖续接

用户指令“已具备依赖的部分继续推进”后，只读核对AGENTS、Git说明、公共路线/进度、R05分批收口/待运维清单、V2.3报表及更正规则、现有PDA/PC记账与回执、后台任务接口。当前开发依赖基线为 `32ff90f`（R05备份为 `7d50af0`）；R05持久任务基础已具备，但正式导出快照、R06/R07结果与权限下载仍需后续独立接入。

R08目录/分支保持 `D:\acrylic-pda-worktrees\r08-export` / `codex/r08-export-rendering`，HEAD及原始起点仍为 `2369392076083a5a4f59d8ea3d6edebe5067a03f`。原有40个未跟踪源码/文档文件保留并续接，没有重置、覆盖或合并开发分支。主目录本轮起初干净，收尾时会话1已有授权续期源码/测试等新改动，本任务未触碰。

### 本轮完成

- 新增模块自有 `ConfirmedV1Input` 和 `ConfirmedV1Adapter`，接收调用方固定的正式订单首版事实、产品/整单持久化汇总及原成功回执，派生无调整的版本1报表。输入包装不是服务器正式接口，映射和提取责任见[首版接入说明](CONFIRMED_V1.md)。
- 分别核对PDA和PC既有回执字段，绑定订单/提交/结果版本，核对全部计划产品（含零实绩）、精确克数及包数，拒绝错单、错批次、混设备、乱序、错汇总和无效回执。PDA同时核对高水位、首尾序号、回执时间不晚于截点及原上海出库日；PC保留原pc-work命名空间，不伪造PDA设备。
- 仅允许 `ORIGINAL_CONFIRMED`。未查询当前版本、未生成更正、未把原始首版冒充最新净结果。原抬头/明细/异常/前导零/精度与顺序保留。
- CLI增加 `--confirmed-v1`，原三参数调用保持兼容。新增明确标注TEST_SAMPLE的PDA/PC两单样例、44项适配/文件测试及接入文档。没有修改依赖版本、共享构建、server、数据库、正式OpenAPI、公共路线/总进度或应用版本。

### 本轮实际验证

| 检查 | 2026-09-20结果 |
| --- | --- |
| 完整离线构建 | `test samples verifySamples installDist --offline`通过，最后一次构建1分36秒；任务专用缓存/浏览器/临时目录 |
| 模块回归 | **91项通过，0失败、0错误、0跳过**；原47项本轮重跑，新增37项首版适配及7项JSON/CLI/文件测试 |
| 边界 | PDA/PC、空成员、多产品与零实绩、原序号空缺、跨来源/订单/提交混入拒绝、回执/汇总不符、超Excel精度克数、跨日/晚于截点拒绝、重复JSON字段、数字冒充字符串、恶意单元格及旧文件不覆盖均通过 |
| 原有规模/版式 | 本轮重跑50000条Excel验证，重新生成全部样例；PDFBox重开空1页、更正3页、长文本13页、首版4页，逐页样例标识/页码/字体嵌入/无链接检查通过 |
| 新Excel实际文件 | POI测试与openpyxl重开复核：6原始行，序号1/3/5、1/3/5，条码00000000001～003及00000000101～103，精确kg均300.501；恶意=HYPERLINK保持STRING，0公式、0超链接；原日期2026-09-18 |
| 新PDF视觉 | Poppler渲染4页并检查中文、重复表头、合计、页码和带订单号签字区；最终第1/2页PNG与已检查版本SHA256一致，第3/4页重新检查，无观察到的缺字、裁切或重叠 |
| 分发包实跑 | `.bat --confirmed-v1`分别生成xlsx/pdf；重新打开核对前导零、来源、合计及PDF4页。全部为合成样例，不涉及真实回执验签 |

样例每单3包/901.503kg，两单6包/1803.006kg，原上海出库日2026-09-18。PDA和PC使用不同条码，源序号与原序号分别保留；零实绩第三产品及VOID另列。真实Microsoft Excel客户端与现场打印仍未验证。openpyxl提示工作簿没有默认样式并采用其默认值，实际数据读取通过；不能据此宣称阅读端兼容性全部验收。

### 本轮产物与复现

- 输入：`report-rendering/examples/confirmed-v1-test-input.json`。
- 样例：`report-rendering/build/samples/confirmed-v1.xlsx`、`confirmed-v1.pdf`；分发：`report-rendering/build/install/acrylic-report-rendering/`。
- 分发包产物：`.local/r08/confirmed-v1-cli.xlsx`、`.local/r08/confirmed-v1-cli.pdf`。
- 测试：`report-rendering/build/test-results/test/TEST-*.xml`；完整日志：`.local/r08/verification-confirmed-v1.log`。
- 文件重开/91项汇总/产物SHA256：`.local/r08/qa/confirmed-v1-verification.json`；检查脚本：同目录`verify-confirmed-v1.py`。
- PDF最终视觉证据：`.local/r08/qa/confirmed-v1-final-{1,2,3,4}.png`；PDFBox复核：`report-rendering/build/samples/pdf-verification.json`。

本机实际构建命令（从本工作树根目录）：

```powershell
./report-rendering/run.ps1 `
  -GradleExecutable 'C:/Users/qq113/.gradle/wrapper/dists/gradle-8.11.1-bin/eac4u065zwes5phgltp5f9b9e/gradle-8.11.1/bin/gradle.bat' `
  -GradleArguments @('-I','D:/acrylic-pda-worktrees/r08-export/.local/r08/local-driver.init.gradle',
    '-Pr08FontFile=D:/acrylic-pda-worktrees/r08-export/.local/r08/NotoSansCJKsc-Regular.otf',
    'test','samples','verifySamples','installDist','--offline')
```

本轮测试无失败；收尾复核追加了回执截点检查和对应测试后，已重新完整验证。环境查询中两次错误路径/glob搜索已改为实际文件及`rg -g`，没有修改源文件。Windows分发脚本的中文完成提示在本机管道显示乱码，退出码0且文件内容/中文PDF正常；原始构建与文件验证记录保留。

### 限制与下一动作

首版适配仍需上游从可信的一致性快照提供数据，本模块不验证签名、正文摘要、授权、审批或数据真实性，不能从自洽的输入推断源数据真实。PC回执没有确认时间/完整抬头，提取方必须冻结原版本字段；跨日时刻有歧义时拒绝猜测。原有R06/R07更正样例继续只是提案。

后续仅续接本模块的文件验收与明确快照适配。服务器快照提取、真实数据联调、R05 handler注册及租约发布、导出中心、权限下载、真实R06/R07接入仍未执行。没有数据库、账号、端口或业务服务；没有公网部署或PDA操作。

实际动作：本轮编码、构建、生成、验证及私有进度更新完成；**未提交、未推送、未合并、未打标签、未发布、未部署**。本子项完成不代表完整R08完成。

## 2026-09-18首次交付记录（历史证据）

## 分支与隔离

- 工作目录：`D:\acrylic-pda-worktrees\r08-export`；分支：`codex/r08-export-rendering`。
- 起点提交：`2369392076083a5a4f59d8ea3d6edebe5067a03f`，从当时`codex/development`已提交状态创建。当前HEAD仍是该提交，本次新增内容未提交。
- 主目录开工时有AGENTS、README、公共路线/进度、设计规范等未提交文档；收尾时会话1又新增了PDA升级代码。本任务没有复制、覆盖、暂存或纳入这些改动。
- 所有新增源码/文档均在`report-rendering/`和`docs/parallel/R08/`；共享构建、正式OpenAPI、迁移、授权、同步、记账、应用版本及公共路线/总进度未改。
- 使用工作树内的Gradle缓存、浏览器、临时目录、字体、产物和验证证据。没有创建或使用数据库、账号、监听端口、业务服务，没有操作公网或PDA。

## 已交付

1. 独立Java21库、Gradle工程、依赖锁、隔离运行脚本和CLI分发包；无需Spring/API/数据库即可运行。见[运行说明](../../../report-rendering/README.md)。
2. 不可变模块输入：固定成员顺序、订单修订、结果版本、截点、期间、来源、交接顺序依据；原事实、调整效果和完整净结果逐包核对。缺失净行不会被当作退货，跨设备没有合法依据不猜测排序。
3. POI SXSSF流式Excel：订单/产品汇总、原始明细、调整凭据、当前有效明细、异常、快照说明；文本单元格保护前导零及恶意文本；BigInteger整数克求和、精确kg文本列和超精度数值空白策略；拆表保持顺序。
4. 固定中文字体的Chromium A4 PDF：原始/净明细、调整依据、计划/实绩/差额/重量、中文长文本、重复表头、合计、带订单号的签字区及中文页码。固定字体嵌入、HTML转义、禁JS和网络访问；CLI失败不发布半成品、不覆盖已有文件。
5. 明确虚构JSON样例、样例生成器、47项模块测试和实际文件重开核验。字段映射与R06/R07接入提案见[INTEGRATION.md](INTEGRATION.md)，设计见[DESIGN.md](DESIGN.md)。

## 本次真实验证

| 验证 | 最终结果 |
| --- | --- |
| Java21编译 / installDist | 通过；标准Java应用分发包可运行 |
| 模块测试 | **47项通过，0失败、0错误、0跳过**；包含7项调整、11项Excel、1项5万行、3项PDF、2项跨机/多单顺序、6项JSON/CLI、17项快照验证 |
| 离线复验 | 本任务依赖缓存就绪后用`--offline`运行`test verifySamples installDist`通过，没有调用数据库或业务服务 |
| 实际CLI | 从分发包分别执行JSON→Excel及JSON→PDF成功，产物`.local/r08/cli-verified.xlsx/.pdf` |
| 5万条Excel | `large.xlsx`原始与净明细各按1万行拆成5表。XML流式重开逐行核对1～50000顺序、11位前导零条码、精确300.501kg；合计15025050kg；未发现公式 |
| 空数据、多产品、长条码、跨页 | 空集合正常；A/B/A顺序与产品分组互不影响；160位样例条码、长型号及长备注完整显示；单测另验证189位条码与超Excel精度重量 |
| 更正与日归属 | 样例原确认3包/901.503kg，重量调增后跨日退回1包，净2包/601.002kg；仍归2026-09-18上海原出库日。车号0008→0009保留原值与凭据；不是R07真实业务验证 |
| PDF重开 | 空数据1页、更正3页、长文本60包13页；PDFBox逐页确认样例标记、页码、字体嵌入、无交互链接，签字区含订单号和合计；元信息含截点、版本、模板和口径 |
| PDF视觉 | Poppler渲染PNG，检查全部页面总览及首页、长条码/备注、跨页表头、调整和末页细节；最终无已观察到的页眉遮挡、中文缺字或文本裁切。正文9～10pt |
| Excel视觉与读取 | 7表用Artifact Tool预览；POI/原生XML核对文本类型、前导零、重量和顺序。Artifact预览对数字状文本有自动转换、部分跨范围合并标题不显示，不能据此声称真实Excel客户端已验收；未启动Microsoft Excel |

最大规模测试为50000条；大数据单测本机约6.981秒包含生成与XML检查，不是生产性能承诺。PDF的20000渲染行、JSON512MiB及200万原始行等上限仅为技术拒绝条件，未做完整上限负载验收。

## 产物与证据

- 输入：[corrected-test-snapshot.json](../../../report-rendering/examples/corrected-test-snapshot.json)，全部虚构，TEST_SAMPLE。
- 文件：`report-rendering/build/samples/{empty,corrected,multipage}.{xlsx,pdf}`、`large.xlsx`。
- 分发：`report-rendering/build/install/acrylic-report-rendering/`；库：`report-rendering/build/libs/acrylic-report-rendering.jar`。
- JUnit：`report-rendering/build/test-results/test/TEST-*.xml`；汇总：`.local/r08/test-counts.json`。
- 文件复核：`report-rendering/build/samples/pdf-verification.json`、对应`.txt`、`.local/r08/qa/files-verification.json`；SHA256：`.local/r08/artifact-hashes.json`。
- 最终生成/完整测试日志：`.local/r08/verification-complete.log`；最终隔离缓存离线复验：`.local/r08/verification-offline.log`。
- PDF渲染与Excel预览：`.local/r08/qa/`。`closing-3.png`是最后修订后订单合计与签字同页核查；`multipage-v2-contact.png`是已修复页眉的全页总览，末页随后去掉重复总合计。
- 二进制、浏览器、缓存、PNG和原始日志由现有`.gitignore`排除；仅源码、合成JSON样例、字体许可证和文档可进入后续提交。

## 失败及修正记录

1. Java下载Playwright大驱动及字体停滞，停止的是本任务Gradle daemon，原始日志因此显示daemon disappeared。驱动单独HTTPS下载、核对Maven SHA1后放入任务专用本地Maven目录；字体核对固定SHA256后经r08FontFile输入。没有放宽摘要或使用系统字体替代。
2. PowerShell的`-I`与公共参数缩写冲突，未执行构建；改为显式`-GradleArguments`数组，运行说明已记录。
3. 首轮44项中1项A4宽度断言失败：Chromium约595.9pt而测试取整要求595。改为A4宽高各1pt容差，后续44项全部通过。
4. 首轮PDF图片暴露固定页眉落在页脚覆盖表格；改为Chromium打印页边距区域。窄汇总重量断行改为更宽的原/调整/净列；去掉单单重复总计，签字区与订单号/合计同页，最终重新生成核查。
5. 新增JSON精度边界后47项中1项失败：Jackson默认允许数字转字符串，即使关闭一般标量转换。已对Textual明确禁止Integer/Float/Boolean转换；最终47项全部通过。
6. pypdf对部分中文返回字形部首别名（例如页字U+2EDA），字面页码检查失败。最终用PDFBox的ActualText读取逐页验证，Poppler视觉核查通过；pypdf仅辅助页数和元信息核对，没有修改文件掩盖问题。
7. POI初次生成提示缺少Log4j provider，已在独立模块运行时接入固定版本JUL桥接，最终日志不再出现该提示。

## 限制与下一动作

- R06临时来源、R07交接、更正凭据与逐包净结果依旧是**接入提案**。样例不证明审批、实物、签名、账务或接口已经实现/验收。
- 未接导出调度、权限下载、导出中心、真实更正适配、计划单/待审/历史模板、MOVEMENT_DATE。不自动开始这些后续事项。
- 模块校验结构与效果一致性，上游仍须验证快照真实性、授权成员、完整异常、可信交接凭据、结果版本及日界。不以非空凭据引用宣称已验真。
- 输入快照及校验索引驻内存，只有Excel写表流式；PDF整体HTML/浏览器渲染。后续Worker仍需单并发、OS资源限制、硬超时和磁盘配额。超长单元格明确拒绝，不静默裁切。
- PDF字体已固定嵌入；Excel显示依赖客户端中文字体。未验证真实Microsoft Excel、现场打印机、真实业务数据、数据库、真机、异地或生产负载。
- 继续时先核对此工作树和本文，只处理本模块问题/文件验收；真实R06/R07接入及完整R08由后续明确任务推进。

实际动作：已创建worktree/分支、编码、构建、生成及验证。**未提交、未推送、未合并main或development、未打标签、未发布、未部署、未操作PDA。** 应用、数据库、正式接口版本保持起点状态；不能据此宣称完整R08或系统完成。
