# E01 故障撤回与独立重发：设计和验收记录

输入树 D:/acrylic-pda-outbound，codex/development，HEAD 8ecafa9de79c701009751efb2b7bccc434491427；版本事实见看板。唯一写者为本聊天，继承源码与失败证据不删除。范围为D06/D07及D10关联入口，不恢复已取消的进度承接。

## 自主决定与退出标准

沿既有change-requests入口增加FAULT_WITHDRAWAL，能力默认关闭、隔离显式启用。无需原机STOP；原订单、原领取、原正文和回执保留。撤回与上传/审批采用相同scope→order锁序，成功终态拒绝。无包占用时直接撤回；有服务端未完成占用时，另须order:dispose权限、明确卸货/实物核验及凭据，逐包保存释放前事实和签名证明后释放，禁止凭撤回权限无证据清占用。恢复、隔离、续作及外来源占用拒绝并保全。

选用新计划修订、同业务订单和新领取资格，保留原发布历史；新设备本轮有效数量为零，不接收旧输入。提交scope只推进轮次，不迁移旧前缀；原扫码序号按本次计划修订分隔防重，全设备输入序号仍唯一。与删除旧事实、直接重用旧发布/轮次两种方案相比，此方案保持不可变历史且能验证新机从头作业。客户端使用新签名资格的轮次；旧资格不能写，新成功只记账一次。

验收分别覆盖：无STOP撤回；已收正文/占用的有权实物释放及无证据拒绝；原键/异文；撤回与旧提交/确认竞争；新修订发布与新机空起点；旧下载/续期/迟到提交；原回执只读；重发及领取幂等；末SQL回滚。MockMvc/MySQL、真实HTTP、Web操作、Android实际采集/冻结/验签和重启消费分别记证，未跑不记通过。

## 已收集证据

`.local/goal-20261011/e01-mysql-1344/`：7项MockMvc/真实MySQL定向通过、零失败/错误/跳过；source-before/after一致。这仅证明无占用最小撤回，不证明整链。运行器身份检查未选择数据库且未规范化Windows路径，保全停机首次失败；修正后明确选库/用户/端口/绝对datadir核验、dump及哈希、认证shutdown退出0且监听空。原日志/XML/datadir保持。

新增占用释放、重新发布、HTTP/Web/Android及组合证据尚待实施/执行；D06/D07/D10未达到全部退出标准，总目标保持active。

扩展定向输入`.local/goal-20261011/e01-mysql-1345/`共10项，1项失败：`INVALID_SUBMISSION`“作业轮次或恢复代次无效，续作须专用票据”。这是旧Control解析限制与新重发无前缀高轮冲突，非环境故障，连续计数1。新增锁内核验高轮与签名资格后，1346同10项零失败/错误/跳过、源码前后哈希一致；新修订/新机合成正文成功、已有占用有凭据释放、保留原输入/序号、重复成功不重复记账和尾写回滚通过。两库均认证dump/停机0且监听空，1345原日志/XML/库保留。未冒充真实Android/HTTP链。

Web撤回产品入口及按人/仓/单持久原键、原正文、恢复代次已编码；回执持久后才通知页面更新，CSRF等待后重新核对身份。页面沿订单异常页，成功撤回后沿原发布入口操作。Web类型检查退出0，浏览器及文件存储故障/跨页未验，发布弹窗的跨重启原键仍须补。Android冻结/停止正文改为消费签名资格轮次及校验；尚无当次Android测试/构建。

全部脏/未跟踪原字节和环境快照在`.local/goal-20261011/e01-checkpoint-01/`；看板前件为[归档](ORCHESTRATION_STATE_before_goal_e01_2026-10-11.md)。尚未提交，下一动作继续E01真实HTTP/并发/重启/浏览器/Android和受影响回归，不恢复旧换机接收链。

## 后续实际验证与自审

以下为同树后续输入；详细源码前后哈希、命令、退出码及原XML均在各证据目录，旧结果仅对未受影响输入适用，不拼为最终候选通过。

| 证据目录（`.local/goal-20261011/`） | 实际结果与适用范围 |
| --- | --- |
| e01-mysql-1349 | 真实HTTP3项通过：原键重放、重发、新机成功；撤回对旧提交及对审批竞争仅一个有效结果 |
| e01-service-build-01 | bootJar/test退出0；普通服务4292项零失败错误、1跳过（Windows符号链接反例），不包括IT；XML及test-counts已收 |
| e01-consumers-1353 | 新种子实际1项；Android定向19项无失败错误跳过；APK/lint及Web类型/构建通过；浏览器2项通过；模拟器3阶段通过；源码前后哈希一致 |
| e01-mysql-1356 | 可靠性真实HTTP4项与默认关闭1项均通过；并发重发/领取、末审计和末outbox故障完整回滚，关闭不撤原资格 |
| e01-mysql-1357、1358 | 非空前驱升级各1项通过；原扫码字段和其他表守恒、修订回填、Flyway validate；1358为测试函数拆分后复验 |
| e01-service-restart-01 | 重启保留的1353隔离MySQL与新Java进程；真实HTTP原撤回/发布键重放，16个业务表CHECKSUM一致；SQL核Android原摘要与2条dispatch |
| e01-android-final-01 | RecallRepository事务拆分及新增重启测试后产品debug/测试APK与lint构建退出0 |
| e01-service-restart-02 | 再次重启原成功隔离库及Java；新产品进程retainedWithdrawalReplaysIntoStoppedStateAfterNewProductProcess实际1项通过；原Web命令重放/业务守恒通过；认证shutdown0且监听空 |

1353模拟器实际使用Android Keystore独立登记、登录、领取、下载，产品扫描引擎扫描两包、冻结、上传及验签成功，状态SYNCED。force-stop后新进程保留冻结正文/摘要/成功回执。另一个未完成领取消费后台WITHDRAWN签名后持久RECALLED、旧冻结拒绝，prepared原资料保留。全部为合成隔离身份；未使用实体22D2910042、未冒充原坏机事实或独立人员审查。

自审修复：RecallRepository撤回消费先写work().recall()再写recalled()，防止只存签名未冻结本机作业；Web恢复原请求时显示持久原因并锁定原正文；资格workRound纳入契约、签名核验和冻结。Android末次构建APK哈希与目录登记见本机证据，未发布/正式签名。

新增决定：已有REQUESTED召回但坏机无STOP、无恢复包、无续作时，允许后台故障撤回复用原changeRequestId；保留原reason/requested_by/created_at，追加resolution与操作人/时间，不能编造STOP。已有STOP/恢复/续作仍拒绝并沿原受控处置。相比拒绝所有原申请，此方案覆盖真实坏机等待状态且保全原事实。实现为RecallFaultState及Web入口，新增真实HTTP守恒/旧上传拒绝/重发成功测试；1359正在运行，尚不记通过。

## 失败证据及运行器修复

全部原日志/XML/失败库/datadir/dump/binlog保留。1347比较动态HTTP envelope字节失败，改为业务data；1348 manifest解析输入错误导致正文大小拒绝，修复后1349通过。1350 PowerShell5.1把正常JVM stderr警告当终止异常，种子实际1项通过但运行器失败；停机身份输出漏--raw导致路径转义误判，手工复核补XML/dump/认证shutdown0。1351种子UP-TO-DATE导致新库无输入，主动终止当次Android构建避免无效执行，dump/停机完成，不计功能通过。1352 Android19项和Web构建实际通过，但Vite stderr警告再次触发运行器异常，客户端未执行；Run-Gradle/Run-Node隔离stderr收集后1353完整运行。

1354通用count错误假定offline_authorization有order_id，改为JOIN assignment；1355多筛选的竖线被cmd解释为管道、零项退出255，改为重复--tests后1356通过。重启01业务验证、dump/停机均成功但最终监听收集器缺参数空格报错，原before/after哈希相等、端口空核实后补结果并保留收集异常。没有把运行器失败记功能通过；各新假设分别验证，无继承失败计数清零。

完整102份脏与未跟踪原字节/哈希、diff、远端/进程/监听/磁盘在e01-checkpoint-03；看板前件[重启前归档](ORCHESTRATION_STATE_before_goal_e01_restart_2026-10-11.md)。唯一活动项目写者仍为本聊天，模拟器运行，共享3306/ADB保持，其他项目聊天idle/notLoaded。新增产物在D盘，无既有证据清理。

## 无STOP原申请修复的收口

1359编译阶段失败、零项执行：新增测试把字符串orderId直接传给Ids.bytes；修复为UUID解析。1360实际19项、1失败：Java Map比较requested_by的byte[]使用引用相等，两次字节完全一致仍失败；改SQL返回BIN_TO_UUID后1361同19项零失败/错误/跳过，source-before/after一致、dump/认证shutdown0、监听空。上述都是测试夹具根因，原失败库/XML/log保留，不记功能通过。

e01-web-final-1362采用新一次性输入：种子1项实际执行，最终服务bootJar/普通test4292项零失败/错误、1个Windows符号链接跳过；Web类型/构建退出0，浏览器2项零失败/跳过。浏览器先真实申请停止装车使订单RECALL_PENDING，再无STOP故障撤回；丢HTTP响应、两次页面重载恢复原命令/原键/原因，重发进入READY；存储写入故障反例无撤回POST。源码前后哈希一致，原XML/JSON/log及dump保留，认证shutdown0、监听空。普通test不等于专项IT。E01 Android源码未再变化，沿android-final-01与service-restart-02的相同输入复用；期间独立E02源码不在本次服务/Web输入哈希或本次提交范围。

最终E01 debug APK保留在`.local/goal-20261011/e01-delivery-01/acrylic-fault-restart-debug.apk`，SHA256登记于同目录apk-sha256.json；未发布、无正式签名。作者自审范围为锁序/成功终态/旧资格、原申请和原正文守恒、重发轮次、占用释放、默认关闭及页面原请求恢复；工具消费者是隔离HTTP/Web/Android，不称独立人员审查。

本范围拟明确路径提交推送开发分支；继承的取消换机代码/测试/工具与历史改动继续保留，不混入提交。D01盘点可用，D06/D07已补本次后台故障撤回/独立重发产品链的隔离证据，D10仅关联消费者范围；D04/P04、D05/P05、D08/P06、D09及D11–D18和O/U仍有开发缺口，需最终组合继续。总目标active，下一动作为E02旧包调整Room/回滚/页面验证及高频页面，随后E03文件导出和电脑核验，不转现场清单。
