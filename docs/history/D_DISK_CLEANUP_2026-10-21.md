# D 盘清理记录（2026-10-21）

清理范围为 `D:` 上本项目 `.local` 的过期测试运行产物，以及项目外可重建的 Maven 输出和旧应用日志。日期只用于筛选候选，没有按日期整目录删除。分支 `codex/development`，输入 HEAD `2fc5483e83691c3a3cd9fe6fe3336aaf112c8fd8`；清理前 Git 状态和 diff 快照保存在 `.local/disk-cleanup-20261021/`。

## 清理结果

- `.local/goal-20261012`、`.local/goal-20261013` 中 35 个旧 `server.jar` 测试运行产物已删除，SHA-256 各不相同、无硬链接。逐文件路径、大小、时间和 SHA 在 `old-server-jars-plan.json`；删除回执在 `old-server-jars-results.json`。两处测试目录中的其他日志、配置、数据库和证据未纳入。
- 项目外删除两处 Maven 编译/测试缓存和 `D:\logs` 内 7 个 2026-06-04 以前的 JimuReport 日志，共 7,044 个文件、86,099,068 逻辑字节；逐文件校验与结果见 `external-cleanup-plan.json`、`external-cleanup-results.json`。
- `D:\paper-mes-unified\target` 的两个 JAR 保留且哈希复核未变；该目录只清除了编译类、生成文件和测试报告。`D:\paper-mes-release-fix\target` 已移除；两项目源码、未提交改动、依赖、配置和前端输出均未触碰。
- 删除的外部测试 XML 已汇总：paper-mes-unified 的 Surefire 2,279 项中 0 failures、1 error、1 skipped；Failsafe 1 项通过；paper-mes-release-fix 的 1,942 项中 0 failures、0 errors、1 skipped。唯一历史 error 为 `ProcessOrderCustomerSpecReissueBusinessFlowIT.issuedOrder_whenPrintedCustomerSpecificationChanges_keepsV1AndAutomaticallyIssuesV2`，类型 `IllegalStateException`。这是历史结果，不代表本次清理验证。
- 七份旧应用日志共 25,599,111 字节，日期为 2026-05-03 至 2026-06-03；保留每份 SHA、时间范围、级别统计和异常类型摘要，重复的 `NotLoginException` 记录不再保留原始行。
- 本次生成的 4 份临时扫描 JSON 随后删除，共 3,547,408 字节；基线快照、逐文件 JAR 哈希和外部清理回执保留。

## 空间与保留项

第一批操作前 `D:` 可用 4,020,076,544 字节；清理结束复测为 13,802,049,536 字节（约 12.85 GiB）。三批分别实测可用空间增加 9,819,013,120、97,824,768 和 3,555,328 字节；合计约 9.24 GiB。首末净增 9,781,972,992 字节（约 9.11 GiB），期间可用空间有并发波动。

原始 AVD 用户数据/身份、Keystore、签名和回执、隔离数据库、APK、未提交源码、近期 `.local/goal-20261020-resume` 与 `.local/goal-20261021-resume` 内容均保留。MySQL 服务和监听未操作。D 盘共享 pnpm store、Android 离线包、其他项目的临时依赖/工作树及回收站内容因存在共享或归属不确定性而保留；D 盘尚有内容未清理。

当前看板由并行开发写者维护，本记录未改写 `docs/ORCHESTRATION_STATUS.md`。没有提交或推送。

## 续清理（2026-10-21）

- `.local` 全盘只读盘点运行 892.1 秒，枚举 8,244,786 个文件、405,688,262,387 逻辑字节；66 个路径在扫描时已消失，均为 Android `dexBuilder` 中间目录的并发变化。完整结果在 `.local/disk-cleanup-20261021-continuation/local-size-audit.json`。日期仅用于筛选，没有按日期删除目录。
- 三份闲置 AVD `sdcard.img` 使用独占句柄执行 `FSCTL_SET_COMPRESSION 0x9c040 format=1`。每份逻辑长度 536,870,912、SHA-256 `5F2D56E8DCD5F647175EA2FF9818471A0757F0BB28EC3959E658B99716ABCD1A` 不变；实际分配从 1,610,612,736 降至 24,576 字节，释放 1,610,588,160 字节。逐项回执为 `avd-sdcard-compression-plan.json` 和 `avd-sdcard-compression-result.json`。
- 另对 33 份已停用 AVD userdata QCOW2 执行同一独占句柄压缩，33/33 成功且逻辑长度、SHA-256 不变。逻辑长度合计 48,163,520,512 字节，实际分配减少 220,909,568 字节；该批前后盘余量变化为增加 220,864,512 字节。逐文件配置哈希、镜像哈希及结果见 `qcow2-compression-plan-03.json`、`qcow2-compression-result-03.json`。
- 对 `e2a-cross-20260925-1140` 至 `1144` 核验了正常停机日志、测试端口空闲、没有 AVD/构建消费者；独立 MySQL `data` 目录已不存在。118 个 10 MiB 载荷文件只有 6 种字节内容，最终 1144 验收路径保留六份各自唯一的 `.bin` 原件。清理计划拟移除其余 112 个逐字节重复副本，以及 5 份已结束测试的 `final.sql` 和 5 份 binlog，共 122 个文件、2,178,143,865 字节；每个路径、SHA-256、依据和保留原件记录于 `e2a-cleanup-plan-02.json`，计划 SHA-256 为 `C8C0487BDEE4D76AEC91EF5A379C0D1D3644F233BB54043D208A850F590F63A9`。Windows 自动策略在命令启动前拒绝该 `Remove-Item` 请求，没有删除任何文件，也未重试。
- 本续批开始时 D 盘余量为 18,174,590,976 字节；末次复测为 20,006,010,880 字节（约 18.63 GiB），净增 1,831,419,904 字节。两次压缩合计实际分配减少 1,831,497,728 字节；差值 77,824 字节来自并发盘活动。C 盘末次复测余量为 70,437,232,640 字节。
- 分支和 HEAD 未变，已有未提交源码及继承改动均保留；清理前 Git/WIP 快照在 `.local/disk-cleanup-20261021-continuation/`。未提交、推送或改写看板。

## 部署浏览器缓存清理计划（2026-10-21）

- 输入基线：`codex/development` / `2fc5483e83691c3a3cd9fe6fe3336aaf112c8fd8`；目标部署 worktree `D:/acrylic-pda-outbound/.local/worktrees/deploy-remediation-20261020` 为 `codex/deploy-preflight-20261020` / `8cb51ded3ef2cd6cd45eaca09d8947ce068e89cd`，`git status --short --branch` 干净。主工作树 WIP 不动。部署安装日志均记录成功，浏览器冒烟 exit 0；应用测试 XML、报告、构建回执、部署输入和源码不在目标中。
- 已核实无 Gradle/Java/AVD/QEMU/Playwright/Chromium 进程；活动的总目标会话正在等待本清理完成后再开始独立 Python 消费者测试。当前未发现目标目录消费者。
- 可恢复依据：两份 `playwright-core` 包的 `package.json` 均为 1.55.0，SHA-256 `36ca1b094edaa37835521c008b26cab5375cbab895ad4e7a9ab6577db23abec5`；`browsers.json` SHA-256 `6a346d802543e845c5e79715b99d58fc2bbf58f2a509dccc00f08b634dcb7894`，浏览器 build 为 1187。安装成功凭据为 `linux-browser-install01.log` SHA-256 `f44f0233372427ed575002a0f234ae370f3c729a7ebabced2ff748ad7363edb6`、`windows-browser-install01.log` SHA-256 `91ba500e64934bef705804cdabc3220dfd44d775b49aeabdd68daca5078a3ae1`，冒烟凭据 `browser-smoke01.log` SHA-256 `122dae5f06e8ba7fba783584e5f4dafd6fa9586c414506731e8dd980ebec18ac`；对应 exit 文件均为 0，哈希 `13bf7b3039c63bf5a50491fa3cfd8eb4e699d1ba1436315aef9cbe5711530354`。`report-rendering/gradle.lockfile` SHA-256 `e75299d5b0423289c341ac539877ffe886b025bc3c3dc8e6391010e52181aa1d`。
- 精确删除目标及清理前清单摘要：
  - `D:/acrylic-pda-outbound/.local/deploy-remediation-20261020/linux-browsers`：486 文件，962,395,954 字节，清单 SHA-256 `705283bab06c597d26c05deb5ca0b782583a753608a3cbe84c0092ca6732dabd`。
  - `D:/acrylic-pda-outbound/.local/deploy-remediation-20261020/playwright-driver`：339 文件，132,719,005 字节，清单 SHA-256 `f522d7be592146c0a3e3a7467ce30152ed783ff67d6ed8cf4866cd0f965946b3`。
  - `D:/acrylic-pda-outbound/.local/deploy-remediation-20261020/playwright-driver-windows`：339 文件，96,310,478 字节，清单 SHA-256 `0b1e23116801a73b64dd98338c4362573aae00a688f3e3ab6453831290cac0db`。
  - `D:/acrylic-pda-outbound/.local/worktrees/deploy-remediation-20261020/report-rendering/build/browsers`：274 文件，576,035,892 字节，清单 SHA-256 `b857e78e32e7e96b544ad4cb18235fe449a41cdb8e3cb736a72842e152ecde88`。
  - `D:/acrylic-pda-outbound/.local/worktrees/deploy-remediation-20261020/report-rendering/build/tmp`：3 文件，248,450 字节，清单 SHA-256 `f615e90f372fdca856f6e535c058768ca33be250da79bef8d1a1d26751cb01f4`。
- 拟执行单一 PowerShell 删除调用：`Remove-Item -LiteralPath @(<上述五个逐字路径>) -Recurse -Force`。不含通配符；计划总计 1,441 文件、1,767,709,779 逻辑字节。操作前 D 盘剩余 20,006,010,880 字节；结果和释放空间待实测后补记。
- 删除调用实际被自动审批策略在进程启动前拒绝，错误为 `blocked by policy`；PowerShell 没有启动，五个目标均未变化。没有换用其他删除接口重试。
- 后续仅对前四个浏览器/Playwright 缓存目录进行可逆 NTFS 压缩，不触碰上述 `build/tmp`。压缩命令逐目录为 `compact.exe /c /s:"<绝对路径>" /q`；压缩前四目录合计 1,438 文件、1,767,461,329 字节，清单 SHA-256 与上文一致。文件内容哈希须复核不变；释放空间以 D 盘前后实测为准。

## AVD RAM 快照压缩计划（2026-10-21）

- 输入清单：`D:/acrylic-pda-outbound/.local/disk-cleanup-20261021-continuation/local-size-audit.json`，SHA-256 `bc1dcdcbcc80b7afbe79dce7b1c55d19af50a58927191365fb1a1c78252f5faa`。筛选其中位于项目 `.local` 且路径形如 `snapshots/<snapshot>/ram.img` 的 52 个绝对路径；逐项存在、长度及修改时间均与清单相符。52 份原内容 SHA-256 与路径/长度构成的排序清单 SHA-256 为 `85bd6b82347700dbe5dc349794fd34446823a2d4f1875f72d1fbcb3c20e11a1b`。逻辑长度合计 223,341,707,264 字节，审计分配量合计 21,475,164,160 字节。
- 预检没有活动的 emulator/QEMU/Gradle/Java 进程；当前另一个总目标会话的待测项是独立 Python 消费者，并明确等待清理完成，不使用这些 AVD。只压缩 `ram.img`，保留全部路径、文件逻辑长度及 AVD userdata；不删除快照或测试结果。
- 操作前 D 盘余量 20,733,022,208 字节。拟对清单中每个逐字绝对路径执行 `compact.exe /c /q "<ram.img 绝对路径>"`；逐项记录退出码，压缩后重算 52 项内容清单哈希及 D 盘余量。

## 压缩结果

- 浏览器/Playwright 缓存四组 `compact.exe` 均 exit 0。Linux Chromium 486 文件输出显示 962,395,954 字节压至 505,145,352 字节；Windows Playwright driver 339 文件输出显示 96,310,478 字节压至 58,616,845 字节；Chromium Windows 构建缓存 274 文件输出显示 576,035,892 字节压至 398,800,308 字节。通用 Playwright driver 339 文件也全部标记为 NTFS compressed。逐文件 SHA-256/长度排序清单复核与压缩前完全相同；四目录内容清单哈希依次仍为 `705283bab06c597d26c05deb5ca0b782583a753608a3cbe84c0092ca6732dabd`、`f522d7be592146c0a3e3a7467ce30152ed783ff67d6ed8cf4866cd0f965946b3`、`0b1e23116801a73b64dd98338c4362573aae00a688f3e3ab6453831290cac0db`、`b857e78e32e7e96b544ad4cb18235fe449a41cdb8e3cb736a72842e152ecde88`。D 盘余量操作前后 `20,006,006,784` → `20,733,022,208` 字节，净增 `727,015,424` 字节（约 693.34 MiB）。`build/tmp` 未清理。
- 52 份 AVD `ram.img` 均逐文件 `compact.exe /c /q` exit 0。压缩后重新计算全量 SHA-256，52/52 文件存在、长度不变，内容清单哈希仍为 `85bd6b82347700dbe5dc349794fd34446823a2d4f1875f72d1fbcb3c20e11a1b`。NTFS compressed 属性 52/52；D 盘余量 `20,733,022,208` → `42,208,186,368` 字节，净增 `21,475,164,160` 字节（约 20.0 GiB）。没有删除 AVD userdata、快照文件、测试报告、证据或源码。
- 本轮两批压缩后 D 盘可用 `42,208,186,368` 字节（约 39.31 GiB）。浏览器目录拒删请求被策略拒绝，不计作释放；此前 122 个文件的删除请求也未重试。

## 继续深度清理（2026-10-21）

- 已验证部署归档 SHA-256 `8539177d1274864b94772c9941a1ff3dfab9735dda545b4fca988f3232c37ad0` 与回执一致。两个解包副本各 648 个文件、逐项字节与 SHA-256 一致，排序内容清单 SHA-256 均为 `43f3b4b10516f85149e00f8d7ad3373200831749acc681731ae15e94fe38af6d`。保留原归档和独立校验解包；两份解包仅 NTFS 压缩，1,296 个清单文件逻辑长度/哈希不变、压缩属性 1,296/1,296；压缩输出合计从 2,980,820,926 降至 2,054,843,880 字节，D 盘实测余量 `42,207,264,768` → `43,133,394,944` 字节。输入/结果及哈希在 `package-compression-plan04.json`、`package-compression-result04.json`。
- 计划移除可从原归档重建的 `packages/preflight-8cb51ded-20261020-01/` 解包副本（648 文件、1,490,410,463 逻辑字节），保留独立解包、归档、校验回执及侧车校验。标准 PowerShell `Remove-Item -LiteralPath ... -Recurse -Force` 在进程启动前被策略以 `blocked by policy` 拒绝，目标仍在；未换命令重试。逐文件计划 SHA-256 `9518c523c14ec5b4aacd1aa0586b7c1c07ec2e93bcada310ae50c1af27a74f32`，详 `package-duplicate-delete-plan05.json`。
- 对 5 个已停用旧 AVD userdata/cache 镜像独占句柄执行 `FSCTL_SET_COMPRESSION=0x9c040`。逻辑长度合计 4,375,642,112 字节、实际分配从 4,375,642,112 降至 4,094,291,968 字节，释放 281,341,952 字节；5/5 长度和 SHA-256 不变、NTFS 压缩属性成立。首个 API 预检因 PowerShell 有符号参数转换在打开文件前终止，未改文件；修正参数后 5/5 成功。清单/结果见 `avd-compression-plan06.json`、`avd-compression-result06.json`。已压缩或当前任务日期目录、`avd-original` 均未纳入。
- 对旧任务专用 Gradle `caches`（8,375 文件、1,146,206,189 逻辑字节）运行一次 `compact.exe /c /s:<精确路径> /q`，exit 0。复核 8,375/8,375 文件长度与 SHA-256 不变；8,363 个标记为 NTFS compressed，12 个未标记的是小型 POM/JAR，未重试。实际分配从 1,145,990,174 降至 846,558,747 字节，减少 299,431,427 字节。该次 D 盘余量 `42,838,593,536` → `42,390,274,048` 字节，受并发盘活动影响反而下降；稍后复测为 `42,811,457,536` 字节，不把并发变化记作本批释放。清单、日志、结果在 `gradle-cache-compression-plan08.json`、`gradle-cache-compression08.log`、`gradle-cache-compression-result08.json`。
- D 盘项目外只读盘点记录于 `external-depth-audit.json`：300,216 个文件。共享 `.pnpm-store`、Android 离线工具包、带 reparse points 且有子模块改动的 WMS 依赖目录，以及其他项目数据库/源文件均保留。`D:/tmp` 约 117 KB，`D:/logs` 为空。QuarkCloudDrive `videoCache` 225 个文件、87,871,210 字节，独占读取哈希完成且未发现应用消费者；精确删除计划 SHA-256 `f797657dd33a7cf65fd19b0700d5f5a125d629b37fb5ef859fbde049ab906df4`，但删除调用同样在进程启动前被 `blocked by policy` 拒绝，未改目录，不换命令重试。
- 末次实测 C/D 可用 `70,390,730,752` / `42,811,457,536` 字节。清理操作仅改 NTFS 压缩属性及上述独立清理记录；未提交/推送，项目源码 WIP 和数据库服务/监听均未操作。
