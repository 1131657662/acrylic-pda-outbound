# 独立任务页原凭据卡消费者

日期：2026-10-22。有界功能修补的开发备份；不记完整D/O/U、第8节或现场验收通过。版本事实只见看板生成块。

## 输入、设计与退出标准

- 固定树D:/acrylic-pda-outbound、分支codex/development；输入HEAD 6a6e3672aee6ccb621fc655f7ab4e8d807b9836b，加本片8路径及受保护继承WIP。不是干净提交独立构建。
- 证据根.local/goal-20261022-resume/independent-task-card01/。baseline.json、inherited-wip.zip保全40项继承WIP；preimage/保存4修改目标原字节；closeout01/preimage/及preimage-manifest.json保存文档原字节/SHA。
- 实际缺口：独立卡已能生成/预置/核验，但任务页仍要求手工转录随机口令；先用RED证明，不重复四状态筛选或历史卡生成/电脑消费者。
- 按总目标第3节自主授权采用最小默认：任务页新增受控选原卡入口，保留手输；仅传递原卡中的口令给原校验器，不赋予卡文件额外信任。原设计见design.txt。
- 退出标准：原卡创建同一计划任务；取消/重建/身份或草稿变化不落请求；原期限、恢复围栏、撤销、持久尝试限制、耐久原请求/原键重试保持。四字号只检查新增按钮几何及滚动可达。

## 改动与安全复核

- IndependentTaskActivity及activity_independent_task.xml增加createWithCard；等待选卡时禁用计划、创建、历史与跳页操作。
- IndependentTaskCardPicker只保留草稿SHA及身份元数据；取消/返回/重建不持久保存URI、密码或grant，旧选择不能绑定新草稿或会话。
- IndependentTaskViewModel接原文件流、前后复核草稿SHA，沿原IndependentTaskRepository创建；失败保留原件及原请求。
- IndependentCredentials选卡前仅读会话七字段、登记钥摘要及可信时间；真正unlockCard在原IO执行器内经过完整恢复围栏、原卡解析和原unlock，grant每次snapshot/recheck仍核原expected及原校验器。
- 新IndependentTaskCardTest、IndependentTaskCardBoundaryTest及专用夹具。原Repository、扫码引擎、数据库结构、契约和生产配置未改；源码/资源8路径及SHA见source-review-final.json。
- authority、JCA、file URI、SKU/身份均为合成；使用原页面、vault与NATIVE Room，不伪造生产授权。未缓存口令或延长信任期限，原卡解析器的大小/编码/重复键约束保持。

## 实际执行与失败

各attempt保存command.json、inputs-before/after.json、gradle.log及exit.json，独立build/XML/project-cache，子进程TEMP/TMP在D盘。

| 执行 | 实际结果 | 结论 |
| --- | --- | --- |
| attempt01 | 单方法1/1/0/0，exit1 | 原任务页缺createWithCard入口；RED原件保留 |
| attempt02 | 五类28/7/0/0，exit1 | 首轮产品cardSelection同步访问Room恢复围栏，主线程被拒，7项页面未打开；是产品线程错误 |
| attempt03 | 同五类28/0/0/0，exit0 | 选卡前改为仅读元数据，真正授权仍经原IO完整围栏；1102输入前后不变 |
| attempt04 | 边界类5/1/0/0，exit1 | 推进三天触发校时守卫，未到签名期限校验；无请求，提示文案断言失败，不算期限通过 |
| attempt05 | 测试编译exit1，无JUnit执行 | 新测试漏checkNotNull处理SessionVault.read可空值；作者错误原件保留 |
| attempt06 | 短期限单方法1/0/0/0，exit0 | 合成签名60秒、推进61秒，先断言可信时钟有效且已过期，再拒绝落请求 |
| attempt07 | 两新类同次13/0/0/0，exit0 | 共享夹具修正后定向回归；1103输入前后不变，不拼成41项同次通过 |

- attempt02/diagnosis.json记录真实产品错误；attempt04/diagnosis.json及pre-fix-*保留时钟前提失败；attempt05/diagnosis.json保留测试编译错误。未覆盖失败原件。
- 最终两类覆盖：原卡不转录创建2包任务、原请求/审计唯一、原卡不变、取消、草稿变化、会话切换、Activity重建、撤销、错误口令持久限制、注销拒绝。
- 七身份字段逐一变化、恢复围栏、可信期限到期均拒绝且无请求；末审计trigger失败保留耐久请求，重建后同卡重试保持原请求字节/授权且审计唯一。
- 四字号100%/130%/150%/200%的主机NATIVE图形几何：按钮高52/72/80/99像素，无省略裁切且可滚动到达；不是原生设备截图、真实IME或外部SAF验收。
- 最终XML：attempt07/xml/TEST-com.acrylic.outbound.pda.ui.IndependentTaskCardBoundaryTest.xml，SHA-256 6fb5cd8e3fdfcd8d5f96f3609e0219f746ac216a63a1c2c531925e62df707e02；IndependentTaskCardTest.xml为de99d205d4abbd14d7f592241d02a2e6c90fb6c3bd40b451ebcdcb04e87566b3。

## 构建、签名与自审

- build01运行器在projectsEvaluated改签名配置，被AGP冻结拒绝，exit1；未执行业务/产物签名。诊断和原运行器保留，产品不为此修改。
- build02的offline独立lintDebug/assembleDebug退出0。lint 0 errors / 558 warnings，含新按钮HardcodedText一项，不称零警告，也不扩为全项目文案重构。
- APK：build02/build/app/outputs/apk/debug/app-debug.apk，14,488,662字节；SHA-256 38c77789ee65ba905be591feb477a94a93cc3689911015c19385f8d96a4ceaad。保留产物，未安装、上传或部署。
- apksigner verify --print-certs退出0；CN=Android Debug, O=Android, C=US，证书SHA-256 2437447bae29492167a90d7dd2adaf696b226b05438f995475d4fff523e51196。既有用户Debug key文件SHA-256 0539b3d0b80ab4619a08db639d433ec2f3ac0a8d7b2fbdc32865d5cfc6b0b6cb。未用正式签名；原Gradle配置会读取signing properties，不夸称从未读签名配置。
- 构建后仅测试夹具和新增边界测试改变，产品/构建/依赖/schema输入核同，因此未机械重建；不是完整最终候选的干净提交构建。
- 作者完成自审，source-review-final.json SHA-256 a52622cd483c0deca4ba456984cb18fbda43dbf15e735803ba3e40ec2e1f041e；不是独立人员审查。

## 保全、清理及接续

- closeout01/evidence-manifest01.json登记输入/命令/XML/日志/诊断哈希；文档收口前继承40项仅看板因事实刷新变化，其他39项原字节核同。历史索引只暂存本片入口，继承条目留工作区。
- 自动审批以blocked by policy拒绝删除attempt06/07的project-cache，命令未执行，释放0字节；cleanup-review.json留记录，不换包装重试。不冒领其他聊天清理空间，未删XML/日志/APK/身份或继承材料。
- 本片只覆盖任务创建选卡；独立扫码页面免转录、完整卡→任务→扫码→导出页面链、真实外部SAF/Keystore/设备/IME、统一最终候选及可信业务回执仍未验。原非空API/文件链按原输入引用，不替代页面消费者。
- 未连接原库/现网/实际test服务，未操作真机；MySQL配置/datadir只读登记不构成实际数据库身份认证。
- localDeletionAllowed=false；取消链及旧run03不恢复。生产开关、正式签名、main、部署和Release关闭。
- 总目标active、未完成。下一有界项先核独立扫码原卡入口及同一非空页面链缺口，只补受影响路径，不重跑已验筛选，不计完整O02/O04/U/D17/D18或第8节通过。
