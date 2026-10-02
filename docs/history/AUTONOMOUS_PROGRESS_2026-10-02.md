# 自主推进记录（2026-10-02）

## 子项状态与SHA

| 子项 | 状态 | 提交/证据 |
| --- | --- | --- |
| P08原主键保护 | 已验证推送，接续未重跑 | 6faf322；10月1日记录及commit-review |
| R06来源分页 | 已验证推送，远端一致 | 33533e4；bindings-closeout/git-final.json |
| R07首次同键并发 | 已验证推送，远端一致 | dce5fe7；handover-closeout/git-final.json |
| R07交接同步status兼容 | 已验证推送，远端一致 | bfcf471；sync-status-closeout/git-final.json |
| R07实际账务贡献 | 离线、迁移、HTTP与导出验证通过，已推送 | 458a943；accounting-repair/closeout/git-final.json |
| R06 Web原命令恢复 | 代码及浏览器/契约回归通过，待本次提交 | web-recovery/final-2、e2a-cross-20260925-840 |
| mapping3+/R1及R06/R07余项 | 未完成 | 沿优先级接续 |

## 自主决策记录

1. 单助手沿修订授权实施、自审、隔离验证及开发分支推送；保护预存PDA文档、更正链IT和旧脚本，仅明确范围暂存。
2. 来源分页传递limit+1给Page.from，末个返回项作游标；真实HTTP逐项比对55来源验证无重漏。
3. 交接首次同键在案件/来源行锁后重查回执，核对正文及签名后返回原字节；新键仍须expected一致。823至825是服务层MySQL证据。
4. 交接新同步消息补ASSIGNED，历史缺字段读取兼容，坏值拒绝，不修改不可变原件。
5. 828包装器URL插值错误，保留失败库/日志；修包装器自检后829通过。早期行补丁纪律偏差已改整文件写入。
6. 账务按实际原扣及贡献图：无扣不虚构、增重负差、归零逐笔反向；撤回独立恢复引用，原账/原结果不改写。
7. 撤回更早更正沿不可变引用链找当前代表；校验唯一、深度奇偶、来源及逐笔逆值，拒绝冲突。
8. 零账务更正仍导出，撤回导出追溯贡献/恢复链；验收分离身份、签名回执、SQL投影、真实XLSX/PDF下载。
9. full-2无结果、full-3编码失败、full-5编译失败均保留；中文整文件脚本统一pwsh。
10. 修复旧主管夹具documentId路径与撤回导出引用；导出IT自包含SUPERVISOR身份，原创建者确认，保护预存更正链IT差异。
11. 830 renderer配置503、831确认凭据409整体失败；不得用前半链替代导出验收。
12. 应用brainstorming、单一职责、测试、安全、SQL schema/joins技能；技术选择直接执行，用户自主授权覆盖设计确认门槛。
13. TEMP确认采用测试内完整服务组装及扣库政策前置；验证签名完整性、原字节回放与唯一决定事件，生产入口仍阻断。
14. 非空旧schema/旧账升级验证全部旧列守恒、重复迁移、精确约束及append-only；833真实MySQL通过。
15. 完整性验证器返回unsigned正文，修夹具断言后完整离线回归通过，产品校验未放宽。
16. 非空账链净零不能等同未扣库；只有entries为空可跳过结果匹配，反例先红后绿，832真实HTTP验证拒绝及回滚。
17. 837审批前401源于测试身份缺pda:login；仅补隔离SUPERVISOR前置。
18. 838 Web登录403源于隔离HTTP和Secure cookie，缺CSRF会话；仅在导出IT启动参数加cookie.secure=false，生产默认不变。
19. 839完成Web登录及真实恢复结果XLSX/PDF下载后再收口；明确暂存37文件，排除预存更正链IT及PDA差异，458a943提交推送。
20. Web恢复把身份、仓库、提交和命令类型纳入localStorage键；保存原JSON并读回核对，未确认回执时禁止换键，Web Locks缺失时拒绝发送。
21. 重试只回放保存的原JSON和原恢复代次；CSRF异步读取后再次校验身份，跨页并发保留首个原请求，匹配回执才持久完成。
22. 发现UI打开时会在存储读取失败后继续拉取事实并清除错误，改为仅无错误时加载；完整浏览器测试覆盖该边界。
23. 真实契约测试缺失样本导致首轮回归2项失败；为隔离MySQL分配840，MockMvc生成build/temporary-review-contract.json，认证停机并保全dump/日志。
24. Web schema原先允许writeConfirmationAvailable=true，契约反例先红；按服务端恒false政策收紧为literal(false)，最终7项契约回归通过。
25. Chrome回归首次在异步清理完成前读取localStorage造成2项假失败；测试等待可观察清理结果后，10项全部通过，失败证据保留。

## 验证与证据索引

已交付：[分页验收](R06_SOURCE_BINDING_PAGINATION_REPAIR_2026-10-02.md)、[原键验收](R07_FIRST_REPLAY_REPAIR_2026-10-02.md)、[同步验收](R07_HANDOVER_SYNC_STATUS_REPAIR_2026-10-02.md)、[账务验收](R07_ACTUAL_ACCOUNTING_REPAIR_2026-10-02.md)。分页单测5项、822真实HTTP1项；823至825服务层MySQL各1项；826服务层、827真实HTTP各1项；829既有MockMvc+MySQL5项。

账务证据根.local/autonomous-20261002/accounting-repair/：offline-full-9/10/11各148套件2956项0失败0错误1既有skip，编译/哈希通过。isolated-suite-832/results.json索引832至836，各1项零失败/错误/跳过；839 E2aWithdrawnCorrectionExportIT 1项零失败/错误/跳过，complete=true。830/831/837/838失败保留。

Web证据根.local/autonomous-20261002/web-recovery/：unit-1历史35项通过，unit-2/final-2/unit均35项通过；regression-1因契约样本缺失失败保留；browser-1两项异步断言失败保留；browser-2及final-2浏览器10项通过。final-2/regression包含build、7项temporary-review、OpenAPI契约及Chrome结果，均exit 0。e2a-cross-20260925-840为TemporaryReviewContractIT真实隔离MySQL+MockMvc 1项，tests=1、failures/errors/skipped=0，authenticated shutdown complete；样本SHA、dump和日志保全。

## 仍未解决与边界

更正原键actor、mapping3+/R1、R06/R07余项。HEADER撤回、历史无引用REVERSAL、旧更正outbox、CYCLE_REOPENED未覆盖；TEMP政策/高轮次/跨来源、526、R05原资料/旧BCD、R09恢复限制单列。fixture/HTTP/SQL不是现场；PDA/Keystore、独立JVM/OS死亡、断电/长时与完整UI未完成。完整服务端integrationTest尚无可解读全量通过，goal active。

## 磁盘与环境

840隔离库已保全停机；无在途重型任务，测试写者及19330/19434监听0。系统MySQL不动，删除0，设备动作0。D盘接续可用值以web-recovery/resume-continued/environment.json及final-2/regression/completed.json为证，仍高于20GiB；未合main/标签/Release/部署，未接原库/现网，未改生产默认开关。
