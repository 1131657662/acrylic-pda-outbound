# 三个未合并提交的合并与 R08 定向验证（2026-09-27）

本页记录选项 B 的执行：把三个"有唯一未合并提交"的工作树分支按顺序合入 `codex/development`，以及第三步要求的定向验证与两个环境发现。

## 1. 合并结果（按用户指定顺序）

| 顺序 | 来源 | 合并提交 | 实际改动 | 冲突处理 |
| --- | --- | --- | --- | --- |
| 1 | `0b1094e`（`codex/sixth-wave-integration`） | `f196887` | 6 文件：新增 `.gitattributes`，改 `deploy/test/*.service`、`activate_backend.sh`、`test_backend_activation.py`、`verify_backend_release.py` | 无冲突（自动合并） |
| 2 | `94305a7`（`codex/r09-v33-recovery`） | `1e98e44` | 17 文件，**全部在 `docs/parallel/R09/`** | `docs/parallel/R09/README.md` 取并集（两侧内容都保留） |
| 3 | `f051535`（`codex/r08-export-rendering`） | `8bb3e57` | 7 文件：`ExportDownloads.java`、新增 `R08AuthorizationRaceIT.java`、Web 导出中心 3 文件、`R08/EXPORT_CENTER.md`、`center.spec.ts` | 无冲突（自动合并） |

### 1.1 `.gitattributes` 的影响面核对（用户要求）

- 内容恰好两行：注释 + `deploy/test/*.sh text eol=lf`。
- 仓库中**全部 6 个 `.sh` 文件都在 `deploy/test/` 下**（`activate.sh`、`activate_backend.sh`、`backup.sh`、`migrate.sh`、`service_checkpoint.sh`、`test_service_checkpoint.sh`），`git check-attr` 逐个确认 `eol: lf` → 属性覆盖 100% 的 `.sh`，无其它影响面。
- 合并后 `git status --porcelain` 与 `git diff --stat HEAD` **均为 0 项** → 没有文件被意外"重新规范化"（合并前这 6 个文件已是 LF，因此没有需要重写的对象）。
- 注意：该属性**不解决**仓库整体的 CRLF 警告（那只覆盖 `deploy/test/*.sh`）；仓库仍无 `core.autocrlf` 之外的通用换行策略。

### 1.2 R09 README 并集核对

解决后按关键词逐个确认两侧内容都在：`V34专项记录`、`V33 TEMP 原来源`、`不是可信 VERIFY 或灾后重建`、`2026-09-26 增量`、`2026-09-25 增量`、`完整灾后恢复依赖 R05` 各出现 1 次；全库无残留冲突标记。

### 1.3 是否碰生产代码（用户要求）

第二步 `1e98e44` 的 17 个文件**全部位于 `docs/parallel/R09/`**，非该目录的改动 **0 个**（含 `ops/*.py`、`tests/*.py`，均为运维脚本与测试，不是服务端生产代码）。第一、三步按各自提交范围改动 `deploy/` 与 `server/exports`+`web/exports`。

## 2. 第三步定向验证

### 2.1 `R08AuthorizationRaceIT`

结果：**tests=9 failures=0 errors=0 skipped=0**（14.3 秒），9 个用例全部通过：

- 计划提交路径 6 例：`retry=false/true` × `revoke=session/role/warehouse` → 期望 401/403/404；
- 下载路径 3 例：`revoke=session/role/warehouse` → 期望 401/403/404；
- 同时断言未入队（`jobs.findByDedupeKey` 为空）与未记审计（`REPORT_EXPORT_RETRIED` / `REPORT_EXPORT_DOWNLOADED` 计数为 0）。

运行环境（本机新建的隔离实例，验证后已停止，datadir 保留）：

- MySQL 8.0.46，专用端口 **23308**，库 `acrylic_r08_it`，datadir `.local/mysql23308-r08/data`；R08 夹具的白名单正好包含 `jdbc:mysql://127.0.0.1:23308/acrylic_r08_it?`（该条不校验用户名）。
- `OUTBOUND_MIGRATE=true`，Flyway V1–V41 在空库上全部执行成功。
- 服务端签名密钥由 `tools/CreateDevelopmentSigningKey.java` 生成到 `.local/r08-signing/`。
- 临时实例已停止；既有 mysqld（PID 4412/5448）全程未动。

### 2.2 Web 构建

`npm run build` → `✓ built in 3.81s`，**exit 0**；仅有既有的 chunk 体积提示与插件耗时统计（信息性）。

## 3. 两个环境发现（下次复现必须知道）

### 3.1 V35+ 迁移需要 `log_bin_trust_function_creators=1`

在默认 `mysqld`（binlog 开启）上跑 Flyway 会在 **`V35__source_evidence_intake_receipts.sql`** 失败：`Error Code 1419`（创建触发器需要 SUPER 或 `log_bin_trust_function_creators=1`）。V35–V40 都含 `CREATE TRIGGER`。

- 仓库既有夹具的做法一致：`E2aMysqlLauncher.java:77` 与 `tools/e2a-cross-source-verify.ps1:62` 用 `--log-bin-trust-function-creators=ON`；`docs/parallel/R09/ops/lab.py:109` 用 `--skip-log-bin`。
- 另有现成的启动期预检：`E2aMysqlTriggerPreflight`（要求该项为 1，否则报 "binlog-enabled trigger creation needs an approved log_bin_trust_function_creators policy"）。
- 首次失败是我起库时漏了该参数，属于**环境配置问题**，不是迁移缺陷；失败证据保存在 `.local/mysql23308-r08/mysqld.log`。

### 3.2 渲染器 `installDist` 必须与 `report-rendering` 源码同步重建

R08 夹具创建 XLSX 作业；未配置 `outbound.exports.renderer-home` 时 POST 直接 503 `EXPORT_UNAVAILABLE`（文档 `docs/R08/WEB_INTEGRATION.md` 第 3 步已写明）。配置后仍失败时，作业返回 `RETRY_SCHEDULED`，DB `background_job.last_error = EXPORT_RENDER_FAILED`，而渲染器自身的错误只写在作业目录的 `renderer.log`：

```
Exception in thread "main" java.lang.IllegalArgumentException: 用法: [--confirmed-v1] <snapshot.json> <xlsx|scan-xlsx|pdf> <output-file>
```

原因是 `report-rendering/build/install/acrylic-report-rendering/lib` 里的 CLI 是 **09-22 的旧构建**，而当前源码（09-27）会传 `--supervised`（`ExportRendererProcess.java:53`、`ReportCli.java:9`）。执行 `gradlew -p report-rendering installDist` 重建后，9/9 全部通过。

**结论：`renderer-home` 必须指向与当前 `report-rendering` 源码同批构建的 `installDist` 产物，否则会出现"配了路径但仍失败"的假象。**

## 4. 本批边界

- 只做了合并与验证：未改任何源码/迁移/契约内容（合并为原样并入），未开启任何生产能力（`localDeletionAllowed=false`、七类生产决定与 TEMP 归档边界不变）。
- 未删除或移动任何工作树；三棵来源树（`r08-export`、`r09-v33-recovery`、`sixth-wave-integration`）在合并后**可以进入选项 C 的删除候选**。
- `R08AuthorizationRaceIT` 以外的 R08 集成用例、Android 侧与真机、部署均未在本批运行。
