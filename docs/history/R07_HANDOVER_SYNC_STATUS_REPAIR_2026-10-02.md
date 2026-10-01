# R07 交接同步状态修复验收（2026-10-02）

主树codex/development，输入dce5fe7。版本事实见看板事实块。实际提交/远端回执见.local/autonomous-20261002/sync-status-closeout/git-final.json及Git历史。

## 改动与自主决策

激活生成的HANDOVER_ACTIVATED载荷写入status=ASSIGNED；消费者对该事件兼容历史缺字段载荷。只有JSON对象缺少status时回退，显式空值、null、数字、布尔、未知状态、非对象或损坏JSON仍拒绝；其他事件缺状态不获豁免。保留历史outbox原文，不回写消息，不改迁移、生产开关或契约。

选择同时修生成端和读取端：单补字段无法消费已持久化旧消息。单测覆盖生成端契约对应的新状态、历史缺字段和拒绝边界；真实HTTP测试直接投入合成新旧消息，验证消费者，不冒充命令生产链或真实设备身份。

## 当次验证

- 826真实隔离MySQL服务层1/0/0/0：REQUEST/APPROVE/PREPARE不增outbox，ACTIVATE仅增一条，status为ASSIGNED，拒绝再次激活不增加。此时消费者是初版；随后收紧后没有改激活端，该输入边界单独在最终回归核对。
- 最终离线146套件2943项0失败0错误1既有skip；主/test编译通过。SyncOrderStatusTest单独12项均通过。完整源码哈希逐项复核未变；预存更正链IT差异未提交。
- 827真实隔离MySQL与真实HTTP1/0/0/0：6次PDA同步GET。新旧消息都返回ASSIGNED；原游标重读data完全相等、同事件/序号，最新游标无重复；两条消息各仅发布一次，投影为最高版本。baseline/modern/legacy/replay原始HTTP响应与projection.txt保留。
- 829既有PdaSyncIT为MockMvc+真实隔离MySQL，5/0/0/0：基线稳定、高水位/增量一次发布、旧代次/坏游标/跨命名空间/未来游标/过期基线拒绝、固定基线分页行为通过。没有改变既有断言。

## 失败、纪律与限制

第一次调用IT包装器误指向旧离线目录，被源码哈希守卫在建库前拒绝；改用当次检查后826通过。828包装脚本把$run?useSSL插值为变量，URL截断，应用启动失败，5个测试未进入业务；修正包装器、自检URL后新库829通过，失败原件/库/日志保留。整文件重写过程一次apply_patch多操作同路径被工具拒绝，无写入；早期一次行级补丁违反用户工具纪律，已用.ps1整文件重写取代，后续只整文件写入。

作者/自审/执行为同一助手；合成会话/fixture消息不替代现场身份、独立消费者、PDA、Keystore、独立JVM死亡、断电/长时或完整UI。同期更正outbox缺status是独立已发现消费者风险，后续记账修复需核查。

826至829均认证停机，完整dump、原datadir及全部失败证据保留，删除0。19330/19434释放，系统MySQL未操作。D盘精确余量见git-final.json；高于20GiB才继续。未合main、打标签、Release、部署、接原库/现网或改生产默认值。R06/R07整体未完成。

证据：.local/e2a-cross-20260925-826至829/，.local/autonomous-20261002/sync-status-repair/full-regression-1/、before-826/827/828/829/，本批sync-status-closeout/。
