# U04 TEMP原记录筛选：真实IME四字号消费者

## 有界结论

固定树D:/acrylic-pda-outbound、codex/development，输入f9eed366cc6acf947e159d296a479317d0995f70及保留WIP；仅新增TemporaryAdjustmentNativeImeDeviceTest，产品源码及APK不变。复用原TEMP隔离AVD、原身份和22条合成记录，在100%/130%/150%/200%实际字号下，真实IME输入、筛选/收键盘、Activity重建、非法0拒绝、键盘打开时清除恢复全部各1项通过。

所有可见调整行保持重启授权围栏并禁用，writer未点；83表逻辑摘要、会话内容与session.enc密文守恒。仅补TEMP真实键盘有界Android L1，不记正常有效授权、非空可信主管保留、完整UI/无障碍、U04/D17整体、统一候选L2/L3或第8节通过。版本事实见看板生成块。

证据根：D:/acrylic-pda-outbound/.local/goal-20261020-resume/u04-temp-native-ime01/。作者和自审为同一助手，Espresso/Python是工具消费者，不是独立人员审查。

## 输入、实现与验证

- 先保存原文件不存在的事实、全部构建输入及前轮final-manifest、看板字节；只新增91行测试，复用AdjustmentStatusDeviceFixture和实际窗口/IME安全区helper。不修改原fixture、产品、权限、session/anchor、Room或契约，不重新造数、wipe、续签或恢复取消链。
- 独立assembleDebugAndroidTest离线构建退出0；新测试APK另存apk/，未覆盖原APK。构建命令/子进程D盘TEMP与Java临时目录、锁定输入、日志及退出码在build01/。没有本轮产品构建/lint/主机测试执行，不将历史结果抄作当次结果。
- fresh核写权、进程、端口后启动同U04StatusConsumer01/emulator-5680。安装前device-before.tar与[上轮TEMP新APK消费者](GOAL_U04_TEMP_NEW_APK_STATUS_CONSUMER_2026-10-20.md)最终tar逐字相同；仅adb install -r -t替换测试包。启动/安装后/最终分别回读产品APK、测试APK与原输入SHA，产品APK始终相同。
- 实际运行nativeKeyboardKeepsTemporaryFilterReachableAndAuthorityBlocked，显式synthetic-only、原契约SHA、新evidenceRun与expectedFontScale；先断言原记录重启阻断，再真实click/typeText输入22，等待实时IME、滚动到完整安全区后点击，结果22且收键盘，重建后原筛选与围栏保持。输入0拒绝不产生行，IME打开时清除恢复1–20，全部行仍禁用。

| 执行 | 实际fontScale | JUnit | 实际keyboard-valid IME bottom |
| --- | --- | --- | --- |
| temp-ime200-current | 2.0 | OK (1 test) | 425px |
| temp-ime100-current | 1.0 | OK (1 test) | 425px |
| temp-ime130-current | 1.3 | OK (1 test) | 555px |
| temp-ime150-current | 1.5 | OK (1 test) | 425px |

原生触屏helper按实际window visible frame与系统栏交集找安全区，未假定键盘固定高度；keyboard-invalid均实际IME可见，clear后均收起。完整命令/退出码/JUnit原日志、实时几何与触达轨迹分别保留，不以adb退出0代JUnit通过。本轮四次消费者无失败。

## 数据守恒与可见边界

四轮database-before/after均与原seed01/database-seeded.json逐字相同，共83表；SessionVault内容相同。session.enc前后密文相同。逐成员分类比较中122个旧受保护应用输入/证据与密文不变，52个新证据成员新增；仅WorkManager的workdb、-wal、-shm三个旧成员变化，Room DB/WAL/SHM未变。整个tar不同，不将逻辑摘要推广到未采样输入。1032项实际测试构建输入无漂移，除授权看板外31条继承路径SHA未变。

实际打开200%keyboard-valid与100%cleared两张内容图：前者输入22可读，部分标签因滚动裁切；后者可见“全部”、筛选/清除和重启围栏，列表底部裁切。Activity内容图没有键盘像素，不能称全屏键盘实拍、整页布局或无障碍验收；真实IME可见/收起与完整按钮可触达由实时insets及实际触屏断言证明。

| 证据 | SHA-256 |
| --- | --- |
| device-before.tar | b80a11e2710a90039f4a7989f6955a9413dccbfce9cb8c72482607e287233bd1 |
| device-final.tar | 9726e35518d737b2dfb40f53d8020d073e01c6a44a1089cc037a7281f1f65a3a |
| consumer-audit.json | c0380d26e2823c8a8fce556c386b3b3c7fbf9817f06ede63108b36ece715cd2c |
| session.enc | 7b8b911dcc52e5358d4699528fdeae8ebb7fade2c7e3550a5797b45e6fc91a28 |
| 产品APK（复用） | 5c1b3502272f3c4f5e6f9eb0ebd18007cdf2aab69a6bd42f7acbe9b606618ccd |
| 新测试APK | 93dd360108915076993692d50adb03756b4049f9dc00223f06e2e39a3f639fcb |
| 新测试源码 | 8da4db91bcf15c862350f87b8e3fcd9c4591ce6195f41507d5f22fa00e366acb |

## 停机、清理与剩余

emu kill退出0；5秒采样仅见emulator退出watchdog，端口已空，原environment-stopped.json保留。后续fresh environment-stop-settled.json无emulator/qemu/Java/Gradle和5680–5683监听；源Pixel_6八镜像与Debug Keystore未变，本TEMP AVD运行时自然变化，原身份保留。未连接任一数据库、未做USB业务或使用正式签名。

可重建build/cache/tmp盘点2469文件、39,772,755逻辑字节，逐项哈希与实际重建输入/命令/结果保留；此前递归清理自动操作审查blocked by policy仍不换方式绕过，本轮删除/释放0字节。旧/新APK、原tar/日志/回执、身份和继承改动保留。本地runner在安装前改为单独不可变input-install，原字节/SHA保存；未覆盖原input或重跑消费者。

仍未验正常有效授权下TEMP同条件四档、非空可信主管保留、完整UI/实体键/M71、原身份/原资料/现场回执、真实HTTP/MySQL/Chrome/Python、非空迁移和统一实际最终候选L2/L3及第8节。O02 SQLite CANTOPEN无新runner假设保持暂缓。下一项先核其他独立页面/文件或实际组合缺口，不继续机械扩字号或重做已验范围。

本测试、新报告与两索引共四条明确路径开发备份，提交/远端及全部继承WIP清单/SHA见git-receipt.json、final-manifest.json、closeout.md、output-manifest.json。总目标active、未完成；生产决定、原库/现网、正式签名、main、部署与Release关闭，localDeletionAllowed=false。
