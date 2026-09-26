# 关闭阶段数据库投影实施标准

接续2026-09-26事务租约验收；本页是下一子项的完成标准，不是通过报告。V39在两树核对后由当前单写者分配；不改既有V1–V38，不回填原事实代次。

1. 增量表记录原安装登记、UUID到既有数字代次的不可变映射和每版控制投影。迁移后为空；没有自动初始化安装身份、复制当前数字1为所有历史UUID或把既有来源改成新代次。
2. 首次显式登记只接受已验证的QUIESCING revision1，在库外租约内锁server_state，并核对调用者固定的原数字代次；登记当前真实MySQL server_uuid/库名。旧数值只作为该安装的当前基线；更早数字代次继续未知，不能反推历史UUID。资料复制到别的MySQL/库名必须拒绝自动接管。
3. 外部原件已提交而DB尚未投影时，原实例不能继续；原命令重试只补同一记录。数据库以installation+revision+原hash追踪，不以mtime或自增ID猜最新。每次新增要求前版hash与库外链一致，不可跳版；同版同hash幂等，同版异hash冲突。数据库缺/回退/错安装/错实例全部拒绝。
4. QUIESCING→RECOVERING新UUID显式映射到原数字代次+1；同epoch的VERIFYING/RELEASE_READY不换数值；溢出拒绝。只更新server_state当前控制投影为RECOVERY_PENDING，原S/Job/审计/签名/JWT/回执中的source epoch原值不变。表的原登记、映射及投影行禁止UPDATE/DELETE。
5. 所有SQL在已验RecoveryLeasedTransaction中；不接NORMAL、不注册业务路由/生产控制bean。此组件只完成关闭阶段映射，尚不能声明UUID传输协议、API/Session/Worker/Flyway共享门禁或恢复放行已完成。旧应用/旧连接实际排空与关闭意图接纳仍须后续整合。
6. 当批必须有非空V38→V39迁移、原业务表/Job原数字证据不变、Python原件→Java→MySQL、两线程原键竞争、阶段推进/重放/回退/错误安装或库实例反例；再跑相关完整回归及真实HTTP兼容。数据库复制目标身份变化的显式迁移、NORMAL初始化/释放/生产审批不在本子项偷偷开放。

实现采用追加投影行、查询最高revision，避免另建可被退回的独立指针。数据库外的权威控制记录仍是事实来源；DB里的副本不能代替可信外部checkpoint。所有例外及原件不满足时保持关闭。
