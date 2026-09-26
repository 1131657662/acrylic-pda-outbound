# R09 已追踪导出进程退出确认

2026-09-26，范围为共享恢复门禁前置修复。完整共享门禁、孤儿进程隔离、R09及R01–R09总目标未完成。

- 输入两树仍为 `codex/development@dccb73b10acbb63020beb3cf827b3b4580d3ed1c` 与 `codex/r06-e2a-migration@568683c1a1f8257ad0596a654c6b3558c767ed95` 加既有未提交改动，无提交/推送/main/部署。
- 修复 `ExportRendererProcess`：取消、超时、丢租约时强制停止后，等待renderer与已追踪子进程真正退出；线程中断不能提前结束等待，退出后恢复中断标志。10秒内不能确认停止则固定错误并关闭该实例后续导出。终止逻辑在 `ExportProcessDrain`。
- 原测试返回后另等5秒会掩盖提前返回；改为真实父/子进程返回当刻即检查全部停止。边界进程替身验证延迟退出、中断和超时，无额外等待冒充产品行为。测试源码与失败案例留 `.local/export-process-drain/`。

## 当批验证及修复

证据根 `outputs/r06-product-20260925-01a0d8fa/`：

1. `process-drain-focused-02.log`，5/5无skip。首轮3失败是嵌入fixture变量名 `java` 遮蔽Java包，已改为executable；`focused-01`日志/XML保留。
2. `process-drain-server-full-01.log`，656=655通过、1符号链接条件skip、0失败/错误；完整XML独立保存。bootJar成功，`acrylic-outbound-server-0.1.0.jar` SHA256 `535da2fb7937e79fe60206bba958c2d7fc3695490d35e04b1cbdfbbfd2972d53`。
3. attempt73独立19330 MySQL，非空V34→V38原资料；真实HTTP TEMP普通确认/短装的XLSX、扫码Excel、PDF、汇总、重试/失败恢复，FORMAL/PC兼容；Java集成1/1无skip，Playwright浏览器1/1通过。原凭据/密钥/HTTP资料保留服务端`.local/e2a-cross-20260925-73`，公共证据仅复制XML、完成标记与已核身份shutdown。
4. attempt73首次启动检查将MySQL批模式转义路径与PowerShell反斜杠路径比较失败；业务测试未开始。核对实际listener2880/父9100、指定datadir后改用 `--raw` 及两侧路径标准化，接续同实例完成验证，没有重置/重建/覆盖资料。原启动脚本和日志保留；结束认证shutdown，无19330/19434/19435/19436监听；共享3306 PID5448未动。
5. `process-drain-source-manifest.json` 858个服务端源码/测试/构建及JAR项，SHA256 `1c2b5c52a8430c639a653b4fbb332aa31ac8ec34fa01d2eb882725d202c2e865`；两树完整未提交路径见 `process-drain-final-main/server-status.txt`。

作者/自审为当前单助手；MySQL/真实HTTP/浏览器为本批组合验证，身份与业务fixture为合成。没有Android修改/APK/lint、真机、现场或独立人员验收，不能拼接旧报告为当批通过。

## 明确限制与下一项

此修复只保证已观察的进程退出。父进程骤死/未观察孤儿、完整进程树隔离及库外租约仍未验，重启本身不证明前实例子进程全部停止。下一项需要独立文件写者租约/OS进程容器，随后接API/Worker/事务/迁移排空与UUID/DB投影。普通生产决定/放行/关闭仍无开放，`localDeletionAllowed=false`。
