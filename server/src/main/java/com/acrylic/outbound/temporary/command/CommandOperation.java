package com.acrylic.outbound.temporary.command;

/** Business implementations are explicitly constructed in isolated tests; none is registered in production. */
public interface CommandOperation {
    String commandType();
    void apply(CommandContext trustedContext);
    default boolean recordsDecision() { return false; }
    /** True when the command may only run with an explicit, authentic reviewed-candidate binding. */
    default boolean requiresCandidateContext() { return false; }
    default com.fasterxml.jackson.databind.JsonNode execute(CommandWrite write) {
        apply(write.context()); return com.fasterxml.jackson.databind.node.NullNode.getInstance();
    }
}
