# D13 FORMAL 分块文件入口：P5 兼容修复与有界验证

## 范围与结论

输入为固定主树D:/acrylic-pda-outbound、codex/development、4d1d89c7724debf63ee356b87049d4bc75cff58a加清单保护WIP。只实跑既有FORMAL同人同机分块入口，不恢复换机、多跳、接收者确认/激活或旧run03。既有E2aSubmissionChunksIT源码并不等于尚未实现；本轮先保存报告原字节，再用新隔离输入执行。

发现实际产品兼容缺陷：P5效果门有限SELECT语法不接受SubmissionStore查当前schema的DATABASE()，真实/upload返回500。现精确支持未加引号、未限定名的零参数内建函数，仍归SOURCE_READ并在执行时检查Authority/围栏；参数、限定名、引号名称和未知调用仍拒绝。不改业务权限、生产开关、接口或迁移；版本事实见看板生成块。

修复后原IT断言不改：85项语法/效果门定向和1项真实TCP IT均零失败/错误/跳过；独立文件消费者核14 HTTP、9块、原回执验签和两笔账务。完整普通回归XML合计310套件4782项、4763通过、0失败/错误、19条件跳过，exit0。只计服务端有界L1，不计完整D13/L2/D18或第8节。作者、执行和自审为同一助手；独立工具不等于独立人员。

## 两次真实隔离输入与原失败

- run1672：MySQL19362/HTTP19462，原IT 1/1失败/0错误/0跳过，exit1；CONTROL_EFFECT_UNCLASSIFIED首发于SubmissionStore.selectForSchema的information_schema.tables查询。此前MockMvc准备已提交合成库存/订单/设备事实，不能称无产品写入的运行器失败。失败XML、HTTP、日志、dump和原签名材料保留。
- run1673：MySQL19363/HTTP19463，独立新库/新UUID，旧非空基线迁移、合成人/机/订单；不复用1672库，不连接原库或现网。RecoveryEffectSqlTest 35项、RecoverySelectSqlTest 50项、原E2aSubmissionChunksIT 1项均通过。准备阶段为MockMvc，14次受验请求为实际TCP，层级分开。
- 两轮数据库身份、命令、退出码、原XML与原始输入位于.local/e2a-cross-20260925-1672和1673；mysql-stopped.json均认证shutdown exit0。端口和活动消费者收口另作现时核对，不沿旧PID推定。

证据根为[.local/goal-20261020-resume/d13-submission-file01](../../.local/goal-20261020-resume/d13-submission-file01/)。input-manifest.json绑定源码/依赖与31项继承WIP，repair-before.zip、acl-repair-before.zip及documents-before.zip保全写前原字节；旧报告原件亦已单独归档。自审确认仅下列四份源码/测试变化，31项继承SHA全保持：

| 路径 | 受验SHA-256 |
| --- | --- |
| server/src/main/java/com/acrylic/outbound/recovery/RecoverySqlExpression.java | 92111c440e6568ac77a9391415bdf0f3ff54050a935972fd270b6704c1c1618c |
| server/src/test/java/com/acrylic/outbound/e2averify/E2aMysqlLauncher.java | 5714d3c72773dcd0e9b9036dc7a0c7a53d64e2dd6d64d0dca5507322405c19e7 |
| server/src/test/java/com/acrylic/outbound/e2averify/E2aMysqlLauncherTest.java | 3e4309f09ce221c3a74d9d8960c3eb695564bc012d984a9cfeccfa51e0390457 |
| server/src/test/java/com/acrylic/outbound/recovery/RecoverySelectSqlTest.java | 2f20e8af33dc7f94438fea492498d4515cb71e5272a78b28a2fb4fadce273c2c |

## 实际HTTP与文件消费者

原manifest 200；缺块finalize 400；9块逆序上传各200；同文块重放200；异文块409 IDEMPOTENCY_CONTENT_MISMATCH；完整finalize 200 SUCCEEDED。规范正文2140856字节，SHA-256为5937d2b737d3834ec7d85d6c6c4d4bd9690dedc997cc2e88d859f4e3ce7f288e。1102条captures、2有效scan保持原序号1/2及601500整数克。独立Python直接读14请求/响应、持久分块/完整payload、原dump及隔离轮原公钥，实际RS256验签通过；9 chunk行、1 payload行、2 dispatch、2 ledger核同。

P5原有实际准入、空根索引与两进程HMAC验证通过，应用关闭后独立后继进程STARTED。未补造原身份或可信生产材料，隔离签名不等于真实身份。原IT不覆盖分块入口并发、末SQL失败回滚、锁等待后撤权，不能用其他入口的历史结果替代；source-evidence等入口及整体组合继续未验。

## 完整回归失败、修复与复验

首轮ordinary-full01：XML4782项，1失败/0错误/19跳过，Gradle exit1；唯一失败为E2aMysqlLauncherTest的目录ACL测试，约0.5秒即失败，非5秒超时。诊断保存原命令输出：Java ProcessBuilder仅按大小写敏感键移除PSModulePath，父环境实际键为PSMODULEPATH；Windows PowerShell因此加载另一运行时的Security模块，类型资料重复而无法加载Get-Acl/Set-Acl。没有证据把它归因内核故障或此前模拟器审批阻断。

最小修复只在测试MySQL launcher的子进程environment按忽略大小写移除该键；不改全局环境、配置、ACL命令/权限标准、超时或审批规则。测试新增环境无同名键断言，原真实ACL限制及二次幂等/单条ACE断言仍执行；显式大写继承环境的acl-targeted01共3项通过。新源码依据下ordinary-full02完整重跑exit0，XML4782/0失败/0错误/19跳过；不是排除失败类后的通过。

首轮Gradle控制台4780/17跳过与XML4782/19口径不同：XML另保留两个被禁用参数化方法容器。报告以原XML逐项为准；19跳过为SKU外部向量1、独立来源外部向量17和Windows符号链接1，清单见result.json，不计通过也未为本轮启用取消链。语法85项、IT1项和完整普通集合分开计数，不相加伪称同次。IT之后只改其未调用的测试launcher环境清理及该测试；产品、原IT/HTTP助手和Python消费者输入不变，无依据不重跑成功业务。

实际命令与子进程专属D盘TEMP/TMP记录在各gradle-command.json和runner源码。完整回归为gradlew.bat -p server recoveryRekeyClasspath test --offline --no-daemon --console=plain --max-workers=1，字体通过本地r08FontFile参数提供；IT命令及85项定向见run_repaired_chunks.py。不含Android/Chrome/设备、APK构建、其他IT或最终整合候选。

## 清理、保护与交接

持续授权下仅清理1672/1673已认证停机且用途结束的datadir及两份binlog/index：694文件共455033148逻辑字节。逐文件路径/长度/SHA、原命令、依赖与重建依据先保存，复核绝对路径无reparse、现时进程不引用、四端口无监听后单一PowerShell删除。MySQL生成的pem/cnf身份原字节先复制到各private/retained-mysql-identity并逐SHA核同；原私有签名、HTTP、失败XML/log、dump、资料、继承源码及全部既有APK不清理。

D盘可用33521508352→33983148032字节，观测增加461639680；当时完整回归仍在运行，卷增量与逻辑删除字节分别记录，不将背景变化记为精准释放。没有执行tools/cleanup-rebuildable.ps1或清共享工具/缓存，旧9.44GiB不重复计入。

独立部署预检看板区原文逐字保护；本轮看板旧字节另存board-before.md，仅刷新本聊天断点与证据入口。精确提交/远端、全部WIP和收口环境见本根final-manifest.json及environment-closeout.json。环境快照首次Windows PowerShell子进程长时间高CPU且无子消费者，保留脚本/失败记录后仅终止本轮该只读采样进程；现有工具shell执行同脚本成功，根因未定，不改策略或业务入口、不计业务复验。源码与文档只沿明确路径作“开发备份”，不能标“验收通过”；忽略的原件仍在本机，不虚称Git异地保存。

| 证据（本根内） | SHA-256 |
| --- | --- |
| input-manifest.json | 0f691371a56c3c4a9a342f4a25038f020d16229abb3ad4f1371408ee77dfb925 |
| consumer-result.json | b39125e7df67f6b9b1cfb4f097e0f44d27cf4a140479a840f89fe5b300022c40 |
| self-review.json | 230e7594a0b552f15071250cda2572baeda72a5608a003e56ca4cebd4fb7b75a |
| ordinary-full01/reports.zip | 00a265af8a01885d3d54b47d1f7b634fe17fb5134e29f193b8bfd9860dc51ee2 |
| ordinary-full02/reports.zip | 8205159af897340886cba43f5a2401bf6893191922e477f60c574f3a6bafca28 |
| cleanup01/plan.json | 42bf5cb4017b0612ee4795b1222dda6aa3daaa73df0ac3262bb0ee7b84d893af |
| cleanup01/result.json | 1e69237f9e615973072eb870cd8e521dece7f1c8cb23e62231a09cbeeb4c4642 |

下一有界动作先只读核对D13其他有效文件入口的既有覆盖与实际调用，记录最小缺口及受影响验证，再选择独立任务；不重复CLI/字号/SAF或本轮成功IT。分块并发/末SQL/锁后撤权、其他文件入口、D13整体/L2、Android/Chrome/可信材料和实际最终候选仍未验。总目标active、未完成；七类生产决定/TEMP生产归档、localDeletionAllowed=false、原库/现网、正式签名、main、部署与Release持续关闭。
