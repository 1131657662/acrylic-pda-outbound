# 验证基础设施 R02：服务端真实 HTTP 构造器（2026-09-27）

独立子项（10 轮时间盒第 ② 项，预算 4 轮，实用 2 轮）。**这是对 R06 attempt 123 产物 `tools/e2a-provision-and-run-106.ps1` 的提炼，不是重新设计。**

## 一条命令做什么

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File tools/e2a-provision-and-run-106.ps1 -Run 145 -Tests '*E2aTemporaryLinkConfirmIT'
```

依次：① 按端口杀旧 mysqld（先 netstat 确认占用者）→ ② `mysqld --initialize-insecure`（exit code 非 0 立即中止）→ ③ 起实例（`--bind-address=127.0.0.1 --port=19330 --log-bin-trust-function-creators=ON`，**不带 `--skip-networking`**）→ ④ **真连接**就绪判定（`mysql -uroot -e 'SELECT 1'`，不用 `mysqladmin ping`）→ ⑤ 生成 64 位 hex 口令并建库建用户（写入口令文件，不打印）→ ⑥ 设 `E2A_CROSS_VERIFY/RUN/PASSWORD` 跑选定的 gated IT（**真服由 IT 在 19434 起，真 HTTP 由 IT 驱动**）→ ⑦ 解析 XML 报 `tests/failures/errors/skipped` 与逐例结果 → ⑧ **停库并确认端口释放**。

## 参数

| 参数 | 说明 |
| --- | --- |
| `-Run <n>` | attempt 号；目录 `.local/e2a-cross-20260925-<n>`；**重跑守卫**：已有 `test-started.marker` 则拒绝并提示换号 |
| `-Tests <glob>` | 跑哪个 gated IT（默认 `*E2aTemporaryLinkConfirmIT`） |
| `-Port <n>` | 默认 19330 |
| `-Workspace <path>` | 默认守卫星树 `D:\acrylic-pda-worktrees\r06-e2a-migration` |
| `-ProvisionOnly` | 只准备库与服务，不跑 IT（实例保持运行） |
| `-KeepServerRunning` | 跑完不停库（默认**停库**，属构造器契约） |

## TEMP 来源路径（本轮已固化并自验）

`-Tests '*E2aTemporaryLinkConfirmIT'` → 临时单冻结上传 → 主管 LINK（写 V41 映射）→ NORMAL_CONFIRM → 逐条落点断言 + 反例（关联后改计划必须 409）。这是 R06 九轮积累的路径。

**FORMAL 来源路径不在本轮**（R07 五轮未成，属 R03 目标）。

## 本轮实测（自验，独立于业务）

```
RUN 145  RESULT tests=1 failures=0 errors=0 skipped=0 time=15.726s
  [pass] linkedSourceConfirmsOverRealHttpAndEffectsLandOnFormalOrder()
markers: started=True completed=True
teardown: port 19330 free = True
gradle exit=0
```

自验成立的含义：**一条命令**完成"空库 → 真服 → 真 HTTP → 一条业务命令 → 断言 → 停库"，且脚本自身在每一步检查 exit code。

## 未覆盖边界（如实）

- FORMAL 来源构造器：**未做**（R03 目标）。
- 脚本仍是"跑既有 gated IT"的形态，**不是**任意命令注入器；新业务命令需要先有对应 IT。
- 未做跨服务端/Android 统一框架；未重构既有 e2a harness（`E2aCrossSourceProfile`/`E2aMysqlLauncher` 等仍原样）。
- 未改生产代码。
- 端口/数据库名/账号仍由 attempt 号派生，未做多实例并行编排。