# R06 Room27 停止产品入口与持久补传队列验收

本批由当前助手接续既有未提交实现、自审、修复并隔离验证；没有独立人员复审，不代表上线。R01–R09其余可独立事项继续。

## 完成标准与实现

- TemporaryEngine 原单写者立即拒新输入，把排在停止之前已接收输入耐久保存后才执行同事务停止与签名；输入保存失败则不生成停止签名。DRAFT、未决输入和重入停止保留原件，停止后不能恢复扫码、修改计划或结单。
- Room27 持久队列只由产品明确选择补传时启用。原停止资料、签名、全部原请求和所有已存回执先复核，坏后序回执也阻止前序未知请求发送。已保存标签缺原件不能触发网络；页面读取只作本地复核，核验失败禁用停止/补传。
- 自审修复原入队未验回执、页面异常误设stopped导致启用上传、后台身份不匹配只退出未持久暂停。新增 LocalDraftQueueGuard：切人/切仓/切设备/代次/授权版本/登录代次及退出时持久关闭自动补传；原身份回来仍暂停，须明确重选。令牌刷新保持同登录不误停。
- 会话写入与Room跨存储：先独立提交队列撤权，再写加密会话；中途崩溃不会先保存新会话却回滚暂停。启动/后台对照原准备身份再次检查。发送循环在每件前检查暂停，已回响应原件保留，后续请求停止；最终进度不重新开启已暂停队列。
- Room26→27只新增队列表，不补造历史队列、键或回执。非空迁移保留停止签名、事件、原请求集合及VERIFIED原响应；恢复原键GET/仅专用404同键POST。HTTP请求不持Room事务。
- `ORIGINALS_STORED`仍只是收存，`businessFactsStatus=NOT_EVALUATED`、`localDeletionAllowed=false`；没有S/K、放行、确认或清理授权。

## 当次验证

证据根 `outputs/r06-product-20260925-01a0d8fa/`，对应源码未提交，不能以HEAD代替实际输入。

| 层次 | 结果 |
| --- | --- |
| 首轮定向 | 16XML、76项：75通过、1专用HTTP跳过；focused1.log/counts/原XML |
| 首次全套 | 111XML、443项：439通过、4专用HTTP跳过；APK/lint通过，full1.log/counts/原XML |
| 最终全套 | 111XML、444项：440通过、4专用HTTP跳过；full2.log/counts/原XML；新增暂停与响应竞争反例 |
| APK/lint | code20/Room27 debug APK通过；lint 0错误/0fatal/189 warning。相对Room26的186，3项来自本批新增Activity的文本拼接与manifest方向/API提示，未写成全部既有warning |
| attempt58 | 新ACL/datadir/schema/user/19330独立MySQL非空V34 seed→V37；服务端IT1/1及Android真实HTTP1/1，无skip；为最后两处自审补强前输入 |
| attempt59最终 | 新独立非空V34→V37，服务端IT1/1及Android真实HTTP1/1无skip；实际TemporaryEngine接收→拒新输入→排空→停止、Room27队列、换人后切回仍暂停、13原请求，OPEN/ITEM/VERIFY各丢1回执、Room重开恢复、不重POST、只签1次；服务端重启/恢复模式/原字节/坏文件/DB不可变约束同场通过 |

本批未出现测试失败。只读探索曾误猜若干不存在的文件名，随后按实际目录读取，未写入它们；日志保留。4专用HTTP跳过不算通过，attempt59只覆盖本批草稿HTTP，其他3条专用链是历史结果。服务端完整577项是前项历史，本批实际重编译与专项IT；没有声称重跑Web/Chrome/模拟器/真机。

Android→Java为独立语言消费/真实HTTP组合；身份准备和设备签名仍为合成隔离夹具，不冒称现场身份或真实Keystore。真实OS死亡/断电、Wi-Fi硬件、独立人员、长时/现场未验。

## 输入、证据与下一断点

- 主树 `D:/acrylic-pda-outbound`，`codex/development @ dccb73b10acbb63020beb3cf827b3b4580d3ed1c`；服务端树 `D:/acrylic-pda-worktrees/r06-e2a-migration`，`codex/r06-e2a-migration @ 568683c1a1f8257ad0596a654c6b3558c767ed95`。两树origin为既有GitHub私库；本批没有提交、推送、整合、main、发布或部署。
- 接管与收尾全部路径快照：`main/server-status-before/final.txt`；两树差异统计 `main/server-diff-before.txt`。不清理既有代码、out.xlsx、崩溃日志或旧证据。
- `android-final-source-manifest.json` 488文件，SHA256 `e2a9e0707dff5de13fd6b8b6940ca0e38c77f7a6de632d41290cc8a18608c1a1`；最终全套后与attempt59后复核无漂移。
- 服务端实际HTTP输入 `server-room27-http-source-manifest.json`；APK `product-room27-final.apk` SHA256 `15ee10c18eeeacd695592608d33ca03e032f624717ec380c2ca3be2b40c50590`。
- 58/59摘要、结果XML、完成标记、认证连接/关闭标识已复制为普通证据；完整库、私密输入与原HTTP在服务端 `.local/e2a-cross-20260925-58/59`。private不复制普通证据/Git。两库已认证shutdown，无19330/19434/19435/19436监听；共享3306 PID5448未动。
- 下一：R06非零候选独立消费者、其他未提交来源/归档历史可信事实、按已定政策推进关联/高轮次/处置续作；R07–R09与既有两树整合尚未完成。排除/拒绝权限等未定政策继续单列，生产七类决定、放行/关闭、TEMP生产归档及main边界不变。下一隔离run从60核目录分配。
