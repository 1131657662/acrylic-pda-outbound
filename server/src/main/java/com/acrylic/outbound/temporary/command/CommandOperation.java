package com.acrylic.outbound.temporary.command;

/** No production implementation exists. Tests may inject a synthetic transaction participant. */
public interface CommandOperation {
    String commandType();
    void apply(CommandContext trustedContext);
}
