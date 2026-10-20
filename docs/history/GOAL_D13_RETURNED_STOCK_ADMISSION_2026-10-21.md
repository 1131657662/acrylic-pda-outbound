# D13 返库库存投影恢复 admitted TCP / P5 有界验证

## 范围与输入

- 输入树D:/acrylic-pda-outbound、codex/development，HEAD/origin/ls-remote为4945630f98eb20edd9d4f4fa023c948f068836ac；main为c6e9e0c238c657baecbf699ba6fdbada5d979b86，未动。版本只见[看板生成事实块](../ORCHESTRATION_STATUS.md)。
- 本片沿[既有返库政策及实现](GOAL_E04_P06_RETURNED_STOCK_2026-10-13.md)，只补RECONCILE_RETURN_DISCOVER/SUBMIT/APPROVE的恢复准入真实TCP/P5消费者；原退货、贡献冲正、周期重开和实际附件由既有合成fixture在准入前准备，不声称原身份/现场实物。
- 证据根.local/goal-20261021-resume/d13-returned-stock-http01；input-and-exit.json/wip-and-fixed-before.zip/tracked.patch保存36输入脏路径和固定源码，35非看板继承逐SHA保持。旧文档在docs-before-closeout.zip与board-before-*.md；run1938-product-before-fix.zip保留原产品字节。
- 本聊天独占源码/共享文档/HEAD，无子代理，另一项目聊天实查idle；其test/M71授权不套用，不修改设备/APK/test或继承部署报告。

## 改动与自审

- 产品仅ReturnedStockFacts：单列JSON使用String.class；原CREATE/APPROVE回执使用已存在CorrectionStore.receiptRow相同bytes/string/timestamp映射，继续逐字段验签/命令摘要/原行/效果/重开周期绑定。
- 原SQL、JOIN、唯一行检查、锁序、原签字节、权限与业务政策不变；SQL/JDBC分类器、效果门、getObject/LOB禁令、契约/迁移/生产配置未改。
- 新ReturnedStockAdmittedInput、E2aAdmittedReturnedStockIT、run_d13_returned_stock.py。测试通过真实Cookie/CSRF/TCP命令、全量原表/文件快照及P5独立后继；同JVM应用上下文重启不称独立服务部署。
- 自审按security-practices、testing-best-practices、single-responsibility和sql-joins，source-review.json保留理由；执行/离线消费者为同助手，不称独立人员。

## 实际运行与失败

| run | 命令与结果 |
| --- | --- |
| 1938 | C:/Python314/python.exe -B docs/parallel/R09/tests/run_d13_returned_stock.py --run 1938 --port 19368 --http 19434；IT1失败，真实返库context GET500，ReturnedStockFacts.read泛型queryForList触发getObject，CONTROL_EFFECT_UNCLASSIFIED。178非会话表/6文件守恒，门正确。 |
| 1939 | 同运行器 --run 1939 --port 19369 --http 19434；上述单文件修复后IT1/0失败/0错误/0跳过、Gradle/运行器exit0，认证dump/停机成功。 |
| 1940 | C:/Python314/python.exe -B .local/goal-20261021-resume/d13-returned-stock-http01/run-original-returned-regression.py --run 1940 --port 19370 --http 19434；原E2aReturnedStockIT受影响回归IT1/0/0/0、exit0、认证停机。 |

- run1938原HTTP/XML/日志/dump、失败摘要及修改前产品字节保留；不以新attempt清零失败。围栏未放宽，不把预期末SQL500改记成功HTTP。

## 真实原件与守恒

- run1939共51 TCP，20业务POST：9×200、3×403、7×409、1预期500；三个context GET200只读。9份原JWS经JCA/Python cryptography验真、五能力全false。
- 原发现/提交为同maker，审批为独立checker；三命令原响应/同键/同JVM上下文重启重放各3份字节全同、异文409。末operation_event注入42000回滚178非会话表/6文件，移除后同原键成功；context/权限/摘要/末SQL/原键/重启共10组全量守恒。
- 本次只将stock DEDUCTED→NOT_DEDUCTED、version2→3；cycle2、blocked=null、upstream1与原重量保持。原结果version2/数量1/重量301000g、既有REVERSAL(+1/+300500g)及原贡献账不变，不再次冲正、不再开周期。
- 原submission/payload/dispatch/inventory账/结果/占用/effect/correction账/cycle_reopening/document/line/attachment全行保持；原CREATE/APPROVE签字原行也在最终全表中逐行保持。唯一returned_stock_release逐ID绑定原dispatch/重开周期/本次案件及审批。
- 原CREATE/APPROVE两份JWS另经离线验真，原申请/审批人分离、文档摘要/状态版本/原行/重开1→2及release引用原周期核同。原实际附件bytes/大小/SHA/原人/仓/案件绑定核同；不以仅摘要声明代替。
- P5 leases排空、独立后继stdout STARTED；originals-consumer核5925输入和35继承全同。原退货签字/回执SHA见original-correction-consumer.json。
- run1940原业务覆盖原附件读取、独立审批/并发原键、异文/撤权/边界与末SQL、释放后禁止撤销原退货；新单/新资格/下载/ACK/周期2提交成功扣账、周期1拒绝且无新增dispatch/账。新成功JWS另验真并绑定原冻结正文/ID；旧周期REVIEW_REQUIRED/DISPATCH_CYCLE_CONFLICT、mayResumeScanning/stockReleased均false。它是合成HTTP/设备fixture，不称Android扫码或admitted再出库。

## 原件哈希与保管

- run1939 inputs SHA a8304d558370ea191639af47badf7ba0cd08d90f58645e7d40a6bb19c66f4bd2；XML SHA 901ad111f2029d7fbb31c907c4d0f486dc9a91afd00aee7f21a9bd2c62b31309。
- run1940 XML SHA 3c99c05293dce93bd35283b367611ca3e0a22f16f45161952752cf6681ec7442；全部输入/命令/退出码在original-regression，逐SHA与新周期回执消费者保持。
- 三命令原body SHA：发现c7b47c0909b5f2772b87cd1e509e1f138cf0d519be0eedf6c4c6a788aee9a07c、提交83bd545a7b60893a2f40e9e555d232fc59256b40d23b5c796962ed412b167b2c、审批c35f447e1ab3c7ecb29750d06b22c04a0cd3f108eb29d888a72eb04f47d8c7f2。
- 原运行根.local/e2a-cross-20260925-1938至1940；本机原件/私有签名不随Git上传，不虚称异地备份。最终manifest/prestage/postcommit/Git回执见证据根。

## 清理与边界

- 三隔离库认证dump/shutdown exit0、进程退出，19368–19370/19434实查无监听。只清data/binlog9目标1041文件、682444380逻辑字节，卷释放695848960字节（约0.65GiB），D余量30603022336→31298871296；cleanup-plan/execution保留逐SHA/输入/命令/身份/dump/依赖锁，auto.cnf与reconstruction-inputs.zip另存。
- 全部原HTTP/JWS/XML/日志/附件/数据库快照/P5/dump/原资料/身份/Keystore/APK和继承保留；开发产物清理不计D/O/U进展，不改变删除资格。
- 系统3306身份未知、不连接或停止；共享Gradle保留，ADB只枚举M71、不操作设备/模拟器，两Keystore SHA不变。C/D/E和实际进程监听见环境快照，普通回归结束后再核最终无重型任务。
- 未验其他隔离解除来源/完整对账admitted链、Android/Web完整消费者、非合成原身份/现场实物、完整恢复、独立API-worker部署、L2/D18/实际最终候选与第8节。总目标active、未完成。
- 不恢复取消的换机承接/多跳/接收者确认激活，不启动旧run03；原库/现网、正式签名、main、部署/Release关闭，生产七类决定/TEMP归档fail-closed、localDeletionAllowed=false。

## 普通回归断点

普通命令 ./gradlew.bat -p server test --offline --no-daemon --console=plain 退出0：310 suites/4804项，4785通过、0失败/错误、19跳过。1项缺SKU_PARITY_OUTPUT；9项缺E06_CLOSURE_ARCHIVE；8项缺E06_REVIEW_ORIGINALS；1项Windows符号链接权限不足。逐项列于ordinary/result.json，skip未计通过。\n\n310份当次XML晚于开始输入时间并逐SHA保存在ordinary/xml；5925输入中只有运行期间更新的看板及两短索引改变，原文先归档；源码/测试/配置/依赖锁无漂移。完整计数与XML SHA见ordinary/result.json。
