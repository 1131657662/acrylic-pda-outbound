package com.acrylic.outbound.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * Makes a non-NORMAL {@code server_state} loud at startup. It never writes, never changes the mode and
 * never fails the boot: the only purpose is that an operator cannot miss a service mode that silently
 * turns every write into {@code 503 SERVER_RECOVERY_REQUIRED}.
 *
 * <p>Read access and evidence extraction intentionally keep working in that state, and the way back to
 * NORMAL is the R09 control chain (external authority + writer lease + RELEASE_READY projection +
 * {@code RecoveryNormalRegistration}); editing {@code server_state} with SQL is not a sanctioned recovery.
 */
@Component
public class ServiceModeStartupNotice implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(ServiceModeStartupNotice.class);

    private final ServerState state;

    public ServiceModeStartupNotice(ServerState state) { this.state = state; }

    @Override
    public void run(ApplicationArguments args) {
        ServerState.State current;
        try {
            current = state.current();
        } catch (RuntimeException unavailable) {
            log.warn("启动时无法读取 server_state，未确认 service_mode：{}", unavailable.toString());
            return;
        }
        if ("NORMAL".equals(current.mode())) {
            log.info("server_state service_mode=NORMAL recovery_epoch={}", current.epoch());
            return;
        }
        log.error("server_state service_mode={} recovery_epoch={}：所有写入将返回 503 SERVER_RECOVERY_REQUIRED，"
            + "读取与证据提取仍可用。回到 NORMAL 只能由既有 R09 控制链完成"
            + "（外部控制权威 + 写者租约 + RELEASE_READY 投影 + RecoveryNormalRegistration），"
            + "不得直接用 SQL 改写 server_state。", current.mode(), current.epoch());
    }
}
