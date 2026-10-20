# D06/D07 默认关闭的真实 Chrome / HTTP 消费者（2026-10-20）

## 范围与结论

本切片补“默认关闭时，有权后台用户与既存未决请求不能触发故障撤回”的真实页面/HTTP边界。产品源码未改，新增四项Playwright测试。输入为合成隔离身份、新生成开发签名、新隔离MySQL和本机Chrome；不计完整D06/D07、统一L2/L3、D18或第8节通过。总目标active、未完成。

运行前分支codex/development，HEAD为69d98046ec14c95a9d71bf350daa5a6417c8d14b。证据根为.local/goal-20261020-resume/d06-d07-current-http01/，原输入2727文件SHA在source-before.json，31条受保护继承路径逐SHA未变；看板单独授权刷新。

## 实际执行

- web/node_modules缺失，首CLI预检未执行测试；文字记录preflight01.txt。按原package-lock运行npm ci，134个包恢复到项目，缓存位于D盘.local/npm-cache；package与锁文件均未变。
- vue-tsc --noEmit退出0，门控单测1/0/0/0，退出0。项目tsconfig不包含Playwright文件，不将类型检查算作新测试的静态类型验证；新测试由Playwright实际解析并执行。
- attempt1708因Gradle任务参数顺序错误，--tests应用到classpath任务，未执行业务测试。原命令、日志、退出1和空库dump保留，数据库认证停机。
- attempt1709修正任务顺序，使用全新隔离库；既有FaultRestartConsumerFixtureIT为1/0/0/0，Gradle退出0。该MockMvc夹具只生产输入，不冒充浏览器正向或非空升级验收。随后以相同编译类路径启动真实Spring HTTP。
- 两个故障撤回开关均未设置，没有显式启用；仅loopback端口MySQL19780、HTTP19781、Vite19782。run1709隔离实例UUID为73449a08-cc41-11f1-910c-d8bbc13a2b36，datadir明确绑定.local/e2a-cross-20260925-1709/data；账号/库名/端口实际回读在attempt02/database-identity.log。

主要命令（完整参数与退出码在各command.json及exit文件）：

```text
node npm-cli.js ci --cache D:/acrylic-pda-outbound/.local/npm-cache --no-audit --no-fund
gradlew.bat -p server recoveryControlClasspath integrationTest --tests *FaultRestartConsumerFixtureIT --offline --no-daemon --console=plain -x :report-rendering:prepareFont
node node_modules/@playwright/test/cli.js test tests/fault-withdrawal-default-off.spec.ts --reporter=json --trace=on --output=<evidence>/attempt02/playwright01
python <evidence>/audit-default-off.py
```

## 四项真实消费者

| 用例 | 实际结果 |
| --- | --- |
| 有order:recall权限用户打开已领取订单并刷新 | 撤回/原请求重试按钮及对话框不出现，页面零撤回POST |
| 合法未决请求重载 | 原键/正文/恢复代次完整JSON字符串逐字保留，显示关闭提示，零撤回POST |
| 损坏未决请求重载 | 原损坏字符串逐字保留，显示不可读取提示，零撤回POST |
| 同有权会话显式直调真实HTTP | 409 / FAULT_WITHDRAWAL_DISABLED，订单状态与rowVersion保持 |

Chrome为expected=4、skipped=0、unexpected=0、flaky=0，原命令退出0。四份trace中只有直调反例的一条撤回POST，HTTP409；其他页面为零。本轮不覆盖开启后的正向、无权/跨仓、并发、回滚或Android。

## 守恒断言失败与独立复核

运行器要求14表dump逐字相同，遗漏了真实登录必然新增审计，故在四项测试通过后仍退出1。attempt02/result.json与business-conservation.json保持原失败，不改为整轮通过；没有重跑业务或重启失败库。

独立只读工具audit-default-off.py消费保全的SQL和四份trace，退出0：13张业务表原字节逐字不变（含5张空表），operation_event原30行全保留，仅新增同一已核合成用户的4条WEB_LOGIN审计。在内存中仅移除这4条新增登录行后，after与before逐字相同；SQL原件未改。网络trace只出现预期显式HTTP反例POST/409。工具消费者与作者为同一助手，不称独立人员审查。

从原trace抽取的损坏/合法请求末帧均实际打开：关闭/错误提示、已领取状态可读，无撤回按钮。仅1440×1050桌面视口，不是完整布局、移动端或无障碍验收。

## 关键原件绑定

| 输入/产物 | SHA-256 |
| --- | --- |
| 新测试源码 | 5944acf7a2c938fd20fc5882860d132e288f124fb675e944f960d70b503381f9 |
| 合成输入 | cd3e7259aca902d2a935301ba617cfaa2c323bef4d6fef521973f7bd7564b60c |
| fixture XML | 21f0733962122004dce531521b07d074761f29ad93098dd2d20b3f67f2911ed1 |
| Chrome原报告 | 5be3291404304eb7681bcb120b76ab9293ececdc6070ed412e9cd4d525acbe8c |
| 独立只读审计 | d5dbf56006c5a1fd8198f1a8c580ae0fe046d02821cccd9a144bfee583e441fc |
| 业务表before | d36f88300f78711e19cc55ef2ba008f2ef5ad50db0368f4bacd9b69824f07dc4 |
| 业务表after | 399eb1a8b2eba98affc8ba613a6f2035cbdb4c81b45ed4a13503a57904d94e72 |
| 最终隔离dump | 9ecd45a7c33b564b821e70483fe6e2da94c4f2dc5d4f38aadce83286be6b1fd2 |
| 原失败回执 | d7b96e67166b119ee89c428b622747c52162edda48fbace0162debda41bd4c63 |

## 复用、收口及未验

旧服务端证据见[故障撤回恢复切片](GOAL_E07_D06_D07_FAULT_WITHDRAWAL_L2_SLICE_2026-10-19.md)，其中记录的8个直接源码SHA本次逐项一致，未为增加数量重跑。旧开启态组合见[run1614](GOAL_E07_D06_D07_ANDROID_HTTP_WEB_RUN1614_2026-10-19.md)，不与本轮拼为统一最终候选通过。

本轮未构建APK、未启动模拟器或操作USB业务，未执行Android、真实正向撤回、非空升级和恢复，web/dist保持。两隔离库均认证停机，HTTP/Vite退出，fresh无19780–19782监听；共享MySQL/ADB只观察，原库/现网未连接。Android Debug Keystore SHA保持。

原日志/SQL/trace及失败回执保留；测试数据和可重建依赖仅盘点，本轮删除/释放0字节，先前递归清理被自动审查拒绝后未换方式绕过。原APK/tar、原资料/身份/回执、继承改动继续保全。最终环境/空间/输出哈希见本证据根closeout.md、final-manifest.json和output-manifest.json。

不恢复取消换机链、不启动旧run03；生产决定、原库/现网、正式签名、main合并、部署/Release关闭，localDeletionAllowed=false。完整D01–D18/O/U及实际最终候选第8节仍待满足。
