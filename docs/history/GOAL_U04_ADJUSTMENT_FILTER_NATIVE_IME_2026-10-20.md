# U04 原记录筛选真实键盘避让：四字号有界验证

## 结论与限制

输入 `60b327fb4b16c552d78281874d0ce9a0326a7958`，固定树 `D:/acrylic-pda-outbound`、分支 `codex/development`。在保留的同22条合成正式订单原记录上，100%/130%/150%/200%实际字号的原生IME消费者各1项通过。有效原序号筛选、Activity重建、非法0拒绝、键盘打开时清除及恢复全部均实际执行；四轮83张Room表逻辑摘要与造数后基线逐字一致，每轮会话内容及本轮前后session.enc密文均守恒。

唯一产品改动是 `ScanAdjustmentActivity` 显式edge-to-edge并将底部留白取系统栏与IME的较大值；未改业务数据、授权、会话、时间anchor、扫码/调整writer、契约或迁移。新增两条AndroidTest路径。仅有界Android L1，不记完整UI、U04/D17整体、L2/L3或第8节通过；版本事实见看板生成块。

证据根：`D:/acrylic-pda-outbound/.local/goal-20261020-resume/u04-filter-native-ime01/`。作者与自审为同一助手；Espresso/Python是工具消费者，不是独立人员审查。

## 输入与原件保护

- 前轮正式四字号[状态筛选报告](GOAL_U04_FORMAL_STATUS_FILTER_NATIVE_CONSUMER_2026-10-20.md)保留；本轮复用原 `U04FormalStatus01/emulator-5682`、独立datadir及其身份，不wipe、不重新造数、不续签或更改anchor。重启时间围栏保持，未点击调整写入。本轮不是正常有效授权条件或原身份的证明。
- 启动前只读取证 `device-before.tar` 与前轮 `device-final.tar` SHA逐字一致；原契约、assignment和旧消费者结果保留。全部ADB专项命令明确5682；USB M71未做业务操作，没有连接项目/系统MySQL或原库/现网。
- 原产品/manifest/layout/索引字节及SHA在 `pre-edit-bytes/`、`preserved-inputs.json`；看板原字节在 `board-before.md`、`board-before-closeout.md`。原产品APK和各测试APK不覆盖。继承32路径中除授权看板外31条逐项SHA未变。
- 最终1031项Android源码、schema、配置、wrapper及夹具清单逐项核同，见 `build03-input-files.json`、`consumer-final-audit.json`；build02到03仅新消费者的IME等待时序改变，产品未再变化。

## 失败、最小修复与复验

| 执行 | 实际结果与处理 |
| --- | --- |
| build01：独立测试APK | 退出0；第一次路径转换preflight在Gradle启动前失败，原件保留后修正路径，不计业务执行 |
| `ime200-first` | adb退出0，JUnit 1项/1失败；实际IME可见，可见区36–182px，筛选按钮y159+h88，35次安全区拖动后仍不能完整触达。83表和session.enc不变；原tar/日志/几何/截图保留 |
| 产品修复 | 沿已有IndependentWorkActivity方式，仅补edge-to-edge和max(系统栏,IME)留白；未降低触达标准 |
| build02：产品/测试APK、lint、定向主机回归 | 退出0；AdjustmentFilterTest 13项、AdjustmentLayoutTest 1项，0失败/错误/跳过；lint 0 errors / 508 warnings，不称零警告 |
| `ime200-fixed` | JUnit 1项/1失败；点击输入框后键盘改变布局，紧接clearText时控件暂不满足Espresso可见约束。原tar/日志保留，未记业务通过 |
| 消费者修正及build03 | 仅在点击后等待真实IME稳定、再滚到输入框并clear/type；相同产品APK、同验收断言。测试APK独立重建退出0；输入差异见input-reuse-review |
| `ime200-wait` | 实际2.0，JUnit 1项通过；原生输入、筛选/收键盘、重建、非法0和键盘下清除通过 |
| `ime100-fixed` | 实际1.0，JUnit 1项通过；相同原记录及同测试方法 |
| `ime130-fixed` | 实际1.3，JUnit 1项通过；相同原记录及同测试方法 |
| `ime150-fixed` | 实际1.5，JUnit 1项通过；相同原记录及同测试方法 |
| 只读tar/Python审计 | 退出0，四轮83表与seed逻辑摘要一致，session.enc前后不变；64个旧成员逐字不变，仅WorkManager运行库三个成员变化 |

运行器使用实际window visible frame与系统栏交集判断安全区，不把adjustResize后的窗口重复扣除IME高度。首失败IME bottom=618px，最终200%为425px，不能声称新旧键盘布局完全相同或只凭两图证明单一因素；最终结论限实际记录的IME/字号和动作。产品缺少IME留白由源码与首失败共同定位，修复后四档按实际安全边界点击通过。

四张最终keyboard-valid内容截图已经打开目视核对，输入22与相应标签可读；部分邻接标签因滚动/视口裁切。内容截图由Activity绘制，不包含IME实际像素，不能当键盘全屏实拍或整页无障碍通过；键盘出现和收起由实时insets及触屏交互断言证明。

## 哈希与证据

| 产物 | SHA-256 |
| --- | --- |
| `device-before.tar` | `27ef607d8a830bd84e44f882ede733555512e178a6e34f8537e10bc01caa955c` |
| 首个失败 `device-ime200-failure.tar` | `b07c20fdaaf0397cf5164372b0b809231a4b19f7b514f23c8b72e25f42802eb2` |
| 时序失败 `device-ime200-fixed-failure.tar` | `b35ff656f739e3b1433463e133741bff26718c35cfc847f53deca8f49e717529` |
| `device-final.tar` | `cd2604fdd71677524b5c56d6fcf5f07b59c19753387cb030f522acbc2aa9d41f` |
| 最终产品 `apk02/app-debug.apk` | `5c1b3502272f3c4f5e6f9eb0ebd18007cdf2aab69a6bd42f7acbe9b606618ccd` |
| 最终测试 `apk03/app-debug-androidTest.apk` | `31f6424cd2342b75c679fbd3c4ca45e1b779855ae13473248dd28e2d96213f5f` |
| `consumer-final-audit.json` | `71d204e5fe3635014c6de5331307baa9a1c866b1bc3a6402d28ac9a6c9e667a9` |
| 四轮83表逻辑摘要 | `3094c13cd9d9d68d7947d4b026b8e18a204aad26c3a13991ab917ce06b25d619` |
| 前后session.enc密文 | `e378fd14b31ae902ad3446c1740e404a09f83263486ad3def18767c24e4c4bf4` |

完整命令/参数、退出码、JUnit原始日志、XML/lint、输入/逐成员清单、原图及两次失败tar保留。本轮前后密文守恒不补写成前轮四次运行前已取证。Room结论是逻辑全表守恒，不声称DB/WAL物理字节或整个应用tar相等。

## 停机、清理与未验

正常 `emu kill` 后fresh核无Java/Gradle/emulator/qemu与5682–5683监听，见 `stop-command.json`、`stop-exit.txt`、`environment-stopped.json`。源Pixel_6的8镜像与前TEMP隔离AVD的6镜像SHA未变，Keystore未变；正式合成AVD运行时文件自然改变，不称其镜像守恒。共享MySQL/ADB只观察，系统MySQL datadir身份仍未确认，不臆断。

可重建build/cache/tmp共9902文件、196,193,475逻辑字节已逐项哈希。旧递归清理被自动操作审查以blocked by policy拒绝，本轮无换方式重试，删除/释放0字节。原清理汇总的字节求和null保留，更正总和另见 `cleanup-summary.json`；输入、命令和既有离线依赖条件保留。APKs、tar、原日志/回执、AVD/身份及继承WIP不清理。

仍未验：非空可信主管保留、TEMP在本次新产品APK上的完整组合、完整UI/实体键/M71、原身份/原资料/现场回执、真实HTTP/MySQL/Chrome、非空迁移及统一候选L2/L3/第8节。旧SQLite CANTOPEN暂缓不变。下一项按实际覆盖选择受影响消费者或独立未完成项，不恢复取消链，不无新假设重跑失败。

仅产品一条、测试两条、新报告及两索引共六条明确路径作开发备份；提交/远端/继承WIP及输出SHA见 `git-receipt.json`、`final-manifest.json`、`closeout.md`、`output-manifest.json`。总目标active、未完成；生产决定、原库/现网、正式签名、main、部署及Release关闭，localDeletionAllowed=false。
