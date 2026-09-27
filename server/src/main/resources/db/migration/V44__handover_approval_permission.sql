-- R07-1 交接审批权限码补齐
--
-- 背景：R07-1 命令层（HandoverCommand.Identity）引用 "order:handover" 作为审批门，
-- 但此前没有任何迁移把该权限码注册进 permission 表（已迁移中已注册 order:cancel / order:dispose /
-- order:publish / order:read / order:recall / order:resume / order:write 七个，独缺 order:handover）。
-- 后果：role_permission 的授予会静默插入 0 行，任何用户都无法通过交接审批门——生产同样不可满足
-- （attempt 162 实测：APPROVE 返回 PERMISSION_DENIED 而非业务错误码）。
--
-- 绑定角色：沿用 WAREHOUSE_ADMIN（与 order:resume / order:recall / order:dispose 等审批类权限的既有做法一致），
-- 不新建角色，避免扩大授权面。
INSERT INTO permission VALUES(UUID_TO_BIN('01994300-0000-7000-8000-00000000ff01'),'order:handover','审批已出库交接（原机可用/更正前置）');
INSERT INTO role_permission SELECT r.id,p.id FROM role r CROSS JOIN permission p
    WHERE r.code='WAREHOUSE_ADMIN' AND p.code='order:handover';
