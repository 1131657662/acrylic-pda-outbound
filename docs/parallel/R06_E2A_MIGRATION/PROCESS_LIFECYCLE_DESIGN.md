# E2a 隔离 MySQL 进程生命周期修复

2026-09-23 执行结论：attempt4–6 均通过启动时专用账号、直接子进程身份核验与鉴权停机（marker 为 `stopped`，19329 零监听）；attempt4/5 合成 fixture 阻断已修，attempt6 在非空 V34 后被 V35 `CREATE TRIGGER` 的 binlog/SUPER 前置条件阻断。此设计的进程安全目标有作者动态证据，但不是 V35 迁移通过或独立组合验收，详见 `ACCEPTANCE_STAGES.md`。

2026-09-23。仅修改 one-shot 验收器，不修改生产 V35、业务 API、现网或已有三次失败目录。第三次失败的专用实例已按精确 PID、datadir 与监听核实后停止，但属非鉴权停机；原数据和证据保留。

## 启动与身份

1. 在新的精确私有目录先设置只允许当前用户的 ACL，再以 CREATE_NEW 写入独立库/账号的 bootstrap SQL；密码为本轮生成的随机十六进制值，不进入命令行或 Git。仅新的固定 datadir 可消费该文件，`mysqld --init-file` 在常规启动时执行；保持 `--skip-name-resolve` 和回环绑定，不打开 root TCP。
2. 以已启动 mysqld 的 PID 为锚，监听 PID 只能是该进程或其直接子进程。必须同时核可执行文件为指定 mysqld、命令行内精确 datadir、端口和回环绑定；任何不一致拒绝连接、清理或写库，留证人工接管。
3. 专用账号必须在服务就绪后通过原精确 JDBC profile 验证 `@@datadir`、`@@port`、schema 和 `CURRENT_USER()`，再开始 V34 seed。第一到三次目录、库名、marker 与证据路径不能成为新 profile 的候选。

## 停机与证据

正常路径使用专用账号的 SHUTDOWN 授权和私有 defaults 文件调用 mysqladmin，核父进程/监听子进程退出及端口空闲。异常路径只在再次核实精确身份后允许对所属进程作有界兜底停止；即使端口归零，也标 `stop-failed` 并保留原异常、suppressed 和未干净关闭风险。无法确认身份时不杀未知 PID。

先做纯身份矩阵和 ACL 重入测试、编译及差异检查，再分配 attempt4 的全新精确 schema、账号、datadir、storage、signing、private、evidence 和 marker。动态只运行一次；失败保存本次 XML/stdout、marker 与数据库，不重置、repair、重复 seed 或在原路径重跑。S1–S4 只按实际结果记通过。
