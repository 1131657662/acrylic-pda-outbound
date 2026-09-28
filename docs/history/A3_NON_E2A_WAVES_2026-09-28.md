# A3剩余非e2a回归：第1批及526待定性阻断（2026-09-28）

> 后续：用户授权定性后，527原样绿、528原样红，实际锁证据将A3-BC判为产品查询侧问题并继续停报，见[定性报告](A3_526_LOCK_DIAGNOSIS_2026-09-28.md)。下文保留第1批结束时的判断，仓库锁候选已被后续取证排除。

## 1. 授权、输入和清点口径

用户授权最多3–4轮，每批12–15类，优先覆盖共享表受影响的读取/写入路径，每批独立记录、提交、推送；真bug立即停报、不自行修。输入主树D:/acrylic-pda-outbound、codex/development、83e633b，远端一致且干净；源码版本事实见[看板生成块](../ORCHESTRATION_STATUS.md)。本轮只执行回归，未改业务、测试或仓库工具源码。

清点136个非e2a IT源文件，67类在批次入口已有非全跳过绿色记录，剩69类缺少该入口绿记录。69类含R08专用入口历史已验8类、固定坐标/前代库/Android原件组合及夹具，**不是69个均可用普通空库直接运行的未知业务测试**。原首批12类结果见[上轮记录](A3_NON_E2A_REGRESSION_2026-09-28.md)，本次不重复跑。

第1批选14类：PcOutbound经PcAccounting涉及结果版本/有效行；TEMP提交/撤销、复核/提醒/续作夹具经SubmissionStore涉及submission/scope；准备/发布/订单并发涉及同步事件及共享读取；条码并发核对库存改变与同步的竞争。包含5个普通回归类和9个夹具类。后续拟按身份锁、一般类推进，**527及以后均未执行**。

执行前跨34棵工作树.local查重513–572。使用主树、现有tools/e2a-batch-it.ps1/-NonE2a和前轮run-batch.ps1，每类单独attempt/空库/账号，非零即停。实际只使用513–526；单助手、没有其他测试写者，系统3306未动。

## 2. 第1批逐类结果

| attempt | 类 | tests / failures / errors / skipped | 分类 |
| --- | --- | --- | --- |
| 513 | PcOutboundIT | 9/0/0/0 | 通过：PC完整/短装出库、幂等、占用、拒绝/续作等 |
| 514 | TemporarySubmissionContractFixtureIT | 1/0/0/0 | 通过：TEMP原正文/提交及前后ABORT回执样本产出；Android消费未跑 |
| 515 | ReviewReminderBrowserFixtureIT | 1/0/0/0 | 通过：真实待审批样本及只读分页合成行生成；浏览器未跑 |
| 516 | ReviewReminderProcessFixtureIT | 1/0/0/0 | 通过：提醒流程独立待审样本生成；提醒外部进程未验 |
| 517 | SubmissionBrowserFixtureIT | 1/0/0/0 | 通过：短装复核样本生成；浏览器未跑 |
| 518 | PcExceptionBrowserFixtureIT | 1/0/0/0 | 通过：PC异常场景样本生成；浏览器未跑 |
| 519 | PcBrowserFixtureIT | 1/0/0/0 | 通过：PC作业样本生成；浏览器未跑 |
| 520 | ContinuationBrowserFixtureIT | 1/0/0/0 | 通过：停止/续作样本生成；浏览器/模拟器未跑 |
| 521 | DispositionBrowserFixtureIT | 1/0/0/0 | 通过：实物处置样本生成；浏览器/模拟器未跑 |
| 522 | RecallBrowserFixtureIT | 2/0/0/0 | 通过：召回样本生成；浏览器/模拟器未跑 |
| 523 | PdaPreparationIT | 8/0/0/0 | 通过：原授权准备包、分片摘要/确认及失效/设备隔离 |
| 524 | PublicationConcurrencyIT | 2/0/0/0 | 通过：发布并发回归 |
| 525 | OrderConcurrencyIT | 4/0/0/0 | 通过：订单并发回归 |
| 526 | BarcodeSyncConcurrencyIT | 4/1/0/0 | **失败待定性**：未提交库存变更用例commit timeout；其余3项绿 |

本批累计14类37项：**13类全绿、1类红；36通过/1失败/0error/0skip**。真实独立MySQL、Spring/MockMvc及真实事务；非外部真HTTP，不替代浏览器/Android/真实身份组合验收。每个XML、测试源码SHA-256、退出码与端口释放均已核对；源码没有在测试期间变化。非e2a无one-shot marker不等于未执行。

526结束后runner退出1，未启动其他类或其他批。批次工具自动标签“D real bug (manual triage)”只是兜底分类，**不等于人工已确认产品bug**。

## 3. 526只读初步定位：并发期望冲突，归属尚待定性

失败用例[BarcodeSyncConcurrencyIT](../../server/src/test/java/com/acrylic/outbound/inventory/BarcodeSyncConcurrencyIT.java)第31–48行：后台READ COMMITTED事务直接修改库存并调用BarcodeChanges，通知ready后等待commit信号；主线程在释放commit前要求一次同步立即返回空事件，然后提交，再验证新事件。

当次可确认的证据：

1. XML用例耗时15.692秒，异常落在第42行pending.get；其原因是后台第36行等待commit信号15秒后主动抛commit timeout，导致事务回滚。不是MySQL启动/迁移失败，也不是普通断言数值不符。
2. 原XML中的第二次基线响应时间05:25:01.904547600Z，增量空事件响应05:25:16.912499100Z，相隔约15.008秒。增量响应仍为OK，events为空、highWatermark为2；随后pending.get取回后台异常。因此“提交后新事件及连续序号”断言**未抵达**。
3. [BarcodeSyncService.barcodes](../../server/src/main/java/com/acrylic/outbound/sync/BarcodeSyncService.java)先调用PdaAccess.barcodes，再进入BarcodeStreamStore.publish。后者确实保留FOR UPDATE SKIP LOCKED，但不能据此推定整个HTTP/MockMvc调用都不等待。
4. [PdaAccess.barcodes](../../server/src/main/java/com/acrylic/outbound/pda/PdaAccess.java)调用guard.lock及PdaAuthLocks.principal；[PdaAuthLocks.device](../../server/src/main/java/com/acrylic/outbound/pda/PdaAuthLocks.java)经WarehouseBaseline.read(...,true)取得仓库FOR UPDATE，还包含人员/设备/会话锁。
5. 测试写者直接调用[BarcodeChanges.mark](../../server/src/main/java/com/acrylic/outbound/sync/BarcodeChanges.java)更新带warehouse/bale外键的pending行；可能持有父行FK共享锁。**前置鉴权锁与该写者的锁集合冲突是候选原因，尚无当次data_lock_waits或线程栈证明具体等待对象。** 不把静态候选写成已抓到仓库锁等待。

既有[锁图](../parallel/R06_M2B/LOCK_GRAPH.md)明确保留粗粒度仓库互斥，且夹具直接库存DML不等同真实业务入口的完整锁前缀。因此还需区分：产品同步不应阻塞却被前置锁阻塞，或既有测试对新鉴权串行语义的“不等待”期望已经过时。没有证据支持数据泄漏、重复发布或错误放行，也不能简单归为机器慢/随机flake。条码发布及该测试的历史提交为7d50af0；未证明由本次关注的共享迁移或JWT修复引入。

**本次结论：新增红色并发阻断，产品/测试归属未定，不宣称已确认真bug。** 不能排除真实回归，按真bug停报边界暂停后续批次，未改业务/测试/超时阈值、未重跑。下一步建议单独完成定性：在原15秒窗口采集两个连接的data_lock_waits/调用栈，比较原夹具与既有真实业务写者的锁前缀，按设计意图区分修产品还是修测试；不直接移除鉴权锁或增大等待时间。

第四个用例注入read failure并期待500，本身通过；该预期ERROR日志不算第二个新bug。

## 4. 证据、环境及未执行准备

- .local/a3-waves-20260928/：输入清点/分类、跨树分配检查、batch1-manifest.json（源码哈希）、batch1-results.json（XML路径/哈希）、batch1-console.log及只含响应时间/计数的526-safe-response-timing.json。完整原日志可含测试凭据，仅留gitignored本机。
- .local/it-batches/下start513至start526各自REPORT.md、logs与XML；526完整路径为batch-20260928-132429-start526/xml/run-526-BarcodeSyncConcurrencyIT/。对应.local/e2a-cross-20260925-513至526隔离库/日志/storage保留。
- batch1-artifacts/保存13份生成夹具/准备包文件及哈希，避免之后build覆盖；只证明当次生成，不证明浏览器/Android消费。
- 每个普通harness收尾仍出现历史已知cleanup空变量警告（tools/e2a-provision-and-run-106.ps1:273），IT退出码和端口释放独立确认。未修工具、未执行额外清理；失败库/日志和旧红均保留。
- 身份锁组拟用同一批次入口配合本机identity-constructor.ps1，在新attempt实例上提供既有守卫要求的库名/账号；草稿及run-wave.ps1只做语法检查，**未执行、未建身份锁专用库，不是已验证基础设施**。第2/3批manifest仅计划，不得视为号已使用。

主树仅本批文档改动，旧验证树D:/acrylic-pda-worktrees/r06-e2a-migration仍detached ea4d166，97条dirty/untracked前后逐条一致；没有同步、重置或提交旧树。所有测试进程结束，19321/19330/19434/23308空闲，系统3306未动。实际attempt止于526，527+重新执行前再查重；未启动R09。

## 5. 剩余与交回决策

完整逐类清单见[A3剩余目录](A3_NON_E2A_REMAINING_2026-09-28.md)：本轮输入69类中14类已执行、55类本轮未跑；55类含R08专用入口历史已验8类，其余47类仍在该批次清点范围。另有526这一类红色待定性，不能从待办中删去。数字只描述本次批次证据清点，不声称其他历史入口完全没跑过。

本轮未发现已定性的产品真bug；526是必须先定性的阻断。第2–4批未启动，没有为了完成轮次跳过失败或补填绿色。请用户决定先定性526并接续A3，或转其他方向；R09 C2仅范围已登记、实施仍未启动。所有生产开关、契约发布边界和ConfirmedV1Adapter保持。
