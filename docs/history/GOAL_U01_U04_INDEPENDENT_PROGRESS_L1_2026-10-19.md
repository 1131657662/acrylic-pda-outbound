# U01/U04 独立采集剩余数与最近保存卡 L1（2026-10-19）

## 范围与标准

本批只覆盖独立采集页的有效进度和最近保存显示：计划减持久化有效数、超计划提示、同任务最近保存记录、VOID 后身份保留、Room 快照一致性和任务切换竞态。独立来源仍待主管对账，不改变保存、授权、VOID、封存或生产默认关闭政策。设计记录在 `.local/u04-independent-progress-run01/design.json`。

验收标准是：排队/未确认/失败/翻页不改变最近卡；保存事务末条写入失败后旧卡和状态可重开；VOID 显示同一条记录并恢复剩余数；跨任务不泄露卡片；Long 计数不丢精度；四档字号首屏和长文本不裁切；查询计划使用任务序号索引、扫描主键和VOID主键，且不出现临时排序。

## 改动

- `IndependentScanDao.latestSaved` 从 `independent_capture` 的任务序号倒序连接已保存扫描和VOID状态，避免把升序分页末行冒称最近保存；没有 schema 或迁移变化。
- `IndependentScanEngine` 在同一 Room 事务读取任务、候选、分页、有效计数和最近卡；任务切换时在最终发布处再次核对请求和当前任务，并用对象锁把切换与 UI 写回串行化。
- `IndependentWorkDisplay` 显示有效数/剩余或超计划数、最近保存包及重量；VOID 保留同一序号并标记已撤销；总重量移入展开核对区。布局增加最近卡，暂停和核对入口并排且保持最小触控高度。
- 新增剩余数、最近保存、重开/VOID、跨任务和发布竞态测试；未修改继承的 `IndependentCredentialDeviceTest.kt`。

## 验证

固定树 `D:/acrylic-pda-outbound`、分支 `codex/development`。输入和命令由各证据根的 `*-input.json` 保存，均从 HEAD `3f450bd76ceca833d440c549623c115b121ed742` 开始，离线、单 worker、no-daemon。run01/03/04使用隔离D盘build/cache/temp；run02隔离脚本配置错误，实际使用默认 `android/app/build`，不声称该目录旧报告保持原字节。默认构建目录保留，未清理。

- run01/before：新增剩余数对照3项均失败，退出1；该时点尚未加入后续最近保存和竞态测试，不能称完整27项预修复反例。输入/日志SHA分别为 `b539445ad4bb55ed064179eac8a31cbd6f8f5289091288417d159188a5fea3ad` / `cde67db5993512ccd734211bdd585210c36bfc9ffc7307d1b3cf73948e827b26`。
- `.local/u04-independent-progress-run01/after-result.json`：7 个套件共 27 项，全部通过，失败/错误/跳过均为 0；`inputSha256=fce35b7ee2fbc4e9ed56a49ba1f3931025f9abe53dda050e675627278bf0eb9f`，`logSha256=eec24cb1c619399bf8bca2742cfeaa574c5363f71b20cb45e794248db5660fb0`。首屏几何的确认/核对入口底部为 505，低于 320×509 合成可见区；四档字号长文本无省略或横溢。
- `after-independent.IndependentRecentSavedTest.xml` 打印的 EXPLAIN 计划使用 `index_independent_capture_taskId_sequence`、`sqlite_autoindex_independent_scan_1` 和 `sqlite_autoindex_independent_void_1`，未出现临时排序。
- `.local/u04-independent-progress-run04/after-result.json`：受引擎发布改动影响的 `IndependentRecentEngineTest` 2/0/0/0 与 `IndependentProgressSwitchTest` 1/0/0/0；`inputSha256=7e11e671bffab9094bea1e79d6bf42ac48e2dfc7e5b8ad6c7f219ff5f5d4007e`，`logSha256=93e55a06df57644e711d600768dafc131b785ed8ced18892c3de95a39aba48eb`。
- run02 曾因隔离脚本路径配置错误未收集 XML；其日志和结果原样保留，未作为通过依据。run03/04 使用正确隔离配置并保留逐套件 XML；run04 的 XML SHA 见结果 JSON。
- 51条分页测试使用直接插入的合成查询基数夹具，不冒称51条真实保存链。其他Room测试通过产品保存链制造末条审计SQL失败、重开和重试；引擎测试消费真实Room并验证手输确认、VOID及新引擎重开。

## 自审与限制

作者自审，测试身份、SKU、任务和签名均为 Robolectric 合成夹具；未连接原库/现网，未安装 APK、操作 USB 设备或模拟器，未跑真实 HTTP/MySQL、Chrome、Python、非空迁移或正式签名。首屏/大字结论是主机布局 L1，不是 Activity/IME/实体键或真机验收。D16 合法退出依据、D18 最终候选和第8节闸门仍未满足，总目标保持 active。

本切片证据、源码改动、继承 WIP 和已有 APK/tar/日志均保留；不启动旧 run03，不恢复换机承接、多跳或接收者确认/激活链，`localDeletionAllowed=false` 保持。

run03/run04六个专用build/cache/temp目录的清理先遇PowerShell旧长路径哈希、OrderedDictionary加和及长路径删除失败；删除阶段发生部分清理，不能称完全未删除。后续PS7删除剩余4255文件/155,736,979字节，D可用60,756,262,912→60,921,966,592字节；这仅是PS7剩余量和该阶段卷净增，不是全部删除总量。PS5原前清单被PS7复用文件名覆盖，原完整哈希/字节数缺失，工具回执留有失败及部分哈希；明确记录这一证据缺口，不补造原清单。PS7逐文件SHA在 `cleanup-before.json`（SHA-256 `AF51B5EAE256269452C8B312B26381FAD44273156738D4D64E61E54A7E0C39C3`），结果在 `cleanup-after.json`（SHA-256 `D075F2D359742F649475C17530222D32FDD922001A09E7410B8A8D5EF4E1D0F1`）。run02默认构建路径错误、各次清理错误及先前不准确口头判断的勘误在同根 `runner-and-cleanup-corrections.json`。

全部217项继承路径收口SHA与takeover一致，见run04 `inherited-protection.json`；首屏切片已推送，旧看板“首屏待提交/剩余未实现”属于历史断点，后续看板已更正。新的本机证据没有声称异地备份。

随后用单独拒绝覆盖回执的 `cleanup-rest.ps1` 清理run01的build/cache/temp与run02的cache/temp，共3129文件/151,845,800字节；完整解析路径、逐文件SHA、依赖/输入结果SHA和停机核对均在 `cleanup-rest-before.json`，结果在 `cleanup-rest-after.json`。D可用60,921,716,736→61,079,003,136字节；C/E在该阶段分别为73,164,832,768 / 195,634,053,120字节。原测试日志/XML/JSON、APK、tar、Keystore和源码均保留。

历史声效切片 `project-cache/project-cache-display` 清理被自动审批拒绝，理由仅 `blocked by policy`；目录保留、释放0字节，未换方式重试。原拒绝证据见 `.local/goal-20261019-resume/sound-closeout-takeover03/cleanup-rejected.json`。
