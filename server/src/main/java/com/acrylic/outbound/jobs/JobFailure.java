package com.acrylic.outbound.jobs;

/** Only a safe, stable code is persisted; exception text may contain SQL, credentials or business data. */
public class JobFailure extends Exception {
    private final boolean retryable;

    public JobFailure(String code, boolean retryable) {
        super(code);
        if (code == null || !code.matches("[A-Z][A-Z0-9_]{0,79}")) throw new IllegalArgumentException("Invalid failure code");
        this.retryable = retryable;
    }

    public boolean retryable() { return retryable; }
}
