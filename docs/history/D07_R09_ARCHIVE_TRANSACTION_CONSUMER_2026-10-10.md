# D07/R09 恢复归档同事务消费

输入主树D:/acrylic-pda-outbound、codex/development，HEAD/实际开发远端88be3e536453ea259949f6f4c007bfe71c8b5a4d。总目标active、未完成，版本事实仅见看板生成块。

## 范围与接入限制

接续核规则、看板、FORMAL/磁盘/四表恢复/归档读取final、HEAD与远端、全部dirty、聊天写者及进程/监听。recovered-handover-preflight-01保全27条dirty副本/hash/diff与环境，原final逐hash一致；69次继承比较和原22保护项零漂移。单助手唯一写者，无其他项目聊天在途；既有MySQL/ADB未操作。

依赖盘点：HandoverTemporaryOriginal仍需原native intake/verification/数据库审计前缀。1189来源原未收正文、未关联canonical，原SOURCE_RESERVED不能借读取变成订单/slot；既有未关联LOCAL产品链明确拒绝交接。HandoverCommandService也必须拥有外层事务，不能直接在closed回调里调用其execute。以上不是本批解除的限制。

选择先补原读取→消费的事务时间差。RecoveryTemporaryEvidenceRestore.withVerifiedArchive在已有closed事务中完成进入前核验、调用受信应用消费者、返回后核验与提交前核验；最后同步在消费者返回后登记，覆盖消费者自己的beforeCommit工作。消费者使用同一DataSource的事务绑定连接，原头/原四表/权限准备/占用保持，失败一起回滚，返回值待commit完成才对调用者可见；不自动重试，消费幂等由具体消费者负责。旧verifyArchive委托此入口，只读语义保持。

没有新bean/HTTP/签发/普通准入，没有新增原机STOP/ACK、native verification或审计。回调是内部可信代码接缝，不能传入未受信业务代码或执行不可逆外部副作用。设计在`.local/d01-d18-20261010/recovered-archive-consumer-design.md`。不代表主管同意、物理控制、接收准备激活、最终记账或D07/R09整体通过。

## 当次验证与保留失败

- 定向02为28/0/0/0、4XML、exit0/sourceUnchanged。01外部PowerShell数组传参被串成一个Gradle任务名，0测试/exit1，原command/log/result保留；修运行命令后02通过。
- 新空目标1206跨33树核分配，真实导入1189保全截点dump，补齐原四表；MySQL1/0/0/0、exit0/sourceUnchanged。验证同事务写探针、独立连接commit前不可见/commit后可见、回调仅一次及消费者幂等；缺行/坏pin不调用消费者，回调异常/尾SQL失败/原行修改/运行中及消费者beforeCommit换pin全表文件回滚；外层事务拒绝。探针不是主管决定或业务恢复。
- 独立Node消费者02为11输入hash不变，核原payload/终态/STOP逐字节、原set/stop-object ID、各独立pin/control、冻结与STOP分界，所有业务表、文件、schema及迁移历史守恒，仅测试探针由空变成同source pin一行。01误将快照表对象作为数组取length失败，原脚本/hash/result及实际错误日志保留；修为rows.length后02通过。
- 受影响recovery/handover普通回归01为3212/0/0/0、106XML、exit0/sourceUnchanged；普通test不含IT、非完整server回归，不以编译或历史回归代当次新接口验证。

作者自审固定SQL、closed前后/提交前重验、同DataSource事务资源、原字节防变与生产未接线；四个改动代码文件均≤200行，git diff --check通过。沿用brainstorming、single-responsibility、security-practices、testing-best-practices及avoid-hasty-abstractions，无子代理或独立人员审查。无新HTTP/Android/APK/页面/模拟器/真机/部署验收。

## 证据、环境与下一步

运行command/source hashes/log/XML/result在`.local/d01-d18-20261008/r09-archive-consumer-*`及`r09-temporary-evidence-restore-mysql-1206`。分配、runner/provision、dump/retention在`.local/d01-d18-20261010/temporary-evidence-restore-mysql-1206`；实际datadir/binlog/dump/导入快照/全表/原字节在`.local/e2a-cross-20260925-1206`。独立消费者脚本/result/input hashes在archive-transaction-consumer-1206-01/02，原日志单独保存；脚本hash在archive-consumer-scripts-hashes.json及各证据目录。

1206 dump独立重算一致，已认证停机/原PID退出，19330/19434空。无清理；新增产物/TEMP/Java/cache仍在D盘。1150/1151/UI-06及1100–1102未重跑，失败库未复用；原失败/原资料/旧BCD/526和恢复限制保持。

下一步：在既有命令系统内确定原无canonical恢复来源的明确登记事实，或以有原canonical与native事实的真实成功来源补精确恢复接口；不得伪造INITIAL_ACCEPTANCE/旧verification。随后接主管故障围栏、接收准备/激活与最终提交，独立新库验证。新同事务接口不能直接嵌套外层HandoverCommandService，需复用其受控事务内操作或明确准入交叉点。

D06/D07整体、D08–D18及第8节仍未完成。生产默认/签名/七类生产决定关闭，localDeletionAllowed=false；不接原库/现网、不合main、不发布部署/标签/Release、不正式签名，不绕既有设备/审批阻断。本机私有物料未上传或异地备份。
