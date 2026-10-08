# D06 LOCAL独立冻结原件与前置拒绝

本记录只验LOCAL-only及相邻拒绝范围，D06整体和D07–D18、第8节闸门仍未完成。版本仅见[看板事实块](../ORCHESTRATION_STATUS.md)。主树D:/acrylic-pda-outbound、分支codex/development，验证输入为HEAD f6d392f1c2f790caed3ddd4cd92694c23ec5a46b加本批dirty；完整副本/hash/diff、进程/监听/磁盘在`.local/d01-d18-20261008/d06-local-matrix-03/`。19条继承路径逐hash保护，原stop-close三项hash一致。

## 实现与边界

真实链为A实际受理/取消/E2A→独立主管交接B→B本人准备/ACK/下载/确认/激活/扫码/冻结→仅注册RECEIVING分块清单→本人LOCAL原件/E2A/专用STOP→独立主管交接C→C实际最终提交、确认记账及重启消费。

pending轮的scope/slot current仍为空、phase CONTINUING；E2A仅另许真实已激活、不可变登记、原canonical、本人身份及固定RECEIVING清单的证据运输。LOCAL核完整历史、规范正文hash/bytes/count，再核本人suffix审计和未受理capture。技术STOP原子选scope/slot、精确将继承TEMP_HANDOVER_RESERVED转本轮REVIEW_RESERVED，suffix保持SOURCE_RESERVED；不释放、不记出库，不写B的accepted payload/submitted_capture/submitted_event/chunks，不手造canonical。

LOCAL STOP签名绑定`payloadPresent=false`、`payloadSha256=null`及独立`localFrozenPayloadSha256`、原请求摘要；Android验证本人冻结原文。旧已受理STOP仍按原正文摘要消费。无新增迁移；契约补独立原件语义，客户端兼容读取扩展，版本只引用看板事实块。

## 失败与修复

- 1121专项1/1/0/0：final事务类不能由Spring代理，启动失败、未进业务链。去掉final。
- 1122专项1/1/0/0：来源和B的prepare/stage/confirm/activate/freeze/localRegister各1/0/0/0；阶段结果名与运行器约定不一致，未到E2A/STOP。对齐结果名及下一跳引用。
- 1123专项1/1/0/0：B LOCAL E2A/STOP、精确尾SQL回滚及C上传通过，最终测试仍假定前驱有accepted payload而空查失败。改读C实际暂存原件，与主管消费正文逐值一致，分别核accepted hash或LOCAL独立冻结hash。
- compile-02因作者调用不存在的Head.scope失败，0测试；改为真实scope查询并核对context路径，compile-03 exit0、0业务测试，不代业务验收。

三失败专项均exit1/sourceUnchanged，保留原XML/日志/HTTP/Room/datadir/binlog/dump；dump独立重算匹配、认证停机、原PID退出、19330/19434无监听，不复用。初稿unit-01为581/0/0/1、unit-02为585/0/0/1；Androidcompile-01仅编译exit0。原命令/报告均在`.local/d01-d18-20261008/`各同名目录。

## 1124实际三轮与拒绝矩阵

1124新号跨33树核无占用，专项1/0/0/0、exit0/sourceUnchanged；来源与两个接收端共16份Android阶段XML各1/0/0/0。未冻结和仅清单注册的REQUEST均409且完整事实不变。LOCAL未验证STOP、错机/坏proof、清单长度变化、原件缺失拒绝。

LOCAL专用STOP末operation_event故障精确SQLSTATE45000/code1644/message `D06_LOCAL_STOP_TAIL`；scope/slot、owner、状态、签名回执、业务表和handover/transport原根完整回滚。丢STOP响应、Room尾写故障及重开保留同一原请求、规范冻结正文及原根，最终本地ABORTED且无成功凭据。

B停止后，APPROVE和PREPARE的LOCAL原件缺失、坏字节、减包均409，主管撤权403；APPROVE减清单409。每项前后业务表及交接/transport根逐列一致，原文件完整恢复；恢复后同一合法输入通过。实际错误响应/原命令和快照见`temporary-handover-local-stop/local-approve/`、`local-prepare/`及HTTP索引。

C保留B全部规范LOCAL字节、累计原ID/原文/采集顺序，三设备局部counter均从1开始且互不混用。最终确认故障精确SQLSTATE45000/code1644/message `D06_HANDOVER_FINAL_SQL`，最后completion及全部业务/续作行完整回滚；实际确认SUCCEEDED，Android丢响应、Room回滚、重开后只保留一份耐久成功。删除仍禁止。

独立Node RSA/canonical消费者和只读SQLite/字节hash消费者均exit0，核三轮前驱/逐值前缀、LOCAL STOP false/null/独立摘要及原请求签名、清单hash/bytes/count、最终数量/重量、11组前置/审批/准备快照守恒、Room丢响应/尾写/重启。证据在`d06-local-consumers/`；仅独立工具、合成身份和开发密钥，不称独立人员、原身份或真机证明。

专项原件在`.local/e2a-cross-20260925-1124/`，报告在`d06-mixed-test-1124/`，保全在`d06-mixed-1124/retention.json`。dump独立重算、认证停机、原PID退出及两个隔离端口无监听通过。

## 1125真实无canonical

首轮Android实际冻结后先取消，再运输LOCAL原件/E2A及原设备STOP，未上传受理正文。专项1/0/0/0、exit0/sourceUnchanged，Android来源XML1/0/0/0。实际head为ABORTED、无accepted payload/目标、scope canonical为空，真实LOCAL对象和完整停止verification存在。

主管HTTP读取交接source context及REQUEST均404 `HANDOVER_CASE_NOT_FOUND`，前后全部业务及交接/transport事实相同；不能把这个LOCAL原件当作已具备canonical的新交接资格。输入不是手工造canonical或删除accepted payload。专项/保全目录为`d06-mixed-test-1125/`、`d06-mixed-1125/`及`.local/e2a-cross-20260925-1125/`；dump重算、认证停机、PID退出/端口核对通过。

无canonical独立Node消费者exit0，核真实首轮ABORTED签名、false/null、no scan/no release、未受理正文/空canonical、两次404及全部快照守恒，见`d06-local-consumers/no-canonical-1125-03.log`。消费者01/02误把LOCAL专用STOP的localDeletionAllowed字段要求用于旧首轮取消回执，失败保留；旧取消回执不含该字段，不凭字段缺失推断授权。消费者修正为该原协议的false/null/no scan/no release，不改产品或原报告。

## 回归与范围交付

作者自审在`d06-local-matrix-03/self-review.md`。服务端普通全回归`d06-local-server-regression-01`为3364/0/0/1、exit0/sourceUnchanged；唯一skip是Windows符号链接条件，不计通过，普通test不含IT。Android当次受影响回归`d06-local-android-regression-01`为514/0/0/10、exit0/sourceUnchanged，HTTP条件skip不计通过。

同次默认debug APK构建和lint通过，lintReportDebug实际执行、XML/HTML当次保存，独立核0 errors、221条提示；不称零问题。保留APK及重算SHA256 `BE3DE94D8C43D513A5689474B3DC1C585F392BA5BA1BC89469F30A0AEB65603B`，登记在`d06-local-consumers/android-artifacts.json`，仅本机D盘、未发布/正式签名。本批只改STOP回执读取、无新产品页面；既有页面历史证据不冒充当次UI测试或真机。

本范围37条拟定明确路径，提交前完整56条dirty快照、暂存diff、commit/push回执须登记在`d06-local-close-04`。生产默认/签名、七类能力及localDeletionAllowed=false保持，不合main、不发布部署或正式签名。1100–1102不重跑，历史失败与原资料/旧BCD/现场恢复限制继续保留；D06整体与D07–D18、第8节未通过，范围提交不是总目标完成。
