<!-- CURRENT_STATE_START -->
## 当前有效状态（唯一接续入口）
### 2026-09-25 正式侧TEMP反向候选提示通过；前九步目标active

- 目标/写权：会话01a0d612-1e51-78c2-9eca-f15671339987当前助手独占两棵既有树及专用隔离环境，旧同目标任务notLoaded且无旧写者；不恢复总调度/子代理，不新建任务或树。
- Git：主树D:/acrylic-pda-outbound，codex/development @ dccb73b10acbb63020beb3cf827b3b4580d3ed1c；服务端树D:/acrylic-pda-worktrees/r06-e2a-migration，codex/r06-e2a-migration @ 568683c1a1f8257ad0596a654c6b3558c767ed95。origin均GitHub 1131657662/acrylic-pda-outbound。无提交/推送/整合/main/部署，最新服务端未整合主树。
- 全部未提交/未跟踪：docs/history/R06_TEMPORARY_CANDIDATES_WORKTREE_STATUS_2026-09-25.md；本批后端候选只读查询/回执入口/测试，主树Web显示/测试/契约/记录；两树既有修改、out.xlsx、失败库/日志全部保留。AGENTS长期规则未变。
- 继承已验：E2a全设备与本TEMP/其他来源分离；Room20/21原请求/键/STOP及丢回执重开；ABORT；共同记账；首轮mapping1无未决候选正常确认/独立短装；Room22成功原件、Room23三文件归档及同Android冻结→成功/归档组合。均按历史范围引用，不冒称本轮重跑。
- 实际服务端：只读GET /orders/{id}/temporary-candidates，order:read+同仓、WEB当前计划，RR快照筛同仓原首映射PDA_TEMP的成功/待审来源；车号二进制相同或产品ID交集，接收日期默认正式建单北京时间±7天，可调最多366天。20条分页/total，成功优先，无自动阻断/合并/关联/写回原成功。
- 回执/页面：正式create/revise/publish共用回执增加稳定查询入口；PC START原保存回执含入口，含DRAFT嵌套发布。动态候选独立GET，不改变原幂等事实；旧PC回执可能无字段。Web详情/PC开始及刷新读取候选，核order/revision/version，失败不冒充空列表；内部滚动、日期/分页/原单链接，已作业提示保留双方证据进入R07。
- 作者/自审：当前助手实现、自审及截图检查，无独立人员复审；无新增服务端迁移（V36），Android code20/Room23本轮未改。服务端API契约0.33.0/128路径；主树仅同步本项，121路径，其原版本及品牌保留，非完整整合。
- 作者当次测试：定向模型3/3；最终全套78 XML/494项，493通过、1 Windows符号链接条件skip、0失败/错误。server-candidates-final.log/final-results及JSON；Web最终vue-tsc/Vite构建通过（既有大chunk warning），两树OpenAPI校验通过。
- 独立环境/组合：attempt41 IT1/1、Chrome3/3。ACL/datadir/schema/user/19330独立非空MySQL V34 seed→V36/binlog；合成身份/准备，内部真实TEMP正常确认后，真实cookie/CSRF HTTP正式建单/发布/READY修订/PC嵌套发布及候选查询。主树Web经19436代理实际19434应用，无Mock替代正向。
- 已验边界：21个成功/待审来源不倍增、成功优先/往返分页、无匹配/只车号/只产品/跨仓成功排除、401/403/404、日期首尾毫秒与非法范围/页码；原TEMP成功/决定/回执/审计/库存/dispatch/ledger/outbox不变。Chrome新建→发布→PC开始、READY修订、分页、查询失败/错版本拒绝通过。
- 证据：docs/history/R06_TEMPORARY_CANDIDATES_ACCEPTANCE_2026-09-25.md；主树outputs/r06-e2a-takeover-20260925-01a0d612/*candidates*；attempt41 XML SHA256 7DB35D46B1998176010A065FC4CADB523958F3DC9D6EF695060A6D66243237A9。final hash清单、截图在attempt41/browser。前当前区存before_temporary_candidates历史。
- 本轮失败保留：34测试Secure cookie/CSRF；35跨仓404误期望403；37隐藏checkbox定位；38漏填PC原因；40首页文本定位。36仅服务端通过、39初次组合通过，41最终通过；34–41均认证shutdown。旧归档/确认/Room失败继续保留，不能清库消除。
- 环境/重型槽：无Java/Gradle/adb/emulator在途，19330/19434/19435/19436无监听，重型槽空闲；共享3306/PID5448父4412未动。下次独立run从42先检查分配。
- 未完：真实businessDecision的独立协议消费者；TEMP产品成功/归档接线；非零候选排除/关联/mapping2+、更高轮次、拒绝/实物处置/续作；R09新增归档V2三文件/确认决定引用完整恢复；E2a未冻结/整单零包/未决输入/其他未提交来源恢复；R07–R09其余可独立项及两树最终开发整合。
- 长期未验：未来映射/轮次对本提示的适配、长时大数据/人工是否同趟政策、真实keyring、独立人员、模拟器/真机Keystore、OS死亡/断电/外网、长时/五年等待、人工现场/生产权限/审批。R05 code20/Room17及R09边界保持。候选窗与空页不是无重复证明。七类生产决定handler/POST/放行/关闭不开放，localDeletionAllowed=false。
- 下一断点：接docs/parallel/R06_LEDGER_PROTOCOL真实NORMAL_CONFIRM/SHORT_APPROVE businessDecision消费者，核原命令/原上下文/签名与嵌套成功；现有消费者仍只接受INTERNAL_TEST_ONLY。内部测试接线，不开生产决定；随后R09归档V2恢复及R06/R07–R09剩余项。排除/拒绝权限尚未定不得臆定，其他独立工作继续。
<!-- CURRENT_STATE_END -->
