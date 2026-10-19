# U01/U04 独立采集首屏与次级面板 L1（2026-10-19）

独立作业页此前把口令、手输、定位、撤销、分页、结束和原件全部平铺，确认/暂停/异常入口被非当次操作挤出首屏。本切片依据 [UI规范§4.2](../PDA_UI_UX_REVIEW_2026-09-29.md#42-扫码首屏构成)，将手输与核对/原件改为显式展开，候选确认、失败确认按状态出现；车号、有效数、阻断和声效提示保留在主区。

## 范围与可验证标准

固定树 `D:/acrylic-pda-outbound`、分支 `codex/development`，输入HEAD及实际origin开发分支均为 `42562412ca9372728239ef2d1a798ff97728c8d9`。设计按AGENTS/总目标§3自主裁定授权记录在 `.local/u04-independent-first-screen-run01/design.json`，不扩展生产权限。继承218项路径/SHA与patch为 `takeover.json`，后续 `takeover02/03.json` 保存各阶段精确状态；216个显式范围外继承路径收口核对不变，结果在 `closeout-inputs.json`。

验收标准为：四档字号初始隐藏次级输入；普通手输候选下车号、候选、确认、暂停、返回及核对入口位于320×509合成可见区；长车号/候选在四档字号可换行、无省略与横溢且可滚动到达；切换保留非秘密输入、核对收起清口令，64次队列渲染不重置面板或编辑；排队/阻断/暂停/封存时资格继续收紧；既有增量文本和声效显示回归通过。

改动路径→验证层与必跑：

| 路径 | 影响与验证 |
| --- | --- |
| `activity_independent_work.xml`、`IndependentWorkActivity.kt`、新增 `IndependentWorkPanels.kt` | 页面顺序、显式面板、焦点与清口令；主机布局/面板测试，设备消费者仅编译；Activity/IME/实体键/生命周期本次未实跑 |
| `IndependentWorkDisplay.kt` | 空反馈/候选动作隐藏、封存禁用、候选文案；首屏/面板及既有文本3项、声效2项组合 |
| 新增 `IndependentFirstScreenTest.kt`、`IndependentWorkPanelsTest.kt`、`IndependentScreenFixture.kt` | 合成任务/候选、真实TextView布局与按钮状态；不创建可信身份、原PDA或业务回执 |
| 新增 `IndependentWorkDevicePanels.kt`；`IndependentScanDeviceFixture.kt`、`IndependentClosureDeviceFixture.kt`、`IndependentClosureDeviceTest.kt`、`IndependentSealedReadDeviceTest.kt` | 消费者通过产品toggle打开输入/原件；`compileDebugAndroidTestKotlin`，不重跑旧场景 |

所有Android路径均位于 `android/app/src/`。引擎、Room/服务端、迁移、HTTP、Web及Python未改；本项未运行这些业务链，未以无关历史结果代当次通过。`ui.rows`是升序历史分页，未把末行标为“最近保存”。本切片也未实现剩余数或最近保存产品卡，不能计U01/U04整体。

## 实际验证及保留失败

证据根 `.local/u04-independent-first-screen-run01/`。`run-test.ps1`保存最初before/after，后续 `verify.ps1 -Phase afterNN` 拒绝覆盖已有日志/输入；离线、单worker、no-daemon，使用本根隔离build/cache/temp。

| 阶段 | 结果与归因 |
| --- | --- |
| before | 原布局新增3项均失败，退出1；此后新增失败确认项，不能称before和最终四项输入完全相同 |
| after | 首屏4项4失败、面板7项5失败；文本3项/声效2项通过。未挂窗口的`isShown=false`为夹具问题；首屏位置问题继续独立排查 |
| after02 | 挂真实宿主窗口后16项中14通过；核对toggle底531>509；130%字号20.8sp换算为21px导致0.1px容差过严 |
| after03 | 改用实际`AppTheme`、记录各控件几何并允许Android整像素取整后，15/16通过；核对仍底531。标题与禁放行提示各占41px，提供新的布局根因证据，未机械重跑 |
| after04 | 合并常驻标题和禁放行提示为“独立采集 · 待主管对账，不得放行”，保留按钮最小高度。首屏4、面板7、文本3、声效2项全通过，0失败/错误/跳过，退出0；核对toggle底490≤509 |
| compile-after01 | `:app:compileDebugAndroidTestKotlin --offline --console=plain --no-daemon --max-workers=1`，隔离build/cache，退出0；仅编译，未assemble/安装/跑设备用例 |

字号测试使用实际主题，四档fontScale为1/1.3/1.5/2，候选像素16/21/24/32；0.5px容差只处理平台整像素舍入，没有放宽首屏509或裁切断言。大字通过滚动可达，未声称所有大字内容都在首屏。确认候选场景的手输toggle底551仍需滚动；确认/核对入口在首屏，键盘出现后的行为尚未验。

after04输入/日志SHA-256分别为 `64e46c4beff5e10f15e87e5787ca35a7d549eda669647dabbf9b15e07675be68` / `7c1de9d987bba660f3a19b42e7043754271dbd2ba9d445640c4a883a80fa0862`；四套XML分别为：

- 首屏：`89b09300529e5efc2d3e57bc62d4a63f738b86d736b14c429faa96c3c02f2dca`。
- 面板：`30385589c6637d3afcad80e1ff2b0576f9e7df6925e34650949ffaeff0981507`。
- 文本：`bd0f3ad74e98304609d2f7a84d17cac598568e9cc23331c9f4e81c21982eba44`。
- 声效：`c79e53c6016dae8358ef85f32a4d59fc981ae932cc34b210095d838315f2f25c`。

编译输入/日志SHA为 `2a14bb0db37287725a6d44f1b5e14cd249a0b9b5c870ecd06c87cad056783fc6` / `9bc56018a5821fb6881322d150051ebc6c0ffdf0e26a32179c64c6966ea19224`。输入、命令、失败、各次XML及源码/依赖配置逐文件SHA全部在同根结果JSON和 `closeout-inputs.json`（SHA `4174b0877bd04597e65a176e0a4e6904736ebb70904c7abc39784c409036a77d`），保留原字节。

## 自审、环境与清理

作者自审而非独立人员审查：面板不改引擎或授权；核对展开不主动暂停扫码，撤销/结束仍须原暂停条件；收起清口令并清焦点/收软键盘，非秘密文本保留。封存任务手输、校验、确认、撤销及结束资格均禁用；64次render保留展开与编辑。面板默认折叠和具体焦点顺序还须实际Activity/IME/实体键验证。`IndependentScanDeviceTest`经已有`S.authorize/S.text`进入对应面板；未修改继承WIP `IndependentCredentialDeviceTest.kt`。`git diff --check`通过。

fresh核实唯一写者为本聊天，MySQL80父4288/子5188、3306/33060仅观察，系统库UUID未知且不连接/停止；ADB2436/5037只读枚举USB M71 `22D2910042`，无模拟器。Debug Keystore哈希与接管一致，未用正式签名。源码/报告以外未操作设备数据或外部业务服务。收尾无Java/Gradle在途，精确采样及全部WIP见同根 `closeout.json`。

本切片专用build、project-cache、project-cache-compile、t、t-compile核实无消费者后清理，所有原日志/XML/输入/结果保留；无APK/tar在目标中。Windows PowerShell旧长路径哈希失败发生在任何删除前，保留 `cleanup-attempt01-failure.json`，以现有PowerShell 7完成相同目标审计与清理。逐文件输入/长度/哈希为 `cleanup-pre.json`，实际删除3668文件，逻辑长度82,383,921字节；D余量61,004,599,296→61,093,257,216字节，卷净增88,657,920字节。`cleanup-post.json`的logicalBytes误为null，独立逐项加和勘误在 `cleanup-byte-correction.json`，未篡改原回执或重做清理。共享依赖、前序证据/产物及声效切片被拒目录未清。

此前自动审批拒绝声效切片 `project-cache/project-cache-display` 清理，理由仅“blocked by policy”；目录仍保留、释放0字节，未换方式重试，原回执见 `.local/goal-20261019-resume/sound-closeout-takeover03/cleanup-rejected.json`。

本项仅合成Robolectric UI L1和instrumentation编译，不是Activity/设备、HTTP/MySQL、U01/U04/D17整体、E06 L2、D16/D18或总目标§8通过。没有构建/覆盖现有APK、重跑旧用例、启动run03、恢复换机/多跳/确认激活链、接原库现网或部署/Release/main；`localDeletionAllowed=false`保持。开发提交与实际远端SHA另存同根 `git-delivery.json`，总目标继续active。
