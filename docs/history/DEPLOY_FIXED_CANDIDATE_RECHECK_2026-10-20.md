# 固定候选部署整改复验结论

日期：2026-10-20。结论：**仍不放行，不部署**。本轮仅完成测试语义修复、部署工具链和整包自洽性；可信非合成数据库闸门阻断，普通测试仍有跳过，不记整体预检通过。

## 唯一候选与来源

- 候选提交：8cb51ded3ef2cd6cd45eaca09d8947ce068e89cd；codex/deploy-preflight-20261020；树D:/acrylic-pda-outbound/.local/worktrees/deploy-remediation-20261020。
- 上游文档备份64eaf4919d10c03d2f611c72cfc51ff0a314f209已先提交推送；工具/租约测试修复a95d66326c3b2568b56d4a7bbab81cec942e009d，后续夹具等待修复到上述唯一候选。旧69d98046诊断、a95探索及主开发分支其他通过不拼入候选结论。
- 全部5856份tracked源文件原字节SHA保存在fixed-input-8cb51ded.json；构建结束前后逐项一致，git树干净。Android版本、契约与迁移版本事实仅见看板生成块。
- 固定外部输入：获授权docs/sku.txt SHA256=3b6b8902184718c6714fd1ea00f87cd30c8d8527e744dea93c7e16e319495833；字体SHA256=2c76254f6fc379fddfce0a7e84fb5385bb135d3e399294f6eeb6680d0365b74b。未提交原样本。

## 各闸门实际结果

| 子项 | 同一候选的实际结果 | 限制 |
| --- | --- | --- |
| 四处原断言 | 审计旧mock未馈入流式回调；三租约错误把有活动操作时的延迟退出当死亡；均为测试缺陷，生产实现不改；完整server含修正后类零失败 | 详见独立语义审查；租约文件/OS夹具非跨JVM崩溃或恢复业务验收；主开发会话另外的子JVM测试不拼入此候选 |
| 服务端普通完整测试 | 311 suite，4776 tests，4757通过、0失败/错误、19跳过；Gradle实际执行 | 普通test既有规则排除IT；19跳过为条件原件/SKU模板与Windows符号链接权限，未执行不能记通过 |
| renderer测试 | 16 suite，138项全部通过、无跳过；二轮实际重执行 | 首轮2失败为独立树缺匹配Windows浏览器，原失败XML/日志保留；补环境后仍同一候选/源字节，server在二轮UP-TO-DATE没有被算作再次执行 |
| 构建 | bootJar和完整renderer installDist完成；同SHA成功构建回执绑定32份构建产物 | 不构建或发布Web/APK，不等同LinuxJava进程运行验收 |
| Linux部署工具自测 | 18项零失败/错误/跳过，exit0 | command-boundary桩，只修改临时目录；无系统服务激活或DB连接 |
| checkpoint/shell | 七场景全过；六个shell bash -n通过 | systemctl边界桩，不是实际API/worker启停 |
| Linux浏览器 | 既有WSL about:blank截图退出0，完整Linux浏览器依赖已装配 | 此次候选包内LinuxJava报表CLI/API/worker仍未启动；本地WSL仅Java17，不能冒充Java21部署验证 |
| 候选包完整性 | 新包生成exit0；独立Linux解包重新算归档/manifest/source及647文件SHA，685成员、5856源清单自洽；shell全LF、脚本/ELF/renderer启动器执行位正确 | 自洽性证明来源与字节绑定，不等同可信签名、真实库升级或部署批准 |
| 真实非合成库 | **阻断/未验**；本机没有符合闸门要求的真实数据副本，详见本地溯源报告 | 未连接/启动任何数据库，未新导出；无可信输入不运行升级、恢复、API/worker，不用合成数据代替 |
| test部署 | **未执行、未授权** | 预检任何局部通过都不能自动部署 |

### 失败与同SHA复验

a95冻结首轮服务端4776/0/19而部署18项1超时，不能当最终通过。只把测试启动子进程等待上限15→60秒，业务断言和产品readiness不改，提交8cb后重跑部署全18项。8cb构建首轮完整server成功但renderer2失败；保留原XML，安装匹配Windows浏览器后同SHA二轮renderer138实跑通过，sourceHashes不变。构建日志明确保留首轮exit1与二轮exit0，不掩盖失败或将UP-TO-DATE伪写为重跑。

所有结果属于作者执行/自审。独立解包是独立字节消费者，不称独立人员审查。

## 不可变候选包

本地路径：D:/acrylic-pda-outbound/.local/deploy-remediation-20261020/packages/preflight-8cb51ded-20261020-01.tar.gz。

- 归档：803808146字节；SHA256=8539177d1274864b94772c9941a1ff3dfab9735dda545b4fca988f3232c37ad0。
- manifest.json SHA256=3a0102f95c4c4ea44ba2200f9105e74e1f0fbba1e7a36c3d48d07574274425bb。
- source-sha256.json SHA256=ea431b20a4288a3d59204b3f7374beb6c0490275e3e74ffa2267536ff70e5c72。
- package receipt、tar外SHA文件与独立解包fixed-8cb51ded-independent-package-verification.json三方一致。
- JAR内完整迁移清单/每文件字节与包内源摘要一致；server内renderer依赖与外置installDist JAR一致；Linux浏览器与打包Playwright driver派生revision相符。
- 包未上传服务器，未复制运行配置/私钥/DB/真实业务数据；没有Release、标签或main变更。该包仅保留本机，Git推送不是二进制异地备份。

## 命令与证据索引

全部位于D:/acrylic-pda-outbound/.local/deploy-remediation-20261020/：

- fixed-input-8cb51ded.json：源与样本/字体输入、构建命令；fixed-8cb51ded-build-receipt.json：完整源/32产物/实际构建结果。
- fixed-8cb51ded-build.log/.exit、fixed-8cb51ded-build02.log/.exit：首失败与同SHA环境补齐后成功；固定server/renderer原XML分别在attempt01与final目录，不覆盖失败。
- fixed-8cb51ded-test-summary.json：server/renderer逐suite统计；fixed-8cb51ded-deployment.log/.exit：Linux18项；fixed-8cb51ded-checkpoint.log/.exit：七场景及shell。
- fixed-8cb51ded-package01.log/.exit、packages/*.receipt.json、*.tar.gz.sha256：打包及外部摘要；fixed-8cb51ded-independent-package.log/.exit和verification.json：独立解包/摘要/执行位。
- windows-browser-install01与linux-browser-install01日志/退出码；activate-eol-normalization.json：工作树CRLF→已提交LF原字节（无Git内容修改）。
- local-data-*：本地候选文件名/元数据、私有文件逐SHA和额外溯源；重点内容见DEPLOY_LOCAL_DATA_PROVENANCE_2026-10-20.md，数据库/业务内容不入Git。
- 旧assertions-exploratory01、activation/deployment exploratory与fixed-a95失败证据全部保留。不清日志/XML/隔离库，不停止他人服务；本轮无数据清理。

## 接续

只按新可信来源证明重新打开真实数据库闸门；候选来源先列清单给用户确认。若之后代码/构建输入变化，冻结新SHA并重跑对应整套，不能把本次绿色拼接到新版本。主开发树有独立未提交WIP与后续提交，本候选没有自动吸收，也未声称是最高开发HEAD。部署目标实际运行SHA此次未查询，不重用历史f196作为实时证明。

开发备份推送和最终环境结果见本次收口证据；本报告本身后续文档提交不是新业务候选。main、正式签名、生产开关、原库/现网、test部署及Release继续关闭；localDeletionAllowed=false。
