package com.acrylic.outbound.recovery;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * G0-K1 定向反例（测试侧第 2 类）：<b>围栏状态迁移模型</b>。
 *
 * <p>本类按“状态迁移”而不是“历史动作计数”计算阻断依据：模型保存每个进程的**终态**（存活、
 * 进程记录锁、围栏共享锁、租约记录是否存在），观察者在自己的执行时刻**只看终态**。
 * 确认终止必须释放该进程模型中的全部锁；终止后不得再执行本机效果门。
 *
 * <p>本机拒绝不通过 `local = true` 这类赋值证明，而是通过**调用入口与委托轨迹**验证。
 * 模型只在测试源码内，不修改 src/main、不连接数据库、不启动服务、不执行真实 halt/kill。
 */
class G0K1FenceMatrixTest {

    // ------------------------------------------------------------------ 状态迁移模型

    /** 一个进程的模型状态；所有迁移都显式，且终止释放全部锁。 */
    static final class ProcessState {
        private boolean alive = true;
        private boolean recordLockHeld = true;
        private boolean fenceLockHeld = false;
        private boolean leasesPresent = true;

        boolean alive() { return alive; }
        boolean recordLockHeld() { return recordLockHeld; }
        boolean fenceLockHeld() { return fenceLockHeld; }
        boolean leasesPresent() { return leasesPresent; }

        /** F1′：取得 fenced.lock 共享锁（共享模式，可与其它围栏进程共存）。 */
        void fenceSignal(String ignored) {
            requireAlive("fenceSignal");
            fenceLockHeld = true;
        }

        /** F3：显式释放进程记录锁（真实实现用 FileLock.release() 并读回确认）。 */
        void releaseRecordLock() {
            requireAlive("releaseRecordLock");
            recordLockHeld = false;
        }

        /** 正常收尾：退役租约记录（仅在仍在运行时由本进程执行）。 */
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
        void terminateWithoutReleasing() {
            alive = false;
        }

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

    /** 观察者：只看观察时刻的终态，不数历史成功动作。 */
    static final class Observer {
        static List<String> basis(ProcessState state) {
            var evidence = new ArrayList<String>();
            if (!state.alive()) {
                if (state.leasesPresent()) {
                    evidence.add("进程已确认终止且租约未退役 ⇒ 逐记录探针判孤儿");
                }
                // 终止后 fenceLock 必为 false（不变式），不得再出现“共享锁仍被持有”
                return List.copyOf(evidence);
            }
            if (state.fenceLockHeld()) {
                evidence.add("fenced.lock 共享锁仍被持有 ⇒ 门的独占 tryLock 必失败");
            }
            if (!state.recordLockHeld() && state.leasesPresent()) {
                evidence.add("进程记录锁已释放且租约未退役 ⇒ 逐记录探针判孤儿");
            }
            return List.copyOf(evidence);
        }

        static String outcome(List<String> basis) {
            return basis.isEmpty() ? "UNCONFIRMED_NO_CROSS_PROCESS_BASIS" : "BLOCKED";
        }
    }

    /** 本机效果门模型：拒绝必须由调用入口＋轨迹证明。 */
    static final class LocalGate {
        private final List<String> trace = new ArrayList<>();
        private final boolean guardEnabled;
        private boolean fenced;
        LocalGate(boolean guardEnabled) { this.guardEnabled = guardEnabled; }
        void fence() { fenced = true; trace.add("F1"); }
        List<String> trace() { return List.copyOf(trace); }
        /** true＝允许委托；false＝拒绝。 */
        boolean enterEffect(String callId) {
            trace.add("gate(" + callId + ") readFenced=" + fenced);
            if (fenced && guardEnabled) { trace.add("refuse(" + callId + ")"); return false; }
            trace.add("delegate(" + callId + ")");       // 负控：守卫关闭时此处即为反例
            return true;
        }
    }

    // ------------------------------------------------------------------ 8 种确认组合（按状态迁移构造）

    private record Row(ProcessState state, String label, List<String> basis, String outcome) { }

    private static Row combination(boolean f1p, boolean f3, boolean f5) {
        var state = new ProcessState();
        if (f1p) state.fenceSignal("F1′");
        if (f3) state.releaseRecordLock();
        if (f5) state.terminate();
        requireTerminationReleasesAllLocks(state, "combination(f1p=" + f1p + ",f3=" + f3 + ",f5=" + f5 + ")");
        var basis = Observer.basis(state);
        return new Row(state, "F1′=" + (f1p ? "ok" : "unconfirmed") + ",F3=" + (f3 ? "ok" : "unconfirmed")
            + ",F5=" + (f5 ? "ok" : "unconfirmed"), basis, Observer.outcome(basis));
    }

    @Test void terminationReleasesEveryLockInTheModel() {
        var state = new ProcessState();
        state.fenceSignal("F1′");
        state.terminate();

        assertThat(state.alive()).isFalse();
        assertThat(state.fenceLockHeld()).as("终止后不得仍持有围栏共享锁").isFalse();
        assertThat(state.recordLockHeld()).as("终止后不得仍持有进程记录锁").isFalse();
        requireTerminationReleasesAllLocks(state, "terminationReleasesEveryLockInTheModel");
        assertThatThrownBy(() -> state.fenceSignal("again")).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> state.releaseRecordLock()).isInstanceOf(IllegalStateException.class);
    }

    @Test void basisIsComputedFromTheFinalStateNotFromHistoricalSuccesses() {
        // F1′、F3、F5 都曾确认成功；终态是“已终止且租约未退役”
        var row = combination(true, true, true);

        assertThat(row.basis()).hasSize(1);
        assertThat(row.basis().getFirst()).contains("已确认终止").contains("孤儿");
        assertThat(row.basis()).noneMatch(text -> text.contains("共享锁"));
        assertThat(row.basis()).noneMatch(text -> text.contains("记录锁已释放且"));
        assertThat(row.outcome()).isEqualTo("BLOCKED");
        System.out.println("[G0K1-fence] final-state basis (" + row.label() + ") = " + row.basis());
    }

    @Test void everyConfirmationCombinationYieldsAStateBasedOutcome() {
        var rows = List.of(combination(true, true, true), combination(true, true, false),
            combination(true, false, true), combination(true, false, false),
            combination(false, true, true), combination(false, true, false),
            combination(false, false, true), combination(false, false, false));

        for (Row row : rows) {
            if (row.basis().isEmpty()) {
                assertThat(row.outcome()).as("%s：无终态依据 ⇒ 必须保留为阻断项", row.label())
                    .isEqualTo("UNCONFIRMED_NO_CROSS_PROCESS_BASIS");
            } else {
                assertThat(row.outcome()).as("%s：终态存在可见依据 ⇒ 阻断", row.label()).isEqualTo("BLOCKED");
            }
        }
        System.out.println("[G0K1-fence] 8 组合（终态依据）："
            + rows.stream().map(row -> row.label() + "=" + row.basis().size()).toList());
    }

    @Test void allUnconfirmedKeepsTheBlockerLabelAndBasisIsEmpty() {
        var row = combination(false, false, false);

        assertThat(row.state().alive()).isTrue();
        assertThat(row.state().recordLockHeld()).as("F3 未确认 ⇒ 记录锁仍持有 ⇒ 属在途，不阻断").isTrue();
        assertThat(row.basis()).isEmpty();
        assertThat(row.outcome()).isEqualTo("UNCONFIRMED_NO_CROSS_PROCESS_BASIS");
    }

    @Test void retiredLeasesAfterTerminationGiveNoBasisAndAreNotAGap() {
        var state = new ProcessState();
        state.retireLeases();          // 正常收尾先退役租约
        state.terminate();             // 再终止

        assertThat(Observer.basis(state)).isEmpty();
        assertThat(Observer.outcome(Observer.basis(state))).isEqualTo("UNCONFIRMED_NO_CROSS_PROCESS_BASIS");
        // 这是“正常完成 + 进程退出”的干净路径：没有未退役租约，因此没有需要阻断的对象。
    }

    // ------------------------------------------------------------------ 本机拒绝与负控

    @Test void localRefusalIsVerifiedByCallEntryAndTraceNotByAFlag() {
        var gate = new LocalGate(true);
        gate.fence();

        assertThat(gate.enterEffect("c2")).isFalse();
        assertThat(gate.trace()).containsExactly("F1", "gate(c2) readFenced=true", "refuse(c2)");
        assertThat(gate.trace()).noneMatch(event -> event.startsWith("delegate"));
    }

    @Test void guardBypassIsDetectedByTheTrace() {
        var unguarded = new LocalGate(false);
        unguarded.fence();
        assertThat(unguarded.enterEffect("c2")).as("负控：关闭守卫后调用进入委托 ⇒ 反例可达").isTrue();
        assertThat(unguarded.trace()).containsExactly("F1", "gate(c2) readFenced=true", "delegate(c2)");

        var restored = new LocalGate(true);
        restored.fence();
        assertThat(restored.enterEffect("c2")).isFalse();
        assertThat(restored.trace()).contains("refuse(c2)").noneMatch(event -> event.startsWith("delegate"));
        System.out.println("[G0K1-fence] negative control: " + unguarded.trace() + " | restored: " + restored.trace());
    }

    @Test void terminationInvariantDetectsARetainedLock() {
        var bad = new ProcessState();
        bad.fenceSignal("F1′");
        bad.terminateWithoutReleasing();      // 负控：故意保留锁状态

        assertThatThrownBy(() -> requireTerminationReleasesAllLocks(bad, "negative-control"))
            .isInstanceOf(AssertionError.class).hasMessageContaining("仍有锁被持有");

        var good = new ProcessState();
        good.fenceSignal("F1′");
        good.terminate();
        requireTerminationReleasesAllLocks(good, "restored");
        System.out.println("[G0K1-fence] termination invariant: retained-lock detected, restored run passes");
    }
}
