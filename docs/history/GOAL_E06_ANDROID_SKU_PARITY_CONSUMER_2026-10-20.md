# E06 Android 消费实际服务端 SKU 向量（2026-10-20）

## 结论与输入

在 codex/development / 31cc9921d7ca713caa4894db6daf68f4fda19402 加保留 WIP 上，既有 Android IndependentSkuParityTest 实际执行：**1 test / 0 failures / 0 errors / 0 skipped，Gradle exit 0**。逐一消费同一份服务端输出的47组原字节，比较所有预检/行/字段、错误码与消息、顺序及整数克；日志47个案例名称与向量逐项同序。

本轮不改源码、测试、契约、迁移、生产开关或签名配置。1024份 Android/构建源输入、10份既有 fixture、31条非看板继承路径与原向量 SHA 全部保持。另会话新增预检报告/共享索引改动单列保护，不代提交；输入展开脏路径36条，与Git折叠计数分别记录。

向量来源见 [封存消费者报告](GOAL_E06_CURRENT_SEALED_CONSUMERS_2026-10-20.md)。本轮没有重新生成或改写历史输出：

- 输入：.local/goal-20261020-resume/e06-current-sealed-consumers01/sku-parity-current.json，10453671 bytes。
- SHA-256：641d2a444b9332999fc44bc68b35b29e1f46149870795aa9b07dfe1838191a2d。
- GB18030、BOM/换行/空行、列字节边界、重量/条码错误、重复/冲突、大小/行数及原样本等案例清单在本轮 case-coverage01.txt；47组不是47个JUnit测试。

## 实际执行与缓存隔离

证据根：.local/goal-20261020-resume/android-sku-parity-consumer01/。

    gradlew.bat -p android :app:testDebugUnitTest
      --tests com.acrylic.outbound.pda.independent.IndependentSkuParityTest
      --offline --no-daemon --console=plain --max-workers=1 --no-build-cache
      -I .local/goal-20261020-resume/android-sku-parity-consumer01/parity-init.gradle
      -Pkotlin.compiler.execution.strategy=in-process

SKU_PARITY_OUTPUT指向上述向量；TEMP/TMP/Java tmp仅对子进程指向本轮D盘目录。确切环境和绝对路径见 command01.json。仓库构建未登记这个环境变量为测试输入，所以本轮init明确登记向量文件，禁用目标up-to-date/缓存并使用专有XML/HTML/binary输出目录；没有修改产品构建文件或全局环境。

目标输出PARITY_ACTUAL_EXECUTION，XML时间为2026-10-20T07:50:35（原XML时间，不补造时区）。32个actionable任务中目标1个实际执行、31个编译前置up-to-date；目标没有UP-TO-DATE/FROM-CACHE/SKIPPED。旧报告预先封存，专有输出不覆盖旧XML/HTML；未调用APK打包或设备业务。

最初内联保全命令的Python语法错误发生在解释执行之前；修正后完成快照。启动前发现另一会话新启动独立树Gradle，守卫阻止本轮测试；其进程实际退出后才运行。准备/核验脚本语法错误不计产品失败，也未重跑Android；本轮只有一次有效测试。

## 证据与哈希

| 文件 | SHA-256 |
| --- | --- |
| input-manifest.json | da4f9d666b051ab353552da6e90f0744eac4417ec797713ced953c4ae8008969 |
| parity-init.gradle | dfb27f467a7e6b694010e930f2b825d407f9e8791fb5cb70242e65e36c0314d9 |
| gradle01.log | 1b94eeb518e80affc1479cdf8a12bea665248cb88fca244883bf12fafb884eca |
| xml01/TEST-com.acrylic.outbound.pda.independent.IndependentSkuParityTest.xml | bc81183d119734401a9868c0ee603ed3dfe661bc2b9dbb1f2fc8ec937cc2a7a6 |

逐案例与守恒结果见 test-result01.json；全WIP原字节见 dirty-before-bytes.zip 与 tracked-before.patch。环境、全状态、输出清单和实际Git回执见 closeout.md、final-manifest.json。本机忽略产物没有因提交报告而获得远端备份。

## 限制与下一步

作者、执行和自审均本助手；产品Android解析器为实际消费者，非独立人员审查。仅主机Robolectric解析一致性，不证明Room事务、设备、资料激活/扫码/导出/主管对账、HTTP/MySQL、O04/E06整体、D18或第8节通过。历史普通回归的19条skip不变，不与本轮拼成统一候选零skip。

后续消费者仍须确保外部输入触发实际执行。继续按证据矩阵选择独立缺口；O02 CANTOPEN无新依据不重跑，已验字号/SAF及已取消链不重做。原库/现网、正式签名、main、部署、Release和生产决定继续关闭，localDeletionAllowed=false。治理、开发备份和清理不计验收；总目标active、未完成。
