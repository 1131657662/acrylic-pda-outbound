# U04声效未就绪时耐久保存回归（2026-10-16）

新增四项测试在实际产品WorkSound与三类扫码engine上通过；最终受影响组合31项0失败、0错误、0跳过。覆盖未加载声效时已提交原件/页面状态一致，以及末条SQL失败不发成功反馈、原ID恢复一次。本批为测试L1及有界组合开发备份，不计U整体、E08 L2、D18 L3或总目标完成。

## 输入与路径→影响层→必跑→复用

固定树`D:/acrylic-pda-outbound`、`codex/development`，输入HEAD/实核远端`4b3a4debbef74f64f6b08b46b1dbaf63ddde58d5`，86项继承改动保护、暂存为空。唯一写者仍为本聊天`01a13a57-8082-7dd2-a08f-987483dcda4b`，清理聊天实际idle、已释放写权。旧看板另存`ORCHESTRATION_STATE_before_u04_sound_20261016.md`。

完整输入`.local/goal-20261016-resume/input25.json`共5413项，SHA`9f8bf38c66aff72efeced57c2856439cca1dcc1c2e96d26ee1e55efbf9729b98`；input26/27与同号status/binary diff/未跟踪原件/旧看板/写权/worktrees/进程/监听/磁盘齐全。最终测试源码绑定input27共5416项，清单SHA`2fc791d32fbb883bdd9f100f8c527cc454134a2b53f3a8a3b9db59825ac030ca`。

证据根`.local/goal-20261016-resume/u04-sound-readiness01/`。`design01.md`先明确范围：只新增三文件，不改产品/资源/旧测试/迁移；SoundPool在Robolectric设备边界未产生加载完成通知，WorkSound及保存/反馈逻辑真实执行，Room使用原生SQLite，不以无音频主机冒充真实PDA物理声音。

路径前缀为`android/app/src/test/java/com/acrylic/outbound/pda/`：

| 改动路径→影响层 | 本次必跑与最终SHA | 复用依据 |
| --- | --- | --- |
| `work/WorkSoundReadinessTest.kt`→FORMAL反馈/事务 | 2项：保存后未就绪警示/原ID重开不重报；COMMITTED末UPDATE故障回滚无成功反馈/同ID恢复。SHA`695e4acdc1bb27d1ba1c994c50d9ce2a6902048d94c8ca70f4ce6283787da964` | WorkEngine/WorkRecovery/WorkDevice/UpdateGate测试本次组合重跑 |
| `temporary/TemporarySoundReadinessTest.kt`→TEMP反馈/持久事实 | 1项：原生提交后实际WorkSound未就绪仍保存、页面不得放行提示、原capture重开保持、无submission。SHA`22443399b04f7ed6ccf3630e18321f74d5fa49dc50ecb42a9e1fe38716690fc9` | TemporaryEngineTest本次重跑；继承TemporaryFixture按全量输入保护 |
| `independent/IndependentSoundReadinessTest.kt`→独立采集反馈/待对账 | 1项：实际授权/保存、页面待对账、原capture重开不重复。SHA`9f94213fa392a9800a79a2da5628334df5132e019975aa72fc1d5f91a12ddfe8` | IndependentScanEngineTest本次重跑；无新增正式资格/账务 |
| 报告/索引/看板→断点 | 继承保护、代码/输入哈希、失败保全、版本生成/上限 | `.local/goal-20261016-resume/e08-aux-combination01/source-binding01.json` SHA`4190d97746c6ca2c60638fb37f8d9c382edf43826ea5a9f153b737a71da344b3`中938条Android产品/旧测试路径逐条核同，实际AVD导航按上一报告复用 |

## 实际结果、自审与失败

- host01：测试编译漏传`IndependentScanProcessor.drain`的active参数，退出1；业务未执行。原源码`IndependentSoundReadinessTest.failed01.kt`和日志保留，指纹次数1。运行器随后错误拷贝旧XML并断言类集合失败；`host01-xml`明确为旧结果，不当本批证据。修正新runner为编译失败即停止读取XML。
- host02：补显式active后四项通过，35秒；真实音频播放没有发生，WorkSound正确报告“声音未就绪，请以页面保存状态为准”。
- regression01：九类31项全部通过，37秒；自审发现仅在回调读到scan不够明确证明已出事务，三类engine测试各补`db.inTransaction()==false`断言。旧通过仅适用于补强前输入，input26未跟踪副本保留旧字节。
- regression02：最终三测试文件及受影响WorkEngineTest/WorkRecoveryTest/TemporaryEngineTest/IndependentScanEngineTest/WorkDeviceTest/UpdateGateTest，共31项全部通过，45秒；九份XML/SHA与`regression02-tests.json`保存。不是重试同故障，重复执行因新增事务边界断言有实际输入变化。

成功回调中断言事务已结束且数据库计数已提交；声音警示未使engine停止/重造输入，页面仍报告本机已保存及TEMP/独立不得放行、待主管对账。关闭engine并重开Room，原capture/scan逐值相同，仅一条记录，没有重报SAVED。FORMAL尾UPDATE触发异常后scan/guard/总数/成功事件均回滚，CONFIRM_REQUESTED原意图保留；仅在新合成夹具中解除故障注入后同capture成功一次，没有修改或复用旧失败库。

`review01.json`记录最终输入和XML核对，回归日志SHA`10b1ed3a69d99c05d99eae42a026eece8622fcbcf89246fbc46a379dacdfb81f`。三文件49/50/72行、各方法体小于30行，未加入密钥/凭据/真实资料。作者、执行及自审同一助手，XML/哈希核对不当独立人员审查。

## 环境及未验边界

构建前复测C/D空间并将预计增长和实际命令写入各command.json；TEMP/TMP/Java临时目录仅子进程D盘短路径。没有新服务/MySQL/Chrome/模拟器instrumentation或APK构建，本次不改产品，不对原AVD/真机写业务事实。原AVD/ADB保留、共享mysqld只观察，不连接或停止。失败/旧XML/日志保留，无清理。

input28全量5418项，SHA`db65b4982f09ea2141a37cece176067992adeb3a416cf3ba7b36fbec41b6aa29`及同号附件保存报告后状态；C/D余量82355429376/44341858304 bytes，无Gradle/Java残留。`inheritance-review02.json`核5410项非本切片既存输入不变。63份证据247657 bytes长度/SHA双核一致，manifest SHA`683aa05c7ff76e42debf5e0433fc8c306f80804d4f1d2bf008a989e2837f1e7f`；事实块/上限与diff检查通过。

本批只证明本机可隔离声效未就绪及持久事务边界，不证明真实扬声器、音量/噪声、声音加载失败恢复时间或目标PDA性能。U整体和完整故障组合、E06/E07/E08剩余L2、D16正常目标合法退出/复杂引用及D13–D15组合、E09候选和D18 L3继续；Chrome1540审批阻断不绕过。生产七类能力、TEMP生产归档和`localDeletionAllowed=false`保持关闭，不合main、不发布部署、不用正式签名，总目标active。
