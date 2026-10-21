# 独立采集凭据卡：受控读取与原核验器消费者

## 输入与范围

沿P03、凭据交互接入和持续自主设计授权，在原独立身份页补显式读卡。作者自审、合成主机消费者及Debug构建仅覆盖有界切片，不计完整O04/U03/U04/D17、第8节或最终候选通过。总目标active、未完成；版本事实只见看板生成块。

基线e1555ba06b9432fd55eea734c9c9218c8b80f414干净git archive叠加overlay-paths.json明列10路径；5965其他归档成员逐SHA核同，10份原合成辅助fixture另列。33份非看板继承WIP未叠加、未改、未暂存。source.zip SHA256 96d9d6478ee94bba4404c60aacd1ce303f953abf68f86a80bb771c800d5529e0；最终source-manifest-final02.json逐项绑定。

证据根.local/goal-20261021-resume/independent-card01/；设计design.md、自审self-review-final.md。源码/文档写前原字节和不存在记录见preimage.zip/json、fix02/preimage.zip、document-preimage.zip/json；全WIP见resume-20261021-115722/。10路径是IndependentCredentialCard/IndependentCredentials/IndependentCardPicker/IndependentCredentialActivity/IndependentCredentialViewModel、activity_independent_credential.xml及Card解析/页面/边界/夹具测试。

## 行为与安全边界

显式OpenDocument读取原独立用途卡：4096字节上限、严格UTF-8、字段白名单，拒绝重复键、错类型、未知字段、尾文、坏用途与非原人机仓钥。选卡前后及原核验后复查登录代次、恢复代次、权限版本和钥摘要，拒绝旧回调或换登录。卡只是口令载体，不是签名或授权。

仍走原verifyLocally、PBKDF2、加密vault、签名、可信期限、持久限速/撤销及恢复围栏。仅显示本次身份核对，不保存采集grant、不创建任务、不延长期限；任务创建仍须原核验。不授出库提交/确认、放行或删除，不用于本人救援/主管授权/文件解密。普通作业未增加前置。

FLAG_SECURE、通用失败文案；无口令上传/日志/文本回填，不保存URI/卡到Bundle或剪贴板。可变数组finally清零；JSON和解码String不宣称全堆擦除。取消、重建、旧卡或撤销不替换原资料，不把读取成功称为业务成功或物理外置证明。

## 实际测试及失败保全

| 运行 | tests/failures/errors/skipped | 退出码与定性 |
| --- | --- | --- |
| android01 | 38/10/0/0 | exit1，选卡开始前未得Intent |
| android02 | 38/10/0/0 | exit1，运行器漏同步修后夹具，重复旧输入，原件保留 |
| android-fixed01 | 38/10/0/0 | exit1，已同步仍cardSelection为空；旧按钮渲染归因不充分 |
| diagnose01 | 1/0/0/0 | exit0，隔离仪表化单方法，不代表整组 |
| diagnose02 | 12/11/0/0 | exit1，选卡前saved-vault为空 |
| diagnose03 | 12/10/0/0 | exit1，后续测试读取首个测试已清理vault路径，旧Application引用残留 |
| android-fixed02 | 38/0/0/0 | exit0，最终同次六类全部实际执行 |

前三轮失败未清零，以新增证据推进；诊断从11:28:52至最终12:00:23约32分钟收敛。diagnose03只记合成路径/实例/线程/存在性，无凭据正文。本地Lifecycle字节码factory-audit01证实AndroidViewModelFactory静态_instance持有Application。只在本片IndependentCardUiFixture初始化/关闭finally重置测试单例，并assertSame证明模型使用本测试Application。产品工厂未改、无新增sleep、未删业务断言或放宽门；隔离仪表化逐字还原，5965其他归档成员核同。

最终11:57:24–12:00:23，六类为Card解析5、原Proof3、原Store7、CardActivity5、CardBoundary7、原SetupActivity11；原方法/时间戳/SHA见android-fixed02/xml-summary-final.json。原XML压缩包SHA256 0604d2135c8c0364c9c07e40befb39f0a52eca223665f4e4bac03a88f0bdf318；输入input-original.zip、command/result/gradle.log保留。本轮复用android02/outputs，前各轮XML已分别封存，不把复制XML计新执行。

正向为页面生成卡→原在线预置/签名/vault→停止合成服务→显式选择→原离线核验；断言无口令回填/上传、无任务创建。反例含重复键/未知字段/坏编码/超限/错用途、人机仓钥、换登录、重建旧回调、旧卡、撤销、五次失败限速跨重建、重启、过期及恢复围栏。本人卡与主管未受影响范围按源码SHA历史复用，不重跑旧用例。

运行器fixed02首次在Gradle前因旧临时路径已存在而失败；续接一次因已有resume03/out.mkdir失败，同样未启动Gradle。先保存runner原件、确认输出目录空，再用全新.local/i21cf02执行。详runner-incidents.md。APK审计首次因Python默认GBK读UTF-8中文路径失败；保留脚本和已输出badging/signature，改显式UTF-8写新final回执成功，不覆盖原件。运行器失败不计业务测试执行。

## 构建与产物

同源build01执行lintDebug和assembleDebug，12:01:15–12:04:32，exit0；lint 0错误/536警告，本片路径16项：HardcodedText14、Overdraw1、LabelFor1，不称零警告或全部历史。Debug APK 14386919字节，SHA256 358d1eacc439ffb0a18ae71df6b42b7aef8c7233ce36a3c3bfa286eaa66b53fd，保存在本片android02/outputs/AcrylicOutboundPda_app/outputs/apk/debug/app-debug.apk。

badging、Debug测试证书及命令/退出码/输出SHA见build01/artifact-audit.json、badging-final.txt、signature-final.txt；此前7件APK/JAR逐SHA保持。未安装、上传或部署，构建不替代业务验收。

## 切片收口清理

仅本片18处可重建Gradle缓存/中间目录，7652文件、151819742逻辑字节；清理前核无Java/Gradle/AVD/Python消费者、绝对路径与reparse，逐文件大小/SHA和重建命令/依赖配置在cleanup01/manifest.json，SHA256 f1db518de7d0d2788ed8df784b7e824504b628758d327ca01b4c82d1142667e0。单一PowerShell明确路径执行exit0；D余量5121896448→5290381312，观测增量168484864字节，与逻辑清除长度分列。40项输入/产物删前删后核同。源码、所有原XML/日志/仪表化证据、原件、数据库/身份、最终APK及继承WIP不清；旧拒绝目标不重试，历史空间不重复计。

## 未验与接续

Robolectric、随机合成JCA、MockWebServer和file://仅主机边界，不是实际Keystore、外部DocumentsProvider、原身份、M71或真实后台HTTP。本片无非空独立任务/导出、外置介质、物理保管、输入次数或最终候选恢复/非空迁移组合。主管安全生成、分用途复用、包解密材料与分步导出仍需独立实现/验证；凭据整体整改及总目标未完成。

不恢复换机承接/多跳/接收者确认激活链，不跑旧run03。生产开关、原库/现网、正式签名、main、Release、部署/上传/更新清单关闭，localDeletionAllowed=false。原资料、身份、签名/回执、唯一证据、原APK及33继承保持；清理和提交另记，不作验收进展。
