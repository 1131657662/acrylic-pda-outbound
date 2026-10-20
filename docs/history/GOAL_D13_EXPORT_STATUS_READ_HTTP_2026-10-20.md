# D13 导出中心状态筛选只读 HTTP（2026-10-20）

本报告记录导出中心既有 `status` 筛选在恢复只读/P5 分类器下的有界兼容修复。只将纯函数 `REPLACE` 加入有限 SQL 函数 allowlist，仍受 SOURCE_READ 分类及效果门约束；未知或限定函数仍拒绝。未改查询业务规则、权限、API、迁移、生产配置或下载/生成路径。合成任务行仅用于元数据筛选，不曾运行 producer、renderer 或 worker，不生成业务成功或导出产物。此项不计 D13 整体、L2/L3、O/U 或总目标第8节验收。

## 输入与变更

- 固定树 `D:/acrylic-pda-outbound`，输入 HEAD `c221a8ec175bcc6e6da785dd10cbe7630c114734`，分支 `codex/development`；开发远端仍为该提交，`main` 为 `c6e9e0c238c657baecbf699ba6fdbada5d979b86`。开工时工作区路径及 SHA 清单为 `.local/goal-20261020-resume/d13-export-status-read01/input-manifest.json`，SHA-256 `b7b10dfbddd5ff1603f1eaeb573be30610ffbfa5c7170fece800e9b469812ccb`。它保存35条输入路径；本轮后续自有断点快照见 `resume-review01/input-manifest.json`，SHA `a7aecce2463702315db86fcebae7db6b04cfba4f632ebbe3007c7c4bf8480113`。
- 有界改动路径及本次最终SHA：`RecoverySqlExpression.java` `9d92f7982166e01c72c7bc4d0c2779cbffc5bae740b8f473772ff4c46284c8a6`；`RecoveryEffectSqlTest.java` `2ee4edc408893b4fc946fbb878058a6c4d91851c47f1a9551e14ec21d50de74e`；`RecoverySelectSqlTest.java` `53f648278020f399d024988e62f8e6c0c81cae5fd3c8cdd7bf6d7128c08c8b54`；新增 `E2aExportStatusReadIT.java` `31d15890c0e58d6e4d7a55731fab62f334fbb7161548ccd730f902938bad3f21`；新增 `ExportStatusHttpInput.java` `cc027ae7ec5736bc79ec31142a350ccda186677fd66e9bbcbdf50c31b88af6f4`。
- IT 使用隔离 MySQL `127.0.0.1:19340`、HTTP `127.0.0.1:19444`、schema/user `acrylic_d13_read_20261020_status_03` / `d13_read_20261020_status_03`，通过实际 TCP/P5 会话做只读列表 GET。运行器先做真实 owner 登录，插入33条合成背景任务元数据，覆盖同创建时间的25条FAILED分页和PENDING/RUNNING/RETRY/SUCCEEDED/EXPIRED/三种INVALIDATED。未调用任何导出工作器。

## 结果与失败

- run01 在启动前检查 renderer 目录不存在，未启动数据库或Gradle。输入保留于 `preflight-before/`，随后限定为不依赖renderer的元数据只读夹具；该轮不计业务测试执行。
- 修复前 run02 的首个带状态列表请求在隔离真实 HTTP 返回500；原始 XML、HTTP 正文、完整日志及数据库逻辑快照仍在 `.local/goal-20261020-resume/d13-export-status-read01/run02/`，本根输入 ZIP SHA `9bac2d4599f4fdeeb3579be9b3923880fca77f5b511c1e84b4fd10602ac12828`。栈明确为 `CONTROL_EFFECT_UNCLASSIFIED`，落点为嵌套 `REPLACE`。此失败保留，不覆盖或改写为通过。
- 新 run03 指定测试 `E2aExportStatusReadIT#effectiveStatusFiltersRemainReadOnlyAcrossTiedPaginationAndAccessFailures` 实际执行：1 test、0 failure/error/skipped；Gradle exit 0，认证 MySQL shutdown exit 0。XML SHA `e94d2971b340bdccd8a65ad454c2205784c2283d78a8a4b66f5ec450a98800be`；run03 输入 ZIP SHA `f9e0d073f16446032f6617442159957f56f395c1c37da8b937ad547b11f101ae`，其中2447个源码/资源成员对当前源码逐SHA比较，零差异。
- 独立 Python 消费者实际逐份核对38个HTTP元数据/正文SHA、状态码、返回任务ID及状态/格式、时间并列排序、游标分页无重漏、匿名/跨仓/同仓其他用户/撤权/会话撤销响应；34个导出列表请求均与合成输入期望一致。原始返回正文均保持 `downloadable=false`、无artifact摘要/字节。消费者结果SHA `560824cbc00838400e05a94be225810955a3ee492cd754ca2535116ca1efd33f`。
- 六组权限/查询前后数据库快照逐表比较守恒，每组178表；包含仓授权和用户角色撤权的 `finally` 恢复，4个storage文件的SHA保持。P5 leases index 实际为空、无unknown文件，独立后继进程输出 `STARTED`；完整消费者与差异摘要见 `originals-consumer01.json`、`run03-source-parity.json`。
- 同一主树 WIP 上执行 `./gradlew.bat -p server test --rerun-tasks --no-daemon --offline --max-workers=1 --console=plain --stacktrace`，exit 0；310个JUnit XML共4804项、4785通过、19跳过、0失败/错误。19个skip逐类逐方法及原XML SHA位于 `server-test01/result.json`，跳过计通过0。构建输入ZIP SHA `851340abacc1dcb83af6d0d8ff7f646157d18f2795955c871c7d3c5d50d6660c`。
- 与前一D13源码批次class path比较：3538个运行时条目，4个既有条目SHA变化，5个新增本轮测试类，无旧条目移除；差异详见 `runtime-classpath-delta.json`。本结论仅描述运行输入差异，不把既有切片结果拼成统一候选。

## 清理与限制

- run02/run03 各自目录和身份先核对，端口均无监听、MySQL进程无在途消费者、认证shutdown exit 0，原SQL身份指向各自隔离datadir。删除前逐SHA保存在 `cleanup01/before.json`（SHA `4ff7d6644771c51f814ccbf251479565f31761237426ba11b1b02d28e2a86e90`）；仅清两个可重建 datadir，694文件、444770951逻辑字节，D盘采样可用空间31935684608→32387661824 bytes，观察增量451977216 bytes。删除后回执在 `cleanup01/after-recovered.json`（SHA `7e146624131b5fcaf6c51d75b2bdf1a33449abc183c3493056527f984c56e710`）。原PowerShell回执在删除完成后因字典求和兼容问题非零退出；错误原文保留于 `cleanup01/receipt-error.txt`，没有重跑删除，按预先保存逐文件清单补算。run02/run03全部非datadir日志、原HTTP/XML、P5资料、输入及快照保留。
- `server/build` 类路径仅本地构建输入；debug keystore、设备、原库、现网、正式签名、main、部署、Release未操作；`localDeletionAllowed=false`。
- 用户已批准TEMP原记录筛选的“全部/有效/已撤销/主管保留”设计；该Android实现与消费者未在本批变化。已有四字号隔离证据分别见 `GOAL_U04_STATUS_FILTER_NATIVE_CONSUMER_2026-10-20.md` 与 `GOAL_U04_STATUS_FILTER_REBOOT_FONT_CONSUMER_2026-10-20.md`，不重跑，也不将两个不同条件拼作同输入四字号或U04/D17通过。
