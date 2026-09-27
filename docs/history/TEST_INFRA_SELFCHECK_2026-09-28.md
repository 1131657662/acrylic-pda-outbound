# 测试基础设施自验与经验契约（2026-09-28，自主运行 v11 / E2）

本页记录**测试工具本身的缺陷模式**与自验要求——这些缺陷会让"断言通过"变成假象，
本会话在取证时真的踩到两次（A2）。后续任何新增/修改 harness 都按本页自验。

## 一、工具清单（`tools/*.ps1`）

| 工具 | 用途 | 文档头 | 环境守卫 |
| --- | --- | --- | --- |
| `e2a-provision-and-run-106.ps1` | e2a 单类真服 IT（一 attempt 一库） | ✅ SYNOPSIS + `#requires` | `OUTBOUND_COOKIE_SECURE=false`；`-NonE2a`/`-CoordinatesOnly` 才导出库坐标 |
| `r08-provision-and-run.ps1` | R08 专用库（23308 / `acrylic_r08_it`） | ✅ | 渲染器 `renderer-home` / `browsers` 必须传，否则 503 |
| `e2a-batch-it.ps1` | 批量：每类一 attempt + XML 聚合 + 分类 | ✅ | 透传 `-ExtraConstructorArgs` |
| `e2a-cross-source-verify.ps1` / `e2a-android-link-l4.ps1` / `e2a-link-counterproof.ps1` | 专项（跨源、Android L4、link 反证） | 部分缺 SYNOPSIS | 各自守卫 |
| `version_facts.ps1` | 版本事实单一来源（`-Write`/`-Check`） | ✅ | 校验事实块漂移 + 看板/进度上限 |
| `build_demo.ps1` / `install_demo.ps1` / `package_test_release.ps1` / `pull_evidence.ps1` / `development_server.ps1` | 构建/打包/取证据/本地服务 | 缺 SYNOPSIS | — |

**待补**：无 SYNOPSIS 的 7 个工具补参数文档头（低风险，未做）。

## 二、本会话实测的两个"假通过"模式（**必须防**）

### 1. 管道喂 SQL 被静默截断（attempts 393/395）

`$sql | & mysql …` 这条写法在前半段执行成功后**静默丢掉后续语句**，**退出码仍是 0** ⇒
`Assert-Zero` 抓不到 ⇒ 账号实际缺少该权限，而 provisioning 报告成功。
后果：`E2aJobFenceHttpIT` 因 `performance_schema` 无权限报 1142（391/392），被误当成产品/夹具问题查了两轮。

**自验要求**：provisioning 后**必须探测一次实际效果**（本 harness 现探测
`SELECT COUNT(*) FROM performance_schema.data_lock_waits`），而不是只看退出码。
另两个语法坑：SQL 注释用 `--` 在管道路径下会吃掉后续语句；用 `#` 会被 `cmd` 当注释 ⇒ **统一用 `/* */`**。

### 2. 环境守卫按"库坐标逐字比对"（多例）

`R08CenterFixture` / `R08ExportIT` / `IdentityLockFixture` / `ExportFacts` 的 IT 守卫比对的是
**完整 URL 前缀 + 库名 + 账号名**（例如 `jdbc:mysql://127.0.0.1:23308/acrylic_r08_it?`，**带问号**、
不接受任何后缀）。因此"改库名做隔离"或"少写查询参数"都会让整类**静默跳过或静态初始化失败**。

**自验要求**：新增 IT 前先读它的 `static {}` 守卫，按守卫的**字面值**准备环境；
一类一 attempt（每次 DROP+CREATE 同名库），不要用库名后缀做 per-class 隔离。

### 3. 附图：报错行号错位（LF-only 脚本）

LF-only 行尾的 `.ps1` 在 PS 5.1 下会**报错行号错位**（实测把第 151 行的异常报成 145 行，信息为
`Empty path name is not legal`），排查成本很高。**自验要求**：`tools/*.ps1` 统一 CRLF。

## 三、证据与诊断输出的契约（本会话确立）

1. **诊断必须真的打印出来**：`A3 残余②` 的教训是诊断写进了 `loginBody`，随后被同一方法里的后续赋值覆盖
   ⇒ 报告里什么都看不到。现约定：夹具诊断逐条 `System.out.println`（进 XML `system-out`），
   并用不会被覆盖的字段留存关键交换。
2. **失败信息要自证**：断言消息里带上原始响应体、cookies、`SPRING_SESSION` 对拍等；
   IT 侧尽量把产物写到 attempt 目录（marker / `.txt` / 渲染文件）而不是只留在内存。
3. **红的就是红的**：失败 attempt 目录、XML、日志一律保留（本会话 attempts 365-401 中所有红均留档）。
4. **探针要能识别**：临时探针统一带可从代码里检出的标记（本会话曾用 `A3-DIAG`，收口后改名 `WEB-FIXTURE`），
   并在收口时**回滚或改名**，否则会与正式诊断混淆。

## 四、尚缺的自验（未做，建议下一步）

- **已补一半**：新增 `tools/it-coverage.ps1`（只读）从 JUnit XML 汇总"哪些 IT 类跑过、结果如何"，
  并把"最新一次即红"与"历史红但已复跑"分开显示。**已知局限（必须知道）**：它只读
  `it-batches/<batch>/xml/` 与当前 `build/test-results/`，而 **e2a harness 不按 attempt 归档 XML**
  （每次运行覆盖），所以**"直连 harness 的复跑"不在它的视野里** ⇒ 它的 `LATEST-RED` 列表应读作
  **"需要重新确认"队列**，而不是"当前失败"。实测首跑：102 个 IT 类、133 个套件，其中 16 条 LATEST-RED
  全部是历史红（背景见下）。
- `tools/e2a-batch-it.ps1` 的"每类一 attempt 号"没有机读台账；`it-coverage.ps1` 的 `-Csv` 可导出
  attempt,class,tests,failures,errors,skipped,state，供跨会话累计（未接 CI）。
- 非 e2a 类的**环境守卫清单**已写进 `it-coverage.ps1` 输出尾部（R08 族 / IdentityLock 族 / ledgerverify 族）。
- **harness 建议**：把每次运行的 XML 复制进 attempt 目录（如 `attempt/test-results-<class>.xml`），
  即可让覆盖率工具看到直连 harness 的复跑，消除上面那个局限。

## 五、首跑发现（供接续者核对，非本轮新缺陷）

`it-coverage.ps1` 首跑列出 16 条 `LATEST-RED`，逐条对照文档后**均为"历史红 + 已有绿复跑"**，例如：
`E2aHandoverHttpLayerIT`（237 红 → **374 绿**）、`E2aTemporaryCommandWebHttpIT`（273 红 → **363/364 绿**）、
`E2aJobFenceHttpIT`（265 红 → **401 绿**）、`E2aClosedProjectionIT`（238/281 红）、
`IdentityLock*`（449/463 红 = 环境守卫）、`IdentitySetPcIT`（303）、`LedgerHistoricalReadIT`/`LedgerReadAccessIT`
（335/336 = ledgerverify 专用环境）、`SubmissionArchiveIT`（297 红 = 本会话已修的 `36d2368` 真回归，316 复验 14/14 绿）。
⇒ **没有发现新的当前失败**；但也说明"哪一类现在是绿的"目前**只能靠文档叙述**，这是上面第 4 条要补的洞。

