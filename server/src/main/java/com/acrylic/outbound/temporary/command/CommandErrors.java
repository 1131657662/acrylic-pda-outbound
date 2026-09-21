package com.acrylic.outbound.temporary.command;

import com.acrylic.outbound.common.BusinessException;

final class CommandErrors {
    private CommandErrors() { }
    static BusinessException missing() { return new BusinessException(404,"COMMAND_RECEIPT_NOT_FOUND","原命令不存在或无权读取；不能据此重做或放行"); }
    static BusinessException corrupt() { return new BusinessException(409,"COMMAND_RECEIPT_INTEGRITY_FAILURE","原命令证据不可核验，请保留资料"); }
    static BusinessException conflict() { return new BusinessException(409,"IDEMPOTENCY_CONTENT_MISMATCH","同一原键对应不同命令，请保留原意图"); }
    static BusinessException stale() { return new BusinessException(409,"COMMAND_CONTEXT_CHANGED","原上下文已变化，请核对原意图"); }
    static BusinessException unsupported() { return new BusinessException(409,"COMMAND_EXECUTION_NOT_IMPLEMENTED","此命令决定尚未开放"); }
    static void integrity(boolean valid) { if(!valid)throw corrupt(); }
}
