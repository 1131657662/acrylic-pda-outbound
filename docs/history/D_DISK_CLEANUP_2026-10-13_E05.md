# E05收口磁盘清理记录

用户明确要求本子项收口、清理磁盘并交新会话。执行树D:/acrylic-pda-outbound，分支codex/development；输入HEAD为8de486976e46db0007c208e689189acc71874b14，完整113个改动路径及原字节在.local/goal-20261013/e05-delivery-01/pre-close。版本事实见看板生成块。

## 范围与重建条件

仅删除.local/goal-20261013内21份已停用runtime目录的server.jar。逐份SHA256证明与9组各自保留的同字节JAR相同；重建只需从登记保留路径复制，无须重新编译，也未删除任何唯一版本。精确白名单及原路径→保留路径见交付根cleanup-plan.json；jar-inventory.json保存删除前全部运行JAR字节数和哈希。

执行前保存全部脏输入、Git二进制差异、实际远端、依赖锁/构建输入及运行原命令/退出结果/日志的清单哈希。原运行目录中的service-command、原日志和已有结果留在原位置。脚本cleanup-identical-jars.ps1逐项核绝对路径在项目既定目录、所有祖先无reparse point、无命令行消费者、文件独占可读且保留源哈希相同，使用单一PowerShell及明确LiteralPath删除；删除后再核保留源哈希。cleanup-results.json逐项记录21次成功及时间。

## 实际空间

| 指标 | 字节 |
| --- | ---: |
| 删除21份重复JAR逻辑大小 | 5889780001 |
| D盘清理前可用 | 24720642048 |
| D盘清理后可用 | 30609887232 |
| D盘实际可用增量 | 5889245184 |
| C盘清理前可用 | 56271183872 |
| C盘清理后可用 | 56259522560 |

清理D盘实际增加约5.49GiB，清理后D约28.51GiB、C约52.40GiB；逻辑大小与磁盘净变化分开记录，系统及保留预览仍可能写入。最终交接余量另见断点final/disks.json。C盘未执行删除，不将其系统变化归因于本次清理。

## 保留与检查

预览1494/19330、API19435、worker及Vite5174保持；网页只读GET返回200。report-rendering/build/install和browsers是活动消费者依赖，不清理。共享MySQL/ADB、两个原AVD身份与Keystore不动。

数据库、datadir、dump、binlog、原XML/日志/签名HTTP、原资料、唯一证据及APK全部保留；失败1493/1495等原件保持。无业务数据删除、无生产操作，无共享工具/缓存清理。未执行tools/cleanup-rebuildable.ps1。此操作不改变localDeletionAllowed=false和D16完成标准。
