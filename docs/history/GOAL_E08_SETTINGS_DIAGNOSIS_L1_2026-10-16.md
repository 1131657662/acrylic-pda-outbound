# E08 设置分组与连接诊断 L1（2026-10-16）

本切片整理设备设置、保留可展开的原始诊断详情，并区分后台不可达、身份/权限、服务恢复、服务错误及未知原因。它不改变作业资格、上传结果或更新安装规则；不计E08/U整体、L2、D18 L3或总目标通过。

## 输入与实现决定

- 固定工作区、分支`codex/development`，输入HEAD及远端`5dd06695af6595a95d16a170fcc00830d5191eb8`，唯一写者为本聊天。证据根`.local/goal-20261016/e08-settings01/`；旧看板先存`board-before-start.md`。
- input03绑定build01，input04绑定build02：全量5338项，input04 SHA `ed1de1983ad0dda2247ff0f044d14cf2e3cc75be0624e5a0ffd0ca251427364b`。快照含全部改动/哈希/未跟踪原件、Git/worktrees、写权、进程/监听及磁盘。
- 按总目标第3节已有自主开发授权，选择在现有Activity内分组，沿用既有身份和GET，不新增健康探针或业务请求。当前身份、声音、连接与更新、身份维护依次展示，低频完整标识/版本/公钥/绑定移至可折叠区域，退出仍须原确认。
- 连接成功只说明本次后台/身份检查通过；未收到响应不推断Wi-Fi已断开，HTTP响应错误不冒充断网，恢复响应继续停止。默认隐藏原始异常/业务码/HTTP状态，可由用户展开；页面仍有FLAG_SECURE。重新读取或其他设置反馈同时清旧详情，避免混用两次结论。
- 继承的恢复接收确认属于已取消的换机链，源码与证据保留，不执行、不提交、不恢复激活。D16有效防重继续阻断，不以年限释放。

## 路径 → 影响层 → 必跑 → 复用

| 路径 | 影响层 | 本切片验证 | 复用边界 |
| --- | --- | --- | --- |
| `ui/DeviceSettingsActivity.kt`、`ui/ConnectionDiagnosis.kt` | 设置交互与诊断分类 | 原模拟器实际导航/声音往返/离线检查/详情/更新页/取消退出；定向分类及HTTP成功/403反例 | 既有PdaSession/PdaHttp协议不变，代码原字节SHA在输入快照 |
| `layout/activity_device_settings.xml`、`values/settings_styles.xml` | 原生布局/样式/折叠 | 480×800、density240，默认及三档放大字号，真实点击/滑动/文字裁切检查与截图自审 | 只验本页及对应入口，不据此计所有页面U04通过 |
| `ConnectionDiagnosisTest.kt`、`SettingsNavigationDeviceTest.kt` | 独立网络夹具与客户端消费者 | 每轮原日志、XML、APK/哈希，83表全量与原件/身份核对 | MockWebServer为合成HTTP夹具，不是真实MySQL/原设备现场 |

本切片无Web、Python产品、模型或迁移改动，不机械重跑Chrome/MySQL/非空迁移；Python独立消费者实际读取本批设备原件。后续受影响组合和最终候选L3仍必跑。

## 已执行验证和保留失败

- build01：定向6项0失败/错误/跳过、产品/测试APK及lint成功。build02在自审清旧详情和滚动夹具修复后，同6项、产品/测试APK及lint再次成功，完整日志及XML分别保全。
- 单测实际通过本地MockWebServer GET成功及403返回；原始错误码/原文在details，摘要给出原因和下一步；DNS、连接拒绝、超时、TLS、传输、身份、恢复、服务/限流与未知原因分类通过。它不是服务端MySQL联调。
- `font100-01`：首轮在滚动到连接按钮时失败；声音已切换还原，未发诊断请求。保全设备和失败日志后，Python核83表不变、原绑定不变。根因是ScrollView动画未稳定时的Espresso scrollTo断言，改为真实滑动并等待稳定，不改产品滚动或调用点击监听器绕过触摸。
- `font100-02`：实际“我的→设备设置”、声音切换还原、停机后台连接拒绝、详情展开/收起、更新页往返、身份详情折叠、取消退出及返回通过，71.581秒。83表328行无业务变化，244个原件（包括原session/绑定/救援凭据）逐字保留。
- 独立消费者首轮将AndroidX `files/profileInstalled`安装标记误列业务不可变输入而失败；旧脚本/失败记录保留。只读补核确认唯一差异为该运行时标记，consumer修正只排除该精确路径并单列前后SHA，再消费原证据通过；没有为取证修复重跑业务。

`font130-01`、`font150-01`、`font200-01`同一产品输入分别81.535/94.252/112.133秒通过；每轮83表328行无变化、244个原件不变，身份/救援凭据与原STOP/批次等证据均保留。四档均实际滑动、点击和检查文本布局，无省略号/文字盒裁切，截图自审对应默认诊断、放大原始详情/长标识及更新页。字号已恢复1.0，App已强停，reverse为空，未点击检查更新或安装，不绕过原HTTPS/设备审批阻断。

`evidence-manifest01.json` SHA `4e591004d4a1688f6ca29a07fd07b0f13edaee7520eec580f067f388b75fe5e9`，1956份证据长度/SHA双次一致，产品代码与input04无漂移；`manifest-double-check01.json`保存核验结果。build02 lint434警告/0错误；产品APK `build02-artifacts/app-debug.apk` SHA `8357a8748c7bad73c34fea7f36e98e95c5c12a7d74b53255fd87f8a26b5b1a48`，测试APK SHA `0d2b7f4bbdd4a9c6d2154fa00a90c1d6a05f8818feca6ef797e03101827907cf`。产物继续保存在D盘，不发布。

未受影响复用：`network/PdaSession.kt` SHA `998b53a06bec2f2fcf10afebb43581935424902fb6bfd70150d4ea80edbd5cea`、`network/PdaHttp.kt` SHA `99fa156f28e13e6dc026b7a38154d6b57e2fcfbcad462256ceacc821d103b11a`、`ui/MainActivity.kt` SHA `c6531a47e99d153c1d950b9f0b7646cff7b79952298879a640cd170ae3db19b1`。路径前缀均为`android/app/src/main/java/com/acrylic/outbound/pda/`；原Room模型/迁移和服务端未变，合成网络测试不能替代最终组合的真实HTTP/MySQL。

作者与自审为同一助手，独立Python消费者不等于独立人员。未覆盖实际成功后台连接的设备组合、真机字号/键盘/操作员、所有其他辅助页或U整体，保持相应未验范围。

## 环境、提交断点与后续

input05/06全量5339项SHA `1be0a668a55cbb42a0ba5ae5e11fb32985d3d882f82faed9513a1af4ae6b0ab5`，旧看板、全部差异/未跟踪原件及唯一写权均保全。5331项非本切片原有文件相对input01无漂移。input06采样C/D余量83,064,414,208/34,044,616,704 bytes；所有新增产物/临时路径在D盘，未清理既有证据。

无本批Java/Gradle/MySQL或新服务监听；原TEMP AVD/ADB保持，只有既有共享mysqld身份未核清而未连接、复用或停止。编译的三类旧交接/续作开发开关均false（生成BuildConfig原件已保存），七类生产能力、TEMP生产归档和`localDeletionAllowed=false`保持。仅本切片明确路径提交推送开发分支，继承Android/server/工具/文档原件不混入。

下一步继续E06/E07受影响组合、E08其余辅助/大字与D16恢复引用依赖、E09全闸门；已取消交接链不恢复。历史1540浏览器审批阻断、失败和暂缓计数保留；D18最终候选L3仍必跑，总目标active。
