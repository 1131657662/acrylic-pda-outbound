# D13 闭链只读准入的实际数据库身份核对

本片补 `RecoveryAdmissionVerifier` 的实际 MySQL UUID/schema、登记身份及外部 intent schema/baseline 对照。14项定向单测通过，新隔离 JDBC 集成测试1/0/0/0，离线原件消费者通过；**运行器收尾退出1仍是失败记录**。仅只读组件的合成有界验证，不代表全部入口接线、跨实例错库、完整恢复、D13整体或最终候选通过。总目标active、未完成。

## 输入与实现

固定树 `D:/acrylic-pda-outbound`，分支 `codex/development`，基础HEAD及开工远端 `05f30d6c57159babc0c685ee66fbf52ec42bb0af`。版本事实见看板生成块。40项继承WIP保持，本聊天为源码/索引/看板唯一写者，另一个清理聊天仅有磁盘范围写权。

原实现分次读取绑定、admission、projection、server_state，未读取实际数据库UUID/schema，外部intent schema/baseline也未传入比较。**没有动态复现旧版错误准入，不称已证漏洞**。自主设计及判据在证据根 `resume-checkpoint02/decision.md`。

7个源码/测试路径：

- `RecoveryAdmissionEvidence.java` 增加实际/登记身份及单次闭链观察，旧供应者默认无证据并拒绝。
- `RecoveryAdmissionQueries.java` 委托新增 `RecoveryClosedAdmissionQueries.java`，一条SELECT在同一连接/statement读取物理身份及相关行。LEFT JOIN保留缺失证据，singleton/复合主键约束防fanout；缺server_state或SQL失败也拒绝。初始准入路径未改。
- `RecoveryAdmissionVerifier.java` 对照实际/登记UUID、schema及外部intent schema/baseline，不回退分次查询。正向也保持写入/本地删除能力false；不登记、修复、初始化NORMAL或新增生产bean/入口接线。
- `RecoveryAdmissionVerifierTest.java` 原8项补身份夹具；新增 `RecoveryAdmissionIdentityTest.java` 6项反例，包括错UUID/schema、缺身份、intent不符及旧供应者拒绝。
- 新增 `RecoveryAdmissionDatabaseIdentityIT.java`，真实Flyway/JDBC，MODEL Java控制链和手工登记行。同实例另schema只复制5张所需表，CREATE TABLE LIKE没有FK/trigger，**不是完整恢复**。

逐路径SHA及作者自审在 `closeout01/source-review.json`；固定查询无外部字符串输入或DML。调用点搜索确认实际HTTP/Session/worker/文件入口尚未消费本闭链Verifier，继续列缺口。自审和独立工具消费由同一助手执行，没有独立人员审查。

## 实际验证与失败

证据根 `.local/goal-20261023-resume/d13-runtime-admission-audit01/`。各轮新实例/目录，旧库未重启；未重跑旧run03或旧P5场景。

| 运行 | 实际结果及限定 |
| --- | --- |
| run1659 / 19835 / `.local/s23k` | 编译exit0，原8项及新6项单测14/0/0/0。IT1/1/0/0：Verifier调用前SHOW MASTER STATUS因缺REPLICATION CLIENT失败；认证停库/进程退出0/端口释放，原XML、日志、库保留 |
| run1660 / 19836 / `.local/s23l` | 编译exit0，IT1/0/0/0、13.623秒。新合成账号仅补只读观测权限；源码未变，14单测按全源码/依赖SHA复用，不机械重跑 |
| run1660顶层运行器 | exit1：IT、dump、认证停机及binlog解码已exit0；finally写source-after后verify_sources再次exclusive创建同名文件触发FileExistsError，不能记运行器成功 |
| consumer01 | exit1：错把revision1可信检查点与revision4链头要求完全相等，原脚本/失败输出保留 |
| consumer02 | exit0：分别核检查点、完整链及链头；全程离线补核，没有重跑IT/运行器/库 |

原XML的匹配schema为 `p5_lost_1660`，admitted=true、gaps空；复制schema `p5_lost_1660_copy` 为admitted=false，精确gaps=[DATABASE_SCHEMA]。每次Verifier恰1条SELECT、DML计数0、binlog位置保持；五次五表行哈希观察相同，IT另断言外部9文件前后守恒。

离线核最终dump两schema五表原行相等；binlog只有夹具准备的11条相关DML（原schema6条、copy插入5条），无相关DELETE。控制链4条canonical JSON的command/record哈希、前链、可信检查点、链头、身份及intent绑定均核同。2839源码及109依赖SHA保持；source-before额外含local-runner，移除后与after、磁盘一致。

**离线工具没有另封观察前外部文件manifest**；外部9文件守恒依据IT前后断言及最终原件，不能宣称独立工具掌握另封前字节。运行器缺失的final-classpath由消费者在新目录保存，不补写原目录。只读binlog曾按UTF-8解码失败，改bytes读取成功；历史文件名猜测和首个文档patch也失败后按磁盘内容纠正，无输入被覆盖，均不计业务验收。

## 命令、身份和原件

实际入口为 `C:/Python314/python.exe` 加证据根的 `run-identity01.py`、`run-identity02.py`、`consume-identity01.py`、`consume-identity02.py`，分别记录结果。编译/定向test/integrationTest/身份/dump/shutdown/binlog完整参数及退出码在run目录和实例private的 `*-command.json`；消费者命令/stdout/stderr/退出码在各 `consumer0N-invocation/`。合成凭据仅private/env，不上传。

19836身份前后原字节相同：datadir `.local/mysql19836-r09batch/data`，schema/账号 `p5_lost_1660` / `p5_lost_1660@127.0.0.1`，UUID `f9977cbe-ce48-11f1-8ebc-d8bbc13a2b36`。认证shutdown exit0、进程退出0、端口释放；19835也已认证停止。系统3306/33060内部身份未认证，仅观察，未连接/停止。

| 原件 | SHA-256 |
| --- | --- |
| run1660专项XML | 50736ef76f472e322c970cd3bc2452ef2b5b1b91f1935d2b93c444a3e37cee6e |
| run1660 source-after.json | 005621c1e0f8be4fed29c0f85f43f9723cd4744df739dae43e05c9dfff7d89ed |
| run1660 binlog.stdout | a1a021d50b5a0cdedcf3384bac9362492d2d580f3f7f4a6f5e9bf6876d0b174e |
| final-dump.stdout | 4f94e8a1d42fe320c75a262a9e0b91913c42df63200bb52cfbee75f9862ff2e4 |
| consumer02/result.json | cdee8b790133adfb4cd3189cd4efc5f9bcc9443dd19c81ca9e57a53c7173286f |
| consumer02/input-manifest.json | 13dcdf5fff7c5a535d6c6ead354f3ea71e9ab02717e8f4266c7b56f9370e284f |
| consumer02/final-classpath.json | 23431e1ce6b056f893ebd278ad84b98a56e4ebf98e34fea24331034883ce5bc9 |
| closeout01/originals.zip | d140b638e3e6e4c94aa7921dc9422458f61dc2be1fdc19670b7261c350438d5c |

`inherited-before.json/zip`、implementation及resume-checkpoint01–05保存输入原字节/SHA；checkpoint05保存全部47项dirty原件，其creation-plan记录报告原先不存在。`closeout01/originals.zip/json`保全看板、两索引及consumer01失败原字节；key-evidence、自审、扫描、精确暂存patch、Git回执、最终40路径SHA/ZIP及完整证据manifest同置closeout01。history仅提交本片入口，继承索引内容及看板留工作树。忽略证据仍只在本机，不虚称异地备份。

## 收口及未完成

未运行真实HTTP、Android/APK、模拟器、真机、部署或完整普通回归。契约/迁移/Android/Web未改；跨实例实际UUID拒绝、全部入口接线未验。下一有界项先只读审核实际跨实例UUID证据供应及入口消费缺口；OS拒绝自halt无新方法不重跑。

前片删除遭自动审批拒绝，本片不换通道重试。失败库及唯一原件保留，删除0、清理释放0，无新增APK。fresh进程/监听/ADB/Debug Keystore及C/D/E余量在closeout01环境断点，未操作设备。

D13–D15整体、P5其余故障/真实OS拒绝自终止、D16有效guard/复杂引用、D18实际最终候选及第8节仍缺。生产开关/TEMP归档、正式签名、原库/现网、main、部署/Release继续关闭，`localDeletionAllowed=false`；取消链及旧run03不恢复。开发备份及本片成功不改变总目标未完成状态。
