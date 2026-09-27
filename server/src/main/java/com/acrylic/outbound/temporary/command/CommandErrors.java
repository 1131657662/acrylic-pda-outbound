package com.acrylic.outbound.temporary.command;

import com.acrylic.outbound.common.BusinessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class CommandErrors {
    /** Server-side only: integrity failures keep their cause in the log, never in the user-facing message. */
    private static final Logger log = LoggerFactory.getLogger(CommandErrors.class);

    private CommandErrors() { }
    static BusinessException missing() { return new BusinessException(404,"COMMAND_RECEIPT_NOT_FOUND","原命令不存在或无权读取；不能据此重做或放行"); }
    static BusinessException corrupt() { return corrupt((String)null); }
    static BusinessException corrupt(String detail) {
        var frames = new Throwable().getStackTrace();
        var where = new StringBuilder();
        for (var frame : frames) {
            if (frame.getClassName().equals(CommandErrors.class.getName())) continue;
            if (where.length() > 0) where.append(" <- ");
            where.append(frame);
            if (where.length() > 300) break;
        }
        log.error("COMMAND_RECEIPT_INTEGRITY_FAILURE detail={} where={}", detail, where);
        return new BusinessException(409,"COMMAND_RECEIPT_INTEGRITY_FAILURE","原命令证据不可核验，请保留资料");
    }
    static BusinessException corrupt(Throwable cause) {
        log.error("COMMAND_RECEIPT_INTEGRITY_FAILURE cause={}", cause, cause);
        return new BusinessException(409,"COMMAND_RECEIPT_INTEGRITY_FAILURE","原命令证据不可核验，请保留资料");
    }
    static BusinessException conflict() { return new BusinessException(409,"IDEMPOTENCY_CONTENT_MISMATCH","同一原键对应不同命令，请保留原意图"); }
    static BusinessException stale() { return new BusinessException(409,"COMMAND_CONTEXT_CHANGED","原上下文已变化，请核对原意图"); }
    static BusinessException unsupported() { return new BusinessException(409,"COMMAND_EXECUTION_NOT_IMPLEMENTED","此命令决定尚未开放"); }
    static BusinessException confirmationRequired() { return new BusinessException(409,"TEMPORARY_MANUAL_CONFIRMATION_REQUIRED","缺少人工核对候选的绑定，系统不会自动判定或自动关联"); }
    static void integrity(boolean valid) { integrity(valid,"unnamed"); }
    static void integrity(boolean valid,String label) { if(!valid)throw corrupt("integrity check failed: " + label); }
}
