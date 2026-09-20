# R06-D 临时与正式作业审计接续

日期：2026-09-20。接续[R06-C本机作业](R06_LOCAL_WORK_GUIDE.md)，本批完成TEMP操作记录的连续补传、签名归档和正式单召回中的全设备审计接续。R06-D仍在开发，临时单冻结/提交控制头、原批次上传、正式化、独立确认、关联与拒绝实物处置尚未开放，车辆仍不得放行。

## 来源与连续性

- 正式事件保持原`assignmentId`字段、规范正文和摘要，不给旧事件补写新字段。TEMP事件使用`source={kind:TEMP,creatorDeviceId,clientOrderId}`及`authorizationId`，保留原操作员、事件UUID、设备序号、采集引用和时间；不以最终正式单号替换来源。
- V31新增`temporary_work_origin`，首次接收时按创建设备＋本机订单UUID固定原TEMP资格。同一来源后续不能换用新资格；数据库唯一键与复合外键、设备锁、插入后复核共同保护。原资格必须属于认证设备和仓库，原操作员须一致。批次中任何来源、UUID或设备序号冲突均整体回滚。
- 正式和临时事件按同一`work_device.nextEventSeq`合并，每批最多200条，保留既有512KiB限额。缺口、跨来源序号重复或内容篡改不推进确认。待传原JSON先持久保存，丢响应或Room重开后恢复原请求。
- 接收历史证据仍要求有效PDA会话、`pda:submit`、认证设备/仓库和当前传输恢复代次；原资格到期或临时建单权限关闭不删除历史。服务端允许激活声明尚未确认的原资格补传证据，但不代替激活确认，不新增作业权限，不正式化订单、不出库、不释放防重记录或恢复屏障。
- 签名审计归档读取同一混合事件流，沿用不可变摘要、连续前缀、至少5年留存及`localDeletionAllowed=false`。跨代次恢复保留原资格和原事件代次。正式单召回的全设备覆盖可包含其他TEMP作业事件，当前正式单自身事件和最终停止事件仍须完整。

## 版本与兼容

Android `0.14.0-r06-audit` / code19，Room保持16，本批未改变本机实体表。契约0.24.0，路径仍102条；服务端本批迁移为V31，当前仓库最新为V32（另一任务的FH日期流水订单号，提交`bbf9cc2`及`b830f4c`）。订单编号功能与本批独立，R06-D后续正式化复用`OrderNumbers`。

旧正式事件、冻结正文及回执不重新生成。code19向旧后台发送TEMP审计会被拒绝，原待传JSON及后续事件保留，不越过TEMP序号。启用本功能需配套0.24后端；本轮没有升级现网。V31增加审计列后，旧版使用无列名`INSERT INTO pda_audit_event VALUES(...)`的程序不能直接回退运行；回退须停写并按备份/兼容方案处理，不删除新事实。曾单独应用V32且缺V31的开发副本须独立核对迁移历史，不自动开启out-of-order或忽略迁移。

## 实际验证

| 范围 | 本轮结果 |
| --- | --- |
| Android主机 | 最终261项通过，0失败/错误/跳过；本批7项TEMP专项覆盖原请求丢响应与Room重开、缺口、跨来源重复序号、200条分页、混合归档/召回、旧后台拒绝保留和真实服务器签名 |
| 服务端 | 32项单元、118项真实MySQL/MockMvc集成通过，0失败/错误/跳过；覆盖TEMP来源绑定、并发首次绑定、跨来源冲突回滚、旧资格证据、混合归档跨代次恢复及正式召回/提交回归 |
| 跨端契约 | `TemporaryAuditContractFixtureIT`生成真实后端签名混合归档；Android使用自身TEMP序列化重组正文、核验规范摘要、字节数和签名，改动原资格后拒绝 |
| 数据升级 | 只读复制R06-B合成库至独立新库，V30→V31→V32通过，90张旧表原有字段的数据摘要保持；另在独立副本补入旧格式正式审计/连续检查点复验，详见本机证据 |
| 构建 | JAR、debug APK及测试APK构建通过；APK签名核验通过；lint 0错误/179警告；OpenAPI 102路径通过 |

失败记录：首次后端命令把`--tests`接在`bootJar`后，Gradle拒绝参数，未运行测试；修正任务参数顺序后通过。Android首轮31项有1项失败，原因是混合归档测试夹具缺恢复代次观察记录；补齐夹具后31项通过，后续最终261项全量通过，未删断言。全部失败日志/XML保留。

本机测试使用真实Room、MySQL、签名与事务；HTTP边界为MockMvc或替身。混合Android查询夹具显式对齐两份独立样本的事件设备字段，仅验证本机合并；服务器混合用例使用同一真实授权设备。Room重开不是操作系统跨进程验收；合成过期不是实际48小时离线。未运行实际HTTP监听、浏览器、模拟器或M71，未完成R06现场/整体验收。

证据在ignored `.local/r06d-audit-20260920/`：`server-first.log`、`server-second.log`及XML、`server-final.log`及单元/集成XML、`android-first.log`及XML、`android-second.log`及XML、`android-full.log`及XML、`upgrade-summary.json`、`upgrade-seeded-summary.json`、`apk-signature.log`、`summary.json`及两份签名夹具。凭据、数据库副本、私钥、原始证据不入Git。

## 交付与下一步

APK：`outputs/pda/Acrylic-PDA-0.14.0-r06-audit-code19-debug.apk`，11,653,148字节，SHA-256 `f8c28a7c862332249d830e4796a14f41eb609220eb37692ba4a44218159b0d39`；包名`com.acrylic.outbound.pda.debug`，未配置服务地址，未安装或发布。测试APK 2,027,932字节，SHA-256 `572fd4d01f541db122305987e2a66cb3e878d3369c1e45f8847fdd037b309d19`。

新建`acrylic_outbound_r06d_audit_20260920`及独立升级副本，测试/无Web迁移进程退出，共享MySQL保留。R06-B原库仅只读复制；R05原归档/持续同步/续期数据和RECONCILING断点、AVD/M71、现网0.18/V23/code16及R08/R09工作树保持。既有API18093/Web5181、专用代理和运维审批阻断没有重试。

本批源码、测试、契约和接续记录按已有授权提交推送`codex/development`，实际提交号以Git为准；`docs/out.xlsx`不入Git。未合main、打标签、发布或部署。

下一具体开发项仍为 **R06-D TEMP稳定来源冻结与原批次上传恢复**：沿本批`temporary_work_origin`固定原资格，接不可变正文/提交控制头、abort先到、原键查询/分块恢复，再接独立`temporary_order:confirm`、短装/冲突、重复正式单人工核对、受控关联与拒绝后的实物处置。审计补传完成不等于订单上传或确认完成。
