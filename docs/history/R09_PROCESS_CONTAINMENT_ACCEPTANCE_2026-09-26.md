# R09 导出进程容器阶段验收

2026-09-26。两树仍为主树 `codex/development@dccb73b10acbb63020beb3cf827b3b4580d3ed1c` 与服务端树 `codex/r06-e2a-migration@568683c1a1f8257ad0596a654c6b3558c767ed95` 加未提交改动。本项是共享门禁前置；R09及完整R01–R09目标未完成。

## 完成标准与实现

Windows导出父进程死亡后不得留下可继续写入的renderer后代；归组成功前不允许renderer读取快照或生成文件；正常结束以Job实际活动进程归零为准。`ExportWindowsJob` 使用不可继承的KILL_ON_JOB_CLOSE Job Object，禁止breakaway。`ExportRendererProcess` 先建Job、启动等待stdin的renderer、归组，再发送START。`ReportCli --supervised` 在任何快照/浏览器/输出操作前验证握手，旧三参数及confirmed-v1入口保持兼容。

Linux沿已配置systemd容器边界，将两个服务模板的KillMode=control-group与SendSIGKILL=yes显式写出；只在WSL独立用户服务验证整个cgroup停止，不部署项目服务。单个导出取消仍依赖已观察后代退出，不能据此宣称Linux每次导出都有独立容器。

自审检查原生结构大小/字段偏移、非继承句柄、归组前死亡、归组后未放行死亡、ShutdownHook与正常finally竞争、线程中断恢复及drain失败禁新导出。x64以外Windows拒绝运行。Job失败不降级为无容器导出。父进程死亡后的内核清理是异步动作；后续恢复门禁仍须验证容器及DB事务实际排空，不能只凭Java租约自动释放宣称无写者。

## 当批结果

证据根 `outputs/r06-product-20260925-01a0d8fa/`：

- `windows-job-focused-02.log`：9/9通过，无skip，包括真实owner→child→leaf、owner强杀、归组前后死亡、正常返回时全部退出、已有排空边界。测试原件保留服务端`.local/windows-export-jobs`和`.local/export-process-drain`。
- `windows-job-renderer-01.log`：监督入口测试通过，installDist成功。
- `windows-job-full-01.log`：服务端660项=659通过、1符号链接条件skip，0失败/错误；bootJar成功。report124项中两PDF测试缺本树Chromium，不能算完整通过。失败XML完整保留。
- `windows-job-report-full-02.log`同样两项失败：本机init脚本早期环境设置被项目配置覆盖，XML保留。改为执行前设置现有Playwright 1.55.0对应Chromium1187路径，`windows-job-report-full-03.log`及XML：124/124通过，无skip；未改产品代码来跳过PDF检查。
- attempt74独立非空V34→V38 MySQL及真实HTTP：Java1/1、浏览器1/1无skip；TEMP普通/短装XLSX、扫码Excel、PDF、汇总、失败重试与FORMAL/PC兼容，使用本批installDist。已认证shutdown，原库/密钥/凭据留在服务端`.local/e2a-cross-20260925-74`，公共证据见`windows-job-attempt74/`。
- `systemd-drain-01.log`：隔离Linux用户systemd/cgroup两个真实进程忽略SIGTERM，正常stop和主进程SIGKILL两种情形均确认全部PID消失，2/2通过无skip。原件`/var/tmp/acrylic-r09-systemd-drain-01`及`systemd-drain-evidence.tar`保留；复查没有活动测试unit。
- `containment-source-manifest.json`覆盖两树本项源码/测试/构建、R09文件、服务模板及JAR；全部改动清单见`containment-final-main/server-status.txt`。这份manifest固定当前输入，不把旧消费者/排空manifest当作本批整仓输入。

作者和自审均为同一助手。HTTP/浏览器为组合验证，原身份为合成fixture。无Android变更，未重跑APK/lint；无独立人员、PDA、真实断电或项目Linux143服务部署验收。

## 下一项与边界

接跨进程写者生命周期租约、API/Worker/事务/文件全部入口及UUID/DB投影。Windows容器和Linux隔离服务证明不替代完整恢复排空。58–74隔离库均已shutdown，下一run75先核目录/监听。main、发布、生产、原库均未操作，七类生产决定/关闭能力仍不开放，localDeletionAllowed=false。
