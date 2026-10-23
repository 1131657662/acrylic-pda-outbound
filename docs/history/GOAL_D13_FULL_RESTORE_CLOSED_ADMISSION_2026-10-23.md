# D13 完整恢复后的闭链登记与真实 API/worker/文件有界组合

本片在**原截点尚无不可变闭链绑定**的非空合成源上，通过真实 SQL/storage 恢复、生产四阶段投影与 NORMAL 登记，再启动实际 Spring API/JDBC Session、独立非 Web 生产 worker，验证旧任务隔离及新双格式下载。发现并修复闭链借用遇短暂协调锁争用时立即拒绝的问题。77项定向回归、新隔离组合1项及事后离线原件消费者通过；**非 D13 整体、真实业务恢复或实际最终候选通过**。

## 输入与范围

- 工作树 `D:/acrylic-pda-outbound`，分支 `codex/development`；输入提交 `3902d3f1c04a4ea4e07fd6bce1ec1d5632d45b34`。版本事实见[看板生成块](../ORCHESTRATION_STATUS.md)。
- 本机证据根 `.local/d13-restored-admission-20261023/run01/`，初始23条展开脏路径原字节/SHA/patch在 `baseline/`。本片14路径清单及自审在 `closeout-review/`；22条无关继承改动逐字节保留、不入提交。收口前37条全集另存 `closeout-resume04/` 和 `closeout-resume05/`。
- 设计依据 `decision.md` 及三份 `decision-amendment*.md`；沿用户批准的最小设计和总目标自主技术决策授权。未扩大业务政策、权限、生产或已取消范围。
- 仅生产两处变更：`RecoveryControlReader` 增显式有界等待能力，默认离线reader仍立即拒绝；`RecoveryClosedConnectionAdmission` 的物理连接校验采用1000ms预算，取得锁后重读权威。无缓存准入、SQL重试、错库修复或绕过触发器。
- 其余为新组合夹具、四项争锁回归、运行器接入与离线消费者。非P5测试收尾以可选Bean调用 `checkEffect`，存在时异常仍传播，scheduler停止和liveRoots归零仍必需；生产P5配置不变。

## 实际验证

| 证据 | 实际结果与适用范围 |
| --- | --- |
| `contention-red/` → `contention-green/` | 确定性争锁red四项二过二失败；修复后包含它的77项全执行、零失败/跳过/中止。涵盖超时拒绝、排队后权威损坏拒绝、原离线立即拒绝及相关闭链/Session/root响应。 |
| `restore09/result.json` | 直接javac编译exit0，冻结2691源码输入、110依赖，无漂移；不是Gradle完整回归或APK构建。 |
| `run1706-restore09/test-result.json` | 新实例 `e2a-cross-20260925-1706`，DB23706/HTTP24706；组合found/started/succeeded均1，failed/skipped/aborted均0，exit0且completed=true。实际认证身份及shutdown exit0另存实例目录。 |
| `resume02/regression-reuse.json` | 77项对应的1122产品输入及110依赖与最终编译输入核同；后变4个测试文件不在该回归已编译class内，22继承不变。明确复用范围，没有把两轮当同一次完整回归。 |
| `consumer-green/` | 无网络/数据库的原件消费者exit0，绑定2691冻结输入、110依赖、292原输出、65控制原件；与作者/自审同助手执行。 |
| `closeout-review/review.json` | 收口再次核同14待提交路径、22继承、冻结输入、依赖及原输出；事后消费者修订单独绑定其实际执行SHA。 |

编译、运行器、子进程及消费者的完整命令/参数、源码与依赖锁、退出码、输出哈希均保存在各轮原目录，不以本文简写命令代替原记录。消费者 `proof.json` SHA256 为 `9e3049af47767a065e43e09819e1d11b85257e3ed635dd64fd96ee9e0c8eeede3`。

## 业务事实与守恒

1. 非空合成源真实创建恢复前PENDING双格式任务，完整dump/storage及合成验签物料恢复到同实例新schema；179表、schema、迁移历史和原文件核同。源截点未有不可变binding，不宣称验证已有绑定的合法迁移。
2. 独立Python生产控制原件绑定本轮dump SHA与源事实，生产投影写入QUIESCING、RECOVERING、VERIFYING、RELEASE_READY，再以外置intent执行NORMAL登记及重放。仅 `server_state`、`recovery_installation_binding`、`recovery_control_epoch`、`recovery_control_projection`、`recovery_normal_admission` 五表变化，原schema及文件保持；准入校验通过。
3. 恢复权威用于原schema冷启动时精确拒绝六gap：DATABASE_BINDING_MISSING、DATABASE_IDENTITY_MISSING、DURABLE_INTENT_SCHEMA、DURABLE_INTENT_BASELINE、NORMAL_ADMISSION_MISSING、PROJECTION_MISSING；原源表/文件无写。不是跨实例UUID正向恢复证明。
4. 原跨代次任务所有列、payload和epoch保持，实际API呈现INVALIDATED，下载/重试与旧幂等键均拒绝。用户须重新查询、用新键生成当前代次任务；独立生产worker生成XLSX/PDF，各一次成功且实际下载，独立解析核原顺序、整数克、原members与快照。未让旧任务自动恢复资格。
5. API与worker为不同进程，真实CSRF/持久Session及产品primary adapter生效，两进程正常关闭。运行期变化仅在background_job、operation_event、spring_session及spring_session_attributes。
6. 转RECOVERY_PENDING后六个实际HTTP入口均JSON503/RECOVERY_DATABASE_ADMISSION_REFUSED；拒绝区间全表与文件守恒，最终源快照及65控制原件保持。

## 失败与修订保留

- restore01/02为PowerShell运行器兼容问题；run1700漏javac的 `-parameters` 导致Spring参数夹具500。保留原件，只改运行器，后续新输入/实例。
- run1703原断言要求旧代次PENDING自动执行，违背现行ExportJobQueries/ExportRetry/leases合同。按 `decision-amendment01.md` 修订为原任务不变/拒绝与新键重查生成，未放宽生产合同。
- run1704真实API/worker遇协调锁争用拒绝，为有效产品问题；保留cause和争锁red后做上述最小修复，未机械重跑旧目录。
- run1705双格式已成功，但非P5收尾helper强取P5 Bean导致worker退出1；整轮仍1失败，不算组合通过。仅测试可选接线修复。restore08的受检SQLException方法引用编译失败同样保留。
- consumer-red在Spring外层异常摘要中找不到嵌套gap而失败。原Java组合已核完整cause；消费者改从同次tests.stdout唯一Application run failed链精确核六gap，以同一原件重新离线消费，未重跑业务。旧脚本/新脚本SHA分列，见 `resume02/consumer-review.md`。
- 收口检查器首次把事后消费者修订误判为产品输入漂移，原失败保存在 `closeout-review-attempt01/`；之后明确绑定consumer-green实际SHA，其他冻结输入要求保持。未覆盖失败原件。
- 文档首轮用Windows PowerShell 5.1读取无BOM中文脚本，导致新看板叙述/归档说明乱码；错误字节及SHA在 `doc-encoding-attempt01/`。改用PowerShell 7从原UTF-8脚本与旧看板重写，并核新叙述逐字匹配；未重跑业务或修改原证据。

## 清理、安全与交接

按用户持续清理授权，先核绝对路径、认证停机、无监听/消费者、identity前后SHA、非空dump及逐文件输入哈希，再仅清1700/1703/1704/1705/1706的 `data/`。逻辑删除1,240,744,117字节；D余量21,104,267,264→22,382,432,256字节，观察净增1,278,164,992字节（约1.19 GiB）。当时C余量74,881,802,240、E余量195,634,053,120字节。命令与前后记录在 `cleanup/`，旧清理脚本原件/SHA在 `closeout-review/originals/`；没有再次删除。

保留全部dump/binlog、identity/shutdown、原HTTP、合成签名/控制原件、冻结输入及失败日志；1701/1702、共享缓存、原资料/Keystore/APK和继承改动未碰。清理和Git备份不计D/O/U或第8节进展。

安全扫描7处命中经逐行复核为测试CSRF字段、继承的公开合成测试口令及loopback动态测试端口，无真实凭据、用户资料或生产连接入Git；完整定性见 `closeout-review/security-review.md`。原SQL/DER/cookie、数据库、APK与本机产物不提交。

fresh收口环境在 `closeout-resume05/environment.json`；另一项目及清理聊天核idle，仅观察到只读搜索，未发现并发写者。本片无在途Java/Gradle/模拟器，测试端口无监听；系统3306/33060身份未认证，仅观察未连接或停止。ADB仅枚举两原设备为device，未操作设备；debug keystore原SHA保持，未构建签名APK。模板writer字段偏差已在安全复核补注。

接续先读[AGENTS](../../AGENTS.md)、[Git工作流](../GIT_WORKFLOW.md)、[看板事实与断点](../ORCHESTRATION_STATUS.md)，再读本报告及本机closeout；fresh核全部dirty/远端/写者/环境后才写。前看板叙述见[归档](ORCHESTRATION_STATUS_BEFORE_FULL_RESTORE_ADMISSION_2026-10-23.md)，完整原字节/SHA另存，不恢复历史状态。

## 未完成与边界

本片是合成权威、原源无binding、非P5的有界组合；不是可信保管员/原设备/现场证明。已有不可变绑定跨UUID/schema的合法恢复、真实P5与闭链同输入组合、其余worker/文件入口及D13–D15整体仍缺。D16整体、D18实际最终候选及总目标第8节仍未完成；历史局部通过不能拼成最终候选。

契约、迁移与APK未改，1000ms等待仅影响上述运行时闭链读取；版本事实只见看板生成块。本片未跑完整普通回归、Android、模拟器、真机或部署。既有测试部署/M71报告按其独立范围继承，不属于本片通过。

总目标保持active。localDeletionAllowed=false；生产开关/TEMP归档、正式签名、原库/现网、main、生产部署及Release关闭；不恢复取消链、多跳、接收者确认/激活或旧run03。开发备份不是验收通过。
