# 协作断点看板

<!-- CURRENT_STATE_START -->
版本事实只引用主树看板生成事实块；本归档不复制版本数字。
B3 来源、停止保全与候选消费开发中，尚未提交。范围见[无人值守计划](history/AUTONOMOUS_PLAN_2026-10-05_UNATTENDED.md)，当次失败、修复与证据见[自主进度](history/AUTONOMOUS_PROGRESS_2026-10-06.md)，替换前 B2 交付断点见[归档](history/ORCHESTRATION_STATE_before_b3_checkpoint_2026-10-06.md)。

## Git、写权与环境
- 树 D:/acrylic-pda-outbound，分支 codex/development，HEAD/远端 dc7a01ad4395cb1263ad3e87927ac8cbd39d3d8d；当前聊天唯一写者；服务端回归在 `b3-server-regression/` 执行中。
- B3 B0 为 `.local/autonomous-20261005/b3-source-preflight/`，跨日完整快照为 `b3-checkpoint-20261006/`；保存全部脏路径、diff、哈希、监听和进程。13 条受保护既有路径哈希未变，不代提交。
- B3 未提交：Android 回执验签与四项恢复/HTTP/迁移测试；服务端回执持久化/完整性、三项测试（含新增往返测试）；来源证据契约与本批文档。精确路径见快照和自主进度。
- 1005～1007 隔离 MySQL 均 dump/认证停机，失败库保留；19330/19434/19435/19436 无监听。3306/13316 既存 MySQL 未操作。D 盘可用 25452441600 字节，高于 20 GiB 阈值。

## B3 当次证据与缺口
- 1006 真实 HTTP 1/1/0/0：签名 VERIFY 为 VERIFIED_COMPLETE_AT_STOP，数据库却写 PARTIAL，原件 GET 被完整性校验拒绝。修正按原状态持久化并严格核对原文/数据库/签名；历史坏记录仍拒绝，不重签。Android 兼容合法 VERIFY 状态，冻结队列和全部能力保持关闭。
- `b3-draft-http-1005/`：服务端包装与 Android 各 1/0/0/0；停止写者、原件队列、丢回执、Room/服务重启、身份暂停和坏资料拒绝。发生在状态修复前，不能替代最终组合。
- `b3-receipt-focused-v2/`：306/0/0/1、50 XML，唯一既有 Windows 符号链接条件跳过；签名原字节、两种合法状态、状态篡改/非法状态/缺引用拒绝。
- `b3-unsubmitted-http-1007/`：1/0/0/0；原件 GET、同键 POST、来源缺失/伪造/错仓/损坏拒绝及占用守恒。
- `b3-android-focused-v2/`：9/0/0/0、2 XML；完整停止 VERIFY 丢回执重开恢复、STOP 冒充状态拒绝与非空来源绑定迁移。
- 1005、1007、服务端定向、Android 定向各轮前后分别 1860/1861/1341/549 条哈希一致；轮间有修复，不能称最终输入完全一致。
- 失败轮保留：1006 原件 GET 409；服务端定向 306/5/0/1 为 Mockito varargs 测试取参错误；Android 首次 filter 启动失败 0 测试。均保留日志/XML/库，已在新目录定向复验。
- 1008 E2A Android 真实 HTTP 组合：包装/Android 各 1/0/0/0；五次丢回执、Room 重开及原字节恢复。1009 非零候选包装 1/0/0/0、Python 消费者 54/0/0/0，21 条候选五页遍历与未解决候选确认拒绝。两轮各 1861 条哈希无漂移。
- 1010 精确 SQL 故障组合 1/0/0/0：OPEN/RAW/PAGE/STOP/VERIFY 写后报错，18 张表完整回滚、原键恢复/重放通过；1862 条源码无漂移。Android SQL 回滚定向 23/0/0/0。1008～1010 均已 dump/认证停机。
- `b3-android-full/`：137 XML、577/0/0/7，专用实时环境用例条件跳过；Debug APK、签名/metadata/lint 通过，lint 0 error、199 warning；549 条源码前后无漂移。APK/哈希见自主进度，未安装/发布。
- 作者实现、自审和执行，使用生成身份、JVM Room、回环 HTTP 与独立 Python 消费算法。1008 后只有测试改动，926 条产品输入哈希与其组合输入一致。

## 已交付与下一步
- B1 交付 b55b4796a77c7938cab643cc8b21f56c1c897e45；B2 交付 b90f0a7f2a98daf89899a160de5eaa673ba64380，均已推送。B2 Android 574/0/0/7、服务端 3103/0/0/1、1004 双消费者各 1/0/0/0、普通集成 41/0/0/0；细节与 APK 见前日进度，不能替代 B3 验证。
- 下一步完成服务端全套及既有续作集成回归；脚本核对全树分配后使用新隔离轮次，满足标准才提交推送 B3。
- 真机、APK 安装、两个物理设备、原身份/Keystore、现场网络、旧客户端、断电和长时未验。TEMP 高轮次、候选排除/跨来源权限、LINK currentEffect 契约遗漏（B8）、R1 生产放行、R05 原资料/旧 BCD/526、R09 恢复限制继续单列。
- 生产 gate 默认值与七类生产决定边界保持，localDeletionAllowed=false；不合 main、Release、部署、接原库/现网或删除 .local。长期目标 active，阶段交付不代表 R01–R09 全部完成。
<!-- CURRENT_STATE_END -->

> 长期规则见 AGENTS.md；版本事实见生成块；文档提交自身 SHA 以 Git 回执核对。
