# D11 R08 导出集成套件 L1 开发备份

## 范围与输入

本单元只收口 `server/src/test/java/com/acrylic/outbound/inventory/R08ExportIT.java` 的完整隔离集成套件。它补 E05 记录的“未跑完整 R08ExportIT”缺口，不把本单元外推为 D11、E06/E07 组合、D18 L3 或总目标完成。固定树为 `D:/acrylic-pda-outbound`、分支为 `codex/development`；接手输入快照为 `.local/goal-20261017-resume/input43.json`，SHA-256 `2e4ddaf1a5fc08d8e50735746076ffbe2db8a0a47b4eee1da7fa2f06a42594f5`。版本事实见看板生成事实块。

### 改动路径 → 影响层 → 必跑 → 复用依据

| 路径 | 影响层与必跑 | 输入绑定 |
| --- | --- | --- |
| `tools/r08-provision-and-run.ps1` | R08 隔离 MySQL、真实 Spring Boot/MockMvc、renderer、端口停机；删除自动清理旧 datadir 的副作用 | 修改后 SHA `2b7fe3cd9026013f4daaf4217f74bcd6ac147f1c562b1e3a21585d786a05dac8`；只影响 harness 收尾，不改变产品入口 |
| `server/src/test/java/com/acrylic/outbound/inventory/R08ExportIT.java` | 14 项真实 HTTP/MySQL、任务、导出文件、幂等、权限、篡改、PDF/XLSX 正向及拒绝路径 | SHA `2fd624b7fdf7987724aee73de6326a80a43d5ae5fa18e54b1d573b6dc90bb3c3`；未改本单元产品测试源码 |
| `server/src/test/java/com/acrylic/outbound/inventory/R08CenterFixture.java` | R08 数据源及身份 fixture 守卫 | SHA `5a4e7eef2818a5b9d0b87c136150e18c4abb22fa1c315c3556e7ca3c9249f22a`；未改本单元 fixture |

首次完整执行 678 使用旧工作树浏览器路径，14 项中 13 项通过，PDF 项返回 503 `EXPORT_UNAVAILABLE`；该运行器失败日志和 678 全部 storage 原件保留，未把运行器缺口记作产品失败。复核确认主树浏览器目录为 `report-rendering/build/browsers`，未重新使用旧工作树或旧数据库。

## 实际执行

运行器为新隔离编号 680：MySQL `127.0.0.1:23308/acrylic_r08_it_680`、用户 `r08_it_680`、数据目录 `.local/r08-it-680/data`，端口与库均在执行前确认未占用；renderer 使用主树安装目录，浏览器使用主树 `report-rendering/build/browsers`。数据库以真实 root 连接就绪，再用专用用户查询 `@@port,DATABASE(),CURRENT_USER()`；测试结束强制停机，postflight 记录 23308/19461/19387 均无监听。

`R08ExportIT` 完整结果：14 tests、0 failures、0 errors、0 skipped，Gradle exit 0。覆盖 PC XLSX 冻结原事实、PDA PDF 异常/日期、SCAN_XLSX 人员快照、同键幂等与异文拒绝、同键并发单任务、创建者/仓库/撤权、未完成订单拒绝、篡改 artifact/snapshot、短装 VOID 证据、占位人员拒绝及 ASCII 人员正向。`server/build/test-results/integrationTest/TEST-com.acrylic.outbound.inventory.R08ExportIT.xml` SHA-256 `444bbb923154046a2302b130868f34cbd375e331e30e358f07b1bd11e278710f`。

关键证据均在 `.local/r08-it-680/`：

| 证据 | 字节 | SHA-256 |
| --- | ---: | --- |
| `integration-test.log` | 1695 | `a4c8716cd1a1460195932be612821282ccd6c4321ff523488c8238bd9a6e9baf` |
| `preflight.json` | 542 | `131792ea4c0c9c943dff7b240cc32aff6b3dcf497c9f00c417d3be578bacd402` |
| `postflight.json` | 85531 | `1fcb80a42588811e771e58ec8a25d0b7cf57cb89033d907d18879b0fb5b5ae41` |
| `manifest.json` | 81518 | `5d1e392d4170d6508dac88f5589800018bbfb1bec3d78e8779e48bbacee1d166` |
| `storage/verified-pc.xlsx` | 14010 | `c5051fb4db0c20f70f5a0e9e591852875ae2827ab167a5ed953ad0a351c46bd0` |
| `storage/verified-pda.pdf` | 362317 | `094b0e89ffa9d06895d87c6f55c54eb11fa64fd2d15a4715ad56348ea69b305d` |
| `storage/verified-pda-scan.xlsx` | 4389 | `96d0d161911c9efe6635155419a2e872f5d5d0381bb7c2fdbe0dafa5489086cd` |

680 datadir 结束后仍存在，postflight 记录约 236 MB、零目标监听；Gradle daemon 仍是共享构建进程，未连接业务库。C/D 余量、进程和监听以 preflight/postflight 为准，重型任务结束后未清理 680 证据。

## 运行器清理异常与边界

679 的目标 PDF 补验已经通过，但旧 harness 的自动维护阶段删除了 495 个旧 attempt datadir 并报告释放约 79.07 GB；该事实不能撤回，也不把被删除 datadir 当作可取回证据。678/679 的日志、marker、storage、密码文件和失败输出仍保留，删除事件单独计为运行器缺陷和证据损失。已移除 `r08-provision-and-run.ps1` 的自动清理调用；后续 datadir 只可由独立、明确路径的清理步骤处理，不能随测试自动删除。

本报告不证明 Python 独立消费者、Android、真实现场身份、Chrome、非空迁移、E06/E07 组合或 D18 L3；这些仍按矩阵及看板列为未完成。生产决定、TEMP 归档、`localDeletionAllowed=false`、原库/现网和发布边界均未改变。
