# 已收正文TEMP实际时间截点与原binlog恢复

本批是D07/R09跨截点恢复子项，替代先前单表缺失样本的时间截点局限，不计D07/R09或D01–D18整体完成。版本事实见[看板生成块](../ORCHESTRATION_STATUS.md)。树D:/acrylic-pda-outbound，输入分支codex/development、HEAD/实际远端0657248e114e41e2aa71423ec749b7a6dfd4cd0b；未合main、发布部署或正式签名。

## 退出标准、实现与自审

实际开发来源完成准备/ACK后关闭应用，保存注册根、原准备及五gzip。迁移由root执行，约束应用账号仅DML、锁定root，FTWRL采集权限begin；Android扫码前生成无业务写入的dump截点，核dump期间binlog位置不变。随后真实HTTP应用/Android Room执行冻结、正文上传、完整E2A与ABORT，关闭连接再采集权限end与原binlog。以实际origin/payload/result/occupancy/permission验证严格初轮来源；新空目标导入checkpoint并按原位置回放原日志，逐表/文件/DDL/触发器/迁移核对最终来源。

RecoveryBinlogTransactions明确增加已收正文链实际涉及业务表，权限表、未知表、DDL、非ROW、坏CRC、越界及未闭事务继续拒绝。Android测试保全原OPEN/PAGE/STOP/RAW请求，保存时间使用实际执行时刻。测试目标自审拆清导入、触发器、截点与最终事实职责，所有pin及权限连续性验证先于任何SQL导入。新反例只损坏复制材料，拒绝前后空目标表清单和导入输出守恒。

这仍是测试运行器执行原mysqlbinlog恢复；未增加生产Bean/HTTP或恢复准入。独立pin/control权威仍开发合成，准备为MockMvc、Android为Robolectric，非原PDA/可信异地保管或现场证明。原正式身份、C档及旧BCD/原资料限制保持。

## 当次证据

| 范围 | 实际结果与入口 |
| --- | --- |
| 业务表名单正向/权限未知表拒绝及旧连续性 | received-permission-test-01：90/0/0/0、3XML、exit0/sourceUnchanged |
| 实际来源1233 | server专项1/0/0/0、Android XML1通过、exit0/sourceUnchanged；严格InitialSource/登记/历史权限/E2A组合通过，NORMAL只读全表/file/schema守恒 |
| 原目标1235 | 专项1/0/0/0、exit0/sourceUnchanged；实际导入/回放原日志及全量事实守恒 |
| 最终目标1237 | 专项1/0/0/0、exit0/sourceUnchanged；坏dump pin、重pin坏CRC、权限边界变化均写前拒绝，再完整正向重建 |
| 独立Node消费者 | received-checkpoint-consumer-01为24输入hash不变/exit0；02为65输入hash不变/exit0，追加来源manifest/准备/占用pin、两端全部实际私有文件字节及反例证据核验；原RSA准备/ACK/终态、设备ECDSA STOP、权限窗口及binlog framing/CRC/table-map通过 |
| 受影响普通回归 | received-checkpoint-regression-01：3265/0/0/0、108XML、exit0/sourceUnchanged；普通test排除IT，非完整server验收 |

作者实现/自审、独立工具消费者、MySQL/HTTP/Android组合分别计证，独立工具不等于独立人员。没有重跑1150/1151/UI-06、1100–1102。未新增APK、模拟器或真机验收。

Gradle命令/输入hash/日志/XML/result在`.local/d01-d18-20261008/r09-temporary-evidence-restore-mysql-<编号>/`和上述普通回归目录；分配、runner/provision/hash/dump/retention在`.local/d01-d18-20261010/temporary-evidence-restore-mysql-<编号>/`。原数据库/datadir/binlog/HTTP/Android/文件在`.local/e2a-cross-20260925-<编号>/`，1233外部原件在`.local/received-checkpoint-originals-1233/`。设计及独立消费者脚本/副本/输入清单在`.local/d01-d18-20261010/`。

## 失败保全

- compile-01未完成目标检查类，编译失败、0测试；compile-02通过。1223跨树分配守卫被既有随机目录子串命中，未建库。
- 1225占位audit水位0拒绝；改为1，占位只用于扫码前准备origin读取，不冒充最终来源。
- 1226触发器definer为应用账号，撤DDL权后上传缺TRIGGER权；新来源root迁移，应用保持DML-only。
- 1228 Android/HTTP通过，业务表名单缺项拒绝；主包名单修复并补定向反例。
- 1229 RAW采集遗漏call路径，原page页码位于pageCore；补原RAW请求及正确页码读取。
- 1231保存时间取旧ACK早于权限begin；改实际执行时间。
- 1232严格初轮来源通过，注册根在声明恢复集合内被拒；新来源外部原件目录。
- 1234全表导入/触发器一致，SHOW CREATE冗余CHARACTER SET导致文本不同；完整原DDL继续保全，仅正规化明确utf8mb4/utf8mb4_0900_as_cs组合。原146表对比在ddl-comparison.json，无其他差异。
- 1236反例快照误依赖空库尚无Flyway表，专项失败；修为空目标information_schema表清单/导入输出验证。1237新库复验通过。

以上失败库均dump成功、认证停机、原PID退出/监听空，未复用、未清理，原日志/XML保留。未杀项目外进程。

## Git、继承保护及下一接缝

received-trusted-resume-01保全30条dirty、前final逐hash无漂移；received-checkpoint-resume-02保全39条dirty及全部进程/监听/磁盘，69历史比较/22原STOP保护无漂移。最初final保护误用PowerShell简写把actual当字面量，纠正scriptblock后零漂移，原误报与纠正结果都保留。范围暂存/commit/push及最终实际Git、全部dirty副本/hash/diff见received-checkpoint-precommit-01/postcommit-01/final-01；继承改动不混入本批。

新增产物和TEMP/Java/cache位于D盘，未清理任何旧证据；既有3306/33060/13316/ADB不操作。所有一次性库跨33树分配；1233/1235/1237原dump哈希及认证停机在retention，19330/19434空。本机证据不代表异地备份；最终C/D精确余量见final-01/disks.json。

下一步用1233实际原件与新空目标重建事实，新增已收正文的closed归档/native消费接缝，保留原验证行/owner/文件，验幂等、篡改、消费者/尾提交回滚。HandoverCommandService需自持外层事务，不能直接嵌套closed回调。随后继续主管故障围栏→接收准备/激活→最终提交，再D08–D18；D04/D05政策缺口保持拒绝。

生产默认/签名及七类生产能力关闭，localDeletionAllowed=false，不接原库/现网，不合main，不发布部署/标签/Release，不正式签名。
