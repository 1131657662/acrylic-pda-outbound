# E07/D14 独立检查点 Python 消费补验（2026-10-18）

本记录补充 run722 独立检查点候选的只读 Python 消费。消费者不打开数据库、不建立网络连接，只读取已经认证停机的候选目录，并以输入前后 manifest 证明消费没有改变输入。该补验不能替代 Android、Chrome、原模拟器、现场身份或外置介质，仍不计 E07/D14 L2、D18 L3 或总目标完成。

## 输入与命令

- 候选输入：`.local/e2a-cross-20260925-722/`，其 `connected-identity.tsv` 与 `shutdown-identity.tsv` 一致，`test-completed.marker` 存在；数据库和 HTTP 监听已认证停机。
- 输出：`.local/goal-20261018-resume/e07-independent-consumer-run01/`。
- 命令：`python tools/e07-independent-checkpoint-consumer.py --source .local/e2a-cross-20260925-722 --output .local/goal-20261018-resume/e07-independent-consumer-run01`。
- 消费脚本 SHA-256：`8C223B05213D9D569E15D281B6CEE228AFDE853693E0D0EB2B6611DBD7B9228E`。

## 结果

- 退出码 `0`，`consumer-result.json` 的 `passed=true`、`failures=[]`。
- 输入前后均为 854 个文件；消费者报告 `inputUnchanged=true`，结果文件 SHA-256 为 `9321BE78F6E418A7FF1DDC948129CD21BA89E38F69272BC20B5DBCC99AF0D3F3`。
- 最终快照与检查点重建均为非空 179 张表、622 行，迁移历史记录由候选事实校验；HTTP 读取记录均为 `200/OK`，关联保持 `releaseAllowed=false`、`pendingReconciliation=true`，连接与停机身份字节一致。
- 输出辅助文件：`input-root.txt` SHA-256 `20D3B45850AF53057FC63CE602E55DCA73DA94E32DAFA9438709F542AB9B6BAA`；`exit-code.txt` SHA-256 `5FECEB66FFC86F38D952786C6D696C79C2DBC239DD4E91B46729D73A27FB57E9`。

## 范围结论

该结果只补齐 run722 的独立只读 Python 消费证据。候选仍缺 Android/Chrome/原模拟器/现场身份等组合条件；原候选和本次输出均保留，不把 Python 消费通过扩写为 E07/D14 或第 8 节闸门通过。

