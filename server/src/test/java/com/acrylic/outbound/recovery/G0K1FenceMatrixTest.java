package com.acrylic.outbound.recovery;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * G0-K1 定向反例（测试侧第 2 类）：<b>按“观察时状态”计算的围栏矩阵模型</b>。
 *
 * <p>模型只保存**观察时刻的终态**（进程存活、进程记录锁、`fenced.lock` 共享锁、租约记录、本机 FENCED），
 * 观察者据此计算跨进程依据，<b>不累计历史成功动作</b>。每条跨进程依据都带必要前提：
 * <ul>
 *   <li>`fenced.lock` 依据：**观察时进程仍存活且共享锁仍被持有**（“本轮曾确认取得过”不算）；</li>
 *   <li>孤儿依据：**观察时进程记录锁已释放且租约仍未退役**；</li>
 *   <li>终止依据：**观察时进程已确认终止且租约仍未退役**；F5 已确认时同一进程的共享锁必然已被释放，
 *       因此不得再并列“共享锁仍被持有”。</li>
 * </ul>
 *
 * <p>本机拒绝的效力**不在此类用赋值断言**，而由 {@link G0K1OrderingModelTest} 的调用入口＋事件轨迹用例证明；
 * 本类只记录本机 FENCED 状态并给出指向。模型仅在测试源码内：不改 src/main、不连接数据库、不启动服务、
 * 不执行真实 halt/kill，也<b>不</b>验证真实 {@code FileLock}、真实进程终止或其他 JVM 的可见性。
 */
class G0K1FenceMatrixTest {

    // ------------------------------------------------------------------ 状态迁移模型

    /** 一个进程在观察时刻的状态；所有迁移都显式，终止释放全部锁并禁止后续动作。 */
    static final class ProcessState {
        private boolean alive = true;
        private boolean recordLockHeld = true;
        private boolean fenceLockHeld = false;
        private boolean leasesPresent = true;
        private boolean locallyFenced = false;

        boolean alive() { return alive; }
        boolean recordLockHeld() { return recordLockHeld; }
        boolean fenceLockHeld() { return fenceLockHeld; }
        boolean leasesPresent() { return leasesPresent; }
        boolean locallyFenced() { return locallyFenced; }

        /** F1：本机内存围栏置位（其拒绝效力见 G0K1OrderingModelTest 的轨迹用例）。 */
        void markFenced() { locallyFenced = true; }

        /** F1′：取得 fenced.lock 共享锁（共享模式，可与其它围栏进程共存）。 */
        void fenceSignal() {
            requireAlive("fenceSignal");
            fenceLockHeld = true;
        }

        /** F3：显式释放进程记录锁（真实实现用 FileLock.release() 并读回确认）。 */
        void releaseRecordLock() {
            requireAlive("releaseRecordLock");
            recordLockHeld = false;
        }

        /** 正常收尾：退役租约记录。 */
        void retireLeases() {
            requireAlive("retireLeases");
            leasesPresent = false;
        }

        /** F5：确认终止 ⇒ OS 释放该进程全部锁。 */
        void terminate() {
            alive = false;
            recordLockHeld = false;
            fenceLockHeld = false;
        }

        /** 负控专用：故意违反“终止释放全部锁”的不变式。 */
        void terminateWithoutReleasing() { alive = false; }

        private void requireAlive(String action) {
            if (!alive) throw new IllegalStateException("FENCED/TERMINATED 进程不得再执行 " + action);
        }
    }

    /** 终止不变式：已确认终止的进程不得仍持有任何锁。 */
    static void requireTerminationReleasesAllLocks(ProcessState state, String where) {
        if (!state.alive() && (state.recordLockHeld() || state.fenceLockHeld())) {
            throw new AssertionError(where + "：已确认终止但模型中仍有锁被持有（recordLock="
                + state.recordLockHeld() + ", fenceLock=" + state.fenceLockHeld() + "）");
        }
    }

    /** 观察者：只看观察时刻的终态。 */
    static final class Observer {
        static final String FENCE_PRECONDITION = "观察时仍存活且已确认取得 fenced.lock 共享锁并仍持有";

        static List<String> basis(ProcessState state) {
            var evidence = new ArrayList<String>();
            if (!state.alive()) {
                if (state.leasesPresent()) {
                    evidence.add("进程已确认终止且租约未退役 ⇒ 逐记录探针判孤儿（终止时该进程全部锁已释放，"
                        + "因此不再并列围栏通道依据）");
                }
                return List.copyOf(evidence);
            }
            if (state.fenceLockHeld()) {
                evidence.add(FENCE_PRECONDITION + " ⇒ 门的独占 tryLock 必失败");
            }
            if (!state.recordLockHeld() && state.leasesPresent()) {
                evidence.add("进程记录锁已释放且租约未退役 ⇒ 逐记录探针判孤儿");
            }
            return List.copyOf(evidence);
        }

        static String outcome(List<String> basis) {
            return basis.isEmpty() ? "UNCONFIRMED_NO_CROSS_PROCESS_BASIS" : "BLOCKED";
        }

        static String followUp(ProcessState state, List<String> basis) {
            if (basis.isEmpty()) {
                return "跨进程无确认依据 ⇒ 必须由运维外部隔离（停机/隔离该实例）；这不是已实现能力";
            }
            return "跨进程：后续接纳与效果在下一次观察处拒绝";
        }
    }

    /** 一行结论：本机结果、跨进程依据、后续唯一结果。 */
    record Row(String label, boolean localFenceSet, String localResult, List<String> crossBasis, String outcome,
               String followUp) { }

    private static Row evaluate(String label, ProcessState state) {
        requireTerminationReleasesAllLocks(state, "evaluate(" + label + ")");
        var basis = Observer.basis(state);
        String local = state.locallyFenced()
            ? "本机 FENCED 已置：F1 之后**新进入**的调用被拒（效力由 G0K1OrderingModelTest 的调用入口＋轨迹用例证明，"
                + "此处不用赋值断言）"
            : "本机 FENCED 未置";
        return new Row(label, state.locallyFenced(), local, basis, Observer.outcome(basis),
            Observer.followUp(state, basis));
    }

    private static ProcessState state(boolean f1p, boolean f3, boolean f5) {
        var state = new ProcessState();
        state.markFenced();
        if (f1p) state.fenceSignal();
        if (f3) state.releaseRecordLock();
        if (f5) state.terminate();
        return state;
    }

    // ------------------------------------------------------------------ 必须分别验证的四种状态

    @Test void f1pConfirmedAndAliveIsTheBasisThatRequiresStillHeld() {
        var state = state(true, false, false);
        var row = evaluate("F1′ 已确认且进程仍存活", state);

        assertThat(state.alive()).isTrue();
        assertThat(state.fenceLockHeld()).isTrue();
        assertThat(row.crossBasis()).hasSize(1);
        assertThat(row.crossBasis().getFirst()).contains(Observer.FENCE_PRECONDITION);
        assertThat(row.outcome()).isEqualTo("BLOCKED");
        assertThat(row.localResult()).contains("新进入").contains("轨迹用例证明");
        System.out.println("[G0K1-fence] " + row);
    }

    @Test void f3ConfirmedMeansTheRecordLockIsActuallyReleased() {
        var state = state(false, true, false);
        var row = evaluate("F3 已确认", state);

        assertThat(state.recordLockHeld()).as("F3 已确认 ⇒ 进程记录锁确实已释放").isFalse();
        assertThat(state.leasesPresent()).isTrue();
        assertThat(row.crossBasis()).hasSize(1);
        assertThat(row.crossBasis().getFirst()).contains("记录锁已释放").contains("孤儿");
        assertThat(row.outcome()).isEqualTo("BLOCKED");
        System.out.println("[G0K1-fence] " + row);
    }

    @Test void f5ConfirmedMeansTheProcessIsActuallyTerminatedAndNoLockIsStillHeld() {
        var state = state(true, false, true);
        var row = evaluate("F5 已确认（此前 F1′ 也曾确认）", state);

        assertThat(state.alive()).as("F5 已确认 ⇒ 进程确实已终止").isFalse();
        assertThat(state.fenceLockHeld()).as("终止后不得仍持有共享锁").isFalse();
        assertThat(state.recordLockHeld()).isFalse();
        assertThat(row.crossBasis()).hasSize(1);
        assertThat(row.crossBasis().getFirst()).contains("已确认终止").doesNotContain(Observer.FENCE_PRECONDITION);
        assertThat(row.crossBasis()).noneMatch(entry -> entry.contains("仍被持有"));
        assertThat(row.outcome()).isEqualTo("BLOCKED");
        System.out.println("[G0K1-fence] " + row);
    }

    @Test void noneConfirmedKeepsTheBlockerLabelAndEmptyBasis() {
        var state = state(false, false, false);
        var row = evaluate("F1′/F3/F5 均未确认", state);

        assertThat(state.alive()).isTrue();
        assertThat(state.fenceLockHeld()).isFalse();
        assertThat(state.recordLockHeld()).isTrue();
        assertThat(row.crossBasis()).isEmpty();
        assertThat(row.outcome()).isEqualTo("UNCONFIRMED_NO_CROSS_PROCESS_BASIS");
        assertThat(row.followUp()).contains("外部隔离").contains("不是已实现能力");
        System.out.println("[G0K1-fence] " + row);
    }

    // ------------------------------------------------------------------ 观察时状态语义

    @Test void fenceBasisDisappearsOnceTheProcessIsTerminated() {
        var state = state(true, false, false);
        assertThat(Observer.basis(state)).hasSize(1);

        state.terminate();                       // “本轮曾确认取得过共享锁”不得继续作为依据
        var basis = Observer.basis(state);
        assertThat(basis).hasSize(1);
        assertThat(basis.getFirst()).doesNotContain(Observer.FENCE_PRECONDITION);
        assertThat(basis).noneMatch(entry -> entry.contains("仍被持有"));
        System.out.println("[G0K1-fence] after terminate: " + basis);
    }

    @Test void everyConfirmationCombinationYieldsAnObservationTimeOutcome() {
        var rows = List.of(
            evaluate("111", state(true, true, true)), evaluate("110", state(true, true, false)),
            evaluate("101", state(true, false, true)), evaluate("100", state(true, false, false)),
            evaluate("011", state(false, true, true)), evaluate("010", state(false, true, false)),
            evaluate("001", state(false, false, true)), evaluate("000", state(false, false, false)));

        for (Row row : rows) {
            if (row.crossBasis().isEmpty()) {
                assertThat(row.outcome()).as("%s：观察时无依据 ⇒ 保留为阻断项", row.label())
                    .isEqualTo("UNCONFIRMED_NO_CROSS_PROCESS_BASIS");
            } else {
                assertThat(row.outcome()).as("%s：观察时有依据 ⇒ 阻断", row.label()).isEqualTo("BLOCKED");
                for (String entry : row.crossBasis()) {
                    assertThat(entry).as("每条依据都必须带观察时前提").matches(
                        ".*(观察时仍存活|已确认终止|已释放).*");
                }
            }
            assertThat(row.localResult()).isNotBlank();
            assertThat(row.followUp()).isNotBlank();
        }
        System.out.println("[G0K1-fence] 8 组合（观察时依据条数）："
            + rows.stream().map(row -> row.label() + "=" + row.crossBasis().size()).toList());
    }

    @Test void retiredLeasesAfterTerminationGiveNoBasisAndAreNotAGap() {
        var state = new ProcessState();
        state.markFenced();
        state.retireLeases();
        state.terminate();

        assertThat(Observer.basis(state)).isEmpty();
        assertThat(Observer.outcome(Observer.basis(state))).isEqualTo("UNCONFIRMED_NO_CROSS_PROCESS_BASIS");
        // 正常完成 + 进程退出：没有未退役租约，因此没有需要阻断的对象。
    }

    // ------------------------------------------------------------------ 不变式与负控

    @Test void terminationReleasesEveryLockAndForbidsFurtherActions() {
        var state = new ProcessState();
        state.fenceSignal();
        state.terminate();

        assertThat(state.fenceLockHeld()).isFalse();
        assertThat(state.recordLockHeld()).isFalse();
        requireTerminationReleasesAllLocks(state, "terminationReleasesEveryLockAndForbidsFurtherActions");
        assertThatThrownBy(state::fenceSignal).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(state::releaseRecordLock).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(state::retireLeases).isInstanceOf(IllegalStateException.class);
    }

    @Test void negativeControlTerminationInvariantDetectsARetainedLock() {
        var bad = new ProcessState();
        bad.fenceSignal();
        bad.terminateWithoutReleasing();

        assertThatThrownBy(() -> requireTerminationReleasesAllLocks(bad, "negative-control"))
            .isInstanceOf(AssertionError.class).hasMessageContaining("仍有锁被持有");

        var good = new ProcessState();
        good.fenceSignal();
        good.terminate();
        requireTerminationReleasesAllLocks(good, "restored");
        System.out.println("[G0K1-fence] negative control (terminateWithoutReleasing) detected; restored run passes");
    }

    @Test void negativeControlGuardBypassDelegatesAndIsDetectedByTheTrace() {
        var unguarded = new LocalGateProbe(false);
        unguarded.fence();
        assertThat(unguarded.enterEffect("c2")).isTrue();
        assertThat(unguarded.trace()).containsExactly("F1", "gate(c2) readFenced=true", "delegate(c2)");

        var restored = new LocalGateProbe(true);
        restored.fence();
        assertThat(restored.enterEffect("c2")).isFalse();
        assertThat(restored.trace()).containsExactly("F1", "gate(c2) readFenced=true", "refuse(c2)");
        System.out.println("[G0K1-fence] negative control (guard removed): " + unguarded.trace()
            + " | restored: " + restored.trace());
    }

    /**
     * 本机门的最小探针，仅用于负控：证明“撤去守卫后仍会委托”可被轨迹检出。
     * 正向结论（F1 之后新进入必拒）以 {@link G0K1OrderingModelTest} 的调度用例为准。
     */
    static final class LocalGateProbe {
        private final List<String> trace = new ArrayList<>();
        private final boolean guardEnabled;
        private boolean fenced;
        LocalGateProbe(boolean guardEnabled) { this.guardEnabled = guardEnabled; }
        void fence() { fenced = true; trace.add("F1"); }
        List<String> trace() { return List.copyOf(trace); }
        boolean enterEffect(String callId) {
            trace.add("gate(" + callId + ") readFenced=" + fenced);
            if (fenced && guardEnabled) { trace.add("refuse(" + callId + ")"); return false; }
            trace.add("delegate(" + callId + ")");
            return true;
        }
    }
}
