# 项目治理盘点与接手整理（2026-10-20）

本记录是治理工作结果，不是 D01–D18、O01–O05、U01–U04 或第 8 节的验收报告，也不改变任何业务范围、生产边界或 fail-closed 政策。

## 实际核实结果

### 工作树

在治理开始前重新读取了分支、HEAD、远端和完整 status，并为所有 dirty 文件保存了输入字节、长度和 SHA-256：

- 基线目录：`.local/goal-20261020-resume/governance-audit01/`
- `baseline.json`：分支/HEAD/远端、tracked/untracked 计数、差异补丁和 manifest 哈希。
- `git-status.txt`、`tracked-working-tree.patch`、`tracked-modified-deleted.txt`、`untracked-files.txt`：原始清单和补丁。
- `dirty-manifest.json`：逐文件路径、类别、字节数和 SHA-256。
- `pre-edit/manifest.json` 及同目录副本：本次可能调整的入口文档的原字节和哈希。

基线有 247 个 dirty 条目：70 个 tracked modified、177 个 untracked file，没有 tracked deletion，暂存区为空。dirty 路径中 docs 159 个（其中 history 154 个）、Android 40 个、server 39 个、tools 5 个、web 3 个，另有根目录 `AGENTS.md`。这不是一个可以安全批量 reset、按日期移动或按扩展名清理的工作树：源码和测试 WIP 与历史报告同时存在，单从路径无法证明批次归属。

实际观察到共享系统 MySQL 和 ADB server 的监听；没有项目 Java/Gradle、模拟器或 qemu 任务。共享服务只观察，未连接、停止或写入。治理期间没有启动测试、构建、设备操作或旧业务 run。

### 文档层

当前看板确实承担了唯一当前入口，生成事实块能由脚本校验，当前区也明确总目标仍未完成。路线和剩余计划已经声明“无状态”，政策页也把政策与实现状态分开。这些是可复用的基础。

但职责仍有三处实际混杂：

1. 根 `README.md` 虽写“无状态”，正文仍保留旧版本、旧测试数量、旧下一步和旧部署描述；首次浏览者会把历史叙述误当当前状态。
2. `docs/DEVELOPMENT_PROGRESS.md` 约 210 KB，远超短索引需要，包含大量逐轮叙述和历史语境；它仍可作为入口，但接手者必须读大量内容才能找到最近条目。
3. `docs/history/README.md` 约 172 KB，既是索引又混有接续说明和当前措辞；历史条目本身应保留，不能靠删除报告来“变干净”。看板的“前序历史事实”区也把一些历史环境放在当前页，虽然标注了历史，仍增加误读机会。

因此，问题不是证据缺失，而是“当前结论、历史结论、规则和继承 WIP”没有一张稳定的导航图把读者分开。

## 已做整理

1. 新建 [`docs/PROJECT_DOCUMENT_MAP.md`](../PROJECT_DOCUMENT_MAP.md)，明确每个入口的读者、允许内容、禁止内容和零记忆接手顺序。
2. 新建本报告，绑定治理前 manifest、补丁和入口文档原字节哈希；没有删除、移动、覆盖任何历史验收报告、原资料、原身份、APK、tar、日志或唯一证据。
3. 将根 `README.md` 的开头改成静态产品/仓库导航，去掉会随开发进度失效的旧“当前版本/下一步”段落；原 README 原字节已保存在 `governance-audit01/pre-edit/README.md`，并登记在 `pre-edit/manifest.json`。README 后面的 Demo 资料保留并明确为历史/演示资料入口。
4. 在看板当前区加入治理报告入口和暂停说明，明确本轮不推进业务验收；看板旧字节已在 `pre-edit/` 保全，修改后仍通过事实脚本检查。
5. 在历史索引顶部加入治理报告入口；没有把本报告或治理动作写成验收通过。
6. 对入口文档做了静态链接抽查，发现根 README 指向已不在工作树的旧 Demo APK；已改为明确的历史资料说明，没有伪造替代下载地址。

整理后的复核快照为同目录 `post-baseline.json`、`post-dirty-manifest.json`、`post-vs-baseline.json` 和 `post-tracked-working-tree.patch`；`post-baseline.json` 保存这些输出的最终 SHA。复核时仍为 250 个 dirty 条目（71 个 tracked modified、179 个 untracked file）；相对治理前只出现本报告、文档地图，以及 README、看板、历史索引的预期变化，未出现源码、测试或证据路径的意外变化。

治理结束时的实际进程、监听、分支、远端和 C/D/E 余量见 `final-environment.json`，SHA-256 为 `6FC0E30B2B52DBBBD40BA15ABC8DAF74F1CEF4C68992FEA8EAA318644E72F5DC`；共享 MySQL/ADB 仍只观察，没有项目 Java/Gradle/模拟器在途。

这些调整只改变文档导航和交接可读性，不改变产品源码、测试、接口、迁移、权限、开关或证据内容。

## 有意没有处理的事项

- 没有按日期、目录或文件名拆分/提交 70 个 tracked 修改和 177 个 untracked 文件。它们可能是继承 WIP、当前批次或唯一证据，无法从磁盘安全推断归属；错误拆分会破坏接续。
- 没有删除 `docs/history/` 的旧报告、看板快照、失败 XML、tar、APK、数据库或 `.local/` 数据。它们中有不可替代的失败和输入证据；即使内容可文字化，也应由下一次有明确消费者核验后按清理授权处理。
- 没有把 210 KB 进度索引或 172 KB 历史索引大规模重写。压缩、去重和移动每一条历史记录都需要逐条判断引用关系，当前没有足够事实证明可安全合并；本次只提供边界和入口。
- 没有重新运行 rescue 失败类、重跑任何旧 run、连接原库/现网、使用正式签名、启动模拟器、部署、Release、合并 main 或改变 `localDeletionAllowed=false`。
- 没有改变总目标状态；它仍由第 8 节全部闸门决定，治理工作不计入完成度。

## 需要用户决策的事项

1. 是否授权下一阶段按明确批次边界把源码 WIP 拆成若干开发备份提交；在获得授权前，所有 dirty 源码默认继续作为继承保护集。
2. 是否接受将旧根 README 的完整历史内容仅保留在治理快照/历史归档，并进一步把 `docs/DEVELOPMENT_PROGRESS.md`、`docs/history/README.md` 压缩成短索引；本次没有擅自做这一高风险重写。
3. 是否指定仍需长期保留的失败/原始证据清单。没有明确消费者和可重建条件的文件继续保留。

## 下一次接手顺序

先读 `AGENTS.md` → `docs/PROJECT_DOCUMENT_MAP.md` → `docs/GIT_WORKFLOW.md` → 看板 `CURRENT_STATE_START/END`。然后读取治理基线目录中的最新 manifest，重新核对实际 Git/写者/进程/监听/数据库/ADB/Keystore/磁盘，不按旧 PID 或旧交接页推定。确认唯一写者后运行事实脚本，再按看板“下一动作”读取对应路线、剩余计划、政策和专项报告。任何新工作先在新的 `.local/<任务根>/` 保存输入 SHA、命令、环境、退出码和输出哈希；不要从 history README 或旧 handoff 直接恢复任务。

## 当前项目状态与距离完成

治理前后的结论都保持：总目标仍 active/未完成。已有若干当前源 L1、合成输入和开发备份，但它们不能替代实际最终候选上的组合验收。现场/真机/原身份/外置介质/真实 HTTP/MySQL/最终候选、D01–D18 未完成项及第 8 节闸门仍需按看板和专项报告逐项核对。当前源 rescue 主机回归的失败原件仍是未验资料；本轮按用户指令暂停诊断，没有把失败改写为通过。

本次治理的收口标准是：所有变更有原字节/哈希可追溯，导航入口明确，未授权的源码和证据没有被移动或删除，下一次接手可从磁盘文件和实际环境重新建立事实。该标准已满足；它不等于业务开发或总目标完成。

## 用户授权后的 WIP 分类、索引压缩与清理

用户授权后先完成只读 WIP 分类，未提交任何源码或业务批次。逐路径表、输入 SHA、重启状态对照和分类口径见 `.local/goal-20261020-resume/wip-classification01/`：当前 250 条 dirty 中 236 条在重启时已 dirty，10 条为重启后新增，5 条治理变更路径可能叠加在继承改动上；74 条是开发备份候选，161 条需条件审阅，13 条明确暂不纳入备份批次。`RecoveredConfirmation`/`TemporaryHandover` 等取消的确认/激活范围被保留但标为不可提交；这不是范围恢复，也不表示验收通过。后续批次提交仍等待用户确认。

索引压缩后新增的两个 `docs/archive/2026-10-20/` 文件列在同目录 `post-governance-additions.md` 附表中，作为治理归档单独处理，不并入继承 WIP 批次。

进度索引和历史索引先以原字节归档，再替换为短入口：

- `docs/archive/2026-10-20/DEVELOPMENT_PROGRESS.md`：210555 字节，SHA-256 `080E4DA00340E3ED57A7C79C4D79678344286E4FC553BFDC78F8786D5C0F006B`。
- `docs/archive/2026-10-20/HISTORY_README.md`：172132 字节，SHA-256 `B6B1739DDAB53E5F25A620C3325DD091A062183A783FF234458CE07E6E231E14`。
- 新入口只列当前有效结论、未验边界和历史报告链接；旧新映射及原字节记录在 `.local/goal-20261020-resume/index-compaction01/pre-index-manifest.json`。`GOAL_*.md` 未移动或改写。

清理只触及已确认可重建且无在途消费者的缓存、构建中间物、Node 依赖、临时目录和两个 AVD `snapshots/default_boot`。第一批释放 `1544904704` 字节，第二批释放 `8590065664` 字节，合计 `10134970368` 字节；D 盘可用空间由 `44468994048` 增至 `54603923456` 字节。清单、依赖锁 SHA、命令、路径 containment、进程核对和结果哈希见 `.local/goal-20261020-resume/cleanup-governance01/`。

`.local/mysql/data`、U04 Room/隔离数据库、AVD 用户数据、私有目录、审计目录、APK、唯一回执/签名原件以及失败日志/XML/dump/binlog没有批量删除：其中存在未能绑定到明确停机回执或唯一证据消费者的对象，按“拿不准就保留”处理。详细保留原因见 `skipped-candidates.txt`。清理未运行测试、构建、HTTP、数据库业务写入或设备操作。

索引归档后，事实块中的 `dirty_entries: 251` 是 `version_facts.ps1` 按 `git status --porcelain` 的目录折叠计数；同一时点用 `--untracked-files=all` 展开为 252 条路径（71 tracked、181 untracked），其中新增 `docs/archive/` 目录展开为两个归档文件。两种计数的原始输出保存在 `cleanup-governance01/post-clean-status.txt`；不应把目录折叠数和逐文件清单混为同一事实。
