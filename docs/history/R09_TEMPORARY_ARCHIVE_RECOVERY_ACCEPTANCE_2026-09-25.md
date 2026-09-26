# R09 TEMP V2 / FORMAL V1 归档快照恢复验收

日期：2026-09-25。当前助手实现、自审和隔离复验；无独立人员复审。持续目标 active，未上线。

## 验收范围

本项完成首映射、首轮、零候选 TEMP 成功归档 V2 的原正文、成功回执、确认命令三文件恢复核验，并沿数据库原 BLOB 核正常确认、独立主管短装批准及批准引用。另以同一恢复集中的 FORMAL V1 两文件样本证明兼容分支。无新生产 handler、迁移、API、Room 或放行能力。

Python 从冻结计划与 ACTIVE 扫描独立计算产品数量、重量、短装、序号、计划摘要、候选上下文；核原来源、资格、仓库、原操作人和设备、命令域摘要、原响应、签名、事件原字节、幂等与审计记录。历史批准按原签名权限快照核验，不要求批准人今天仍启用。未知签名 key、未来 mapping/轮次/归档版本失败封闭。

## 实现与环境

- 主树 `docs/parallel/R09/ops/` 新增 TEMP 专用消费者，扩展原 `submission_archives.py` 的 V2 分流；全局 `consistency.inspect` 仍仅支持 V21/V28/V32/V33，未放宽到 V36。
- 服务端测试树新增归档恢复夹具、全 SQL/files 快照恢复器、专用 Python 调用和 IT；工具 `-ArchiveRecovery` 才创建独立恢复 schema。修复测试应用启动时同名配置拼接问题，改为后参数覆盖前参数；生产配置未改。
- 专用 ACL、datadir、schema、账号、19330，非空 V34 seed→V36，binlog 开启。共享 3306 未动。应用关闭后 dump，再恢复到**同一专用 MySQL 实例里的另一空 schema**，并复制全部业务文件及合成签名密钥到受保护目录。不是两个独立 MySQL 实例。
- 全表逐列字节、DDL、Flyway 历史、全部文件 hash 比对；只规范化 MySQL dump 多余的 `CHARACTER SET utf8mb4` DDL声明。故障注入仅作用于恢复 schema/files，SQL rollback、文件 finally 原样还原；完成后再次比对完整快照。

## 当次验证

- Java `compileTestJava` 通过；最终 attempt47 集成测试 **1/1**。
- 源/恢复 Python 消费者均通过：3 份归档、8 个文件引用，其中 TEMP V2 两份、原决定三份（正常确认、短装批准、批准后确认）。完整快照 **102 张业务表、15 个文件**，另比 Flyway 历史。
- 隔离恢复故障测试 **11/11，无跳过**：TEMP 三文件各自缺失/损坏、FORMAL 两文件缺失、合法重签错误来源/资格/命令引用/长度/版本/权限/留存/额外字段、坏事件原件、缺审计、错幂等引用、坏批准 BLOB、未知 key；另验证今天撤权不改历史批准。
- 恢复后实际启动应用；真实 cookie/CSRF 登录，命令 original/detail HTTP 原字节一致；TEMP 内部归档原请求重放一致；FORMAL 提交成功 GET 与归档确认 POST 真实 HTTP 一致。
- R09 Python 全套 **85 项：74 通过、11 条件跳过**；跳过的是要求本次恢复库的同一组 11 项，已由 attempt47 实际执行。最后日志 `r09-archive-python-reviewed.log`。测试用例内部子场景不额外计数。
- 两树 `git diff --check` 通过（存在既有 LF/CRLF 提示）；新增 Python 文件均少于200行、函数体不超过30行。作者自审修复了公钥读取失败后的连接释放以及签名内外 bool/int 宽松相等问题。

## 失败保留

1. 首次使用 C:/Python314 未安装 PyMySQL，67项发现中1导入错误；后改用已有 R09 专用 venv，不安装共享包。基线71项通过属于新增恢复测试之前。
2. attempt44：源/恢复和10项故障通过，恢复应用启动因重复 storage 参数被逗号拼接而失败；修测试启动覆盖后新 attempt45 通过（2 TEMP、12文件、10故障）。
3. attempt46：新增 FORMAL 样本准备漏 RequestIdFilter，审计 request_id 非空约束拒绝；补齐夹具后新 attempt47 全部通过。
4. 44–47 均已认证 shutdown，数据库、私有 dump/密钥、日志全部保留，不复用。最终已无专用监听或 Java/Python 测试在途；共享3306/PID5448父4412未动。

## 证据与 Git

- 最终 run：`D:/acrylic-pda-worktrees/r06-e2a-migration/.local/e2a-cross-20260925-47`。
- XML SHA256：`c0901481c6d140a918c1f0c2966e498e9c133e51bdbf6fa8767a0f089c33d242`。
- `archive-proofs.json` SHA256：`24ebb6773200f2eda7364581fd5cfed9cb216b4696ad22492c6a1aeb2ad8f1e2`。
- 主树 `outputs/r06-e2a-takeover-20260925-01a0d612/r09-archive-attempt44-*` 至 `attempt47-*` 保存非敏感证据；`r09-archive-source-manifest-reviewed.json` 绑定最终825个源码/资源文件。受保护 private/restored/config 的密码、私钥和数据库 dump 不复制到普通证据或 Git。
- 主树 `codex/development @ dccb73b10acbb63020beb3cf827b3b4580d3ed1c`；服务端树 `codex/r06-e2a-migration @ 568683c1a1f8257ad0596a654c6b3558c767ed95`。完整未提交/未跟踪见同目录 WORKTREE_STATUS。没有提交、推送、两树整合、main合入或部署。

## 范围限制与下一项

V34种子含迁移专用合成占位记录，本项证明全快照保持与新增真实业务归档闭合，不是所有 V36 业务语义或灾后事实重建验收。共享控制/fence、截点外 FH 高水位、历史 keyring、R07 调整链、RPO/RTO、异地/防覆盖、真实PDA/Keystore/OS死亡断电、长时与现场/权限/审批均未验或未开发。

下一回到 R06 Android 产品成功原件消费接点：先读上传/刷新/取消结果路径，保持关闭的生产决定与 TEMP 归档入口，不把接收历史成功回执当作放行。非零候选/映射2+/高轮次、排除/拒绝/实物处置/续作及 R07–R09 其他可独立事项继续推进；未定权限政策不得臆断。
