# D13完整恢复后真实worker halt与孤儿P5组合（2026-10-23）

## 范围和输入

固定树D:/acrylic-pda-outbound，输入3e66a278ac2c19aeea0599e09da97568c23c59c6。8个代码路径仅测试、封闭runner选项及离线原件消费者；产品源码、配置、契约、迁移、Android/Web未改。版本事实见[看板事实块](../ORCHESTRATION_STATUS.md)。已批准四状态只读筛选沿已有证据，不重复开发。

证据根.local/goal-20261023-resume/d13-restored-worker-crash01/；baseline.json/inherited-wip.zip保全40继承逐路径SHA/原字节，edited-originals及additional-edited-originals保存改前代码。acceptance-design.md先定有界退出标准，pre-execution-design-revision保存修订前字节/SHA；最终执行8代码以execution04-edited-sha.json和source-review04.json为准，早期pre-execution-review并非最终输入。2640执行源码及275外部依赖前后逐SHA相同。

实际错库不是上一片错误恢复根：独立数据库校验器在所核生产运行入口尚无调用，合成权威也缺初始NORMAL原库证据供应。本片推进独立的真实worker崩溃组合，未把数据库供应缺口改称现场问题。

## 实际执行

非空迁移→真实HTTP原PENDING双格式→完整SQL/storage/合成验签恢复→独立API和非Web生产worker启用P5/cross-process effect gate→正常双格式下载→排空scheduler但保留worker JVM→第三PENDING→worker持真实operation/P5租约→API实际checkEffect正向→仅自有worker测试JVM Runtime.halt(71)→实际OS生命周期锁释放→首HTTP拒绝并退役孤儿lease/index、旧API本地fence→全表/文件守恒→API正常关闭后独立fresh P5 successor启动。

| 轮次 | 隔离资源 | 实际结果和归因 |
| --- | --- | --- |
| run1651/execution01 | DB23651/HTTP24651/build .local/s23c | Java反斜杠运输导致编译失败，未启动隔离库；改为(char)92，原稿及诊断保留 |
| run1652/execution02 | DB23652/HTTP24652/build .local/s23d | IT1测试/1失败/0错误/0跳过，65.555秒；观察器用canonical reader读pretty证据，未到halt；认证停库exit0 |
| run1653/execution03 | DB23653/HTTP24653/build .local/s23e | IT1/1/0/0，64.646秒；已halt71、OS锁释放和HTTP按序拒绝，但IntNode/LongNode快照节点比较失败；认证停库exit0 |
| run1654/execution04 | DB23654/HTTP24654/build .local/s23f | 新隔离IT1/0/0/0，66.578秒，completed=true、exit0；认证停库exit0 |
| run1654编译 | 独立s23f构建输出 | testClasses/recoveryControlClasspath/report-rendering installDist均exit0；编译不替代业务验收 |
| consumer04及离线消费者 | 仅读取run1654原件 | Python原件消费者、mysqlbinlog、PDF渲染exit0；两页实际view_image目视通过 |

前三轮未计通过，原日志/XML、源快照和失败库全部保全，不复用数据库或覆盖原输出。execution03-observer-diagnosis及numeric-node-proof原输出证明相同JSON整数的IntNode/LongNode类型差异；仅修测试为完整canonical内容比较，路径、长度、SHA及lease/index允许变化不放宽，产品未改。三次失败根因不同，没有把同根因计数换会话清零。旧run1650、一次性旧用例与旧run03未重跑。

精确命令/参数/退出码见execution01–04-launch/exit、各execution的compile-command/integration-command、consumer04-command、binlog04-command、pdf-render04-command及stdout/stderr/XML/test-result。独立消费者不等于独立人员审查；本片自审由作者完成。

## 原件与守恒

- 正常阶段只有background_job、operation_event、spring_session、spring_session_attributes变化。崩溃拒绝阶段179表/DDL/迁移/15文件完整快照相同；源全快照/原8文件保持；第三任务PENDING、attempt0、无artifact/lease。
- 真实API/worker历史PID23604/18984，worker实际exit71、无正常关闭标记，OS lifecycle lock由持有变释放。租约/index/process原件HMAC及绑定由离线消费者核验；首HTTP为503 RECOVERY_LEASE_OUTSTANDING，其后五个503 RECOVERY_LEASE_FENCED，API直接admit及checkEffect也FENCED。HTTP三阶段分别7/16/11条，原响应正文SHA逐条绑定。
- P5扫描退役确认孤儿的lease并更新index，原process record保持，其余路径/长度/SHA不变；API正常关闭后fresh successor成功，仅新增一个process record。初设计错误假设“不退役/后继拒绝”已在执行前按生产扫描合同纠正并保留修订原稿，没有为测试改生产。
- 原XLSX7表/2明细/0公式，原顺序与精确整数克；PDF2页/2明细/0注解。两页实际Poppler渲染并view_image目视，无裁切、重叠或缺字，测试样例标识、原序号、2包/601.5kg及页码完整。closeout/pdf-visual-review04.json记录作者审查和原图SHA。
- 离线mysqlbinlog的source/restore Table_map374/408；恢复切点后source Table_map和全限定源行引用均0。按原解码字节核ASCII数据库token，不假定二进制行值为UTF8。输入/解码输出SHA见binlog-consumer04.json。

| 原件 | SHA-256 |
| --- | --- |
| XLSX | 1a3c8dc060e1033e7a3e32224e756ac00eaaa609e514be59701d491088cf68ab |
| PDF | 34be68fc8708686a747cbea53bb96d859976be7794357efbb83bea9b6ac62463 |
| final dump | e770dbc51d6e066adc65856464760a3cc7af507c7c501733b03161017e8540a3 |
| binlog | 4aa27b2d15c8c9ed5d56d883dabd118942d72df0e1205e49ba21b5d774823e3d |
| PDF页1渲染 | 1002d85829fbf505d0f015e33126969906d9f300f93a8b176f1603fa423de88c |
| PDF页2渲染 | 5662841ff87348341fd5b8a13a402250072011f7eed63ca0bc71f4b7bd0ddd21 |

## 环境、空间与开发备份

三次启库的identity-before.stdout/identity-final.stdout及命令保存认证端口/datadir/数据库/账号/UUID；run1654前后身份一致。mysql-stopped.json记录认证shutdown exit0及监听消失。收口另fresh进程/监听/ADB/Debug Keystore/C/D/E核对，见closeout/environment-before-stage及final记录；不按历史PID推定存活。系统3306/33060仅观察，未连接SQL、内部身份未认证。无APK安装、无正式签名或原库/现网连接。

删除策略此前被拦截，本片没有换通道重试。只对已结束自有s23c–s23f构建及run1652–1654/data执行无损NTFS压缩：14319文件长度/SHA前后全部相同，减少分配684134653字节，删除0。D卷观测33506729984→34219962368字节，净变713232384字节与文件分配减少分列；不累计历史释放量。compact-plan/result保存绝对路径、命令、停机/进程/监听、重建输入、逐文件前后长度/SHA/分配。首次预检误把只读采集命令文本判为消费者而拒绝，在压缩前停止；重采完整快照后执行，诊断和两脚本SHA保留。

仅8代码+本报告+片前看板叙述归档+两索引共12路径作“开发备份”；不是整体验收通过。history索引按HEAD blob加本片精确入口暂存，继承内容原字节留在工作树；看板只本地维护。40继承保护、全部dirty SHA/ZIP、Git实际回执与覆盖本片及完整run1651–1654证据根的manifest见closeout/。本机忽略原件未虚称异地备份；提交、文档与压缩不计验收。

## 未完成与接续

仅完整恢复后实际worker halt/孤儿P5有界合成组合通过，**非commit UNKNOWN、OS拒绝kill、物理断电、实际错库绑定或全部入口覆盖**。D13–D15整体、数据库证据运行准入供应、P5剩余故障、D16有效guard/复杂引用、D18实际最终候选及第8节仍未完成；不能拼接历史结果替代最终候选回归。下一有界项先核P5 commit UNKNOWN/OS拒绝kill的实际控制边界与可验证入口，确定独立新输入；同时保留实际错库供应为开发缺口，不以全拒绝代替正向准入。

总目标active、未完成。生产开关/TEMP归档、正式签名、原库/现网、main、部署/Release关闭；localDeletionAllowed=false。换机承接、多跳、接收者确认/激活链及旧run03不恢复。原资料/原身份/Keystore/原签回执/唯一证据/最终APK/继承源码保持。
