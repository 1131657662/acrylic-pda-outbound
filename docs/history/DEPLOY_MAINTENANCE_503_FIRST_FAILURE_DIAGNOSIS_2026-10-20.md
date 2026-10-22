# window02 首次维护围栏 503 断言失败诊断

日期：2026-10-20。下文正文时间均为北京时间（UTC+8），原始回执保留 UTC。

## 结论与执行边界

首次请求实际返回 **502**，不是无状态码，也不是后来那次回环 502 被误用。高置信原因是：脚本在 Nginx 重载命令返回后立即探测，尚未确认新配置已实际接管；请求由重载前已有的 worker 处理，仍代理到已经停机的 test API，连接拒绝而返回 502。后续同一公网入口返回 503，证明围栏稍后已生效。

另有确定的脚本证据缺陷：状态码已读取到内存变量 `public`，但记录和输出都放在断言之后；断言失败便丢失首次状态码、请求时间及响应细节。断言本身正确地阻止了后续业务操作，不应删除、放宽或把 502 当作 503。

本轮只读取既有本地证据、授权 test 的历史日志和 Nginx 单元定义，新增本地诊断证据及本文；没有重新维护、reload、启停服务、业务 HTTP 请求、数据库访问、演练或部署，没有修改源码、原维护脚本或服务器配置。生产、原库、main、Release、正式签名及删除开关边界保持，`localDeletionAllowed=false`。

## 首次调用与原始日志

本地原件：`.local/deploy-real-client-20261020/window02/maintenance02.py` 及其 `.receipt.json`、`.stdout`、`.stderr`。

- 调用开始：UTC `2026-10-20T11:36:32.135838+00:00`，即 19:36:32.135838；这是整个 SSH 调用开始，不是 curl 或断言的精确时间。
- 调用结果：exit 1；stdout 0 字节；stderr 127 字节，仅 `AssertionError: External test clients must be fenced`。
- 脚本 SHA-256：`55325561a815eb1787bf6f244eb68ff672c8f1ba187d9ad0778e2380827b3e9b`。
- Nginx access/error 日志将首次 HTTP 响应定位到 19:36:32，日志精度为秒；没有断言执行时刻的毫秒级回执，不能补造。

| 时间（北京时间） | 原证据 | 事实 |
| --- | --- | --- |
| 19:35:50.939454–19:35:51.014210 | test API systemd journal | API 正常停止；这是备份脚本的停写步骤 |
| 19:36:32.805806 | 保存的 `nginx-maintenance-check.stderr` 文件时间 | `syntax is ok` / `test is successful`；配置语法检查成功 |
| 19:36:32.822433 | Nginx systemd journal | 开始 reload |
| 19:36:32.844721 | Nginx systemd journal | `signal process started` |
| 19:36:32.846335 | Nginx systemd journal | reload 命令完成；不能据此认定新 worker 已接管请求 |
| 19:36:32 | access.log 第 4272 行、error.log 第 113 行 | 公网 GET `/api/v1/auth/csrf` 返回 502；worker `2860371` 连接 test 上游遭 `Connection refused` |
| 19:37:07 | access.log 第 4273 行 | 后续公网 GET 同一路径返回 503 |
| 19:37:07 | access.log 第 4274 行、error.log 第 114 行 | 后续回环 GET 返回 502；worker `2887332` 代理到仍已停机的 API |

首次相关的脱敏日志摘录：

```text
[20/Oct/2026:19:36:32 +0800] GET /api/v1/auth/csrf HTTP/2.0 -> 502; curl/8.5.0
19:36:32 [error] 2860371#2860371: connect() failed (111: Connection refused)
server: [authorized test]; upstream: http://127.0.0.1:18085/api/v1/auth/csrf
[20/Oct/2026:19:37:07 +0800] GET /api/v1/auth/csrf HTTP/2.0 -> 503; curl/8.5.0
```

筛选覆盖 access/error 日志及保留的轮转文件。11:36:20–11:37:20 UTC 窗口内符合路径和 curl UA 的请求只有三条，分别对应首次公网探测、后续公网探测、后续回环探测。error 日志同时记录 test Host、上游及来源，支持唯一对应，未用同机其他环境日志充当 test 证据。原始来源 IP 仅保存在本机私有证据。

## 原因分类

### 时机：高置信根因

实际单元的 `ExecReload` 是 `/usr/sbin/nginx -g 'daemon on; master_process on;' -s reload`，没有额外的请求就绪握手。原脚本在 `systemctl reload nginx` 返回后立刻运行 curl。首次请求实际进入上游，而成功应用的维护配置对该非回环来源应直接返回 503，不应访问上游。

首次处理 worker `2860371` 在 reload 之前 18:41:03 的 test 错误日志中已存在；后续回环请求由 `2887332` 处理。结合首次上游访问和后来公网 503，支持旧 worker/旧配置与新配置切换间的就绪竞态。

限制：Nginx error 日志配置未启用 notice，未保留 master 的完整新旧 worker 启停时间线；access/error 只精确到秒。因此无法证明精确生效毫秒数或耗时。两次探测相隔约 35 秒，**不代表 reload 花了 35 秒，也不能据此固定 sleep 35 秒**。

### 脚本：确定存在记录缺陷，非“没有读取状态码”

`subprocess.run(... capture_output=True, text=True, check=True).stdout` 已赋给 `public`；失败发生在紧接着的 `assert public == '503'`。curl 未使用 `--fail`，HTTP 502 不会令其进程退出失败，因而可正常进入断言。

`maintenance.json` 写入和 `print` 都位于断言之后；服务器该文件确实不存在。本地 stdout 也为空。首次响应头/体、curl stderr、请求起止时间和状态码值未持久化；状态码 502 是本轮从原始服务器日志恢复，不能称原脚本已保存。

### 配置：未发现导致此次失败的持久配置错误

保存的 `nginx -t` 明确成功，reload 无失败回执，临时配置中的 `if ($remote_addr != 127.0.0.1) { return 503; }` 后续实际返回 503。临时配置摘要与失败后读取一致，原配置及恢复记录保留。没有依据把本次失败归为配置语法、证书、身份认证、数据库权限或模拟器问题。

此结论只覆盖首次探测路径和本次失败原因，不等于完整维护围栏、多路径或已有连接验收通过。后续回环 502 是 API 已停机的独立事实，不是首次公网探测证据的替代品。

## 最小修复建议（本轮未实施）

1. 在断言前落盘每次探测回执：请求起止时间、curl exit code、HTTP code、响应头/体及 stderr、探测路径/来源、配置摘要。敏感响应只能进私有证据，公开报告脱敏。即使失败也必须保留回执。
2. 将“reload 返回后一次立即断言”改为有总时限的围栏就绪检查；每次使用新连接、保留每次原始响应，达到稳定连续 503 才进入下一步。总时限和稳定次数须写清，不无限重试，不把失败覆盖成成功。
3. `nginx -t` 或 reload 失败立即停止；503 未稳定或超时继续关闭并执行既定恢复。502 不计围栏通过，且本轮首次失败历史保持。
4. API 尚未恢复时回环 502 不用于认定围栏错误；真实业务开始前须另外验证获准维护通路的 API/worker 就绪。此项不能靠围栏 503 替代。

增加短暂等待有针对性，但单一固定 sleep 缺少可靠上界。建议先修“断言前记录 + 有界就绪检查”，再另行决定是否重开维护；本报告不执行重试，也不作为部署放行。

## 证据、保存和交接

新增证据根：`.local/deploy-real-client-20261020/window02/diagnosis503/`。每次 SSH 读取均保留 stdout/stderr、源命令摘要、UTC 起止或完成时间和退出码；全部 exit 0，业务请求数为 0。

| 文件 | SHA-256 |
| --- | --- |
| historical-nginx-metadata.json | `52179e6cd664febc0a0fc25761cc2e583fd2e61917daa1a78acc2221b41eecc5` |
| historical-http-selection.json | `ea03697bf13a0848d895ec51855638e75466a4581c909ecafe6011b82926048b` |
| historical-test-stop.json | `584fa8a25e50e17c9e345e4b061264ce8152cde86004f192329c043d4c604d0e` |
| worker-correlation.json | `9559d9a44b1aa50749a9ec12e3a56ad4deacd21f6427eecf10033b5a15725220` |

关联历史：[真实客户端路径与回退](DEPLOY_REAL_CLIENT_PATH_ASSESSMENT_2026-10-20.md)。本次未生成可信演练后数据副本，真实升级/还原/API-worker 闸门仍未验；候选编译失败及其他预检未验沿原报告保留。

输入本地主树 HEAD：`4a7fb48df3a05082e195a98706772b545385a685`，分支 `codex/development`；共享看板与历史总索引写窗仍归另一开发聊天，本轮不抢写、不提交或推送其改动。专项目录内 `evidence-index.json` 登记本轮证据，`shared-doc-update-pending.md` 保存待原写者归并的看板/历史索引增量。本文及私有证据落盘不计 D/O/U、维护、真实数据链或部署通过。
