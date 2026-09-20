# 下载 PDA 应用：交付与启用说明

日期：2026-09-19。分支：`codex/development`。固定下载页已部署到独立测试公网，staging code16首次安装已开放；隔离空白模拟器完成Chrome下载、用户来源授权/系统安装、启动、配对登录、同步及更新检查，并复验网页按钮下载到系统安装器。M71原应用与数据保持，真机/异地后补；未提交、推送、合并、打标签或GitHub发布，R05整体仍在开发。

## 后续旧版升级验证（2026-09-19）

在新的隔离`PdaUpgradeVerify`中，原始code15经Chrome下载同一已发布code16、用户来源授权和系统覆盖，已验证取消重试、Room11→14、原身份/会话/私钥及全部原表/未上传冻结批次保留，升级后原会话同步与当前版本UI通过。7项原生阶段检查、首轮测试夹具失败修复及证据见[code15→16升级记录](PDA_UPGRADE_15_16_GUIDE.md)。这是浏览器/系统覆盖，App内完整升级链路受专用验证渠道限制尚未完成；公网16版及最低可升级版本16保持，不把本轮证据写成已开放旧机升级。真机、异地和R05其余待办仍按原路线推进。

## 浏览器与系统安装接续：已完成并启用测试分发

在新的空白 `PdaBrowserInstall`（`emulator-5582`，API37，Chrome145.0.7632.218）中，先从Chrome通过HTTPS下载code16候选，核对下载目录文件为11,173,106字节、SHA-256 `9bc281b2c9a91972e17843004f08208c172b862cb745a3c5baeda4e275df796c`。打开APK后系统要求Chrome来源授权，用户亲自开启“Allow from this source”并完成安装，随后明确回复“已经打开了应用了”。实际界面显示设备登记页。

| 验证阶段 | 实际证据 |
| --- | --- |
| 空白系统首次安装 | 安装前包名查询为空；实际包为code16/`0.11.1-r05-download-test`，系统安装器为`com.google.android.packageinstaller`，来源`com.android.chrome`；首次安装/最后更新时间均为2026-09-19 06:51:40 UTC；提取已装`base.apk`摘要与候选完全一致 |
| 首次安装后 | 原生冒烟1项通过：短码登记、原密钥保持、签名登录、订单/条码同步、列表读取、Room14完整性和更新检查；安装来源只读断言1项通过；启用code16后更新UI1项通过 |
| 发布清单 | 新ID `r05-download-16`，沿用原staging APK证书及原更新签名公钥，`firstInstallAllowed=true`，后台兼容标记0.18.0；公网清单验签、APK全量大小/摘要和no-store通过 |
| 公开页面 | 桌面浏览器显示16版和“下载安装包”；Android Chrome手输固定页并点击按钮，经页面校验后保存 `r05-download-16 (1).apk`，完整摘要仍一致；打开该文件正常进入系统安装器 |
| 安装器衔接复验 | 因此前同一模拟器已安装16版，网页按钮下载后的安装器提示“Update this app?”，已取消重复安装；未把该同版提示记为第二次空白首次安装 |

两段证据共同覆盖浏览器/系统安装和公开页面下载：首次空白安装使用当前站点的候选APK HTTPS地址，网页按钮是在启用签名清单后复验，二者文件逐字节一致。没有把ADB安装当作本次系统安装；ADB只用于读取证据及安装原生测试辅助包。测试模拟器密度临时调整后已恢复，旧模拟器身份、数据和M71均保留。

现在可使用[固定下载页](https://outbound-test.nbsmzwl.cn/pda-download)或[测试APK](https://outbound-test.nbsmzwl.cn/updates/staging/r05-download-16.apk)。本次仅批准新设备首次安装，签名清单的旧包覆盖下限为`minSupportedVersionCode=16`；15及更早版本的App可能提示兼容范围尚未验证/不在可升级范围，不能通过卸载、清库或网页强行覆盖绕过。该限制不影响全新设备，首次安装不套用旧包覆盖范围。现有设备到16的资料保留升级和Android9/M71/异地仍待单独核验。

本轮公网已原子切换16版清单，SHA-256 `bdede672609f486a74267b3efdfc786872c21a3a5d0e65ee9b49d73a0d1d1ab7`；前版清单及启用时间保存在 `/opt/acrylic-outbound-test/update-records/r05-download-16/`。后端仍为0.18.0/V23，进程PID2007878、JAR摘要和业务发布目录保持，未重启API或部署0.20.0/V28。停发仍使用下文410开关，不覆盖同ID APK或用降版伪造回退。

本轮机器证据为 `.local/pda-download-stage/browser-installer-verification.json`、`browser-system-installed-package.txt`、`browser-installed-final-package.txt`、`page-download-apk-sha256.txt`、`published16-verification.json`、`code16-activation.log`及`server-after-code16.txt`；3项原生运行日志为 `.local/pda-download-browser-installed-smoke.log`、`.local/pda-browser-install-source-test.log`、`.local/pda-browser-installed-update-ui.log`。旧冒烟测试原输出的`installMethod`曾硬编码ADB，已修为中性说明；本次安装方式以PackageManager来源、已装APK摘要及用户确认交叉验证，保留原始记录，不篡改旧日志。修正后的测试源码已编译，来源与更新UI测试使用新测试包执行，未重复配对已绑定设备。

173项主机、33项浏览器及3配置UI为上一轮同一候选的证据，本轮只执行上述3项原生检查及实际浏览器/安装链路。API37模拟器不代表Android9硬件、异地或生产验收；空队列归档不计为归档API验收。

## 前一轮接续：隔离模拟器与测试公网

用户选择“先用隔离模拟器验证，真机稍后补验”。本轮只向独立 `PdaDownloadVerify`（`emulator-5580`，Android API37）安装候选包和原生测试APK；安装前该包不存在。没有对M71执行安装、卸载、清库或重新绑定。

| 项目 | 实际结果 |
| --- | --- |
| 候选APK | staging `0.11.1-r05-download-test` / code16 / Room14，包名 `com.acrylic.outbound.pda.staging`；11,173,106字节，SHA-256 `9bc281b2c9a91972e17843004f08208c172b862cb745a3c5baeda4e275df796c` |
| 签名与构建 | `aapt`及`apksigner`核对包名/版本/API28最低要求/原staging证书通过；173项staging主机测试0失败/错误/跳过，APK与测试APK构建通过，lint 0错误/126项警告 |
| 空白安装后冒烟 | 1项通过：真实公网短码申请、管理员核对、原密钥确认、人员签名登录、订单/条码刷新、空授权列表和可领单读取、Room完整性及可信更新检查 |
| 更新UI | 3次通过：480×800、480×600、200%字体；点击检查显示当前版本，无下载/安装动作，返回及状态可达、文字无裁切。只对模拟器调整显示，完成后已恢复 |
| Web本轮重验 | 重新构建独立测试环境Web，33项隔离HTTPS浏览器通过，0失败/跳过；10项工具单测及95路径契约为此前源码收尾证据，本轮未重复运行 |
| 公网接入 | [固定下载页](https://outbound-test.nbsmzwl.cn/pda-download)匿名可达；实际浏览器确认登录入口、直接刷新、二维码显示、复制反馈及“当前版本暂不适合首次安装”状态 |
| Nginx与分发 | 修正权限问题后目标`nginx -t`通过；页面、配置、清单均200/no-store；debug返回404；停发时清单/APK均410，恢复后原清单及APK可读，原清单签名与完整APK摘要一致 |

本轮后端仍为公网既有0.18.0/V23，JAR SHA-256 `7edb592b109055a5301067cef97578e9758734b062a0c6a03d88d270fed249f7`；部署前后进程PID、JAR和`current`指向均一致，未部署本机0.20.0/V28或重启API。候选包空审计/空提交队列的归档检查没有调用归档接口，不能算作新版归档功能或404兼容回退验收。API37模拟器也不替代Android9/M71硬件验证。

Web单独位于 `/opt/acrylic-outbound-test/web-releases/pda-download-20260919-01/web`，Nginx根目录指向此处；旧哈希资源保留供已打开标签页使用。原业务发布仍为 `/opt/acrylic-outbound-test/releases/r05-sync-20260919-01`。配置与回退保存在该Web发布目录，`rollback.sh`恢复原站点后执行`nginx -t`和reload，不触碰API、数据库或APK清单；脚本语法已检查，未为演练而执行实际回退。

部署首轮公开JSON返回403：`/etc/acrylic-outbound-test`为受限0750目录，Nginx worker无法穿越。已将公开JSON及停发标记改用root所有的0755目录 `/opt/acrylic-outbound-test/public-config`，文件0644，保留后端私有目录原权限。不能通过放宽后端目录权限修复。本机及部署配置已同步；首轮构建UI测试时PowerShell拆分版本参数导致Gradle找不到任务，改用参数数组后测试APK构建和三次UI检查通过，失败日志保留。

本轮未上传code16，也未切换签名清单。公网仍为code15，`firstInstallAllowed`缺省false；页面不提供首次安装按钮。下一步是在隔离/专用空白设备补齐浏览器下载、来源授权、系统安装链路，随后按对应候选包证据启用新发布；用户已把真机补验后置，不重复要求清空M71。

本机证据位于 `.local/pda-download-stage/`：`candidate-verification.json`、`first-install-evidence.json`、`update-ui-verification.json`、`public-verification.json`、`web-deployment-plan.json`、`server-after.txt`及部署/修复日志。候选包为 `pda-download-candidate-16.apk`。原部署归档保留首轮配置用于审计，实际生效配置为 `nginx-site-fixed.conf`，复用时以源码中修正后的公开目录为准；不得直接重放首轮归档。凭据fixture和签名私钥不在Web部署包内，所有上述本机证据均不进入Git。

## 使用流程

- 登录页的“下载 PDA 应用”和设备管理页的同名入口均指向 `/pda-download`，无需登录。设备管理以独立标签页打开，关闭后原仓库、页码和列表仍在。
- 页面显示当前环境、测试/正式渠道、已发布版本、文件大小、时间、Android 要求及发布说明。二维码和复制按钮只包含当前站点的固定 HTTPS 页面地址，不含查询参数、账号、配对码或 APK 版本路径。
- 新 PDA 在浏览器打开页面，下载并校验 APK，再由 Android 系统安装器确认。打开 App 后按短码登记、人员登录、App 内检查更新的顺序操作；下载不代表安装成功。
- 已有设备优先使用 App 内更新。网页不判断旧包签名、作业状态或迁移是否安全；没有更新入口的旧版按原覆盖接入记录处理，不卸载或清空数据。

## 发布与校验

公开配置为 `/pda-download-config.json`，清单为 `/updates/{channel}/manifest.json`。配置只含当前环境名称、HTTPS origin、渠道、公钥、APK 签名证书摘要及已验证的后端兼容标记，不含私钥、业务数据或凭据。

浏览器实际验证 ECDSA P-256 清单签名，并核对渠道、包名、证书摘要、兼容标记、Android 要求、首次安装许可和同渠道不可变 APK 路径。APK 下载完成后重新核对实际大小及 SHA-256。进入页面、点击下载前、APK 下载校验后均重读配置和清单；最后一次核对通过才创建浏览器下载。发布变更先更新显示并要求重新点击，停发/断网/校验失败移除旧下载推荐。页面重新可见时也会刷新。

`firstInstallAllowed` 是签名正文中的可选布尔字段，缺省为 false。旧清单仍可用于原 App 更新，但不会自动成为公开首次安装推荐。首次安装不套用 `minSupportedVersionCode`/`maxSupportedVersionCode` 对已装旧版的覆盖限制。

生成允许首次安装的清单时，在既有 `tools/publish_pda_update.py` 参数基础上增加：

```text
--first-install-allowed --android-build-tools <包含 aapt 和 apksigner 的目录>
```

启用前须有对应候选 APK 的空白/专用设备首次安装及后端兼容证据。工具核对真实 APK 的包名、versionCode、versionName、最低 API 28 和唯一签名证书；不一致或 Android 工具失败时不写发布。debug 渠道禁止此标志。继续使用原渠道可信签名材料、新发布 ID 和更高 versionCode，不能覆盖原 APK 或重写同版清单来绕过首次安装验收。

源码收尾阶段没有生成新 Android 版本，后续code16候选见上节。该阶段一次本地工具验证使用已有 staging `0.11.0-r05-sync-test` / code15，实际大小 11,065,731 字节、SHA-256 `2c302a14cc24c9c0e7f00c64da9316e72ed73ed6f16f38972816eb56f934baf0`；临时生成的清单签名密钥仅用于本地测试且已随临时目录删除。这不是公网发布或首次安装验收证据，与 debug code15 的 APK 摘要也不是同一产物。

## 测试环境部署接入

1. 构建并验证本轮 Web，产物为 `web/dist`。把它及本轮 `deploy/test` 配置纳入待部署的测试发布目录；既有 `activate.sh` 会安装新的站点配置。使用完整应用发布流程时，必须另行核对其后端/迁移范围及产物版本，不能把这次 Web 验证当作后端部署依据；旧 `package_test_release.ps1` 的 R02 固定版本字段也不能充当当前版本记录。
2. 复制 `deploy/test/pda-download-config.example.json` 到本机 ignored 准备目录，逐项填入当前站点和已核验发布源。`origin` 必须是准确的 HTTPS origin、不带路径或尾斜线；`publicKeyBase64` 必须来自既有受信更新公钥，不能仅因下载得到某个公钥就信任它；证书摘要须与原渠道签名一致。`serverCompatibility` 只填实际部署且验证兼容的标记，不能直接把本机最新契约号填入旧服务器。
3. 在已授权的部署窗口，先创建root所有、权限0755的 `/opt/acrylic-outbound-test/public-config`，再把核对后的 JSON 原子安装到该目录的 `pda-download-config.json`，root所有、权限0644，并以Nginx worker身份检查可读。它是公开元数据；私钥和后端环境文件绝不能放到此路径。示例中的占位符不能直接启用。配置缺失/无效时页面保持“下载入口尚未启用”。
4. 保留站点、Web 和旧公开配置备份，再按测试部署流程启用本轮 `nginx.conf`。配置为固定页面、公开 JSON 和更新内容设置 `no-store`，禁止目录列表，拒绝 `/updates/debug/`，沿用 HTTPS 和安全响应头。必须在目标主机执行 `nginx -t` 成功后才 reload；本轮已完成目标站点语法/运行验证；后续每次部署仍须重新检查。
5. 先上传完整唯一 APK，远端校验摘要后原子切换签名清单。只有完成首次安装验证的发布才使用 `firstInstallAllowed=true`。没有标志的既有公网 code15 不因新增 Web 页面而自动获准首次安装。
6. 无登录分别打开登录页入口、设备管理入口和手输固定地址，检查实际配置/清单/APK、HTTPS 与 `Cache-Control`；然后用专用空白设备完成浏览器下载、来源授权、系统安装、短码登记、登录及 App 内更新检查。二维码由真实设备扫码打开另记证据，不以桌面解码代替。

## 停发与回退

新增站点配置生效后，在目标服务器创建 `/opt/acrylic-outbound-test/public-config/pda-updates-paused` 文件即可令 `/updates/` 返回 HTTP 410；App 更新与公开下载页同时停止取得清单/APK，不需要替换旧文件或改写原清单。核验并恢复分发时移除该文件。已交给浏览器、系统安装器的文件无法靠此开关撤回。

页面/配置上线失败时保留停发状态，恢复上一份已核对的 Web、站点及公开配置，`nginx -t` 通过再 reload。APK 修复走新 ID 和更高 versionCode；不通过降版、替换同名 APK、卸载或清库回退。此次页面接入不要求数据库或 Room 迁移。

## 源码收尾阶段验证（历史证据）

| 验证 | 结果与范围 |
| --- | --- |
| Web 类型与构建 | `npm run test:pda-download` 内的 `vue-tsc --noEmit`、Vite 构建通过；保留既有大包体积提示 |
| 隔离 HTTPS 浏览器 | 33 项通过、0 失败/跳过；真实 Web Crypto、临时签名清单及合成 APK 字节，HTTP 边界隔离，不连接业务库 |
| 页面与二维码 | 两入口、匿名直达/刷新、保留设备分页、独立 jsQR 解码和复制；1440×900、320×533、320×400、28px（200%根字号）无横向溢出，按钮字体随字号变化；已检查截图 |
| 下载安全 | 首次安装独立于旧版覆盖范围；不匹配渠道/包名/证书/路径/兼容、debug、缺少首次安装标记、坏签名、截断/过大/篡改 APK、断网、新发布及下载中停发均阻断错误交付 |
| Python 工具 | 10 项单测通过；Python 编译检查通过；另以本机真实 staging APK 跑通工具读取、签名清单生成/验签、复制后摘要核对和错误证书拒绝 |
| 接口契约 | OpenAPI 3.1 的 95 条路径校验通过；本功能无新增业务 API、数据库或 Room 变更 |

本机 ignored 证据：`.local/pda-download-verification.json`保存本轮源码摘要与验证汇总；详细证据为`.local/pda-download-web-final.log`、`.local/pda-download-browser-results.json`、`.local/pda-download-python-tests.log`、`.local/pda-download-real-apk-check.json` 和 `.local/pda-download-{desktop,narrow,low-height,large-font}.png`。首轮 7 项和补充 32 项均通过；截图发现大字体按钮/标题固定字号，已修正后重新构建并重跑通过；随后补充清单传输中断场景，最终全部 33 项通过。专项浏览器测试源码另经严格TypeScript检查通过。

源码收尾阶段没有重跑无关业务浏览器、后端或Android回归；审计归档子项32/336/173项为独立历史证据。本次接续另行完成173项staging主机测试、模拟器及目标Nginx验证，见页首。M71浏览器下载与来源授权、完整系统首次安装、异地网络、production签名/分发和长时设备行为仍待验。浏览器检查 Android User-Agent 只作兼容提示，系统安装器仍会检查实际平台；旧浏览器缺少所需 Web API 时不绕过校验。

## 下一具体动作

测试页面与code16首次安装分发已启用，浏览器/系统安装链路及配对登录证据已保存。下一步在用户可用的专用空白真机/异地设备补验Android浏览器、扫码打开固定地址、系统安装和实际作业；现有旧版到16的保留资料覆盖升级独立核验后再调整后续发布范围。用户已选择真机后补，不重复要求清空M71。原R05同步/任务现场、长时/断电验证继续保留；整单事实90天清理仍等待R09异地备份恢复与引用核对，本入口完成不等于R05或生产验收完成。
