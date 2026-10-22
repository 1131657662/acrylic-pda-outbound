# D13 闭链只读准入的实际跨实例 UUID 反例

两个全新实际 MySQL 实例使用相同 schema；复制五张 MODEL 登记表后，源实例准入、另一实例精确拒绝 `DATABASE_SERVER_UUID`、回源仍准入。定向集成测试 **1/0/0/0、24.988秒**，顶层运行器和离线原件消费者均 exit0。仅补只读组件的实际跨实例反例，不代表完整恢复、全部入口接线、D13整体或最终候选通过。总目标 active、未完成。

## 输入、改动与判据

固定树 `D:/acrylic-pda-outbound`，分支 `codex/development`，基础 HEAD 及开工实际开发远端 `debc00540af7258c0f1bc13503209e14c90d625b`。版本事实见看板生成块。40项继承WIP原字节/SHA/ZIP、完整diff和环境核验在证据根的 `baseline/`；本聊天唯一写本片源码/索引/看板。另一个清理聊天本轮只读核实为 active/inProgress，尚无本轮输出；未消息、委派或替其宣称释放空间。

证据根 `.local/goal-20261023-resume/d13-cross-instance-admission01/`，实施前的 `decision.md` 记录选择和退出标准。审核确认 `RecoveryOperationRegistry`、`RecoveryRequestAdmittedDataSource` 和 P5 已有根/writer等保护，不能宣称所有入口无 guard；闭链 `RecoveryAdmissionVerifier` 尚未被实际 HTTP/Session/worker/文件入口消费。初始NORMAL、INITIALIZATION与裸迁移池的接线另立任务，本片不改变其行为。

仅新增 `server/src/test/java/com/acrylic/outbound/recovery/` 三个文件：

- `ClosedAdmissionInstanceModel.java`：明确MODEL外部控制链及五表合成登记，参数绑定复制全部字段。目标全迁移后先确认四登记表为空、`server_state`只有新建单例，再替换这个合成单例；不是业务数据删除授权或完整恢复。
- `ClosedAdmissionIdentityObservation.java`：每次调用前后观察session SQL计数与binlog位置；以逐列bytes/hex、行排序后SHA核五表，以SHA核外部文件；正向也断言写入和本地删除能力false。
- `RecoveryAdmissionCrossInstanceIdentityIT.java`：两个原生JDBC连接，实际认证loopback端口/账号/schema/datadir，核UUID不同而schema相同；源正向→复制实例精确拒绝→源再次正向，三次后均核两库五表守恒；另封外部控制目录（含trusted检查点）前后manifest。

main源码、契约、迁移、Android/Web、生产设置均未改。固定表allowlist、列名校验、值绑定与环境拒绝边界已作者自审，记录在 `closeout01/source-review.json`。凭据只在private/env，无硬编码密码/私钥或真实用户资料上传。作者、自审与离线工具消费者为同一助手，非独立人员审查。

## 实际运行与离线核验

入口 `C:/Python314/python.exe` 加证据根 `run-cross-instance.py`，只借用既有prepare/identity/shutdown helpers，不调用旧scenario入口。两个新实例为 `.local/mysql19837-r09batch`、`.local/mysql19838-r09batch`，共用合成schema/账号 `p5_lost_1661`；构建输出 `.local/s23m`，目标准备目录 `.local/s23n`。TEMP和产物均在D盘，offline、no-daemon、单重型构建/单Java测试线程。未重跑旧库、旧用例或旧run03。

实际命令和退出码在两run目录及实例private的 `*-command.json`：编译 `testClasses recoveryControlClasspath` exit0；仅 `integrationTest --tests '*RecoveryAdmissionCrossInstanceIdentityIT'` exit0；两份五表dump、身份前后核对、认证shutdown、进程退出及binlog解码均exit0。顶层会话61100返回exit0，接续后另录工具回执；未改写原run输出。两实际UUID：

| 实例 | UUID | 准入观察 |
| --- | --- | --- |
| 源19837 | `2a608f72-ce4c-11f1-8511-d8bbc13a2b36` | 前后均 admitted=true、gaps空 |
| 复制目标19838 | `2ccc3609-ce4c-11f1-be0f-d8bbc13a2b36` | admitted=false，精确gaps=[DATABASE_SERVER_UUID] |

三次Verifier各恰1条SELECT、DML增量0、binlog位置不变；共8次五表行哈希观察相同。两库最终dump五表INSERT原行完全相同；binlog相关DML源6条、目标7条，均为迁移/合成准备。目标其中1条DELETE仅替换新建 `server_state` 单例，不能宣称全程无DELETE；观察期无写入。

`consume-cross-instance.py` 全程离线、不启库：原XML、实际身份、两dump/binlog、4条canonical控制历史的command/record/前链、可信检查点、链头、identity及intent绑定核同。2842源码、109依赖与2个运行器输入前后及磁盘SHA保持；保存编译class哈希。外部9文件（含trusted.json）另封的before/after manifest原字节SHA相同，且逐文件与最终原件核同。consumer01及其invocation均exit0，没有覆盖失败输出或补写旧证据。

## 保存、哈希与限制

| 原件 | SHA-256 |
| --- | --- |
| run1661专项XML | e07fb8e269afe1c5b5117ab376c45b35bf3afdd512ddce1cb1c9b17a4e961107 |
| source-before/after.json（各） | 576371ec4191126b51548a63809cea7bce5eefe7e02260e7d89ff28b7abad310 |
| external-before/after.json（各） | 473875186cf36594c5b9286872c03f00379d951773ee8ae308d1c1632d6d1bb7 |
| 两份five-table-dump.stdout（各） | e1cffe25a059d29f223f4d5799b4b38e9a7c30e78746174b86e0ffa393376dbc |
| consumer01/result.json | d38fb76948bca1df6599e6ec44c4b9b91f94d38eb605e4ec8c9fbc421cb998ad |
| consumer01/input-manifest.json | cee0c4c97a8224515ac23a22c775859335110df12e8587e951097fc240421d40 |
| closeout01/documents-before.zip | 2a7a1a196d3421ce7f44879a86408b82d75765be9dca648e473aa0aff6ebd377 |

baseline保存继承全部原字节；closeout01在文档变更前再次保存看板/两索引原字节和哈希、新文件原先不存在的计划。旧看板叙述另归档，版本生成块不复制；完整原字节保留本机ZIP。精确暂存patch、扫描、自审、Git回执、最终dirty SHA/ZIP、继承核验与环境断点同置closeout01。忽略产物只有本机保存，不虚称异地备份。

本片无失败测试；前片run1659权限失败、run1660运行器收尾exit1及consumer01失败均仍保留，不被本片成功覆盖。未改生产实现，未机械重跑前片单测/同实例换schemaIT；未运行完整普通回归、真实HTTP、Android/APK、模拟器、真机或部署。没有动态复现旧版错误准入，没有测试完整恢复后实际UUID策略，也没有全部入口接线结论。

两实例认证停机、进程退出0且端口释放，fresh环境另留证。系统3306/33060身份未认证，仅观察、不连接/停止。ADB仅枚举、Keystore只核Debug哈希，不操作原设备或正式签名。此前删除遭自动审批拒绝，本片不换通道重试；删除0、清理释放0，失败/唯一原件及新隔离数据保留，产物在D盘。

下一有界项为闭链身份在实际入口的消费设计，需区分初始NORMAL、INITIALIZATION、同一物理连接与裸pool、关闭路径；已有根/writer/P5保护不得削弱。D13–D15整体、P5其余故障/真实OS拒绝自终止、D16有效guard/复杂引用、D18实际最终候选及第8节仍缺。生产开关/TEMP归档、正式签名、原库/现网、main、部署/Release关闭，`localDeletionAllowed=false`；取消链/旧run03不恢复。开发备份不计整体验收或总目标完成。
