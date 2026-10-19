# D07 故障附件接续记录

主树 `D:/acrylic-pda-outbound`，分支 `codex/development`，输入 HEAD/实际远端为 `a4ae9a5aef2167ed74acc6c5f468143577c5cdf3`。总目标 active、未完成。版本事实只见[看板生成块](../ORCHESTRATION_STATUS.md)。本记录只覆盖已有 TEMP 案件的 FAULT_FENCE 附件，不外推普通交接命令、FORMAL、无可信归档或 R09 重建。

## 输入保护与实现范围

接续快照 `.local/d01-d18-20261008/d07-attachment-resume-13/` 保存22条dirty完整副本/hash/diff、实际Git/进程/监听/磁盘和跨树迁移扫描。19条继承路径及原 `d06-stop-close-20261008` 三项hash与先前记录一致，回执 `protection.json`；原start-12较早，不覆盖其后的断点文档。其他项目聊天notLoaded，本聊天唯一写者；既有3306/13316/ADB/19387不操作。

设计 `.local/d01-d18-20261008/d07-attachment-design.md`。附件上传要求三个独立默认关闭开关与本仓库独立Web主管；先授权再有界读取实际字节，事务内重验epoch/人员/案件。原件按案件/仓库/人员/原上传键幂等，异文拒绝。单文件10MiB、单次50MiB、单条5件独立校验。

原故障命令消费本人同案原件，将有序引用写入原签名回执；引用绑定同事务中已存在的原键预约，避免提前引用尚未写入的回执。原件/use不可变、复合FK绑定owner；围栏、禁设备、引用、回执和最后审计整体回滚。案件读面、原回执查询和原键重放重新核实际字节与引用；损坏为不可核明，不能让客户端换键。关闭附件/故障写入后，已有凭据及原件下载仍重新核验。

Web保存原上传键、摘要、大小和恢复epoch；刷新后重选未决原文件，再用相同键和相同字节上传。全部原件完成并核对持久记录后才准备故障命令。未决原命令不能修改附件；凭据只显示服务端重验结果，下载为opaque字节，不内联信任。

## 验证与失败保全

- 定向unit-01、02分别41/0/0/0、exit0/sourceUnchanged；仅对应各自输入，不代后续修改回归。Web build-01、02、03类型/构建exit0；原件在 `d07-attachment-web-build-*.log`，构建包体积提示保留。契约解析通过，尚不据此称组合通过。
- 1138为1/1/0/0：同键附件元数据一致，但测试误比较每次不同的HTTP envelope请求ID/时间。修为稳定data比较。dump/hash、认证停机、PID退出及空监听保全；`d07-fault-test-1138`、`d07-fault-1138/retention.json`。
- 1139在provision磁盘门前失败，未建库/未执行测试，原日志目录保留，不复用该编号。
- 1140为1/1/0/0：实际五个最大文件、上限/原键/权限/撤权/跨仓案/末SQL/损坏原件拒绝、接收最终记账与重启原件回读已走到；最后关闭上传PUT被通用异常误报500，未完成专项。修为405，不能将1140外推整体通过。dump/hash/认证停机/原PID退出、端口核实完毕。
- 1141为1/1/0/0，源码/Web无漂移：浏览器区域与文件控件同无障碍标签，定位歧义，未到上传页面验收。已消除歧义，并把Playwright输出放 `artifacts/`，避免清空运行器日志。失败截图/trace/XML、dump及停机回执保留。
- 1142实际Web/HTTP/MySQL/Android附件专项1/0/0/0、exit0/sourceUnchanged且Web无漂移；实际浏览器1项通过，接收端7份Android阶段XML各1/0/0/0。原件 `.local/e2a-cross-20260925-1142/`、运行器 `d07-attachment-ui-1142`、`d07-fault-test-1142`；dump/认证停机/原PID退出和空监听通过。此输入早于后续恢复及缺失原件修复，不代最终回归。
- 恢复01为15项过/3项败：容量错误锁死文件控件；跨页断言定位歧义；布局夹具把裁剪后的回执前置context当完整案件。修两处夹具后02为16项过/2项败，实际确认容量误选锁死及附件状态变化后无法重开。原日志/trace/截图、类型构建和sourceUnchanged保全。
- 修容量错误可重选；仅不可读原请求阻断重新打开，其他核对错误允许返回；关闭表单销毁附件消费者，再打开重读持久原件。恢复03为18/0/0/0、恢复04为19/0/0/0、exit0/sourceUnchanged及类型/构建exit0；后者补全表单五份原件名称/摘要和固定页脚的滚动可达性。目录 `d07-attachment-recovery-01`～`04`，合成HTTP边界不替代真实接口。
- 作者自审补已记录命令的缺失原件：所有原件或绑定不可重验时返回 `HANDOVER_RECEIPT_INTEGRITY_FAILURE`，不能作为普通404让客户端换键。下载先核故障原回执及全部原件，再取目标附件。1143新一次性真实专项1/0/0/0、exit0/sourceUnchanged且Web无漂移；浏览器1项通过，原来源及接收端共8份Android XML各1/0/0/0。缺失/损坏原件的真实下载、案件读面及原键重放均拒绝，查询不改全部事实；测试恢复原字节/全部列和原触发器后事实逐值一致。接收最终记账和重启消费通过，关闭写门仍真实逐字节下载，PUT405拒绝。原件 `.local/e2a-cross-20260925-1143/`，运行器 `d07-attachment-ui-1143` / `d07-fault-test-1143`；dump/认证停机/原PID退出/19330及19434空监听通过。
- 服务端普通回归01/02各3405/0/0/1、exit0/sourceUnchanged，02覆盖缺失原件修复。唯一skip为SourceEvidenceIntakeFilesTest.symbolicLinkTargetCannotBeAccepted，Windows无符号链接权限而未执行；明细 `consumers-02/server-skips.json`，普通test不含IT。OpenAPI实际解析 `d07-attachment-consumers-01/contract-02.log` exit0；前一命令漏web路径，未执行解析，错误日志保留。
- 布局补全后人工发现390宽度按钮只露出一部分；05加强完整视口断言后1/1/0/0，可见比例约0.556。局部故障弹窗使用主题间距约束高度/上下边距，动作区不收缩；06全边界19/0/0/0、类型/构建exit0/sourceUnchanged，五份原件逐项滚动及完整动作区复验，截图人工复核通过。未改其他弹窗样式。1144新的实际页面/HTTP/MySQL/Android组合执行中；未取最终结果前不计最终整合通过。

事实快照覆盖全部耐久列；附件BLOB以实际SHA及长度保存在快照内以控制容量测试内存，原字节另行保全并HTTP逐值回读。独立Node RSA/canonical/原预约/有序owner及附件实际原字节工具 `tools/verify_handover_attachments.cjs`、故障原前缀消费者、只读SQLite消费者均实际消费1142和1143且exit0，分别见 `d07-attachment-consumers-01` / `02`。五份最大原件与关闭写门后的重启下载逐长度/sha重验；Web签名回执绑定/原存储消费者各5/0/0/0。独立工具不等于独立人员。

1143产品输入核对时逐hash一致，回执 `consumers-02/product-input-relation.json` / `server-product-input.json`；此后新增完整视口断言并修弹窗布局，实际页面由1144重新验证。作者实际查看真实form/receipt及06补全滚动截图，按390/720/1440布局分别核名称、摘要、五个下载链接及动作区，无横向溢出；04不作为按钮完整可见的通过证据。表单和凭据分别计证，合成回放不冒充新增实际HTTP下载。Android产品未改；当次组合证明新增引用兼容，不冒称新APK/lint，相关产品输入及已登记APK只引用故障链记录。

## 磁盘与接续

`d07-attachment-disk-14`保存56条dirty完整快照、进程/监听/磁盘。删除临时DLL的命令被自动审批以“blocked by policy”拒绝，没有执行删除；未重试或改包装绕过。改用可逆NTFS压缩已停机模拟器RAM文件和任务TEMP DLL，原路径/字节全部保留，前后hash一致；即时空间读数延迟，压缩后稳定盘余量见 `compression-settled.json`，不能用即时负数声称释放。数据库、失败证据、原资料和APK均未清理。

后续 `d07-attachment-resume-15` 保存77条完整dirty副本/hash/diff和继承/原STOP逐值保护，`checkpoint-16` 保存81条实际完整路径；不能用较早快照覆盖其后修改。失败1138/1140/1141及成功1142的dump独立重算匹配原记录，回执 `consumers-01/dump-independent.json`；1143另存 `consumers-02/result.json`。其他项目聊天仍notLoaded，唯一写者；不操作既有监听。保存位置继续D盘任务范围，未清理业务原件、失败库、日志或APK。

作者自审核权限/锁序/owner与原键、实际字节、不可变引用/原预约、签名重放、关闭门读取及异常恢复；三处产品恢复缺口及一处布局裁剪均修复并取得对应反例。继承19条和另外两份先前历史断点改动不混入附件范围。随后接FORMAL故障、无可信归档及R09跨截点；D06/D07整体、D08–D18与第8节仍未通过，D04/D05政策阻断保持拒绝。

## 最终附件输入验收

1144新隔离实际页面组合1/0/0/0、exit0/sourceUnchanged，Web前后hash一致，浏览器1项通过。原来源及接收端8份Android XML各1/0/0/0；五份最大原件、缺失/损坏原件拒绝与精确恢复、原键丢响应重试、接收最终记账及重启下载均通过。实际390/720表单截图人工核对，底部两按钮完整可见；390凭据能展示原键、主管与故障设备、保管位置、摘要及下载入口，补全滚动可达性由恢复06证明。实际文件在 `.local/e2a-cross-20260925-1144/temporary-handover-stop/fault-fence/browser/`，本机位置不代表异地保管。

独立Node附件/RSA/canonical/原预约/有序owner、故障前缀及只读Room消费者均exit0，Web原回执/存储消费者5/0/0/0；原日志和工具返回见 `d07-attachment-consumers-03/`。Android XML逐项重算、dump独立hash匹配、认证停机及原PID退出，19330/19434/5173空监听、无重型消费者，回执 `result.json`；dump SHA256 `3DF2B6870F29658D6DC065890DF70D634FAD30DF4E0957385405CEAB7176D5C2`。仅独立工具及合成身份/开发钥，未获得原身份、原设备或独立人员证明。

同目录 `input-relations.json`核1144组合、最终Web恢复06与服务端普通回归02全部已记录输入逐hash一致。因此服务端3405/0/0/1与最终Web19/0/0/0、类型/构建结果覆盖提交产品输入；不机械重跑无变化服务端。符号链接skip仍未执行，普通test不包含IT，Android产品未改，APK/lint仅引用此前已登记输入，不冒称本批重新构建。

`d07-attachment-resume-17`保存最终83条dirty完整副本/hash/diff、Git、全部进程/监听/磁盘及聊天写者状态，19条继承和原STOP三项hash逐值一致。附件62条明确范围及暂存完整diff、输入/验证核对、消息和Git回执登记于 `d07-attachment-close-18/`。后续断点文档不得由较早快照覆盖，提交结果按实际回执登记。

生产默认关闭、签名能力关闭、七类生产决定及 `localDeletionAllowed=false` 保持；不接原库/现网、不合main、不发布部署、不正式签名。原身份/设备/物理故障及C01–C08现场条件继续单列。

## 明确路径保存回执

62条附件范围已提交并推送到codex/development，HEAD/实际远端c0c5a3b71dcb3f992d38fb08fd4deda6c7aa93ac，标题feat(交接): 保存故障附件原件与恢复验收。close-18保存全部路径、完整暂存diff、自审、消息、提交/推送回执及提交后dirty。19条继承与两份先前历史断点保持，未混入；本地忽略的原件/数据库/私有物料不代表已异地备份。仅开发范围保存，不合main、不发布部署或正式签名。后续FORMAL/R09及D08–D18继续，总目标active。
