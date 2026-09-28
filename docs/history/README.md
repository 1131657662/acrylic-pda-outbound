# 历史归档

- [A3_526_LOCK_PENDING_SCOPE_2026-09-28.md](A3_526_LOCK_PENDING_SCOPE_2026-09-28.md)：用户将526按疑似缺陷挂待专项，列真实业务入口/执行计划/跳锁/竞争验收条件，不占其他A3回归轮次。

- [A3_526_LOCK_DIAGNOSIS_2026-09-28.md](A3_526_LOCK_DIAGNOSIS_2026-09-28.md)：527绿/528红原样重现，43份锁快照及线程栈确认SKIP LOCKED查询等待pending主键锁；定性b，未修产品/测试，执行计划与真实写入口影响待评估。

- [A3_NON_E2A_WAVES_2026-09-28.md](A3_NON_E2A_WAVES_2026-09-28.md)：新第1批513–526执行14类，37项36通过/1失败；条码并发commit timeout待定性停报，后续批次未启动。
- [A3_NON_E2A_REMAINING_2026-09-28.md](A3_NON_E2A_REMAINING_2026-09-28.md)：47类待跑清单与8类R08已有专用入口验收的去重标记（本次不重复），另列A3-BC产品侧阻断，明确各族环境和输入条件。

- [R09_C2_PROJECT_SCOPE_2026-09-28.md](R09_C2_PROJECT_SCOPE_2026-09-28.md)：应用入口接线独立范围、C1依赖与分段验收建议；仅记录，实施待授权，无代码变化。

- [R06_JWT_CLASSIFICATION_FIX_2026-09-28.md](R06_JWT_CLASSIFICATION_FIX_2026-09-28.md)：JWT定性a、39d1b84最小映射修复；单测6项与501–505共39项绿，红记录及同类静态候选清单保留。

- [A3_NON_E2A_REGRESSION_2026-09-28.md](A3_NON_E2A_REGRESSION_2026-09-28.md)：496–500停报红保留；JWT修复后501复验及506–512余7类34项绿，原首批12类均有通过记录，后续未启动。

- [R07_3_HEADER_WITHDRAW_BLOCKER_2026-09-28.md](R07_3_HEADER_WITHDRAW_BLOCKER_2026-09-28.md)：合法零账抬头被WithdrawConfirm空账守卫拒绝；静态确认、未跑HTTP组合，R07独立待办，只记录不修。

- [R08_D_DELIVERY_ACCEPTANCE_2026-09-28.md](R08_D_DELIVERY_ACCEPTANCE_2026-09-28.md)：用户按已验范围接受D整块交付；片1/2/3、四项基本边界及三项修复的证据与范围外限制。

- [R08_D_HEADER_DESIGN_AND_FIX_2026-09-28.md](R08_D_HEADER_DESIGN_AND_FIX_2026-09-28.md)：B3定性A、68a43f8抬头修复及495/run31验收；凭据扫描发现R07抬头撤销空账静态阻断，已停报。

- [R08_D_WITHDRAW_DESIGN_AND_FIX_2026-09-28.md](R08_D_WITHDRAW_DESIGN_AND_FIX_2026-09-28.md)：撤销版本定性A及493修复验收；494抬头零明细凭据缺陷停工，包含全部失败与未验。

- [R08_D_BOUNDARIES_2026-09-28.md](R08_D_BOUNDARIES_2026-09-28.md)：四项基本样本通过证据索引（489/490/493/495）；历史红及组合未验保留，交付决定见D交付确认。

- [R09_JAVA_CONTROL_ACCEPTANCE_2026-09-26.md](R09_JAVA_CONTROL_ACCEPTANCE_2026-09-26.md)：外部可信checkpoint/完整链/严格路径与Python→Java原件消费，Windows/Linux各16项真实进程锁；失败和共享接入限制保留。

- [R06_UNSUBMITTED_SOURCE_ACCEPTANCE_2026-09-25.md](R06_UNSUBMITTED_SOURCE_ACCEPTANCE_2026-09-25.md)：2026-09-26完成其他未提交FORMAL/TEMP原事实核验，attempt67非空MySQL/真实HTTP、613单测通过/1条件skip，64/65失败保留。

- [R06_DRAFT_ARCHIVE_TRUST_ACCEPTANCE_2026-09-25.md](R06_DRAFT_ARCHIVE_TRUST_ACCEPTANCE_2026-09-25.md)：草稿快照历史归档可信核验，attempt63实际Android归档/换钥重启HTTP，62失败资料保留。

- [R06_NONZERO_CANDIDATE_ACCEPTANCE_2026-09-25.md](R06_NONZERO_CANDIDATE_ACCEPTANCE_2026-09-25.md)：21候选五页真实HTTP、独立DB投影与Python54项、非零短装批准/普通确认阻断；完整单测576通过/1条件跳过。

- [管理文档精简前原文（2026-09-25）](management-2026-09-25-before-refresh/README.md)：七份工作区原文按原字节保存，manifest逐项SHA-256校验；包含旧看板历史、派工指令、失败与未验。本次接续核对与全部未提交路径见[快照](MANAGEMENT_REFRESH_2026-09-25_WORKTREE_STATUS.md)，当前事实仍只读主看板。

- [R06_DRAFT_TRANSFER_ACCEPTANCE_2026-09-25.md](R06_DRAFT_TRANSFER_ACCEPTANCE_2026-09-25.md)：Room26原键/回执耐久恢复、Android424/APK/lint及attempt57；对应WORKTREE_STATUS及before_draft_transfer保存路径与旧当前区。

- [R06_DRAFT_INTAKE_ACCEPTANCE_2026-09-25.md](R06_DRAFT_INTAKE_ACCEPTANCE_2026-09-25.md)：V37独立原件接收/可信准备/原响应，服务端577、Android408、attempt56真实HTTP；对应WORKTREE_STATUS与before_draft_intake保存路径和旧当前区。

- [R06_DRAFT_SNAPSHOT_ACCEPTANCE_2026-09-25.md](R06_DRAFT_SNAPSHOT_ACCEPTANCE_2026-09-25.md)：原件根和全设备边界结构闭合、Android406/服务端549；对应WORKTREE_STATUS及before_draft_snapshot保路径和前当前区。

- [R06_DRAFT_SIGNATURE_ACCEPTANCE_2026-09-25.md](R06_DRAFT_SIGNATURE_ACCEPTANCE_2026-09-25.md)：Room25原机事务签名/旧原件不补签、Android400与服务端522全套；对应WORKTREE_STATUS和before_draft_signature保存路径与旧当前区。

- [R06_LOCAL_DRAFT_STOP_ACCEPTANCE_2026-09-25.md](R06_LOCAL_DRAFT_STOP_ACCEPTANCE_2026-09-25.md)：Room24未冻结/零包/未决本机停止原件、23定向/390全套及attempt54冻结HTTP兼容；对应WORKTREE_STATUS与before_local_draft_stop保存全路径和旧当前区。

- [R08_TEMPORARY_EXPORT_ACCEPTANCE_2026-09-25.md](R08_TEMPORARY_EXPORT_ACCEPTANCE_2026-09-25.md)：TEMP原确认导出、全套及attempt53真实HTTP/Chrome/12文件验证；对应WORKTREE_STATUS和before_temporary_export保存全路径及旧当前区。

- [R06_ANDROID_PRODUCT_SUCCESS_ACCEPTANCE_2026-09-25.md](R06_ANDROID_PRODUCT_SUCCESS_ACCEPTANCE_2026-09-25.md)：Android产品成功RAW消费/事务显示/重开复核，367项、APK/lint及attempt48双端1/1和6/6；对应WORKTREE_STATUS及before_android_product_success保存路径和原当前区。

- [R09_TEMPORARY_ARCHIVE_RECOVERY_ACCEPTANCE_2026-09-25.md](R09_TEMPORARY_ARCHIVE_RECOVERY_ACCEPTANCE_2026-09-25.md)：TEMP V2/FORMAL V1混合归档快照及原确认/短装引用恢复；attempt47 IT1/1、11故障及真实HTTP。对应WORKTREE_STATUS与before_temporary_archive_recovery保存全路径和原当前区。

- [R06_RECORDED_DECISION_CONSUMER_ACCEPTANCE_2026-09-25.md](R06_RECORDED_DECISION_CONSUMER_ACCEPTANCE_2026-09-25.md)：首轮零候选真实决定独立消费者，attempt43实库/HTTP/Python46通过；对应WORKTREE_STATUS及before_recorded_decision_consumer保存路径与原当前区。

- [R06_TEMPORARY_CANDIDATES_ACCEPTANCE_2026-09-25.md](R06_TEMPORARY_CANDIDATES_ACCEPTANCE_2026-09-25.md)：正式侧成功/待审TEMP反向提示、稳定回执入口、真实HTTP及Chrome组合；对应WORKTREE_STATUS保存全路径，before_temporary_candidates保存前当前区。

- [R06_TEMPORARY_ARCHIVE_ACCEPTANCE_2026-09-25.md](R06_TEMPORARY_ARCHIVE_ACCEPTANCE_2026-09-25.md)：Room23 TEMP归档V2及同一Android实际冻结至内部成功/归档组合；对应WORKTREE_STATUS保存全路径，before_temporary_archive保存前当前区。

- [R06_TEMPORARY_SUCCESS_ACCEPTANCE_2026-09-25.md](R06_TEMPORARY_SUCCESS_ACCEPTANCE_2026-09-25.md)：Room22 TEMP成功原件严格消费/耐久、非空迁移与独立MySQL/真实HTTP；对应WORKTREE_STATUS保存全路径，before_temporary_success保存原当前区。

- [R06_TEMPORARY_CONFIRMATION_ACCEPTANCE_2026-09-25.md](R06_TEMPORARY_CONFIRMATION_ACCEPTANCE_2026-09-25.md)：首轮正常/独立短装内部决定、原回执及当前效力、完整回滚/真实HTTP/五组MySQL竞争；对应WORKTREE_STATUS保存全路径，before_temporary_confirmation保存前当前区。

- [R06_TEMPORARY_ACCOUNTING_ACCEPTANCE_2026-09-25.md](R06_TEMPORARY_ACCOUNTING_ACCEPTANCE_2026-09-25.md)：TEMP共同记账内部原资格政策适配、失败回滚、真实HTTP与正式续作回归；对应WORKTREE_STATUS列全路径，before_temporary_accounting保存替换前看板。

- [R06_E2A_ABORT_ACCEPTANCE_2026-09-25.md](R06_E2A_ABORT_ACCEPTANCE_2026-09-25.md)：正文前/部分上传后ABORT独立证据、STOP来源绑定、真实HTTP/非空MySQL与失败原件；对应WORKTREE_STATUS保存全部路径，before_abort_evidence保存替换前当前区。

- [R06_E2A_ROOM20_ACCEPTANCE_2026-09-25.md](R06_E2A_ROOM20_ACCEPTANCE_2026-09-25.md)：原请求/原键/STOP签名耐久当次验收；两树路径见同目录 R06_E2A_ROOM20_WORKTREE_STATUS_2026-09-25.md，前当前区见 ORCHESTRATION_STATE_before_android_room20_2026-09-25.md。

- [R06_E2A_CROSS_SOURCE_ACCEPTANCE_2026-09-25.md](R06_E2A_CROSS_SOURCE_ACCEPTANCE_2026-09-25.md)：当前会话跨来源事实、VERIFY 事务修复及独立非空 MySQL/真实 HTTP 结果与限制。
- [R06_E2A_CROSS_SOURCE_WORKTREE_STATUS_2026-09-25.md](R06_E2A_CROSS_SOURCE_WORKTREE_STATUS_2026-09-25.md)：该子项收口两树未提交路径。
- [ORCHESTRATION_STATE_before_cross_source_acceptance_2026-09-25.md](ORCHESTRATION_STATE_before_cross_source_acceptance_2026-09-25.md)：跨来源验收前看板原文，包含旧红测与未验，不作当前事实。

本目录保存已从根目录协作规则移出的完整历史，供需要时定向核查，不作为当前状态入口。

- [ORCHESTRATION_STATE_before_session_handoff_2026-09-24.md](ORCHESTRATION_STATE_before_session_handoff_2026-09-24.md)：新会话交接前当前区原文；最新状态仍以看板顶部为准。
- [R06_E2A_SESSION_HANDOFF_STATUS_2026-09-24.md](R06_E2A_SESSION_HANDOFF_STATUS_2026-09-24.md)：两棵原工作树交接前未提交路径快照。

- [ORCHESTRATION_STATE_before_android_archive_snapshot_2026-09-24.md](ORCHESTRATION_STATE_before_android_archive_snapshot_2026-09-24.md)：原机签名归档快照前的当前区原文。

- [ORCHESTRATION_STATE_before_android_snapshot_2026-09-24.md](ORCHESTRATION_STATE_before_android_snapshot_2026-09-24.md)：Room19 原机全设备快照前的当前区原文。

- [ORCHESTRATION_STATE_before_room19_stop_2026-09-24.md](ORCHESTRATION_STATE_before_room19_stop_2026-09-24.md)：Room19 原机 STOP 同事务落盘前的当前区原文。
- [R06_E2A_ROOM19_STATUS_2026-09-24.md](R06_E2A_ROOM19_STATUS_2026-09-24.md)：本轮两树未提交路径快照与写权断点。

- [ORCHESTRATION_STATE_before_android_transfer_2026-09-24.md](ORCHESTRATION_STATE_before_android_transfer_2026-09-24.md)：E2a Android 增量转入 Room18 主树前的当前状态原文。

- [ORCHESTRATION_STATE_before_android_coverage_2026-09-24.md](ORCHESTRATION_STATE_before_android_coverage_2026-09-24.md)：全设备审计/捕获连续前缀门槛前的当前区原文。

- [ORCHESTRATION_STATE_before_android_wire_2026-09-24.md](ORCHESTRATION_STATE_before_android_wire_2026-09-24.md)：Android E2a 线协议及 STOP 签名原语前的当前区原文。
- [R06_E2A_ANDROID_WIRE_STATUS_2026-09-24.md](R06_E2A_ANDROID_WIRE_STATUS_2026-09-24.md)：两树 Android 线协议增量的未提交路径快照。

- [ORCHESTRATION_STATE_before_history_archive_2026-09-24.md](ORCHESTRATION_STATE_before_history_archive_2026-09-24.md)：历史命令/归档签名轮换兼容前的当前区与两树路径快照。
- [ORCHESTRATION_STATE_before_k_writer_2026-09-24.md](ORCHESTRATION_STATE_before_k_writer_2026-09-24.md)：逐包 K 窄写者前的看板原文与两树未提交路径快照。
- [ORCHESTRATION_STATE_before_keyring_2026-09-24.md](ORCHESTRATION_STATE_before_keyring_2026-09-24.md)：历史公钥轮换前的看板原文与两树未提交路径快照。
- [ORCHESTRATION_STATE_before_source_read_2026-09-24.md](ORCHESTRATION_STATE_before_source_read_2026-09-24.md)：本轮逐包原字节链与原键只读前的看板原文；不作为当前状态。
- [R06_E2A_WORKTREE_STATUS_2026-09-24.md](R06_E2A_WORKTREE_STATUS_2026-09-24.md)：两树本轮管理更新前的未提交路径快照。

- [ORCHESTRATION_STATE_before_archive_coverage_2026-09-24.md](ORCHESTRATION_STATE_before_archive_coverage_2026-09-24.md)：签名归档与审计 PAGE 覆盖增量前的看板当前区原文；不作为当前状态。

- [ORCHESTRATION_STATE_before_audit_endpoint_2026-09-24.md](ORCHESTRATION_STATE_before_audit_endpoint_2026-09-24.md)：AUDIT 原件/原授权及签名 STOP 审计端点写入前的看板当前区原文；不作为当前状态。

- [ORCHESTRATION_STATE_before_audit_prefix_2026-09-24.md](ORCHESTRATION_STATE_before_audit_prefix_2026-09-24.md)：全设备库内审计前缀增量写入前的看板当前区原文；不作为当前状态。

- [ORCHESTRATION_STATE_before_stop_key_writer_2026-09-24.md](ORCHESTRATION_STATE_before_stop_key_writer_2026-09-24.md)：STOP 内部写入公钥来源对齐前的看板当前区原文；不作为当前状态。

- [ORCHESTRATION_STATE_before_direct_key_2026-09-24.md](ORCHESTRATION_STATE_before_direct_key_2026-09-24.md)：直接登记当前公钥修正、attempt2 失败和 attempt3 通过写入前的看板当前区原文；不作为当前状态。

- [ORCHESTRATION_STATE_before_paired_key_2026-09-24.md](ORCHESTRATION_STATE_before_paired_key_2026-09-24.md)：STOP 已存 OPEN/服务器配对公钥重验前的看板当前区原文；不作为当前状态。
- [ORCHESTRATION_STATE_before_raw_closure_2026-09-24.md](ORCHESTRATION_STATE_before_raw_closure_2026-09-24.md)：PAGE 原始对象/文件核对更新前的看板当前区原文；不作为当前状态。
- [ORCHESTRATION_STATE_before_stop_preflight_2026-09-24.md](ORCHESTRATION_STATE_before_stop_preflight_2026-09-24.md)：STOP 隔离实库写者和只读前置门槛更新前的看板当前区原文；不作为当前状态。
- [ORCHESTRATION_STATE_before_page_closure_2026-09-24.md](ORCHESTRATION_STATE_before_page_closure_2026-09-24.md)：PAGE 根只读核对前的看板当前区原文；不作为当前状态。
- [ORCHESTRATION_STATE_before_stop_persistence_2026-09-24.md](ORCHESTRATION_STATE_before_stop_persistence_2026-09-24.md)：STOP 独立持久接收及本轮自审结果写入前看板当前区原文；不作为当前状态。
- [ORCHESTRATION_STATE_before_v36_dynamic_2026-09-24.md](ORCHESTRATION_STATE_before_v36_dynamic_2026-09-24.md)：V36 动态迁移/OPEN 外键自审前的看板当前区原文；不作为当前状态。
- [ORCHESTRATION_STATE_before_stop_parser_2026-09-24.md](ORCHESTRATION_STATE_before_stop_parser_2026-09-24.md)：STOP 纯解析和 attempt4 写者验证收录前的看板当前区原文；不作为当前状态。
- [ORCHESTRATION_STATE_before_stop_compat_2026-09-24.md](ORCHESTRATION_STATE_before_stop_compat_2026-09-24.md)：STOP 事件标识协议兼容复审前的看板当前区原文；不作为当前状态。

- [AGENTS_2026-09-22.md](AGENTS_2026-09-22.md)：2026-09-22 精简前 `AGENTS.md` 的原文完整字节副本。其 SHA-256 为 `C6F6F8F7BDA490DACCDC23F9AA5C44FC6D1F0A2A8598288D20AEAA1372EE7999`。
- [ORCHESTRATION_STATE_before_e2a_attempt6_2026-09-23.md](ORCHESTRATION_STATE_before_e2a_attempt6_2026-09-23.md)：E2a attempt6 结果写入前，总调度看板旧当前区的原文快照；不作为当前状态。
- [ORCHESTRATION_STATE_before_e2a_trigger_preflight_2026-09-23.md](ORCHESTRATION_STATE_before_e2a_trigger_preflight_2026-09-23.md)：E2a 触发器预检续作写入前，总调度看板旧当前区的原文快照；不作为当前状态。
- [ORCHESTRATION_STATE_before_e2a_trigger_decision_2026-09-23.md](ORCHESTRATION_STATE_before_e2a_trigger_decision_2026-09-23.md)：E2a 触发器授权决策卡写入前，总调度看板旧当前区的原文快照；不作为当前状态。
- [ORCHESTRATION_STATE_before_e2a_attempt8_2026-09-23.md](ORCHESTRATION_STATE_before_e2a_attempt8_2026-09-23.md)：单助手接管及 E2a attempt8 动态验收写入前，旧当前区的原文快照；不作为当前状态。
- [ORCHESTRATION_STATE_before_e2a_independent_review_2026-09-23.md](ORCHESTRATION_STATE_before_e2a_independent_review_2026-09-23.md)：attempt8 独立证据复核与原样本完整单测写入前的当前区快照；不作为当前状态。
- [ORCHESTRATION_STATE_before_e2a_intake_read_2026-09-23.md](ORCHESTRATION_STATE_before_e2a_intake_read_2026-09-23.md)：E2a intake 查询只读事务与本轮单测写入前的当前区快照；不作为当前状态。
- [ORCHESTRATION_STATE_before_e2a_intake_recovery_2026-09-23.md](ORCHESTRATION_STATE_before_e2a_intake_recovery_2026-09-23.md)：E2a intake 冲突分类、回滚后独立重查与本轮单测写入前的当前区快照；不作为当前状态。
- [ORCHESTRATION_STATE_before_e2a_open_2026-09-24.md](ORCHESTRATION_STATE_before_e2a_open_2026-09-24.md)：E2a OPEN 接收及分页协议校验续作前的当前区原文快照；不作为当前状态。
- [ORCHESTRATION_STATE_before_e2a_page_read_2026-09-24.md](ORCHESTRATION_STATE_before_e2a_page_read_2026-09-24.md)：PAGE 解析、私有文件存储与已存 OPEN 绑定读取续作前的当前区原文快照；不作为当前状态。
- [ORCHESTRATION_STATE_before_e2a_page_write_2026-09-24.md](ORCHESTRATION_STATE_before_e2a_page_write_2026-09-24.md)：PAGE 写者和原始对象文件接收续作前的当前区原文快照；不作为当前状态。
- [ORCHESTRATION_STATE_before_e2a_item_stage_2026-09-24.md](ORCHESTRATION_STATE_before_e2a_item_stage_2026-09-24.md)：非空 PAGE item 严格解析及 V36 跨页索引续作前的当前区原文快照；不作为当前状态。
- 当前 Git、执行者、证据、环境和下一步只读 [总调度看板](../ORCHESTRATION_STATUS.md) 顶部 `CURRENT_STATE_START/END` 区段。
- 长期规则和新会话接续方式只读根目录 [AGENTS.md](../../AGENTS.md)；仓库操作规则见 [Git 管理说明](../GIT_WORKFLOW.md)。

归档文件不应被改写来表示新状态。阶段收尾和接续断点写入当前状态区段；需要保留的旧证据按专项文档和提交 SHA 定向引用。

原文字节副本中的相对链接仍按仓库根目录解释；归档位置不会改变这些链接的基准，也不得为修正链接而改写副本。
# 2026-09-25 Room27 产品队列

[停止产品入口与持久补传验收](R06_DRAFT_PRODUCT_ACCEPTANCE_2026-09-25.md)：本批实现/自审/最终444项及attempt59；非现场、非上线。接管前原当前区见 [归档](ORCHESTRATION_STATE_before_product_review_2026-09-25.md)。
# 2026-09-26 接续增量

- [R09 Job恢复代次屏障验收](R09_JOB_FENCE_ACCEPTANCE_2026-09-26.md)：V38旧Job保全、实例启动代次/持锁事务；attempt72专项+23回归、attempt71真实导出/浏览器，完整服务端618pass/1skip。

- [R09库外控制本地模块验收](R09_LOCAL_CONTROL_ACCEPTANCE_2026-09-26.md)：追加历史/CAS/进程锁及崩溃恢复，29新测试通过，完整R09 103通过/11跳过；不是共享门禁或生产恢复完成。
- [进入库外控制前看板快照](ORCHESTRATION_STATE_before_recovery_control_2026-09-26.md)。
