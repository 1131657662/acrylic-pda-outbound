# 自主推进记录（2026-10-02）

## 子项状态与SHA

| 子项 | 状态 | 提交/证据 |
| --- | --- | --- |
| P08原主键保护 | 已验证并推送（接续核验，未重跑） | 6faf322；见10月1日自主记录及本地commit-review |
| R06来源绑定分页 | 已验证并推送，远端一致 | 33533e4；bindings-closeout/git-final.json |
| R07首次同键并发 | 已验证并推送，远端一致 | dce5fe7；handover-closeout/git-final.json |
| R07同步status及历史消息 | 已验证，按独立范围提交推送 | 精确SHA见sync-status-closeout/git-final.json及Git历史 |
| 账务贡献、Web原命令恢复、mapping3+/R1 | 未完成 | 接续用户优先级执行 |

## 自主决策记录

1. 沿用户修订授权继续单助手实施、自审、回归及开发分支推送；保护预存PDA资料、更正链IT和旧脚本，仅明确范围暂存。
2. 来源分页完整传递limit+1给Page.from，以末个返回项作游标；真实HTTP逐项比对55来源，不依靠列表数量推断无重漏。
3. 交接首次同键在案件或来源行锁后重查原回执；先验证原正文与签名，再返回原字节。无回执的新键仍须expected一致。823至825是服务层真实MySQL，不冒充真实HTTP并发。
4. 新同步消息补ASSIGNED，历史缺字段交接消息在读取时兼容，避免修改不可变原件；只缺字段才回退，坏值仍拒绝。用真实HTTP验证新旧消息发布/增量/重试，用既有MockMvc IT回归基线与游标。
5. 828是脚本URL插值错误造成启动失败；保留失败原库与日志，修包装器并增加URL自检，新库829通过。无产品/断言刷绿；初期行补丁纪律偏差已用整文件写入取代。

## 验证与证据索引

分页单测5/0/0/0、822真实HTTP1/0/0/0；823首次并发、824同键冲突、825过期/完整性拒绝均1/0/0/0。交接同步826服务层、827真实HTTP均1/0/0/0，829既有MockMvc+MySQL5/0/0/0。828启动失败5项未执行业务，原证据保留。最终完整服务端146套件2943项0失败0错误1既有skip，编译通过。

[分页验收](R06_SOURCE_BINDING_PAGINATION_REPAIR_2026-10-02.md)、[原键验收](R07_FIRST_REPLAY_REPAIR_2026-10-02.md)、[同步验收](R07_HANDOVER_SYNC_STATUS_REPAIR_2026-10-02.md)。本批源码哈希/XML/日志在.local/autonomous-20261002/sync-status-repair/full-regression-1/，原HTTP响应和SQL投影在827；各attempt保留dump/原库。git-before/working-tree.patch保全全部预存差异，git-final保全实际提交/远端和磁盘。

## 仍未解决与边界

按实际原扣记账、Web耐久原意图、mapping3+/TEMP高轮次与跨来源、R1注入边界及R06/R07剩余功能；更正outbox消费者缺状态需在后续查验。TEMP拒绝/候选排除政策、抬头撤销/526、R05原资料/旧BCD、R09历史限制保持；现场/Keystore、独立JVM/OS死亡、断电/长时和完整UI不因本轮服务证据变为完成。goal active。

## 磁盘与环境

19330/19434已释放，系统MySQL不动；826至829认证停机，原datadir/dump/日志保留，证据删除0。D盘精确可用量见sync-status-closeout/git-final.json及各attempt/disk-after.json，高于20GiB才继续。826/827仅adb枚举前后相同，定向设备/AVD为0；828/829无设备动作。未合main/标签/Release/部署、未接原库/现网、未改生产默认开关。
