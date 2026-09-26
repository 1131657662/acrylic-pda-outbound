# 唯一未跟踪文件回收与归档（2026-09-27）

本页记录选项 A 的执行结果：把工作树清点中发现的 **32 个"只存在于某个工作树目录、不在任何提交里"** 的文件逐个落到安全位置，并回答 `D:\acrylic-entry-verify` 726 个缺失文件的性质。

## 1. 确认方法（每个文件都做过）

1. `git log --all --oneline -- <path>`：**32 个文件全部 0 命中** → 任何分支、标签、历史都不含该路径。
2. `git hash-object <file>` + `git cat-file -e <blob>`：**32 个文件的 blob 全部不在对象库** → 连"提交过又被丢弃"都不曾发生。
3. 提交前对这 30 个文本/源码文件做敏感内容扫描（私钥、口令、连接串）：**0 命中**。

## 2. 32 个文件的去向

### 2.1 提交进 Git（30 个）

| 来源树 | 文件 | 去向 |
| --- | --- | --- |
| datetime-display | `android/app/src/main/java/com/acrylic/outbound/pda/display/DisplayTime.kt` | 提交（同批含下面 9 个集成改动） |
| datetime-display | `android/app/src/test/java/com/acrylic/outbound/pda/display/DisplayTimeTest.kt` | 提交 |
| datetime-display | `android/app/src/test/java/com/acrylic/outbound/pda/display/SyncDisplayTimeTest.kt` | 提交（该测试依赖页面集成，见 2.3） |
| datetime-display | `docs/parallel/DATETIME_DISPLAY.md` | 提交 |
| datetime-display | `tools/datetime-tests.gradle` | 提交（已实测可用：`-I ../tools/datetime-tests.gradle :app:datetimeDisplayTest` 通过） |
| datetime-display | `web/datetime-display.config.ts` | 提交 |
| datetime-display | `web/src/shared/date-time.ts` | 提交 |
| datetime-display | `web/tests/datetime/format.spec.ts` | 提交 |
| datetime-display | `web/tests/datetime/page-fixture.ts` | 提交 |
| datetime-display | `web/tests/datetime/pages.spec.ts` | 提交 |
| datetime-display | `web/tests/datetime/serve.py` | 提交 |
| r06-design | `docs/parallel/R06/README.md`、`DESIGN.md`、`INTERACTION.md`、`DEPENDENCIES.md`、`ACCEPTANCE.md`、`PROGRESS.md` | 提交（6 个） |
| r07-design | `docs/parallel/R07/README.md`、`DESIGN`… 共 13 个 md（`ACCEPTANCE_PLAN`/`BASELINE_UPDATE`/`CORRECTION_RESULT_DESIGN`/`CROSS_TASK_ALIGNMENT`/`DEPENDENCIES`/`EVIDENCE_BASELINE`/`HANDOVER_DESIGN`/`INTERFACE_PROPOSAL`/`PROGRESS`/`SOURCE_RESULT_CONTRACT`/`STAGED_IMPLEMENTATION`/`UI_DESIGN`） | 提交（13 个） |

提交前每个文件的 SHA-256 在"源树文件"与"落盘文件"之间逐一比对一致。

### 2.2 归档到工作区外（2 个）

| 来源树 | 文件 | 为什么不提交 | 去向 |
| --- | --- | --- | --- |
| r06-e2a-foundation | `.local-e2a-vector.log`（29,635 字节） | 运行日志，不是源码 | `E:\acrylic-archive-unique-20260927\from-32\r06-e2a-foundation\.local-e2a-vector.log` |
| r06-e2a-protocol | `docs/parallel/R06_E2A_PROTOCOL/bridge-classes/e2a/review/ProductionBridge.class`（4,689 字节） | 编译产物；其源码 `docs/parallel/R06_E2A_PROTOCOL/ProductionBridge.java` **已在基线**，可复现 | `E:\acrylic-archive-unique-20260927\from-32\r06-e2a-protocol\ProductionBridge.class` |

### 2.3 同批准移的 9 个"已跟踪但未提交"改动（不在 32 之内，但同属唯一内容）

`codex/datetime-display` 树另有 9 个**已跟踪文件**带未提交改动（页面集成，共 17 增 16 删）。其中 8 个用 `git apply` 干净应用；`android/.../ui/OrderCards.kt` 因冻结批次已改过该文件而冲突，按同一语义手工合并 1 行（`离线期限` 改用 `DisplayTime.formatDateTime`）。

结果：**Kotlin 侧 7 个新测试（含 `SyncDisplayTimeTest`）全部通过**；若不带上这 9 个改动，`SyncDisplayTimeTest` 会失败——这是本批发现的真实依赖，已在提交说明中写明。

## 3. 归档清单（工作区外）

位置：`E:\acrylic-archive-unique-20260927\`（磁盘 0，与仓库所在磁盘 1 不是同一块物理盘），含 `SHA256SUMS.txt`：

```
A9679DBB0BC84D715108D25375389E5F1D87B140531B394270E83340951683C1  29635  from-32/r06-e2a-foundation/.local-e2a-vector.log
89B8F356A093E015813D0026555EEC2989E9D4E52439C85992EA2B9192A157B0  4689  from-32/r06-e2a-protocol/ProductionBridge.class
66A279AEFBB213F791739EFE45606FC04098D4C18B032A3B629ED198DF3717D2  24120  patches/datetime-display-tracked-modifications.patch
7186240C919AB53BBFEDFFEA980F42AB514E92DAE3DEC784D27C2E552176A271  7793608  patches/entry-verify-tracked-modifications.patch
E5C97015ECEABC402FF2B2B7989A0CC14E74214A3ADAE125144BD38FB8CF5618  541  unregistered-entry-verify/.codex-patch
70EBAD5CAF7C3D6781EB926EE631B822AADD03AC680E5FE2000C1E5FFEFA97E3  124  unregistered-entry-verify/gradle.properties
```

额外归档两项（清点时的"未注册检出"，不在 32 之内，但同属只在单份目录里存在的内容）：

- `patches/entry-verify-tracked-modifications.patch`：`D:\acrylic-entry-verify` 的 11 个已跟踪改动。
- `unregistered-entry-verify/`：该检出的 2 个未跟踪文件，其中 `gradle.properties` **不在基线**，`report-rendering/gradle.properties` 属本机构建配置。

注意：两份补丁第一次是用 PowerShell `>` 生成的，落成了 UTF-16，`git apply` 无法使用；本批已用 `git diff --binary --output=` 重新生成为字节正确的补丁（首个字节 `d i f`），并已实测 `datetime-display` 那份可用。

## 4. 验证（本批实跑）

| 检查 | 结果 |
| --- | --- |
| Android 主机单测（含恢复的 7 项） | `:app:testDebugUnitTest --rerun-tasks` → **113 XML / 451 项 = 447 通过 / 4 真实 HTTP 条件 skip / 0 失败 / 0 错误**（基线 444 项） |
| 恢复的专测 init 脚本 | `-I ../tools/datetime-tests.gradle :app:datetimeDisplayTest` → BUILD SUCCESSFUL |
| Web 类型检查 | `npm run typecheck`（`vue-tsc --noEmit`）→ exit 0 |

## 5. 回答：`D:\acrylic-entry-verify` 的 726 个"缺失"文件是"本来就不该有"还是"该有但丢了"？

**结论：属于"本来就不该有"——那是一个刻意为服务端入口接线验证而做的部分副本，不是损坏的完整检出；而且即使按最坏解释，也一个文件都没丢。**

证据：

1. **缺失是有模块形状的**：726 个缺失全部集中在与本次验证无关的模块——`android/` 328、`web/` 179、`demo/` 97、`contracts/` 58、`tools/` 41、`deploy/` 21，`server/` 只有 **2** 个。而磁盘上实际存在 1,241 个文件，其中 `server/src/main` 488、`server/src/test` 418、`report-rendering/src` 43、`docs/parallel/*` 约 180。巧合的误删不会呈现"整个 android/web/demo 树都没有、而 server 树几乎全在"的形状。
2. **与当时的验收记录一致**：看板记载该目录是"隔离副本 `D:/acrylic-entry-verify`（受测 10 文件与共享树逐字节相同）"，用途是 `RecoveryAdmissionConfiguration` 入口接线的隔离整体测试——只需要服务端模块。
3. **11 个"已修改"文件也吻合**：均为服务端受测文件（`temporary/command/*`、`e2averify/*`、`TemporaryLinkChecks`、`server/build.gradle`、`.gitignore` 与 R06_M2B 证据契约），即"复制服务端模块 → 就地改受测文件"后的必然结果。
4. **共享 gitdir**：它的 `.git` 指向 `D:/acrylic-pda-outbound/.git/worktrees/r06-e2a-migration`，与已合并的服务端树共用管理目录；也就是说它不是在别处克隆出来的完整检出，而是就地物化的部分工作区。
5. **决定性一条：726 个文件 100% 可从 Git 取回**。逐个用 `git rev-parse HEAD:<path>` + `git cat-file -e <blob>` 校验：**缺失 726，不可取回 0**。抽样（`android/app/build.gradle.kts`、`web/package.json`、`demo/build.gradle.kts`、`contracts/openapi.yaml`、`tools/install_demo.ps1`、`deploy/test/activate.sh`、`AndroidManifest.xml`、`web/src/main.ts`）全部可取回且字节数正常。

因此无论把它读作"刻意只放服务端"还是"曾清掉无关模块以省空间"，**项目内容没有任何丢失**：这些路径都在基线与 origin 里，`git checkout` 即可还原。真正需要留意的反而是它的**反向**风险——它有 11 个改动与 2 个未跟踪文件是不在基线里的唯一内容，本批已通过 3 的两份补丁归档保住。

## 6. 本批边界

- 未删除、未移动任何工作树目录，未执行 `worktree remove/prune`、`branch -d`、`clean/reset`。
- 未把 2 个不可提交文件（日志、class）写进仓库；只在仓库内提交了**清单与索引类**内容。
- `ProductionBridge.class` 未提交是有意的：其源码已在基线，提交二进制产物会违反仓库排除构建产物的约定。
