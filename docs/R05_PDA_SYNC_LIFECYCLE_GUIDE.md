# R05 PDA 周期、断网恢复与显示检查

日期：2026-09-20。接续[持续同步联调](R05_PDA_BACKGROUND_SYNC_GUIDE.md)，沿用本机0.21/V28 API18091、独立库 `acrylic_outbound_r05_background_20260919`、Pixel_6 / API37 和 `com.acrylic.outbound.pda.debug.r05backgroundapi`；不重新登记、领取、扫码或清库，不操作M71或公网后台。分支 `codex/development`，R05整体仍未完成。

## 本轮实现与保护

新增独立原生分阶段测试，先核对旧成功提交、设备身份和签名审计前缀，再把上一轮已经关闭的临时转发地址改回同一个本机API直连。该修复仅在明确包名、模拟器、原设备ID、公钥及本机HTTP来源全部匹配时允许，原绑定文件另存证据；正式产品的禁止直接改绑定规则保持。

原生测试保存本轮前的Room业务表摘要、本次登录标识和公钥；每阶段重新核对原表、审计连续确认点15及签名证明，实际使用原Keystore私钥签名并验签。仅同步状态缓存及调度记录允许变化。云端20张业务/审计表与私有文件另作前后摘要核对。

发现设置页返回按钮固定52dp在200%字号下裁切。修改为 `wrap_content` 加 `minHeight=52dp`，随内容增高并保持最小触控尺寸；本轮唯一产品改动为这一布局属性。

## 断网与跨进程

1. 恢复本机直连后，原会话自动同步成功，保留原身份/凭据。生产调度器重新登记独立测试周期任务，实际初始延迟和间隔均为900000ms；不改系统时钟或工作数据库的到期时间。
2. 关闭模拟器Wi-Fi后，通过生产入口排入30秒任务，保存其唯一ID。强停应用，再在新进程等待超过原到期时间5秒：该任务仍ENQUEUED、runAttemptCount=0，checkedAt没有增长，原业务资料保持。
3. 恢复Wi-Fi后，原任务ID完成为SUCCEEDED，同步时间更新且三流成功，未创建替代业务请求或重新登录。

恢复Wi-Fi的首个测试在系统尚未完成连接时立即断言，产生1次失败；真实Worker随后按网络约束运行成功。修正测试为有界等待Wi-Fi就绪，再读取原ID，复验通过，未放宽产品网络条件。宿主脚本另曾误用PowerShell的 `Select-String` 位置参数，日志读取报错，但原生阶段记录保留；后续改用显式参数判定结果。

## 实际周期执行

周期任务ID `10c51f25-b64e-43da-9902-e2bc270903d4`，登记时间1789859157757ms，首次到期1789860057757ms，恰好相差15分钟。期间另做断网/显示检查及布局包覆盖后，任务ID、原登记时间和900000ms间隔均保持。到期前只读采集WorkManager SQLite副本，period_count=0、无该应用进程，JobScheduler保存原任务并等待TIME约束；没有调用强制运行命令。

首次到期后继续被动观察至1789860502981ms（到期后7分25秒），原任务仍period_count=0、无应用进程。JobScheduler时间/网络/后台准入均满足，`get-job-state`报告ready，但没有执行记录；这是未通过的稳定Wi-Fi纯时间唤醒观察，不能写成准时运行，也尚不足以确定为产品调度缺陷。

随后单独验证“已到期任务遇到网络恢复”：关闭Wi-Fi并确认disabled及无应用进程，恢复Wi-Fi后，系统自行启动PID6728，原任务于1789860607340ms完成，period_count由0增至1，run_attempt_count=0，原ID、间隔保持；三流同步checkedAt更新至1789860607332ms。该过程没有启动Activity、Instrumentation或强制JobScheduler执行，没有修改时钟/到期时间。它证明到期后网络恢复能唤醒原周期任务，不替代稳定网络下仅凭时间唤醒的验证。

保存这份被动执行证据后，再运行`actualPeriodicExecutionPreservesWork`，1项通过（0.174秒）：原周期任务仍唯一、下一到期1789861507340ms，无待执行catch-up链，原会话/业务表/15条签名审计前缀及Keystore签名验证保持。首轮共5个原生生命周期阶段、4种布局组合最终通过；首次定时唤醒未通过的记录和失败日志保留，后续稳定网络观察见下节。

## 接续：稳定 Wi-Fi、无应用进程的周期唤醒通过

2026-09-20沿用原API、库、APK和周期ID `10c51f25-b64e-43da-9902-e2bc270903d4`。恢复环境及正常启动核对后，第二轮稳定观察基线为`period_count=2`、`last_enqueue_time=1789861653334`，原15分钟到期时间为`1789862553334`；采集无进程基线后保持Wi-Fi连接。该准备阶段不计为纯时间唤醒通过，未重跑一次性业务夹具或周期种子。

本轮执行前应用进程为空；稳定观察期间没有启动Activity、Instrumentation、切换网络、强制JobScheduler运行或修改系统时钟/工作数据库，也未查询JobScheduler状态。准备阶段上一到期点的诊断快照单独保留；随后仅采集工作库副本和进程状态，避免查询可能触发系统重新评估到期任务。最终系统自然启动PID `4574`，原任务完成，结果如下：

| 核对项 | 实际记录 |
| --- | --- |
| 周期与重试 | 原ID保持，`period_count 2→3`，`run_attempt_count=0`，间隔仍为900000ms |
| 完成时间 | `1789862706266`，较原到期时间晚152932ms，约2分33秒 |
| WorkManager结果 | `Worker result SUCCESS`，随后原任务排入下一周期 |
| 同步状态 | `checkedAt=1789862706261`；“订单、条码和操作记录已同步；原作业授权期限保持” |
| 下一周期到期 | `1789863606266`，本轮停止环境前未继续等待该周期 |

先保存`passive-observation.log`、`passive-029/result.json`、`stable-periodic-success.json`及自然完成日志，再运行`actualPeriodicExecutionPreservesWork`，1项通过（0.168秒）。原会话、Keystore、业务表和15条签名审计前缀保持；云端20张业务/审计表及私有文件、原归档应用22个文件摘要与本轮基线一致。已安装APK摘要仍与下表相同。

本次接续未修改产品源码，未重建APK或重跑192项主机回归；该主机结果属于前一轮布局修正。这里只证明API37模拟器上原周期任务在稳定Wi-Fi且无应用进程时最终自然完成，实际存在约153秒延迟，不承诺15分钟精确到点，也不替代Android9/M71、长时或断电验收。

## 显示检查

在density240下，480×800/100%、480×800/200%、480×600/100%、480×600/200%四种组合最终各1项通过。同步提示、声音开关、连接检查、更新、退出登录及返回均可滚动完整显示，断言文字布局无横向/纵向裁切、无省略，控件保持最小触控高度，FLAG_SECURE保持。

首轮Espresso自动滚动在滚动完成前判断失败；随后原生 `requestRectangleOnScreen` 未充分避开系统栏，改为在实际ScrollView中立即滚动到控件，再核对可见矩形。该调整后正常/低高度通过，200%两种场景实际暴露返回按钮文字裁切；修改产品布局后四种组合全过，失败日志均保留。结束时恢复原1080×2400/density420/字号1.0。

## 监控告警

用户提供的服务器告警已确认是昨日停止的两项R05演练服务遗留failed/143标记。只读存证并确认无进程后，仅对这两个unit执行reset-failed；两项均inactive/dead，服务器failed列表为空，现网API和备份timer保持active、发布目录/JAR摘要保持。未重启演练或现网服务，未关闭监控；143正常退出修正的实际启停复验仍受原阻断，见[V28说明](R05_V28_REHEARSAL_GUIDE.md)。

## 证据和剩余范围

本机ignored目录 `.local/r05-background-resume/` 保存原生阶段日志、布局每轮失败和最终结果、原周期检查点、只读工作数据库副本、Room摘要、云端/文件摘要、APK和监控处置记录。原归档包和原V28恢复库保持；不得把本轮布局包与上一轮APK摘要混用。正式API/Room版本保持0.21/V28/Room14，开发APK仍code15，不是原公开旧版15或code16。

稳定Wi-Fi接续证据另存 `.local/r05-periodic-20260920/`：`stable-periodic-success.json`、`passive-observation.log`、`passive-*/result.json`、`logcat-natural-completion.txt`、`native-preservation.log`、`cloud-before.json` / `cloud-after.json`、`device/periodic.json`和`verification.json`。到期诊断的`jobs-at-due.txt`、`alarm-passive-after-due.txt`保留原状，不单独作为成功依据。

本轮实际重跑192项Android主机测试，0失败/错误/跳过；Debug与AndroidTest APK构建及lint通过，0错误/129警告。已安装APK逐字节读取后，与本机下列产物摘要一致；原归档应用22个文件摘要与本轮开始前一致。

结束时云端20张业务/审计表及全部私有文件摘要与本轮基线一致。设置恢复1080×2400/density420/字号1.0、Wi-Fi已连接；停止本轮API18091和Pixel_6模拟器，共享MySQL13316保持原进程。独立库、AVD、应用资料及所有证据保留，未清理原恢复库或原归档包。

| 本轮独立包产物 | SHA-256 |
| --- | --- |
| `.local/r05-background-resume/app.apk` | `53d8531ca1670a1088031190be0505e6c8308f8f52f5e0de239bf486b974881c` |
| `.local/r05-background-resume/test.apk` | `df9eccded4053508993db469fed6dbb648f77b8c8b583d4c1496c9a8bf270d56` |

Android9/M71、第二台/异地、真实长时负载与断电、多设备完整验收仍未完成；整单90天事实清理依赖R09实际备份恢复及引用核对。独立HTTPS升级渠道、Linux143实际启停保留原自动审批阻断。没有提交、推送、合并、打标签、发布或部署。

稳定Wi-Fi周期唤醒的API37验证已收尾；下一步按[R05剩余验收](R05_ACCEPTANCE_STATUS.md)推进尚缺的真实API场景，Android9/M71、第二台PDA、异地网络、长时作业和断电恢复继续待验，整单事实清理等待R09备份恢复及引用核对。原库、包和周期ID保持，后续启动可能续接到期任务，须先读取保留检查点；不重跑一次性夹具或周期种子。真机仍按用户决定后补，公网保持0.18/V23/code16。
