# D15 旧程序定时 worker 兼容：有界 L1 开发备份

## 范围与输入

固定主树、单助手单写者；本批基线为 codex/development 的 0aa205676fb3a4e83d438a3a69f26e165eef2319，实际远端相同。本聊天 01a13a57-8082-7dd2-a08f-987483dcda4b 独占源码、证据和看板写权；清理聊天已明确释放且实际 idle。全量接续输入和旧看板、差异、未跟踪原件、工作树、远端、进程、监听、磁盘见 .local/goal-20261017-resume/input34.json 及同号附件：5484 项，SHA db2021c273386d63736929933a719fe3cd88f338c7695f9dc704df6a4ac2080f。该快照只比 input33 改变下述 checks 的离线断言，不改继承业务源码。

本单元验证：从原 667 非空副本恢复，在新程序真实 HTTP 创建 ORIGINAL_CONFIRMED/XLSX 任务，由保留旧 JAR 的实际定时 worker 执行，再分别由旧/新程序读取与下载，新程序重放原键及拒绝异文。范围限该已确认格式；不外推旧写入、新多任务格式、D15 整体、E07 L2、D18 L3 或总目标完成。版本事实见看板事实块。

## 改动路径 → 影响层 → 必跑 → 复用依据

| 路径 | 影响层与必跑 | 复用/输入绑定 |
| --- | --- | --- |
| docs/parallel/R09/tests/d15_old_worker_lab.py、d15_old_worker_process.py | 合成隔离恢复、直接 JDK、旧 scheduled worker；真实 MySQL/HTTP、进程排空、认证停机 | 原 667、旧/新 JAR、renderer 的路径与 SHA 由 677/input.json 逐项绑定 |
| 同目录 d15_old_worker_http.py、run_d15_old_worker.py | 业务入口、原键/异文与跨仓拒绝；实际 HTTP 创建→旧 worker→旧/新下载 | 677/private 各段原 HTTP 元数据/原响应、四段命令/日志/监听/PID |
| 同目录 d15_old_worker_checks.py、verify_d15_old_worker.py | Python 实际消费 XLSX、全表/DDL/文件守恒及只读证据复核 | 原 677 业务输入 48 项中 47 项不变；checks 只改离线断言，verifier 后增，不能声称原 driver exit0 |

新 JAR server/build/libs/acrylic-outbound-server-0.1.0.jar SHA e130aa2b7844daf6380045e3f3e809672b9c15f5ff31c4a24adf4cdf6bdc92ba；旧 JAR .local/goal-20261013/e06-conflict-audit-1535/server.jar SHA adbd314f0fd619997dea0f16e0de36a963c3650173e991cbcf259ad1ae7184e1。安装的 renderer 29 个 JAR 分别绑定，不以源码代替实际二进制。七表消费者 .local/goal-20261016/d15-restored-worker01/xlsx_readback01.py SHA de4980df7a441e23f4d43a8db56befb745da1bef403ad591042fddb335bf2b2e。

最终六文件 SHA：lab=f71902ade28f4e4b4f65b37d8706d408dbccabd087511c5ba7c1204852a44cd4；process=c429e9247882c0b44d2346e735b0fa14df600460c49239a6d6a40608acf8ac88；http=70ac30c2a3869c926de62ea14f9f78bfa53f928eee99814ed20e257ce7f77c9e；runner=8d56b5d8715ee6632dcdb213bb69689228e6050e20bf0391a43f0cde9182c53d；checks=db4bc523604dc5988c0c4f643a6bd0a493d6aa467cac07fec05d634d65722876；verifier=550620d2eb1ce6b9228f99db2ca4de56fe311b740d30c89e660a8c50223c084e。checks 原业务执行绑定 SHA b8cfbebb5462aadd69a570566295cb6d18c4a9d64bf1862805844b6f6ae46877；新增精确断言只允许 job 的 status/payload/attempt_count/updated_at 改变且字段集合保持。六文件均 ≤200 行、函数 ≤30 行，AST 核对通过。

## 实际正向与守恒

- 新分配 .local/e2a-cross-20260925-677、DB19387、HTTP19461（另预留19462/19463），未复用失败库。真实 HTTP 20 次：new-queued 8、old-read 5、new-read 7；每个状态、原响应长度/SHA 和业务 data 实际消费。跨仓为产品定义的 404 WAREHOUSE_NOT_FOUND；原键同结果，异文为 409 IDEMPOTENCY_CONFLICT。
- job 01a14667-461e-70a6-835d-98af496c6b5d 从 PENDING/attempts0 到 SUCCEEDED/attempts1，last_error null、lease 清空；旧 worker PID26996 的 BackgroundWorkerProcess 实际记录 SUCCEEDED，非伪造任务状态或手动 renderer。
- snapshot SHA 6f0719159d0f2573a8e02ac5af5207fb7ad143713e87315fadec8ca9f41fa4b3；XLSX 13697 bytes、SHA a25cfc2fae170fc990b95560a018c210f5fe62271eecc6c7219fdef845b023d8。两次下载均实际 Python 消费七 sheet、原两行、601500 克、前导零 003，无公式/外链；旧/新 API 读取同任务结果，下载同字节。
- 原 180 表 322 行；仅新增合成查询身份五行至327；queued333→worked334→old-read339→new-read344。各业务阶段176表逐值不变；仅 background_job、operation_event、spring_session、spring_session_attributes 有精确预期变化，原审计/会话保留。DDL/trigger/迁移保持，仅归一化 operation_event 下一自增值；二进制 UUID 按数据类型保留 HEX，每个新 session 精确两个安全属性。
- 原10个私有/存储文件不变，只新增 snapshot/report.xlsx/renderer.log。原667的604文件、261439064 bytes多次核同，manifest SHA 180c908a8966ea3bf0a64a4355ad49e521b6a78b2ab4e145d363d5417f8042c3。

## 失败原件与离线补核

676 的 .local/goal-20261017-resume/d15-old-worker-run01.log exit1：运行器错期望403，产品 Access.java 定义404。已经登录写入，按完整保全处理，不作快速复用。原 .local/e2a-cross-20260925-676、DB19386/HTTP19458 停机；d15-worker-failure676 保存415文件/223604182 bytes，manifest SHA 1c82743a8026e9151433d4ef173bc1ff9a14117f93d556acde45c6c564f54813，dump SHA 1f4c760c68973ec8f15e4627bae2a6ce0b9e310bc4cd5365acbe36a65aeacf63。该根因一次保留。

677 的 d15-old-worker-run02.log **exit1 保留**：业务已经全部执行，原审计器对 session 属性数和二进制 UUID 的断言错误。修复为数据类型感知 HEX 及精确两个属性后，仅消费封存原件；offline01.log 因 Windows renderer.log 非 UTF8 exit1，改原字节检查成功后缀且无 Exception/ERROR，原日志未修改。offline02.log exit0，JSON SHA c36bfe50173be145c25bc11c2f1731fd92296a60e662bad7da77dbf995868a66；再加 job 精确变化集合后 offline03.log exit0，JSON SHA d40388d24be15828631b5394cb3024851cc14ad5be2f9d0b88251f06859dcd16。

离线命令：python docs/parallel/R09/tests/verify_d15_old_worker.py .local/goal-20261017-resume/d15-old-worker-offline03.json。该复核未重启数据库、未重复 HTTP，676/677 与原667输入前后核同；不把消费者修复改写成原驱动成功，不删除旧错误或重置根因次数。

## 封存、环境与后续

677 封存 d15-worker-run677-preserved/manifest01.json 与 manifest02.json 完全相同：488文件、227026255 bytes，SHA 5bf81029004a0434ad51ac77c5a00f1f143993366911c83b4b63ec9a3e768deb；完整 dump SHA c72d57a52262bb3f851565cb22fa190cafe17b99401de8d4d3daeb115eb0abe4。mysql PID1576认证停机 exit0、19387零监听；四个直接 JDK 及 renderer 排空。封存根不写新输出。合成密码仅保存在 private，不打印、不入 Git。

接续实核无本批重型任务；共享 mysqld4632/5440身份未知，只观察；原 TEMP AVD emulator12912/qemu12108、5564/5565及ADB保留。input34同号磁盘原件记录C/D余量，重型前再测；新增资料均在D盘，无清理。唯一写权、新输入及实际进程/监听是登记依据，不依旧owner/released字段断言。

本报告只补该格式旧 worker 的有界 L1 开发备份，后续核D13–D16剩余验收、E06/E07/E08受影响组合及E09最终候选。旧程序写入、新多任务格式兼容、实际Linux项目启停/恢复组合和D18全链仍未由本单元证明；原身份/设备/现场事实与异地介质取回限制继续单列。生产七类能力、TEMP生产归档及 localDeletionAllowed=false 保持边界，不合main、不发布部署，目标保持 active。
