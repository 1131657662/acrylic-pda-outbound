# E07/D14 原检查点与 binlog 重建开发切片（2026-10-15）

本批只验证历史开发检查点重建到新隔离库。输入 HEAD `b8bf3db81680457eecfa6cde72b51ff7ed4f9430`，分支 `codex/development`，树 `D:/acrylic-pda-outbound`。原源 `.local/e2a-cross-20260925-1233/` 的合成身份、开发签名和业务事实不冒充原 PDA、现场或生产事实。总目标 active。

## 影响与输入

| 改动路径 | 影响层 | 必跑清单 | 复用依据（路径与 SHA） |
| --- | --- | --- | --- |
| `tools/r09-e2a-entry-rerun.ps1` | 一次性隔离 MySQL 运行器 | 解析；新库/端口/datadir 身份；原 dump 导入/binlog 重放；精确触发器/全表/文件/迁移历史；写前负例；dump 守恒、认证停机、零监听 | 产品源码未改，5,245 项输入 SHA256 见 `.local/goal-20261015/e07-cross602/input-before.json`；最终运行器 SHA256 `623A96279ECF5400B892CA29D8F5D014A8135D3F68E6CD9822766D0D2FD6F2AC` |
| 本报告、看板与索引 | 验收记录 | XML/退出码/marker、独立比较、dump/hash、继承改动核对 | `.local/goal-20261015/e07-cross603/independent-verification.json`、`preserve/result.json`；产品按上述逐文件 SHA 复用 |

全部 tracked/untracked 路径、二进制 patch、旧看板、唯一写者/远端/磁盘/监听及源 393 文件长度/hash 在 `e07-cross602/`。393 是本批实际源清单，不沿用其他源的 347 计数。唯一写者为本聊天 `01a13a57-8082-7dd2-a08f-987483dcda4b`；清理聊天最终释放回执和实际 idle 状态均已复核。

## 失败与修复

- 1551 在脚本参数范围校验时失败，尚未 provisioning；指纹在 `.local/goal-20261015/e07-cross1551-input/attempt-parameter-validation.txt`。预先创建的 `.local/e2a-cross-20260925-1551/tmp/` 保留，不复用编号。
- 601 因缺少 `private/root-client.cnf` 在业务导入前失败。原 XML/stderr/datadir/日志及认证停机记录保留在 `.local/e2a-cross-20260925-601/`。为新实例补生成与既有 loopback bootstrap 一致的客户端配置，不改变产品鉴权。
- 602 三项写前拒绝通过且 dump 已导入，随后 109 个触发器的 `DATABASE_COLLATION` 精确断言失败。独立读回逐字段比较证明实际差异仅库默认排序规则；独立 JSON 的数字/string 采集差别先转字符串核对，未误计为数据库差异。原 provisioner `tools/e2a-provision-and-run-106.ps1` 已显式采用区分大小写的排序规则，旧 rerun 未指定而继承 MySQL 默认值。只修复新实例建库配置，保持原精确断言。
- 602 已有写入，完整保全后才分配 603：147 表、55 非空表 JDBC/dump 前后守恒；11 个 schema 表数前后相同。dump SHA256 `1449cf77f3f951a674131b8ac7fc0076a485dfa4f9962ebae635baec686c785b`，认证停机、19330 零监听、330 文件 hash/长度清单。datadir/binlog/XML/日志和三类拒绝副本保留；`preserve/result.json` 的 PASS 仅指保全，602 业务仍失败。

## 最终实际结果：603

命令见 `.local/goal-20261015/e07-cross603/run.ps1`，明确调用 `r09-e2a-entry-rerun.ps1 -Run 603 -TestClass com.acrylic.outbound.e2averify.E2aReceivedCheckpointIT.rebuildsActualCheckpointWithOriginalBinlogAndConservesAllFacts`。TEMP/TMP/Java 临时目录仅对子进程指向 D 盘短目录，执行前已复测磁盘并估算增长。

- Gradle 成功，XML 1 测试/0 失败/0 错误/0 跳过，`testExit=0`、runner exit 0，开始/完成 marker 均存在。运行器在 MySQL 子进程退出后返回，测试结果以 `exit.txt` 与 XML 共同核对。
- 原 dump SHA、受控权限历史与 binlog checksum 验证通过，实际导入/重放。坏 dump pin、重封摘要但 CRC 错误的 binlog、权限边界变化三项均在目标写入前拒绝，目标保持空表。
- 146 个业务表逐列/原字节、schema、独立列出的迁移历史及 19 个 storage 文件与原源最终快照一致；109 个触发器全字段精确一致。仅沿既有规范处理 SHOW CREATE TABLE 等价字符集写法，未放宽排序规则/权限/事实断言。
- 独立 Python 再比较快照/触发器/负例/XML；源 393 文件长度/hash 不变；5,245 个输入仅运行器发生计划内变化，继承 Android/UI/工具/私有请求保持。独立工具消费者不等于独立人员审查。
- 独立 JDBC 保全连同迁移历史共 147 表、55 非空表、338 行；dump 前后与 storage 守恒，10 个空伴随 schema 仍空。dump SHA256 `35b2718d22ed2769cca1f8a482d63622f525b4ece525295083e2b65ae140527d`；replay SHA256 `1bec54dd82bc82875909ba6e75e2f49b7facc9b0d4b7928b7541ce89d2c2a0ba`。
- 实例身份核对含 DATABASE/CURRENT_USER/端口/datadir/server UUID/log_bin。认证停机，19330/19434 零监听，357 文件 hash/长度及全部原件保留在 `e07-cross603/preserve/`。

## 自审、边界与接续

作者/自审为当前助手。无产品 Java/Android/Web/Python、契约或迁移改动；不将历史 schema 当作现候选非空迁移或完整客户端组合。该开发备份不计普通准入、E07 L2、D14/D15 整体、D18 L3 或第8节通过；七类生产决定、TEMP 归档及本地删除边界不变。

明确路径提交推送 `codex/development`，实际提交/远端/全部改动/hash/写权/环境存 `e07-cross603/post-commit.json`，不循环自引用提交。下一项补原已收归档的闭合事务消费者与主管故障围栏；E06 L2 Chrome/设备审批阻断保持，推进其余独立 D/O/U。外置保管、异地、原机/原身份和生产 RPO/RTO 未证实，所有原失败保留。
