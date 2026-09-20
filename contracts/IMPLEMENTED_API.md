# 已实现接口与运行边界

当前契约版本 **0.28.0**，共 **113 条路径**（2026-09-20）。新增 `GET /temporary-reviews/{id}/duplicate-candidates`，要求当前 `temporary_order:confirm` 及同仓授权，不要求 `order:read`。按同仓 WEB 正式单服务器创建日期、车号精确相同或产品 ID 交集提示候选，返回原 TEMP 批次上下文、双方计划及不可关联原因；`linkAvailable/writeAvailable/releaseAllowed` 恒为 false，空 blockers 或空候选都不证明可以写入或放行。见[候选路径](temporary-duplicate-paths.yaml)、[字段](temporary-duplicate-schemas.yaml)及[第二轮整合记录](../docs/SECOND_WAVE_INTEGRATION_GUIDE.md)。Flyway V33、Android code20/Room17、server/web 工程0.1.0不变；R09无新增共享恢复API。以下0.27及更早段落保留为历史记录。

候选默认日期为 TEMP 正文服务器接收日的北京时间前后各7天，可调整；两日期须同时提供、年份2000至9998、含首尾最多366天，这是技术查询边界，不是48小时业务门槛。非法范围为400 `TEMPORARY_DUPLICATE_RANGE_INVALID`，非法或不存在锚点的游标为400 `INVALID_CURSOR`，非TEMP、正文前ABORT或异仓对象为404 `TEMPORARY_REVIEW_NOT_FOUND`。只读 REPEATABLE_READ 事务内核对原上下文、完整候选版本摘要、总数及SQL分页；候选或原上下文变化为409 `TEMPORARY_DUPLICATE_CONTEXT_CHANGED`，须重新查询。页大小1至200，total为全部匹配候选数；不写决定、排除、关联、占用、记账或回执。写阶段锁图与来源证据文档属于待并发验证的设计，不代表写操作已实现。

契约版本 **0.27.0**，共 **112 条路径**（2026-09-20）。整合 R06 独立只读临时审阅和 R08 完整 6 路径/8 操作：单订单原确认V1的通用Excel/扫码Excel/PDF、北京时间日期汇总、本人导出中心、固定原快照重生成及受权下载。要求当前order:read、仓库授权和本人任务归属；写请求需UUID幂等键、CSRF与恢复代次，锁内复核撤权。TEMP仍不可确认出库或放行，文件不含TEMP、更正净结果及未核验多设备来源。R09无新增共享恢复API。Flyway V33、Android code20/Room17不变；server/web工程版本仍为原0.1.0，不能与API契约版本混称。见[单订单/下载契约](export-paths.yaml)、[汇总/中心契约](export-center-paths.yaml)及[首轮整合记录](../docs/FIRST_WAVE_INTEGRATION_GUIDE.md)。以下0.26及更早段落保留为历史记录。

导出接口沿用统一错误响应。范围超过31天、逆序或年份越界为400 `EXPORT_RANGE_INVALID`；不支持的范围格式/状态为400 `EXPORT_FORMAT_UNSUPPORTED`/`EXPORT_STATUS_INVALID`。未知或非本人任务、非法分页游标为404 `EXPORT_NOT_FOUND`；无权仓库为404 `WAREHOUSE_NOT_FOUND`。100单/汇总包数/已通过原来源5000包检查的PDF包数超限为409 `EXPORT_LIMIT_EXCEEDED`；单订单源不可用（含超过5000包）为409 `EXPORT_SOURCE_UNAVAILABLE`，原数据不一致为409 `EXPORT_SOURCE_INCONSISTENT`，扫码来源不足为409 `EXPORT_SCAN_SOURCE_UNAVAILABLE`。幂等冲突、未完成/未过期在途重生成、旧代次或不可用模板分别使用409 `IDEMPOTENCY_CONFLICT`/`EXPORT_NOT_RETRYABLE`/`EXPORT_GENERATION_CHANGED`/`EXPORT_TEMPLATE_UNAVAILABLE`。未生成或过期下载为409 `EXPORT_NOT_READY`；快照或文件缺失/校验失败为410 `EXPORT_SNAPSHOT_UNAVAILABLE`/`EXPORT_FILE_UNAVAILABLE`。最多5项在途超限为429 `EXPORT_QUEUE_FULL`，生成器未配置为503 `EXPORT_UNAVAILABLE`；共享恢复屏障为503 `SERVER_RECOVERY_REQUIRED`，代次头不匹配为409 `SERVER_RECOVERY_EPOCH_MISMATCH`。列表无匹配任务返回200空列表，不把不存在订单的空列表写成404。

契约版本0.26.0，共106条路径（2026-09-20）。新增TEMP独立只读待确认列表、原批次审阅及原扫描明细分页；要求temporary_order:confirm与仓库授权。当前资格/权限/恢复/库存占用复核仅生成待核对信息，不写审批、扣账、回执或放行。超时提醒按sourceType及两类独立权限分流。Flyway仍V33，Android/code20/Room17不变；M2b写确认、关联及实物处置未开放。见[审阅契约](temporary-review-paths.yaml)及[交付记录](../docs/R06_TEMPORARY_REVIEW_GUIDE.md)。

契约版本0.25.0，共103条路径（2026-09-20）。新增TEMP稳定来源冻结正文接收，复用共享提交查询/分块/取消协议；Flyway V33允许正式来源或TEMP来源二选一，完整证据接收后才登记唯一PDA_TEMP正式记录。Android0.15.0-r06-submission/code20/Room17已接原批次恢复与签名取消；独立确认、受控关联和拒绝实物处置仍待后续，所有临时单均不得放行。详见[TEMP提交契约](temporary-submission-schemas.yaml)及[交付记录](../docs/R06_TEMPORARY_SUBMISSION_GUIDE.md)。以下0.24及更早说明保留为历史记录。

0.20.0新增审计连续前缀归档与跨恢复代次重建，字段、签名凭据和错误边界见[PDA接口](PDA_API.md#r05-审计连续前缀归档与恢复0200)。当前不授权删除本机事实。

契约版本0.24.0，共102条路径，2026-09-20。机器契约见[openapi.yaml](openapi.yaml)。新增TEMP原资格绑定与正式/临时混合审计补传、归档、跨代次恢复和正式召回全设备覆盖，见[接续记录](../docs/R06_TEMPORARY_AUDIT_GUIDE.md)。保留0.23.1的新FH北京时间日期流水及旧OB编号，0.23.0的TEMP_WORK独立48小时资格、固定仓库资料包与激活声明回执。Android已接资料激活、本机临时作业及审计；临时冻结/上传/独立确认完整闭环、PDA换机、已出库后更正、灾后账务重建和文件生成仍待开发。

## 临时准备服务端（0.23.0）

新增`/pda/temporary-preparations`下基线核对、签发、原回执查询、固定gzip下载、激活声明确认5条路径，见[路径](temporary-preparation-paths.yaml)、[字段](temporary-preparation-schemas.yaml)和[PDA协议](PDA_API.md#r06-b-临时仓库准备0230)。资格不依赖正式订单，固定人员/设备/仓库/权限版本/恢复代次及全部资料摘要；不能用于确认出库或覆盖旧临时作业。三份新增表为V30，旧表不变；Android/Room未改，服务端通过不代表PDA激活验收，详见[验证记录](../docs/R06_TEMPORARY_PREPARATION_GUIDE.md)。

## 临时订单准入（0.22.0）

`POST /devices/{id}/temporary-order?warehouseId=…`要求`device:manage`、`temporary_order:manage`及仓库范围，CSRF/幂等键/恢复代次齐全，正文为`expectedVersion/enabled/reason`。默认关闭；停用设备不能开启。新请求递增设备版本、撤销会话并原子保存回执与审计；保留订单、原离线授权、事实和占用。同键同正文返回原回执，回执的`temporaryOrderEnabled`保留当时值。设备列表、PDA设备查询及登记/启停回执均增加该布尔字段。

人员管理在`user:manage`基础上，以`temporary_order:manage`配置三个独立执行角色：create/confirm/link；管理员不自动获得执行权限，仅持执行权限不能转授。查询/详情/可选角色/写操作使用同一可管理范围，其他权限及仓库边界保持。该批数据库V29，APK/Room未变；当时未签发TEMP资格、开放临时建单或部署，验证和回退限制见[R06-A记录](../docs/R06_TEMPORARY_ORDER_ACCESS_GUIDE.md)。

## 当前超时待审（0.21.0）

`GET /review-reminders?warehouseId=…&limit=20&cursor=…`要求当前仓库授权及`submission:review`。仅返回已经生成提醒且仍为订单当前待审批次的记录；已审批/退回/取消/替代的历史提醒保留，不再列作待办。按提醒时间和ID倒序，使用仓库绑定的真实游标，页大小1至200；无效或跨仓库游标400，无审批权限403，无仓库权限404。字段与响应见[契约](review-reminder-paths.yaml)。

Web“超时待审”入口链接至既有装车记录审批页；处理后返回重新查询，历史提醒在订单时间线显示中文动作、系统操作人、批次及到期时间。首次/翻页失败可重试，仓库切换清空旧结果及游标。无新迁移，数据库仍V28，APK/Room/签名格式不变；新Web需要配套0.21后台。仅本机验证，未部署公网。

## 人员与授权管理（0.15.0）

新增6个路径，见[路径](user-paths.yaml)、[字段](user-schemas.yaml)和[设计边界](../docs/R05_USER_MANAGEMENT_DESIGN.md)。均要求当前仓库及`user:manage`；写接口另需CSRF、原请求键和恢复代次。

| 方法/路径 | 行为 |
| --- | --- |
| GET/POST `/users` | 按完整账号、状态及20/50/100页码查询，或新增人员；COUNT与本页在同一只读事务读取 |
| GET `/users/options` | 当前管理员可授的既有角色、权限说明及有效仓库 |
| GET/POST `/users/{id}` | 最新资料，或按`expectedVersion`修订姓名、角色、仓库和启停 |
| POST `/users/{id}/password` | 重置密码；允许本人，不返回密码及验证值 |
| GET `/users/{id}/history` | 每页20条真实游标管理记录，包含操作、人员、时间与原因 |
| GET `/users/requests/{key}` | 当前管理员及原仓库的不可变回执 |

管理范围覆盖目标的全部仓库及角色权限；范围外人员在列表隐藏，详情/变更返回404。拟授角色权限不得超过本人，至少保留当前仓库和一个角色。不能修改本人资料/授权/启停，不改登录账号，不删除人员，不开放自定义角色权限编辑。

写操作使用管理单行锁，取得锁后重新验证执行者，避免管理员并发互相停用。变更、`auth_version`递增、PDA在线会话撤销、回执、幂等记录和审计同事务提交；任何一步失败整体回滚。旧Web/PDA会话须重新登录，但原设备身份、离线资格、订单归属、扫码事实和占用不重写或释放。

新增/重置密码至少8字符（不强制大小写或特殊符号）、最多72个UTF-8字节，使用既有BCrypt。回执中只保留受保护的BCrypt验证值用于同键校验，不使用明文或快速密码摘要；API与审计不返回密码资料。Web仅在当前标签页持久保存原请求元数据，密码须重新输入。未知结果先查询回执，未查到再按原键补发；明确拒绝须关闭窗口后重取最新资料，不能把未知结果当作失败重建请求。

新增V18迁移，新Web须配套本版后台；旧接口、PDA资格和签名格式保持。迁移赋予既有`WAREHOUSE_ADMIN`角色`user:manage`，不自动修改既有密码。本轮仅本机隔离验证，公网仍为原版本，R05其余续期、同步、任务和清理尚未完成。

## PC异常处置与交接（0.14.0）

新增5个路径，见[路径](pc-exception-paths.yaml)、[字段](pc-exception-schemas.yaml)及[交付记录](../docs/PC_EXCEPTION_GUIDE.md)。以下均位于 `/orders/{id}/pc/exception`，要求当前仓库；写接口要求CSRF、原幂等键及恢复代次。

| 方法/后缀 | 权限和事实 |
| --- | --- |
| GET（无后缀） | `order:read`；当前作业、所选停止记录、方案、审核及历史；`caseId`仅限本单 |
| POST `/stop` | `order:recall`；固定停止快照、收回旧窗口写入资格、终止待审提交；原占用保持 |
| POST `/plan` | `order:resume`；完整原有效包集合、周期、逐包去向、原因及指定本仓库接续账号 |
| POST `/review` | `order:resume`；批准减少行另需`order:dispose`，结束另需`order:recall`；校验全部原包及保留库存；批准不释放 |
| POST `/apply` | 指定人员的`pc:operate`与`order:read`；重验审批人、接续人、版本、摘要、周期及占用，同事务交接 |

方案动作是`RETAIN/RELEASE/QUARANTINE`，无默认选择；减少行须实物位置、结果及凭据。`CONTINUE`开启新轮次并要求新窗口显式接续；`CANCEL`不得保留包，结束但不出库、不扣账。批准未交接前可用`REJECT`撤回，历史方案和审核不覆盖。隔离占用不能被PC/PDA或库存导入覆盖，不提供解除隔离。

停止快照和方案分别用`snapshotSha256/payloadSha256`绑定；漏包、重复、串单、变更周期/摘要、无权限和旧恢复代次拒绝。主要冲突码为`PC_DISPOSITION_SET_MISMATCH`、`PC_STOP_EVIDENCE_CHANGED`、`PC_PLAN_SUPERSEDED`、`PC_PLAN_HASH_MISMATCH`、`PC_HANDOVER_PERMISSION_CHANGED`、`PC_DISPOSITION_CYCLE_CHANGED`、`PC_COMMITMENT_CONFLICT`、`PC_RETAINED_STOCK_CONFLICT`。冲突时保留占用并核对最新资料；未知结果继续使用既有`GET /pc/requests/{key}`和同键原正文恢复，不能另建请求绕过。

Flyway V17新增独立停止、方案、审核、处置和保留交接证据。原录入字段与冻结正文保持；录入行只新增处置关联，派生`RELEASED/QUARANTINED`并退出有效汇总。曾冻结或交接保留的包不能普通撤销。新冻结正文含累计处置；原无处置的旧冻结正文仍可核验。新Web须配套V17后台；Android0.7.0/Room6不变。

## PC手工出库（0.13.0）

新增10个路径，见[PC路径](pc-paths.yaml)、[PC字段](pc-schemas.yaml)及[操作与交付记录](../docs/PC_MANUAL_OUTBOUND_GUIDE.md)。Web会话、CSRF和当前仓库授权保持；新增`pc:operate`只授予默认WAREHOUSE_ADMIN。查看作业/记录/本账号原回执用`order:read`；草稿发布并开始另需`order:publish`，审批用`submission:review`，受控恢复用`order:resume`。

- `POST /orders/{id}/pc/start`原子发布并持有订单，READY可直接开始；与PDA共用订单锁，任何一方已领取均不能被另一方抢占。PC证据独立存储，不生成PDA设备、离线凭据或伪造签名。
- 候选`query`去首尾空白/转大写，保留前导零；11位精确，4～10位只在前三位代码之后连续包含，尾匹配优先。当前固定计划代码均可发现，每组5条真实分页，总数来自数据库；指定code翻该组，全部代码只能page=1。无效字符拒绝，120次/分钟/用户的本机内存限速，多实例须网关补充。
- `entries`只接完整条码，由后台取得重量并重查库存/周期/占用/计划上限；显式`physicalConfirmed=true`才计入。记录、序号、PC_RESERVED占用、回执及审计在同一事务；自动扣账关闭仍全局防重。
- 所有写入以`Idempotency-Key`、原正文和`X-Server-Recovery-Epoch`绑定；计包/撤销/结单同时核对`writerId/fence/expectedVersion`。切换窗口仅允许原人员显式执行，不因关页/超时解除订单或包占用。原请求成功时同键同正文返回原回执，不因后来的窗口版本变化改写结果。
- 浏览器发送前把原请求存入localStorage，按账号/仓库/订单隔离；密码、Cookie和设备密钥不进入这份资料。结果未知时暂停后续写入，刷新后先GET `/requests/{key}`；即使PC操作权限已撤销，只要保留原账号的查看权限仍可查已成功回执。未找到再按原键原正文重发。401/403、网络/5xx、格式或关联错误、回执查询失败均保留原资料。
- 撤销要实物核对与原因，保留原行并只释放当前PC占用；曾进入冻结提交的有效包不能以普通撤销办理卸货。结单固定原序号/重量/人员及摘要；0包拒绝、超量拒绝、短装需原因并待审核。满计划且复核通过原子生成dispatch_record、结果及可选账务。
- 审批绑定当前摘要，批准仍复核原人员授权及每包当前库存；冲突不能强批。退回保留全部占用，主管凭实物记录只可恢复原人员、全部原有效包及新轮次，旧提交保持不可变。PC换人/卸货/隔离/灾后重建不由此接口实现。

Flyway新增V15～V16，新Web必须配套新后端；原V1～V14校验和、Android0.7.0/versionCode7与Room6保持。成功PC回执为Web后台事实，不适用PDA验签回执协议；PDA签名验证链保持独立。

## 订单页码分页（0.12.0）

`GET /orders`仍要求`warehouseId`和`order:read`。传`page=1`启用页码模式，`pageSize`仅允许20/50/100，默认20；返回`{items,total,page,pageSize}`。页码范围1～1000000，超出实际末页返回末页，空结果page为1。COUNT和items共用仓库、status及完整订单号/车号过滤，并在同一只读事务内读取；排序为created_at DESC,id DESC。

页码模式不允许同时传非空cursor。省略page时继续使用原`limit/cursor`和`{items,nextCursor,hasMore}`响应，不改变PDA接口。新Web需要本轮配套后台；无数据迁移，APK仍为0.7.0、Flyway V14、Room6。

## Web会话

1. `GET /api/v1/auth/csrf`，同源浏览器保留会话Cookie，读取`data.headerName/token`。
2. `POST /api/v1/auth/web/login`，表单正文`username=...&password=...`，携带CSRF头。
3. `GET /api/v1/me`取得实时权限、仓库及响应元数据；登录后重新获取CSRF用于下一次写入。
4. `POST /api/v1/auth/web/logout`注销。Web不把密码、Bearer令牌或Cookie正文写入localStorage。

Cookie默认HttpOnly、Secure、SameSite=Strict；本机HTTP开发脚本显式关闭Secure。会话持久化在项目自己的MySQL表中，8小时闲置过期；每次业务请求重新核对账号状态、auth_version、权限和仓库范围。停用或增加auth_version可撤销已有授权。登录以来源地址进行有界内存限速；上线通过可信Nginx补充真实客户端IP限速，不能直接信任任意客户端的X-Forwarded-For。

## 上传示例

```http
POST /api/v1/inventory/imports
Content-Type: multipart/form-data; boundary=...
Cookie: OUTBOUND_SESSION=<浏览器会话>
X-CSRF-TOKEN: <当前CSRF>
Idempotency-Key: 01994400-0000-7000-8000-000000000001
X-Server-Recovery-Epoch: 1

warehouseId=<有权限的仓库UUID>
file=<sku.txt原始字节>
```

成功保存样本的`data`包含：

```json
{
  "status": "READY_FOR_REVIEW",
  "parserVersion": "sku-gb18030-fixed57-v1",
  "totalRows": 12639,
  "validRows": 12639,
  "errorRows": 0,
  "duplicateRows": 0,
  "ignoredRows": 0,
  "productCount": 145,
  "totalWeightGrams": "3801362000"
}
```

这是汇总字段节选；完整响应另有导入ID、仓库ID、原文件名、SHA-256和UTC时间。HTTP 200只表示预检结果已保存，错误文件同样返回200及`BLOCKED`，从不表示已发布库存。有效数量和重量排除阻断行与重复行，不代表库存可分配数量。

## 固定解析及持久化

- 按原始字节1–20/21–34/35–47/48–57切分型号、品名、条码、重量，GB18030严格解码。保留型号内部空格和条码前导零。
- 支持CRLF/LF、文件首UTF-8 BOM标记和空行；BOM仅作为可忽略的标记，字段仍按固定GB18030配置解码，不猜测其他格式。
- 正重量不设大小范围，最多3位小数无损转整数克；原重量字符串保留。更高精度、科学计数、负数、零、非法文本均明确阻断，不舍入。
- 同码不同字段、代码对应多个型号或品名：全部相关行标错。相同重复保留行号，进入待确认去重状态。
- 2MiB、40000行是当前上传资源保护限制，不是出库计划上限。异常超长行的页面预览上限1024原字节；原始文件完整留存。
- 原文件用服务端SHA-256命名存于私有目录，先force文件及Linux目录再提交数据库。导入头、逐行结果、幂等键和审计在一个MySQL事务中提交；失败不留下半份可查询的预检。原文件可能先行留存，不自动删除。
- 同仓库、同原文件、同解析配置返回原导入记录。幂等键绑定操作者、路径范围及仓库/文件/配置摘要；同键不同内容409。并发相同文件只创建一条记录。
- 事务内锁定服务器代次/模式，旧代次或恢复模式拒绝业务写入。这里只实现恢复写入屏障，不宣称完整F06灾后对账流程已实现。

## 错误与重试

| HTTP/代码 | 含义 | 客户端处理 |
| --- | --- | --- |
| 400 INVALID_REQUEST / INVALID_CURSOR / INVALID_PAGE_SIZE | 请求或分页错误 | 修正输入 |
| 400 EMPTY_FILE / ROW_LIMIT_EXCEEDED | 空文件或异常行数 | 核对原文件 |
| 401 INVALID_CREDENTIALS / UNAUTHENTICATED / SESSION_REVOKED | 登录失败或会话无效 | 重新登录；不清除PDA数据 |
| 403 ACCESS_DENIED / PERMISSION_DENIED | CSRF无效或无权限 | 刷新凭据或联系管理员 |
| 404 WAREHOUSE_NOT_FOUND / IMPORT_NOT_FOUND | 不存在或越仓访问 | 不泄露其他仓库资源 |
| 409 IDEMPOTENCY_CONTENT_MISMATCH | 同键不同内容 | 不自动重试；新文件使用新键 |
| 409 SERVER_RECOVERY_EPOCH_MISMATCH | 恢复代次不同 | 刷新并核对状态 |
| 409 WRITE_CONFLICT | 唯一约束并发冲突 | 使用原键核对原结果 |
| 413 FILE_TOO_LARGE | 超过上传限制 | 核对文件 |
| 429 LOGIN_RATE_LIMIT | 登录尝试过多 | 等待Retry-After |
| 503 SERVER_RECOVERY_REQUIRED | 恢复对账中 | 等待恢复完成 |
| 500 INTERNAL_ERROR / 网络无响应 | 结果可能未知 | 保留同一文件及幂等键重试，或查询导入历史 |

前端不自动重发写请求；一次文件选择保留同一幂等键，直到换文件/仓库。预检与发布为独立操作；不会上传即发布。订单和政策接口见下文；PDA认证、领取及下载激活见[PDA_API.md](PDA_API.md)，同步、提交、出库账务及文件生成将按V2.3继续实现。

## 库存发布与映射接口

所有接口位于`/api/v1`，要求实时仓库授权。读取需要`inventory:read`，发布需要`inventory:publish`，新增/修订映射需要`mapping:maintain`。发布含候选映射变化时同时检查映射权限。

| 方法/路径 | 内容/分页 |
| --- | --- |
| GET `/inventory/imports/{id}/diff` | 差异数量、候选映射数量、当前库存/映射基线；不把全部明细塞入汇总响应 |
| GET `/inventory/imports/{id}/diff/changes` | 按条码升序；`after`、`limit`；需带`expectedSnapshot`及`expectedMappingRevision`，无快照传`NONE` |
| GET `/inventory/imports/{id}/diff/mappings` | 按代码升序；同上，原映射及候选映射均返回；停用状态保留 |
| POST `/inventory/imports/{id}/publish` | 显式确认后原子发布，返回不可变发布回执 |
| GET `/delivery-mappings` | 当前映射含停用项；`afterCode`、`limit` |
| GET `/delivery-mappings/{code}/history` | 历史版本；`beforeVersion`、`limit`，版本倒序 |
| POST `/delivery-mappings/revisions` | 新增或修订，`expectedVersion`首次为`"0"`，必须填原因 |
| GET `/inventory/current` | 当前库存指针与映射修订号；两者都使用字符串 |
| GET `/inventory/snapshots` | 历史发布回执；`beforeVersion`、`limit`，以current指针判断当前版本 |
| GET `/inventory/bales` | 完整条码/发货代码精确筛选；`presence=all/present/missing`、`afterBarcode`、`limit` |
| GET `/inventory/discrepancies` | 未处理差异，`beforeId`、`limit`，按UUIDv7倒序；处理入口随账务更正模块实现 |

以上均需`warehouseId`查询参数。分页上限200条；页尾`nextCursor`放入对应游标参数。发布/修订另外需要CSRF、Idempotency-Key与恢复代次头。

发布请求正文示例（版本与数量必须来自刚刚核对的差异）：

```json
{
  "expectedActiveSnapshot": null,
  "expectedMappingRevision": "0",
  "acceptIdenticalDuplicates": false,
  "acceptMappingChanges": true,
  "confirmedRemovedCount": 0,
  "reason": "核对上游最新库存"
}
```

回执data示例：

```json
{
  "id": "01994400-0000-7000-8000-000000000021",
  "version": "1",
  "importId": "01994400-0000-7000-8000-000000000020",
  "rowCount": 12639,
  "totalWeightGrams": "3801362000",
  "mappingRevision": "1",
  "manifestSha256": "<64位SHA-256>",
  "publishedAt": "2026-09-16T10:00:00Z"
}
```

`expectedActiveSnapshot`必须存在，无旧版本时显式传null。每个有缺包的文件都需确认确切缺包数；当前不引入未经确认的20%自动阻断阈值。任何解析错误都阻断整个文件。发布前复核原文件SHA，事务内完成映射版本、包标识、快照、差异、上游状态、指针、审计与回执；失败完整回滚。

服务端使用仓库行锁串行发布/修订，写事务READ COMMITTED，确保等待锁后读取新提交的基线。独立预览为只读一致性事务。HTTP分页过程中若版本变化返回409，不拼接来自不同基线的差异。

发布只更新上游声明和主数据；不覆盖扣账状态、冻结原因、出库周期。缺包仍能通过最后一次出现的快照查询原属性；已扣账包再次出现新增账实差异，不自动恢复。条码全局唯一，跨仓文件冲突必须另走明确归属流程，导入不能偷换仓库。

当前库存页展示上游声明、扣账、冻结和周期；首轮提交已保存系统出库事实和条码占用，但库存列表数量未逐一扣除全部占用，因此不称为“可分配”。旧快照、原文件及映射版本保留，后续订单可固定引用。

修订映射正文：

```json
{
  "deliveryCode": "003",
  "expectedVersion": "1",
  "model": "H815B 1.7 RE 38 300",
  "name": "有光短纤",
  "enabled": false,
  "reason": "核实后暂停新订单使用"
}
```

## 发布幂等、摘要与错误

- 同一操作者、路由范围、幂等键绑定仓库和类型化请求正文SHA。同键同内容返回原回执；同键不同内容409。网络失败保留原键与正文重试，修改输入才换键。
- 同一导入记录只能生成一个库存版本。即使换新键重复发布旧导入，也只返回原回执，不重新启用旧快照；回执不声称自己仍是当前版本。
- 手工映射修订同样幂等；已成功的旧键可重取原映射ID，不覆盖之后的映射版本。
- 库存发布回执的`manifestSha256`是**主数据内容摘要**：按ASCII条码升序排列`ParsedBale`数组，以UTF-8紧凑JSON编码后SHA-256；对象字段固定顺序为`model,name,barcode,deliveryCode,rawWeight,weightGrams`，均为字符串。它对应PDA离线凭据的`inventorySha256`；准备包清单另有自己的`manifestSha256`。PDA提交使用独立的固定正文规范SHA-256，详见提交协议；不复用主数据摘要。
- 原始重量格式`300.50`与`300.5`的数值比较相同；原文件和内容摘要仍能保留文本差别。

| HTTP/错误码 | 处理 |
| --- | --- |
| 409 PUBLICATION_BASELINE_CHANGED | 重新获取差异并由操作者核对；不能自动改基线重发 |
| 409 MAPPING_VERSION_CONFLICT | 刷新映射历史，核对后提交新修订 |
| 409 IMPORT_BLOCKED | 修复上游文件后重新上传，禁止仅发布有效子集 |
| 409 DEDUP_CONFIRMATION_REQUIRED / MAPPING_CONFIRMATION_REQUIRED / REMOVAL_CONFIRMATION_REQUIRED | 按显示的具体差异补齐确认 |
| 409 BARCODE_WAREHOUSE_CONFLICT | 核实条码归属，不通过导入转仓 |
| 409 IMPORT_FILE_UNAVAILABLE | 原文件缺失或校验异常，先核实存储，不强行发布 |
| 404 SNAPSHOT_NOT_FOUND | 版本不存在或不属于授权仓库 |

通用认证、幂等、恢复模式及未知结果的处理仍沿用前表。Web目前保留当前页面的请求键；页面关闭后先查看历史确认结果。服务端的每导入唯一发布约束仍防止再次生成同一版本。

## 订单接口

以下路径均在`/api/v1`下，都需要`warehouseId`查询参数及实时仓库授权。读接口需要`order:read`，创建/修订需要`order:write`，作废需要`order:cancel`，发布及READY订单修订另需`order:publish`。写接口均携带当前CSRF、`Idempotency-Key`与`X-Server-Recovery-Epoch`。仓库管理员和`ORDER_CLERK`具备四项订单权限，`ORDER_VIEWER`只读；用户角色管理界面尚待实现。

| 方法/路径 | 内容与分页 |
| --- | --- |
| GET `/orders` | `status`可选；`search`按完整订单号或车号精确查找；创建时间及ID倒序，`cursor`、`limit` |
| POST `/orders` | 新建WEB来源的DRAFT草稿；服务端分配唯一订单号（0.23.1起为FH＋北京时间日期YYYYMMDD＋全系统当日流水，至少4位，超出自动扩展），车号不要求唯一 |
| GET `/orders/products` | 按代码/型号/名称搜索启用映射，`search`、`afterCode`、`limit`；返回库存/映射基线及候选数量 |
| GET `/orders/{id}` | 当前订单头及计划明细；可指定`revision`读取旧计划，订单头仍表示当前状态 |
| GET `/orders/{id}/revisions` | 不可变计划历史；版本倒序，`beforeRevision`、`limit` |
| POST `/orders/{id}/revisions` | DRAFT修订或READY修订并重新发布；当前`expectedVersion`和原因必填；READY另需`expectedPolicyVersion` |
| POST `/orders/{id}/cancel` | DRAFT/READY可作废；保留所有计划和审计，不删除订单；已领取拒绝 |
| GET `/orders/{id}/preflight` | 只读下发前检查，核对状态、库存版本、映射版本及产品；不发布、不预留 |
| GET `/orders/{id}/events` | 创建/修订/作废审计；事件ID倒序，`beforeId`、`limit` |

页大小1～200，默认50；`nextCursor`放入对应游标参数。产品页分页位于`data.products`，其余分页位于`data`。事件列表只用于浏览历史，不能充当PDA同步确认游标。详尽字段见[order-schemas.yaml](order-schemas.yaml)。

创建请求正文示例：

```json
{
  "vehicleNo": "苏A12345",
  "containerNo": "",
  "notes": "每个产品单独核对装车计划",
  "expectedActiveSnapshot": "01994400-0000-7000-8000-000000000021",
  "expectedMappingRevision": "1",
  "lines": [
    { "deliveryCode": "003", "plannedCount": 180 },
    { "deliveryCode": "571", "plannedCount": 181 }
  ]
}
```

基线必须来自当前`/orders/products`或`/inventory/current`；上面UUID仅示意。车号必填；柜号、备注字段必须存在，不填写时传空字符串。每产品计划为正整数，同代码不得重复；产品代码始终为字符串，保留前导零。合计361包，订单合计没有116包限制。最多1000产品行、每行INT上限属于技术资源边界。

成功回执`data`示例：

```json
{
  "id": "01994400-0000-7000-8000-000000000031",
  "orderId": "01994400-0000-7000-8000-000000000030",
  "revision": 1,
  "rowVersion": "1",
  "status": "DRAFT"
}
```

修订请求为`{"expectedVersion":"1","reason":"现场增加10包","plan":<完整Plan对象>}`，`plan`结构与创建相同，重新固定当前库存及映射；不接受只传增量产品行。READY订单另传`expectedPolicyVersion`，同一事务重新发布并保持READY；DRAFT仍为草稿。成功后revision及rowVersion递增，旧计划明细不变。

作废请求：

```json
{ "expectedVersion": "2", "reason": "车辆行程取消" }
```

作废只递增rowVersion，revision仍指向原计划；响应状态为CANCELLED。已作废订单不能再编辑。READY作废会写同步撤销事件并标记待生成计划单任务已替代；已领取状态一律拒绝，不能调用本接口代替召回。

### 版本、幂等及未知结果

- 计划明细保留型号/名称快照、映射ID、产品ID、库存快照及映射修订号。以后导入或更改映射不会静默更新旧订单。
- 写事务依次核对权限和服务器恢复代次、锁定仓库，再锁定目标订单；计划、订单指针、审计、幂等记录与操作回执在同一MySQL事务提交。写事务使用READ COMMITTED。
- 幂等键作用域为当前操作者及动作（create/revise/cancel/publish），摘要含仓库、目标订单及类型化正文。同键同正文返回**原操作回执**；同键改正文/仓库/目标409。即使后续已修订或作废，旧键也不返回伪造的最新状态，读取详情获得当前状态。
- `expectedVersion`是订单`rowVersion`，不是计划`revision`。并发修改同一版本仅一份成功；另一份刷新核对后再提交，不自动合并。
- Web遇网络异常或5xx，在当前组件内存保留原正文及幂等键并锁定输入，允许手动重试原请求。尚无跨刷新持久化恢复；不要把刷新后重新新建当成重试，应先查订单及历史核对。相同车号允许有多份订单，服务端不会按车号猜测并去重。

### 下发前检查边界

`checksPassed`仅表示当前状态、库存/映射版本及产品映射检查通过。候选数量不足放在各产品`shortage`字段提示，草稿可保存。`candidateCount`只计算当前上游在库、未扣账、未冻结且产品匹配的包；没有扣除其他订单占用，不表示实际预留。库存快照数另列`snapshotCount`。

当前`dispatchAvailable`固定为false，此草稿检查不授予设备作业资格，PDA下载激活仍待开发。发布和设备领取分别走显式接口，检查通过或订单READY均不能代替设备作业授权。无出库实绩时页面显示“云端未收到”，不制造0包实绩。

| HTTP/错误码 | 含义与处理 |
| --- | --- |
| 400 INVALID_REQUEST / ORDER_INVALID_PLAN / ORDER_DUPLICATE_PRODUCT | 参数无效、计划非正整数或产品重复；修正输入。JSON小数不会截断为整数 |
| 404 ORDER_NOT_FOUND / ORDER_REVISION_NOT_FOUND | 订单不属于授权仓库或版本不存在 |
| 409 ORDER_INVENTORY_REQUIRED | 仓库无已发布库存，先完成库存发布 |
| 409 ORDER_BASELINE_CHANGED | 库存或映射基线变化；重新加载并核对计划，不能自动替换基线重发 |
| 409 ORDER_PRODUCT_UNAVAILABLE | 产品映射停用或不存在；核对产品代码及映射 |
| 409 ORDER_VERSION_CONFLICT | rowVersion已变化；查看新状态/历史后决定操作 |
| 409 ORDER_STATE_CONFLICT | 操作不适用当前状态；首次发布只允许DRAFT，修订/作废只允许DRAFT或READY |

权限、同键异正文、恢复屏障和服务端错误沿用通用错误表。业务拒绝不无限重试；结果未知保留原请求核对。

## 出库政策与订单发布

| 方法/路径 | 权限与内容 |
| --- | --- |
| GET `/policies/current` | `order:read`；`data.policy`为当前政策，未配置时为null |
| GET `/policies` | `order:read`；版本倒序，`beforeVersion`、`limit`分页 |
| POST `/policies` | `policy:manage`；创建不可变新政策，同键同正文返回原政策 |
| POST `/orders/{id}/publish` | `order:publish`；DRAFT→READY，固定政策并创建计划单任务 |
| GET `/orders/{id}/publications` | `order:read`；发布版本倒序，`beforeRevision`、`limit`分页，显示固定政策及任务状态 |

以上同样要求仓库参数，写接口要求CSRF、幂等键及恢复代次。`policy:manage`仅默认授予仓库管理员，文员不能因此修改扣账政策。

首次政策配置正文：

```json
{ "expectedVersion": "0", "autoDeductInventory": false, "reason": "由管理员核实后明确选择扣账方式" }
```

false只是示例，**系统不会代替业务选择开或关**。未配置时禁止发布。后续设置传当前版本；响应返回policy ID、version、操作者、时间、原因和全部已固定字段。当前政策schemaVersion=1，离线48小时、主管短装批准、现场分堆、B模式为已确认规则，不提供未经批准的新选项。变更政策仅影响后续发布/重新发布；不会补扣旧单。

发布正文：

```json
{ "expectedVersion": "1", "expectedPolicyVersion": "1", "reason": "产品及各自计划包数已核对" }
```

响应`data`仍为OrderReceipt，例如`{"id":"<回执UUID>","orderId":"<订单UUID>","revision":1,"rowVersion":"2","status":"READY"}`。首次发布不改计划revision，只递增订单rowVersion。READY修订传原rowVersion、完整plan及当前expectedPolicyVersion，生成新计划、新发布记录、新计划单任务和新同步事件，继续为READY。

发布时锁顺序沿用服务器代次→仓库→订单；重新校验库存/映射及政策基线。同一事务完成订单状态、`order_publication`、`order_plan_document_task`、`order_sync_outbox`、操作回执及审计。任何一步失败整体回滚。数量缺口提示保留，不按116包或候选数量硬拦截计划；发布不预留具体条码。

`order_publication`以(order_id,revision)唯一，作为不可变发布版本关联原计划，固定policy_id、auto_deduct_inventory、恢复代次及完整计划/政策快照。旧计划无需原位追加/改写。作废和重新发布保留旧发布记录，仍为PENDING的旧计划单任务转为SUPERSEDED；文件历史以后接入导出模块，不通过删除发布记录实现替代。

`order_sync_outbox`持久化订单状态事件。V20增加流序号，V21增加已发布状态投影及固定分页基线；序号只在业务提交后由按恢复代次/仓库串行的发布事务分配。`GET /pda/sync/orders`从该投影与对应高水位生成固定基线，未完页不返回增量游标，完整应用后再拉有序事件。游标绑定恢复代次、仓库和ORDER流，PDA Room9事务内保存状态与进度；恢复保护独立持久保存，重新登录或重建基线不自动解除。字段和错误见[PDA协议](PDA_API.md#pda-订单增量同步)。READY仍只是服务端待领取状态。

计划单任务当前能持久保存和查询PENDING/SUPERSEDED，PDF Worker及下载尚未实现；不会显示“已生成”或提供空文件。任务的publication_id唯一，固定模板版本loading-plan-v1，生成数据从不可变发布快照读取。

| HTTP/错误码 | 处理 |
| --- | --- |
| 409 POLICY_REQUIRED | 管理员先明确设置自动扣库存，之后刷新核对 |
| 409 POLICY_VERSION_CONFLICT | 政策已有新版本；重新查看，不自动采用新值 |
| 409 ORDER_PUBLICATION_BLOCKED | 库存/映射基线或产品映射已变化；先修订计划 |
| 400 INVALID_REQUEST | 发布缺政策版本、READY修订漏政策版本、配置漏明确布尔值或原因 |

发布和政策页面在当前组件中保留未知结果的原键/原正文。重试返回原结果，即使全局政策后来变化也不改原单。跨刷新持久化重试仍未实现，关闭页面前先核对结果。

## 装车提交与短装审批

订单详情新增当前批次、有效/撤销扫码明细、包数和整数克换算重量、短装原因及云端结果。`GET /orders/{order}/submission?warehouseId=...`要求当前仓库`order:read`；`POST /submissions/{id}/review`要求`submission:review`、CSRF及`X-Server-Recovery-Epoch`，默认仅仓库管理员有审批权限。

审批绑定`payloadSha256`、`decision=APPROVE|REJECT`和`reason`，批次及原决定提供幂等，不额外使用幂等头。页面二次确认后提交；网络/服务端不确定响应保留原审批正文，禁止改原因后重试。成功刷新实绩；拒绝/取消仍保留占用。跨页面刷新尚未持久保存待确认审批正文，应先查询当前结果。

具体冻结、分块、终态签名和重试协议见[PDA提交协议](PDA_API.md#正式订单首轮冻结与提交)。当前支持正式订单及原人原机保留/凭据减包续作，主管批准不能覆盖原库存/周期/授权冲突。换机、更正、PDF/Excel与灾后对账仍未完成；订单和条码状态流、通用审计补传单独见上节。


## 原机全量保留续作（0.5.0基线，减包扩展见下节）

订单详情在原机停止并具备 REJECTED/ABORTED 旧批次后显示续作申请。具备 `order:resume` 的主管先申请，再逐包核验并填写实物凭据批准；申请受理不授予作业资格。对响应未知的申请和审批保留原键/正文；须先查明结果，不能换键盲重试。关闭/刷新页面后的重试正文仍未持久保存，应通过召回状态查询原票据。

批准显示新轮次、起始扫码号及固定授权截止。原机完成联网验签和本地原子激活才能恢复扫码，服务器仍保留全部已装包占用。0.5.0基线仅原人原机、计划不变、全量保留；旧成功批次只能更正。参考[当前OpenAPI](openapi.yaml)、[字段](continuation-schemas.yaml)。


## Web设备管理接入

Web `/devices` 使用既有设备接口，不新增路径或迁移，该轮契约为0.9.0（当前版本以页首为准）。`GET /devices`的`nextCursor`用于下一页`beforeId`；登记提交`deviceCode/name/publicKey`，启停提交`expectedVersion/status/reason`。响应使用设备ID、状态和版本核验，旧操作回执与当前列表分开显示。

页面要求`device:manage`，库范围由后端再次校验。待处理请求在发送前按人员/仓库保存在当前标签页，刷新后显式恢复；原键、正文和恢复代次保持，CSRF每次重新取得，存储失败不发送。完整操作和恢复边界见[Web设备管理说明](../docs/WEB_DEVICE_GUIDE.md)。

## 实物处置与原机减包续作（0.6.0）

新增GET/POST `/recovery-packages/{id}/disposition-evidence`；POST需order:recall及order:dispose，绑定完整停止恢复包、原行/周期、实际位置和凭据。独立批准后，REOPEN_WORK支持RETAIN、RELEASE、QUARANTINE，减少行必须引用approvedEvidenceId。Web保存实物批准原键、正文及恢复代次，标签页刷新后可恢复；未批准的选择不会作为有效续作申请提交。

Web召回与提交明细显示原行和已批准减少结果。PDA签名激活扣减有效汇总，保留原事实及本机防重；服务器直到完整替换正文接收时才原子释放/隔离/转占用，后续回执只按有效集合。操作见[实物处置指南](../docs/PDA_DISPOSITION_GUIDE.md)，字段见[处置契约](disposition-schemas.yaml)，当前OpenAPI0.10.0共59路径。新migration V13及Room6只扩展原结构。

## R05 条码状态与审计补传

新增 `/pda/sync/barcodes` 和 `/pda/audit/events`，字段与限制见[PDA接口](PDA_API.md#r05-条码状态与通用审计补传0180)。Flyway V22/V23均只新增表；旧业务、权限、占用及回执规则保持。
