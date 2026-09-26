package com.acrylic.outbound.recovery;

/** Closed failure for an unverifiable external recovery authority. */
public final class RecoveryControlException extends RuntimeException {
    private final String code;

    public RecoveryControlException(String code) {
        super(code);
        this.code = code;
    }

    public RecoveryControlException(String code, Throwable cause) {
        super(code, cause);
        this.code = code;
    }

    public String code() {
        return code;
    }
}
