# E07/D06-D07 Chrome 消费者组合（run739）

本报告只覆盖 D06/D07 的 Chrome 产品消费者入口：后台坏机撤回、丢响应后的原请求重试、页面刷新恢复、重新发布及浏览器存储失败反例。它不覆盖 Android、外部 Python、原 PDA/现场身份、原模拟器恢复或 D18 最终整合，不把本批写成 D06/D07 L2。

## 输入与隔离边界

- 树：`D:/acrylic-pda-outbound`，分支 `codex/development`，输入 HEAD `93710ec5cff979a567654084c80abe089e9a73b7`；运行前后 `source-before.json`/`source-after.json` 相等，`result.json` 的 `sourceUnchanged=true`。
- 新隔离编号：run739；MySQL `127.0.0.1:19349`，Spring HTTP `127.0.0.1:19439`，Vite/Chrome `127.0.0.1:5173`。数据库身份、端口、datadir 和 server UUID 见 `database-identity.txt`；认证停机 `exit=0` 且停机后目标端口监听数为 0。
- 非空迁移：`FaultRestartConsumerFixtureIT` XML 为 `tests=1 skipped=0 failures=0 errors=0`，日志实际执行并校验全部迁移；输入 `.local/goal-20261011/fault-restart-consumer-739/private/input.json` SHA-256 `7BA5C72944F77F04BCC784B75A098C36435496D9431E7ECD1DD309A56F5BBDDB`。
- 当前源码构建的独立服务包复制为 `candidate.jar`，SHA-256 `786CB913713E76E71A8BEED4D004A4AE3BCB57BFE5D6D8109762C36A9DEEEB8E`；依赖、dirty、环境、进程/监听快照和命令均在同一 evidence 目录。

## 实际验证

- Web 类型检查和 Vite 构建均退出 0；测试使用 Chrome channel、真实登录、真实 Spring HTTP/MySQL，不使用 MockWebServer。
- `fault-restart.spec.ts` 两项均通过（`expected=2 skipped=0 unexpected=0`）。第一项实际记录两次页面刷新、一次撤回发布链和一次重新发布链；`web-result.json` 保存两条原请求的完整正文、幂等键和恢复代次。撤回正文与重发布正文分别保持原键重试一致。
- 第一项覆盖原机无 STOP 的后台撤回、撤回请求服务端已处理但浏览器丢响应、刷新后原请求重试、撤回完成后草稿、发布请求丢响应、刷新后原发布重试及发布历史两条记录。第二项把 `Storage.prototype.setItem` 置为配额失败，确认没有发送撤回 POST 且原订单仍保持已领取。
- Chrome trace、JSON 报告、服务日志、隔离库 dump、dump SHA、身份和认证停机回执保留在 `.local/goal-20261011/fault-restart-consumer-739/`；run739 的服务包和数据库均已停止，未触碰外部 3306/33060、33318、原库或实体设备。

## 失败归因与边界

- run736 使用旧服务包，第一项在重试发布对话框处失败；该结果不计当前候选。run737 的 Gradle 运行器把 `--tests` 传给 `bootJar`，在业务测试前失败，隔离库已认证停机。
- run738 已使用当前源码包，存储失败反例通过；正向撤回重试被 Playwright route handler 的网络断言路径报告为 `Failed to fetch`。同一隔离 dump 已证明撤回事务成功并回到 DRAFT，run739 在相同产品源码上两项全通过，故 run738 不作为产品失败计数，也不与 run739 混合组成候选。
- run739 仍使用合成 fixture 身份、隔离库、开发签名和测试 Chrome；它只证明 Web/真实 HTTP/MySQL 消费者切片。D06/D07 所需 Android、原模拟器、外部 Python、现场原身份、占用/末 SQL/并发全矩阵及最终整合仍未满足，D06/D07 L2、D17、E06 L2、D18 L3 和第8节闸门保持未完成。

