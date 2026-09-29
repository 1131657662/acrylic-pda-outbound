# R09 C2 B1-1 效果门对抗性覆盖复核（2026-09-29）

> 后续另获 B1-2 有限授权，见[跨进程围栏记录](R09_C2_B1_2_FENCE_2026-09-29.md)；本文件仍保留 B1-1 交付时事实。A1/A2/A3 与 600 签名判定不回退，新一轮重跑回归；不是把历史无 Authority 结论扩展到 B1-2。

输入：codex/development，2475a44927f5fed7b1b21db02080a89adb74c17e，D:/acrylic-pda-outbound。这是相对 B1-1 原实现会话的独立复核；本轮测试、必要修正及自审由本复核会话单独完成，未另设子代理或第二个修正验收者。不能把本轮自审称作独立第三方验收。

**结论：三处真实覆盖缺口已在门内修正；最终定向单测 2034 项、真实库 IT 536 项全绿（0 失败/错误/跳过）。默认关闭、未接线、未部署、未开生产开关；K1 仍未通过，B1 整体未完成；停止于 B1-1，不进入 B1-2。** 版本事实只引用[看板事实块](../ORCHESTRATION_STATUS.md)。

## 1. 授权、前置与证据口径

- 仅门内必要修正、recovery 测试及文档；不修改 DataSource 注册、HTTP/Session、P5/租约/锁协议，不操作原库/现网/共享树，不合 main、不发布。
- 开工 preflight.json：HEAD 等于实际远端、0/0、干净、index.lock 不存在、工作区/暂存 diff --check 为 0；证据根和实例先查重创建，schema/账号来自全新 datadir。无 attempt 分配或冲突。
- 单写者核对包括应用聊天状态、进程和监听；DSH 19387 的监听存在不被当作活动写者。接续保留已存在的本任务 run1–run5 与未提交修正，未覆盖失败轮；resume-preflight 保存接续状态、源码哈希、差异与监听。
- 共享树 r06-e2a-migration 为 detached ea4d166ef9db2ea7711c1006cff208f3ff88495c，30 修改＋67 未跟踪＝97。开工逐文件内容哈希、字节数、状态快照和收尾对比见证据；只读，不同步、不清理、不提交。接续首次校验因读取错字段/根路径而误报，纠正为 relative path＋sha256＋bytes 后逐项一致，该诊断说明保留在 resume-preflight/receipt.json。
- REAL 表示真实驱动委托及全新独立连接所见的已提交值；MODEL 表示 Authority、准入文件 fixture、同步调度或故障注入。MODEL 不证明可信 Authority、P5、跨进程围栏或现场身份。
- 证据根：.local/r09-c2-b1-1-adversarial-20260929-a/。每次执行使用新的 run 目录，保留 command.txt、gradle.log、exit.txt、JUnit XML、files.sha256；编译失败没有 XML 时明确登记，不补造。数据目录、失败表和日志全部保留、不入库。

## 2. 原实现的缺口与影响面

| 编号 | 原实现缺口及真实影响 | 门内修正 | 回归及独立效果观察 |
| --- | --- | --- | --- |
| A1 | ResultSet.getObject 的无类型、类型提示、Map、列名重载被 get 前缀放行；CallableStatement 的可变 getter 同样放行。Connector/J 在 emulateLocators=true 时，厂商 Blob 类型提示实际返回 BlobFromLocator，其 setBytes/truncate 直接另发 UPDATE。run5 中 FENCED 前取回、FENCED 后 setBytes：getObject=1、Blob.setBytes=1，观察值从 62 变为 58，3/3。不能因 java.sql.Blob.class 在此驱动上转换失败就判整条路径安全。 | 两个接收器的 getObject/getBlob/getClob/getNClob/getSQLXML/getArray/getRef 均在取得处统一 CONTROL_EFFECT_UNCLASSIFIED。拒绝所有重载，包括看似标量的 getObject；不尝试事后包装、类型猜测或默认放行。 | AdversarialAcquisitionTest 穷举 36 个不透明 getter 重载，各 3 次、原始 mock 零交互；LobIT/CallableIT 在真驱动前零委托、独立观察零新增；厂商类型提示也覆盖。 |
| A2 | 简单 SELECT … FROM 被认为只读，实际 FROM 可为含 MODIFIES SQL DATA 函数的视图。run2 在 FENCED 后 executeQuery 委托 1 次、Authority 查询 0、独立观察新增 1 行，3/3。 | SELECT 有 FROM 的有限语法、DESC/DESCRIBE、有限 EXPLAIN 标为 SOURCE_READ，重新经过效果门；只保留无数据源常量 SELECT 与 SHOW TABLES/DATABASES 的免门读取。未知/复杂语法仍直接拒绝。 | ViewIT 的拒绝/正控分别验证零委托零提交／允许时真写入且门检查 1 次；SqlSurfaceIT 逐语法分支拒绝或只读负控。 |
| A3 | PreparedStatement/CallableStatement.getMetaData 被 get 前缀当成本地 getter。驱动 ClientPreparedStatement.getMetaData 实际构造临时语句并 execute 原 SQL 获取列信息。run8 的两类语句、FENCED 前后共 12 条用例全部复现：getMetaData=1、门检查=0、独立观察新增 1 行；连原本会被门拒绝的 SELECT 函数也可执行。 | 只在 Statement 家族取得 getMetaData 时直接 CONTROL_EFFECT_UNCLASSIFIED；ResultSet.getMetaData 仍返回已经执行所得的列信息。不开更宽的“过门后执行未知 SQL”分支。 | MetadataIT 两类 × 两状态 × 3 次取得处拒绝；关闭态正控 3 次确实执行函数。SurfaceTest 单独给该方法 DENY_METADATA_EXECUTION 结论。 |

产品修正仅 RecoveryEffectCalls.java 与 RecoveryEffectSql.java。沿用原开关缺省 false；没有生产 Authority 实现，没有新增/替换 DataSource Bean，原 admission 开关不变。原 B1-1 记录中 G09 与游标 getter 的过宽说法已就地更正并指向本记录。

## 3. 用户清单逐项结论

以下每一参数组合/独立方法均重复 3 次；四类既有 IT 和既有 60 项的本轮重跑另外计数。类名省略 RecoveryEffect 前缀。

| 清单 | 逐路径测试与唯一结论 | 计数与独立观察依据 |
| --- | --- | --- |
| 1 LOB/类型提示/OUT | ResultSet 索引与列名的无类型 getObject、Blob/Clob/NClob/SQLXML/Array/Ref 提示；厂商 Blob 提示；Callable 的相同提示、注册 OUT 参数及名称路径：均取得处拒绝。Map 等全重载由 AcquisitionTest 零交互兜底。 | LobIT、CallableIT：目标 getter 增量 0，观察表仍为原行；CALL/execute 亦为 0。关闭态真实 OUT 可读取 original。DriverIT 正控证明 Blob locator 的 setBytes/truncate 会持久化，Clob.setString/truncate 和 SQLXML.setString 仅改本地对象，不能把可变等同于持久化。 |
| 2 句柄回取 | Statement.getConnection、ResultSet.getStatement、两级回取、DatabaseMetaData.getConnection 复用代理；取得后 FENCED 再写均拒绝。Connection 上不存在 getConnection(user,pwd)；对实际具有该 API 的 DataSource 单独验证，并用真实账号密码调用真实驱动。 | HandlesIT 5 条路线各 3 次，Proxy.isProxyClass/连接身份断言，写委托增量 0、观察行不变；JDBC API 反射 3 次确认错误签名不可达。 |
| 3 unwrap/isWrapperFor | Connection、Statement、Prepared、Callable、ResultSet、DatabaseMetaData × 8 个标准/跨接口目标：兼容类型只能得到同一代理，不兼容与厂商连接接口拒绝。 | HandlesIT 144 组合；unwrap/isWrapperFor 委托均 0；通过对象回取连接后 FENCED 写增量 0、观察不变。 |
| 4 身份/prepare 记忆 | 反向取得 PreparedStatement 是同一对象；execute/executeQuery/executeUpdate/executeLargeUpdate/addBatch/executeBatch/executeLargeBatch 的无参调用保留 source SQL 判定。 | HandlesIT 7×3；原始 SELECT 执行 1 次，后继委托增量 0，拒绝码 FENCED（非遗失 SQL 的 UNCLASSIFIED），独立观察原值。 |
| 5 批边界 | 真 executeBatch 部分成功后再执行拒绝；成功 clearBatch 后空批可执行。clearBatch 注入失败后保持不确定并拒绝执行；clearParameters 不清已入批的 SQL；未证明空批拒绝，证明为空后普通/大型批无效果。 | BatchIT 4×3；部分失败正控新增 1 行，拒绝重试增量 0、不增加行；清空后的空批返回空数组、无新增提交。clearBatch 异常明确标 MODEL，后面的拒绝计数与观察为 REAL。 |
| 6 清理/回收/并发 | 已确认终点之后，10 类执行/行变更使自动提交复位证明失效；FENCED setAutoCommit(true) 拒绝，显式全量 rollback 后复位可用。关闭再借用和真实单连接 Hikari 池复用都仍受共享门约束。两个线程被同一 owner 监视器串行化，等待者在 FENCED 后写被拒。 | CleanupIT、PoolIT 各独立场景 3 次。setAutoCommit/写委托增量 0，独立观察不变；池实际 physicalOpens=1、旧代理 CLOSED、新代理 FENCED；同步 latch 为 MODEL 调度，驱动写计数和表值为 REAL。 |
| 7 旧对象的 FENCED 可见性 | 旧 Statement/PreparedStatement、可更新 ResultSet 的 insert/update/delete/updateString/moveToInsertRow 均检查共享门；启用态数据库 locator 在 FENCED 前即不能取得。连接 createBlob/createClob/createSQLXML 的对象能本地修改，但不具有该驱动的数据库更新能力。 | FenceIT 5×3；HandlesIT、LobIT 取得前/后；DriverIT 的连接新建值正控 3 次，FENCED 后改值且观察提交增量 0。不声称会撤销门前已经开始的执行。 |
| 8 默认关闭 | 缺省配置为 false；原构造器路径实际重跑注释、多语句、会话 SET、setAutoCommit 隐式提交、DDL 隐式提交，各 3 次，并新增 metadata/OUT/LOB 关闭态正控；旧 60 项原测试未修改。 | DefaultOffIT 的数据库结果与各关闭态控制路径一致，Authority 检查数 0；DDL/自动提交前后由独立连接证实；最终单测和 IT 的新 XML 为本轮证据，不复用上一轮结论。 |
| 9 穷举兜底 | JDK 标准 Statement/PreparedStatement/CallableStatement/ResultSet 的全部 600 个接口签名，独立决策表按参数重载逐一归类并运行各 3 次；新接口/未分配方法会直接失败。SQL 分类所有 8 个 kind 的分支由真库清单验证。 | 见[完整接口表](R09_C2_B1_1_JDBC_SURFACE_2026-09-29.md)与本地 MODEL API 输出。getter/本地方法不能仅靠 mock 宣称驱动无效果，另对真实 driver 字节码审计，正是由此发现 A3。 |

## 4. 允许集的效果裁定与仍未判定项

完整 API 表把每个重载绑定一个结论；这里记录真实效果依据，不能用通配 get/is/set 名称本身充当“只读证明”。

| 路径组 | 唯一结论与依据 |
| --- | --- |
| execute*/addBatch | 有 SQL/批记忆才分类；非只读均过门；FENCED 零委托。未知 SQL/无记忆/不确定批一律直接拒绝。已知空批只返回空结果；真实部分失败及清空验证见 §3。 |
| ResultSet update*、insertRow/updateRow/deleteRow/moveToInsertRow | 含仅暂存游标值的方法也过门；所有标准重载的 MODEL 计数与真实主要行操作双向证据结合。 |
| 7 类不透明 getter | 统一取得处拒绝。即使本驱动某个类型提示不可转换，也不让它在其他重载/厂商提示中默认通过。 |
| Statement 家族 getMetaData | 已证实会执行 SQL，直接拒绝；不再归为本地方法。 |
| 标量/日期/字符串/字节/流 getter、ResultSet.getMetaData | 从已经取得的行和列描述构造值/只读流；不是发起新执行的入口。RowId 在被测驱动抛 SQLFeatureNotSupportedException。Clob/SQLXML 本地可变的事实单列，且数据库取得路径仍拒绝。 |
| 游标移动/状态/警告、ResultSet 取消暂存 | 移动/读取既有结果，不构造新业务 SQL；变更另由 update/insert/delete 接口过门。refreshRow 不在允许集，直接拒绝。 |
| 参数 setter、选项 setter、clearParameters、registerOutParameter | 仅配置下一执行的参数/本地状态，不是执行权。clearParameters 不改变已记忆的批；任何后继执行仍检查。驱动不支持的类型先由其能力约束拒绝，不能取得真实数据库效果。 |
| getResultSet/getGeneratedKeys/getStatement/getConnection | 仅返回 owner 绑定代理；反向引用身份复用。getMoreResults 只消费已执行命令的结果，调用体/多语句不能在启用态新入执行。 |
| clearBatch/clearWarnings/cancel/close/closeOnCompletion | 清理或配置行为；不授予新写入。失败清批不清风险记忆；关闭事务走既有回滚；池回收已真实验证。 |
| unwrap/isWrapperFor | 代理兼容接口仅返回代理自身；跨接口/厂商接口不能暴露真实连接或子对象。 |
| 常量 SELECT/SHOW、ROLLBACK/ROLLBACK TO | 真库 FENCED 负控无新增行；回滚是释放/撤销，不是提交。FROM/DESC/EXPLAIN 不再获得只读免门。 |
| DML/DDL/COMMIT/AUTO_ON/BEGIN/SOURCE_READ | 每个允许语法分支 FENCED 零委托、独立观察无新增；正常允许路径已有四类门 IT 和视图正控。未知类型保持 UNCLASSIFIED。 |

本轮被测驱动/精确 URL 和标准 API 集内没有“无法判定但默认通过”的登记项。驱动 JAR 哈希、JDK 接口签名、ClientPreparedStatement/StatementImpl/ResultSetImpl/BlobFromLocator/Clob/MysqlSQLXML/元数据类反编译原文存于本机证据根 driver-*、api-*。这些结论不外推其他驱动、任意 URL 扩展开关、其他 JDBC 版本或绕过代理的反射/私有句柄；它们均未验，不作为放行证据。门不追溯包装启用前由外部直接取得的物理句柄，也不宣称可撤销已开始执行的命令。

## 5. 运行及失败账本

| 轮次 | exit | tests / failures / errors / skipped | XML | 说明 |
| --- | --- | --- | --- | --- |
| run1-baseline-it | 1 | 80/45/0/0 | 5 | 原取得漏洞：45 个取得拒绝断言失败；其余 35 既有 IT 通过 |
| run2-baseline-view-it | 1 | 86/51/0/0 | 6 | 新增视图反例：另 6 条失败，含写入已发生/门检查缺失 |
| run3-baseline-unit | 1 | 234/72/0/0 | 11 | 原方法分类 72 个反例失败；既有 60 项通过 |
| run4-fixed-it | 0 | 380/0/0/0 | 12 | A1/A2 初次修正验证，覆盖尚未最终齐备 |
| run5-lob-baseline-it | 1 | 3/3/0/0 | 1 | 厂商 Blob locator 3/3 真库逃逸，已留源码哈希 |
| run6-complete-unit | 1 | 0/0/0/0 | 0 | 新增池用例遗漏 DataSource 抽象重载，编译失败，测试未启动；0 XML、未补造 |
| run7-complete-unit | 0 | 2034/0/0/0 | 12 | 完整接口枚举首次通过；随后驱动审计发现 A3，不能据此提前收口 |
| run8-metadata-baseline-it | 1 | 15/12/0/0 | 1 | Prepared/Callable 元数据隐式执行：12/12 反例失败，3 个关闭态正控通过 |
| run9-final-unit | 0 | 2034/0/0/0 | 12 | 最终单测：原 60＋既有门 66＋不透明 getter 108＋全接口 1800 |
| run10-original-counterexamples | 1 | 18/18/0/0 | 3 | 精确输入 HEAD 的三类反例全复现：3 locator＋3 view＋12 metadata；源码随后逐字节还原 |
| run11-final-it | 0 | 536/0/0/0 | 16 | 最终真实库 IT：四类既有 35＋对抗性 501，全部 0 失败/错误/跳过 |

每轮精确命令、XML 测试名/参数、源文件哈希（从 run5 起）、REAL/MODEL 时间线及哈希清单按 run 目录引用。run1–run4 没有当轮源码哈希文件，不能事后补造；run10 已用精确输入 HEAD 的两门文件与原 SqlTest 重放三类反例，18/18 都真实写入后被断言检出；original-replay-sources 保存原/修正源码字节与哈希，finally 逐字节还原。最终 run9 与 run11 的全部 116 个 recovery 源文件哈希都与交付源码相同。所有逐轮已列哈希重新验证一致。

## 6. 隔离环境、文档和交付

- 隔离端口 19440；datadir D:/acrylic-pda-outbound/.local/mysql19440-b11-adversarial/data/；schema/账号 b11_adv_20260929，host 127.0.0.1；实例 UUID 090872c3-bbad-11f1-ae02-d8bbc13a2b36。MySQL 8.0.46、Connector/J 9.7.0、JDK 21.0.11、Windows/NTFS。fixture 行是合成测试数据。
- 各 IT 的 BeforeAll 在连接前精确校验 URL、账号、证据根的真实路径与轮次名，明确拒绝 3306；连接后再次核对 @@port、@@datadir、CURRENT_USER、UUID/驱动标识。密码不输出、不入库。
- ENV_三处真实覆盖缺口已在门内修正；最终定向单测 2034 项、真实库 IT 536 项全绿（0 失败/错误/跳过）。系统 3306/33060 只记录 OS 监听，未连接或查询；DSH 19387 未动。datadir、全部失败证据保留。
- 同步设计 §4.2、B1-1 G09/G23–G25、G0 验收接续注、C2 欠债、进度/历史索引；旧看板当前区原样归档。暂存只能为两门文件、recovery 测试与文档；精确路径、检查和推送回执在本机证据。
- GIT_三处真实覆盖缺口已在门内修正；最终定向单测 2034 项、真实库 IT 536 项全绿（0 失败/错误/跳过）。提交标题 BOM-less，正文说明三处缺口、测试与边界。不开生产开关、不发布/部署/合 main。

## 7. 原样保留边界与停止点

fenced.lock 跨进程层（F1′/F3/F5 产品实现）、P5/租约/锁协议、HTTP/Session 接线、生产默认 Session 清理、孤儿解除与生产释放、**窗口 W**、Linux 与其他文件系统、网络/共享盘、**断电与硬复位**、现场组合、Java/Python 允许集差异、C1 历史未验项。

**K1 仍未通过；B1 整体未完成。默认关闭、未接线、未部署、未开生产开关；完成本轮即停，不进入 B1-2。** 之前两项已闭合遗留未重做；七类生产决定、TEMP 生产归档、生产释放保持既有关闭边界，localDeletionAllowed=false。
