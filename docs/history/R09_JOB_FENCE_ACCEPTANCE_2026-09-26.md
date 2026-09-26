# R09 后台任务恢复代次屏障验收

## 实现前完成标准

输入：服务端树`codex/r06-e2a-migration@568683c1a1f8257ad0596a654c6b3558c767ed95`及既有未提交改动；主树`codex/development@dccb73b10acbb63020beb3cf827b3b4580d3ed1c`。当前单助手同时维护两树；本子项是库外控制接入前必需的共享DB事务屏障，不代替完整R09。

1. 统一 `ServerState` 的持锁写校验，必须处于使用同一数据源的实际事务；锁一直持有到事务结束。Worker生产/领取/续租/回收/失败/成功发布先锁恢复状态，再锁Job，不能仅启动时判断。
2. V38给Job增加不可变的原创建代次。旧记录保留原ID、payload、状态、租约、结果和时间且代次为空：无可信证据不得推定属于当前代次，不能自动领取/回收/标成功。新任务必须绑定创建时已锁定的代次。
3. 换代后旧任务的续租/失败/完成回写拒绝，原Job无变化；同dedupe键不跨代次复活或覆盖。即使Job DTO伪称新代次，数据库旧代次也阻断。原NORMAL同代次生命周期/幂等/失败回滚行为保留。
4. 隔离非空V34→V37→V38验证旧Job各状态保全；实库竞争验证生产/完成事务阻止恢复切换越过在途提交；恢复先获锁时旧Worker不能发布，重启不把旧任务改新代次。检查锁等待证据，不以sleep猜并发。
5. 本批独立MySQL、真实HTTP普通写恢复模式拒绝、定向及完整服务端测试留证；共享3306、原库、现网不操作，失败库与日志保留。未改Android则APK/lint不是本批验证。
6. 库外UUID/DB投影、全API与文件写屏障、跨主机隔离、恢复证据及释放两阶段仍待后续同目标推进；无生产决定/放行/关闭能力，`localDeletionAllowed=false`保持。

## 当批结果

本子项已实现、自审修复并复验。完整R09和总目标仍未完成。

- 生产实现：V38 `background_job.recovery_epoch` 可空原代次/不可变触发器及队列索引；`ServerState.lockCurrentWritable` 检查同数据源实际事务；`JobRecoveryFence` 固定实例启动代次；Job生产、领取、续租、回收、失败、完成均持恢复共享锁到事务结束并核数据库原Job代次。
- 自审补强：attempt68后发现仅Job代次仍允许旧实例自动接纳新库，新增实例启动代次固定；attempt69后补重启新代次正例、不可变约束及查询计划；最终补导出未知代次显示/筛选为INVALIDATED、不占额度、禁止通过原快照重试改造为新代次。原payload/文件不改写。
- 最终attempt72：独立非空V34 seed→V37→V38；1项复合IT无skip，五类旧Job状态逐字段一致、原代次为空且不可修改；当前Job正常生命周期/回滚；模式关闭、代次改变、伪造DTO、过期原租约、重启与新代次正例；三种实际锁竞争从performance_schema取证；真实HTTP普通领取503/只读200、无新assignment；旧导出不占额度。该库额外执行既有Job并发5/生命周期10/Worker8项，23/23无skip。
- attempt71：相同生产源码输入的非空V34→V38真实HTTP TEMP原确认/短装XLSX、扫码Excel、PDF、汇总、原键/重试/失败恢复；FORMAL/PC兼容，JavaIT1/1、Playwright浏览器1/1无skip。后续只增加专项测试，没有修改生产导出代码。
- 最终完整服务端89XML/619项=618通过、1符号链接条件skip，0失败/错误。5项新增单测；skip不是通过。本批没有Android变更，未重跑APK/lint。无真机/现场、独立人员或生产部署。
- 证据目录：`outputs/r06-product-20260925-01a0d8fa/job-fence-*`，各attempt68–72的XML、完成marker、认证shutdown identity保留。完整测试日志`job-fence-server-final-02.log`、XML副本`job-fence-server-xml/`；汇总`job-fence-final-summary.json`。
- 最终源码manifest938文件，SHA256 `3e2d06ac56a7d2f860ee8c38bf94b21bb7582656ab2e136288f2589c62f967d6`；两树全部路径`job-fence-final-main/server-status.txt`。运行数据、密钥、原HTTP及数据库保留在服务端`.local/e2a-cross-20260925-68`至`72`；普通证据未复制私钥/凭据。
- 本批compile/测试无业务失败；若干只读定位路径不存在/Windows通配符读取失败未影响实现，未删原资料。68/69为补强前输入，70为最终导出补强前，不能替代72。EXPLAIN小样本选择既有type索引并filesort，未据此声称大规模性能已验。
- 68–72均认证shutdown；19330/19434/19435/19436无监听，共享3306 PID5448未动；无在途重型任务。两树HEAD不变，无提交/推送/main/发布/部署。

## 兼容与下一项

V38升级保留全部旧Job，但未知代次的待执行任务会停止；没有自动回填或自动续作入口。新代码要求V38，旧程序不得在V38上继续运行写任务。当前代次仍是原数字字符串，不声称UUID桥梁或库外authority已完成；同代次DB回滚/恶意复写仍需库外证据及写者隔离。

下一项接本地库外控制记录的Java独立消费者、共享请求/文件/Worker入口及跨存储关停；随后DB投影/UUID桥梁、截点外FH和重建。普通认证仍按原身份/权限边界，生产决定/关闭/释放及localDeletionAllowed=false不改变。
