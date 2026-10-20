# O03处理响应与返还下载一致性修复（2026-10-20）

## 已核问题与范围

输入树D:/acrylic-pda-outbound、codex/development，HEAD ff5150930d0eedfd2bd7b9d41439e2ee428eec46。沿总目标P02/P08及既有中转设计补电脑消费者边界，不新增查询协议或业务功能，不将本轮记为O03整体、当前净值验证、D18或第8节通过。总目标active、未完成。

接续只读检查确认：现时原批次查询与R08固定版本净值已有服务端/消费者及历史证据，不能因新增历史回执命令再重写同职责能力。实际新缺口是tools/pda_rescue/relay.py从处理响应取resultId下载，但RelayJournal.result只核原包摘要、relayId和删除边界；它没有比较下载内容与处理响应。因而同包下不同结果ID/状态/业务数值或签名可能被写成完整返还文件，输出状态却仍取原处理响应。

采用最小修复：transfer在下载后将本次处理响应一并交给journal，严格解析并逐JSON类型比较全部内容；允许排版差异，保持原下载字节。与在电脑端新增完整信任链或现时净值服务相比，本修复仅收紧已存在链路的同次响应一致性，不扩权限或改变原机验签责任。服务端/Android/契约/迁移/生产开关均未改。

## 实现与边界

relay_journal.py复用严格JSON与same逐类型比较；冲突时使用既有durable写入result-conflict-<下载SHA256>.bin，采用排他新建、fsync及既有同字节幂等，不覆盖不同内容。随后明确失败，不发布完整.pda-receipt.json、不写lastResultState或清除pendingRefresh。原包、receiveKey和processKey保留；已进入刷新时保留已耐久保存的刷新键，重启重试不再换键。

原journal.result无expected的内部兼容调用保留；生产transfer固定传本次实际处理响应。与原查询/未处理接收/独立审阅的路径保持原行为。比较不等于可信签名验证，也不查询当前业务净值；原机必须继续使用原清单/预置可信根验签。localDeletionAllowed=false不变，不产生放行或删除资格。

证据根`.local/goal-20261020-resume/o03-result-binding01/`。input-manifest.json和edit-inputs.zip保存编辑前原字节/SHA及31条非看板继承WIP；原32条WIP仍按上轮完整清单保护，看板只授权更新。scope、命令/退出、输入输出哈希及Git回执见commands.md、final-manifest.json和closeout.md。

## 当次验证

```text
C:/Python314/python.exe -m unittest tools.pda_rescue.test_relay_result_binding tools.pda_rescue.test_relay tools.pda_rescue.test_independent_review -v
```

tests-red01：8测试方法、6失败（含参数子例）、exit1。实际复现错结果ID、错状态、同状态不同整数克、变更签名/未知字段及冲突后的误发布，原日志保留。重复JSON与数值0代替false已有旧检查拒绝，未伪称全为新缺陷。

tests-green02：20项通过。自审补冲突材料fsync失败、防覆盖及已刷新键重启重试后，tests-final03：23项、0失败、0错误、0跳过、exit0；其中11项一致性、5项既有中转耐久边界、7项独立只读审阅。合成Wire仅为测试边界，不冒充真实HTTP/签名/用户身份。临时文件仅在本任务子进程D盘证据根。

实际消费者离线回放使用已封存`.local/goal-20261013/o03-python-http-loss-01/`的receive-server-response.json、process-server-response.json、file-server-response.json及journal中的原ZIP/请求键/接收回执。只复制到本轮新耐久目录，通过当前transfer消费原处理响应与原下载字节；不启动服务或重跑原业务。replay_sealed.py及sealed-replay01.log/exit保存命令，exit0，原处理响应与文件仅排版不同，结果SUCCEEDED，6项原输入SHA保持，原receive/process键保持，输出逐字等于封存下载。

原处理响应SHA f086a4d0164bc9a2e33e85dcd5e5af17430387cc356ec8e84ace28cb1e0ee054；下载和本轮输出SHA ac3d73ea35f35b0e96ab75dccd85b5c00f85e1ba05176a13e7ae2f9b2c652363。原证据来自历史隔离真实HTTP成功链，本轮是SEALED_TRANSPORT_REPLAY_NO_NETWORK，不计新增HTTP/MySQL或独立签名验证。作者/自审为当前助手，工具回放不冒充独立人员审查。

## 限制与接续

本轮未运行全量Python/Android/服务端、APK构建或新HTTP/MySQL，未操作设备业务、原库/现网、正式签名、main、部署或Release。不恢复取消链，不启动旧run03。并行预检会话在独立工作树执行，主树文件编辑前核SHA；未代提交或修改预检树。

清理释放0字节：无隔离库或构建大产物，小型失败/回放证据保留；既有清理拒绝不换包装重试。当前结果/净值由原业务查询及固定版本导出负责，本修复不将历史签名结果称为当前有效。实际最终候选组合、原身份/实体PDA、外置介质、O02 Android主机失败等仍未验。

下一可执行动作应对照E08/O02/O03候选输入映射，选择尚缺的一项组合消费者；不要再次实现已存在的查询或重复一次性原业务。若需重型构建或数据库，先重新核预检会话资源和唯一写权；看板写前核原SHA。第8节全部闸门未满足前目标持续active。
