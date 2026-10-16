# E08 独立口令标签与键盘布局 L1（2026-10-16）

独立身份页增加永久口令标签并避让实际软键盘。原隔离模拟器四档字号的直接页面交互通过；完整“数据→SKU→身份→返回”仍在SKU返回夹具处失败。本批仅保存身份页布局L1及明确未完成的导航诊断，不计E08/U整体、D步骤收口、L2/L3或总目标完成。

## 输入与影响映射

固定树`D:/acrylic-pda-outbound`，分支`codex/development`，输入HEAD/远端`7d0e26da65445ec16aebf078f621f7895b67bc29`。唯一写者为本聊天`01a13a57-8082-7dd2-a08f-987483dcda4b`；清理聊天释放回执沿既有证据保存，两个其他同目录聊天实查idle，无并行项目写者。

证据根`.local/goal-20261016/e08-data-credential-layout01/`。input01保存改前输入，input16绑定最终build09及四档直接测试；input17逐项相同，5343项SHA `15075c3e2fdb64d7b582d14bc232531c60e4cfe82be4942fb6a9f624d7064267`。各份保全Git差异/未跟踪原件、长度/SHA、旧看板、写权、进程/监听和C/D余量。input12延迟返回后重复调用触发独占文件保护，原快照未覆盖。

按总目标第3节自主默认，沿原Activity外层ScrollView，底部取系统栏与IME较大值；标签关联原密码框，hint作格式提示。原FLAG_SECURE、密码不自动填充/不保存、协议、权限、期限和生产默认不变；只输入`layout-check-only`，不点预置、核验、撤销、导入或启用。

| 改动路径 → 影响层 | 必跑范围 | 复用依据 |
| --- | --- | --- |
| `ui/IndependentCredentialActivity.kt`、`layout/activity_independent_credential.xml` → Android布局 | APK/lint、身份定向；实际键盘/输入/滑动/返回与四字号 | 10个原协议/身份/资料路径及SHA见`reuse-inputs01.json`，与input16一致；迁移/HTTP/MySQL/Web无行为变化 |
| `IndependentCredentialLayoutDeviceTest.kt` → 直接客户端 | 原TEMP AVD产品Activity、真实IME、安全可见区、文字盒/宽度/省略号、控件与返回 | 导航测试已实际到达该入口；直接测试不冒充完整导航、身份业务成功或现场 |
| `DataCredentialLayoutDeviceTest.kt` → 完整导航诊断 | Main→数据→SKU→身份；失败后全库/原件保全 | 仅显式参数下运行，SKU返回失败不计通过 |
| 报告/索引/看板 → 追溯 | 明确路径暂存、自审、事实块/上限 | 旧结论在各input的`board-before`保存 |

## 实际执行与失败保留

- build01缺服务向量变量，parity失败；build01/02宽泛筛选还误选旧取消链`TemporaryHandoverActivationTest`的一个合成主机方法。原XML保留且不计本批成果，不恢复取消范围；未动原模拟器或业务库。见`runner-scope-correction01.md`。
- build03改精确包/类筛选，实际11项通过（Parity1、Proof3、Store7），0失败/错误/跳过。build04–06/08/09任务UP-TO-DATE，复用build03原XML。服务向量为既有synthetic server unit vector，路径/SHA见`vector-binding02.json`，不是原PDA身份。
- build07因直接测试`closeSoftKeyboard`导入歧义编译失败，原日志保留。修正后build08/09产品/测试APK及lint成功；lint438警告/0错误，标签/hint新增HardcodedText未称全是旧警告。产品SHA `bf4428d36f6ae773aaaae6dc31e4dbbae8aa0c10c9a8bf918d965e57550e5c69`，测试APK SHA `94955cb7a892b26e59e75fa2b52725ee6f0d4049230791e7babbcc6bb77f5814`。
- `font100-01`键盘时序失败；`font100-02`键盘后输入框不可见；`font100-03`按整窗滑动未收敛，受控强停；`font100-04`身份键盘/输入/核验按钮可达/返回已执行，随后SKU返回未收敛并受控强停；`font100-05`改ScrollToAction仍在SKU返回失败。原日志、tar、活动/IME和消费者均保留，不追认完整导航通过。SKU返回诊断暂缓，无新依据不机械重试，原计数不清零。
- `direct100-01`键盘后的ScrollToAction失败且业务不变；新依据支持按实际安全区域滑动，改为每目标最多12次真实滑动及完整可见位置检查。`direct100-02/130-01/150-01/200-01`分别18.470/21.418/23.674/34.402秒通过：键盘→非凭据输入→核验按钮可达但不点击→收键盘/清测试文本→点击返回。系统栏43/36像素、IME461像素，容器留白匹配，关闭后IME为零。截图已自审；滚动边缘自然截断不冒充文字盒裁切。根视图绘制不包含系统键盘图像，真实IME由WindowInsets另证。
- 每轮独立Python读取提取的SQLite副本：83表328行无差异，304原文件逐字不变，含session、绑定、救援凭据和原作业证据。仅精确`files/profileInstalled`作为AndroidX标记单列前后SHA。基线tar SHA `cfa2a1f5a0b3b1e6c664a2fd0d9b307bc7ba2bb72594c64134aadb0991f3b680`；源原件未以SQLite打开。

`evidence-manifest01.json` SHA `d1c1d621dc4ae2737962c08995e587f8395e9cf4433cbc0993ced517a5f43c59`，5056项证据路径/长度/SHA双次一致，运行代码与input16一致。manifest初轮GBK读UTF-8失败，旧脚本保存后只修编码/只读复核；命令生成初轮PowerShell引号错误未生成脚本，均无业务重跑。作者、自审、执行为同一助手，独立Python不代表独立人员。

## 环境与剩余

原TEMP AVD身份保持，字号恢复1.0、硬键盘IME设置0、App强停、reverse空；无本批数据库/服务或在途Gradle。共享mysqld身份未核清，未连接/复用/停止。input17 C/D余量83,033,030,656/33,004,785,664 bytes；新产物/证据/临时目录均在D盘，无清理。debug及模拟器不代表真机/现场。

仅明确路径提交推送开发备份，全部继承改动和本机请求原件保留。完整SKU返回、U整体、E06/E07组合、E09及D18 L3继续；不恢复取消的换机/接收者确认/激活。下一步补E07 644既有证据的独立Python消费，先核输入，不重跑一次性业务。七类生产能力、TEMP归档、`localDeletionAllowed=false`及原库/main/发布边界不变，总目标active。
