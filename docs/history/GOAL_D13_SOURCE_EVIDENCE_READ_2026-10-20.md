# D13 原来源证据只读 HTTP 与 P5 兼容修复

## 范围与结论

输入为 `codex/development` 的 `1de2ccac26cdb00b64b70f2d63143575ec42895d` 加本轮明确改动；fresh origin 同输入，main 不变。源版本事实见看板生成块。本轮不是新恢复功能，不涉及接收者确认、激活、换机承接或 PDA source-evidence intake。

发现并修复一项实际产品缺口：既有 `SourceEvidenceQueries.metadata/mapping` 使用 `JSON_OBJECT`、`DATE_FORMAT`，P5 有限 SQL 分类器未列这两个纯内建函数，真实有权详情读取返回 500。`RecoverySqlExpression` 只增加这两个函数名，仍返回 `SOURCE_READ` 并执行效果授权检查，不变为可绕围栏的 `READ`；未知、限定名、嵌套危险函数、文件写出及多语句仍拒绝。产品权限、路由、契约、迁移、签名、部署配置和生产开关没有更改。

## 输入、方法与证据

证据根：`.local/goal-20261020-resume/d13-source-evidence-read01/`。`input-before/`、`input-manifest.json`、`tracked-before.patch` 保存33项继承路径原字节/SHA；除本轮按脚本刷新看板外，32项非看板继承路径逐SHA保持。执行后另一聊天新增window03报告也单独保留，不混本提交。

`decision.md` 先规定退出标准与适用性；`execution-plan.json`/`server-inputs.zip` 保存首轮2540输入，`run02-inputs.zip`、`run03-inputs.zip` 保留修复后的分别输入。自审后的 `run04-inputs.zip/json` 另存2444项源码/构建输入，构建依赖与工具另见 `build-inputs.zip/json`。旧XML先存 `reports-before.zip`，每轮记录XML前后SHA，只复制本轮变化的新结果；首轮halt没有XML，不取旧报告。`runtime-classpath-sha256.json` 保存原3533个运行时文件哈希，最终文件另存 `runtime-classpath-closeout.json`。普通回归前仅追加分类器测试；其后仅修本IT夹具，产品输入不变。

采用 testing-best-practices/security-practices/single-responsibility；发现产品缺口后按 brainstorming 有界修复审视，依据 AGENTS 和总目标第3节已有自主技术裁定授权实施。无子代理，同助手作者/自审；Python为独立工具消费者，不冒充独立人员。

## 实际验证

命令：每轮新证据目录下 `powershell -NoProfile -File .../runNN/execute.ps1`，内部实际执行 `gradlew.bat -p server recoveryControlClasspath integrationTest --tests '*E2aSourceEvidenceReadIT' --no-daemon --offline --max-workers=1 --console=plain --stacktrace`。使用本轮新建合成库/身份、生成密钥，逐次校验datadir/schema/user/port/binlog；仅环回DB19340/HTTP19444。准备阶段MockMvc，受验阶段真实Cookie/CSRF登录、TCP GET、P5准入/跨进程效果门全部启用。

最终本轮run04：**IT实际1项，0失败/0错误/0跳过，exit0**；41次TCP，其中37次业务GET：200×6、401×6、403×3、404×18、409×4。Gradle总时长1分15秒，XML测试时长47.043秒。

| 覆盖 | 实际观察 |
| --- | --- |
| 已收正文后ABORT | 原7348字节正文逐字下载、原canonical保留、原终态回执不改写；未声明chunk仍404 |
| 部分chunk后ABORT | 原262144字节chunk逐字下载；完整声明正文923660字节未被当作已受理，canonical仍null、缺正文/缺块404 |
| 权限/身份 | 三资源均核匿名401、跨仓404、原creator/client/round不符404、仓库授权移除404、权限移除403、旧Session版本撤销401 |
| 损坏原件 | 正文和块的详情/下载各409；损坏字节与文件清单不被产品修复，测试finally恢复自己的合成原件后可读 |
| 只读守恒 | 12组前后178表逐行类型化快照及全部8私有文件SHA逐字相同；仅排除2张Session运行元数据表，显式身份/权限扰动在各GET组before之前；新增外层核对证明三种权限扰动结束后完整420行恢复 |
| 安全与签名 | 成功GET no-store/nosniff；7项写入/放行/续扫/释放/删除/证据上传能力明确false；Python实际RS256验原两份回执及原HTTP正文/块 |
| P5退出 | 真实root排空后关闭，空index、无unknown，独立后继进程stdout为STARTED |

`consumer-run04.json` SHA `30644befe4930b90e99b23b36327a053e0bcdf3e9598003c3131b3711266893d`；`run04/integration-result.xml` SHA `d761703a9000bc9c7e4c88b9f3761d1097375977a0db538150416ec3699d263e`。`consume_run04.py` 用原HTTP/文件/公钥独立核对，不依赖测试断言摘要；`review01/access-restore-consumer.json` 进一步比较初始正向、权限扰动前后整库原字节相同。`run04-inputs.zip` SHA `9f8c9a2024d2e9774af641c40ea7d530e2c8f451679e101f9456ae3bd5da9230`。

分类器首轮普通定向94项全部通过；随后增加实际代理层有权检查/围栏后拒绝及嵌套危险调用不达driver的7个参数向量，普通完整回归同输入实际4798项：4779通过、0失败/0错误、19跳过，exit0，6分36秒，310份原XML封存于server-test/xml。19项逐方法保存在server-test/result.json；1项外部SKU向量和17项Relay外部原件条件未提供、1项Windows符号链接前置不满足，全部计通过0。不含IT，不拼接最终候选。新增7个实际代理向量包含于本次回归。

## 原失败与限制

- run01仅CSRF/login，遗漏本机HTTP的cookie secure=false测试覆盖导致403。断言后过早关闭，Session root尚在退出；原binary日志证实RECOVERY_LEASE_OUTSTANDING、F3 ClosedChannelException、unknown和halt70。原库/P5/HTTP/log/binary及输入保留，未判P5通过。只在测试增加本机cookie选项和真实liveRoots=0等待，不改产品。
- run02登录成功，首个详情500；原XML完整栈指向JSON_OBJECT/DATE_FORMAT分类漏项，是产品兼容缺口。P5正常排空、无unknown；新run03实际复验通过，旧失败不覆盖。
- run03原IT及11组GET守恒通过，但自审发现夹具删除了同一reviewer的全部仓库授权，只恢复目标仓，漏恢复另一个原fixture仓授权；只读GET未改库的逐组结论仍成立，不能声称测试全程完整恢复。原run03/XML/消费者保留。修夹具为参数化移除/恢复唯一目标授权，并用外层整库/文件快照核全部扰动恢复；新run04实际执行通过，不覆盖run03。run03原XML SHA `6fdb1e322d8e5f74336d57b69d9d4f1b68bc4578ab59b2be9d338d4a1a992a3a`，消费者SHA `b8ed2c4adbe0970ea12ee8a8976549cd6927f3b4c35e7e45f0680c2c2d633aa9`。
- run04只验两个合成来源与既有只读入口；没有运行未知历史密钥全量组合、并发快照压力、REJECT业务链、客户端/设备或灾后完整恢复。旧MockMvc证据按 `docs/R06_SOURCE_EVIDENCE_GUIDE.md` 原范围沿用，不取代本轮TCP。其后未再改受验代码，无需机械重跑普通回归。
- 不是D13整体/L2、D14/D15、D16删除资格、D18或第8节完成；可信原身份/原资料/完整最终候选仍缺，不因局部通过放行。

## 清理与保存

run02/03认证shutdown exit0、逐项连接身份核同、无消费者/监听后，按持续授权只清用途结束可重建datadir：694文件、444846154逻辑字节；D余量32303050752→32751464448，观测净增448413696，与逻辑长度分别登记。`cleanup01/plan.json` 保存逐文件SHA/输入/命令/依赖与重建依据，`result.json` 保存实际删除回执；MySQL生成身份原字节另存。

run04同样认证停机、无消费者后只清347文件的datadir，222435987逻辑字节；`cleanup02/`记录D余量32379301888→32606048256，观测净增226746368。两次累计逻辑删除667282141字节，约636.4MiB，不与分时观测净增混算。全部MySQL生成身份保留。清理后补充只读核对命令首轮因Python默认GBK解码exit1；显式UTF-8后仅重做该核对通过，未重跑IT/清理，准确摘要另存review01，不冒充原stderr。

首轮含unknown的失败库用途尚需诊断保全，未清；HTTP/签名/原件/块/源码/旧失败日志/XML/P5及最终APK均保留。没有碰其他根、共享13316、系统3306、原库/现网、M71或另一聊天test维护。`localDeletionAllowed=false`及全部生产边界保持。
