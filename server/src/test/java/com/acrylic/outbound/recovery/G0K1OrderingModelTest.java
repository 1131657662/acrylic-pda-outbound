package com.acrylic.outbound.recovery;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntPredicate;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * G0-K1 定向反例（测试侧第 2 类）：<b>确定性调度模型</b>。
 *
 * <p>模型显式提供两个调度点：**第一次读取之后**（`afterFirstRead`）与**最后一次读取之后**（`afterFinalRead`）。
 * 因此可以实际构造并断言两条不同轨迹：
 * <pre>
 *   最终读取未围栏 → F1 → 委托        （在途调用：本机重读无法关闭的窗口）
 *   F1 → 新调用读取 → 拒绝            （F1 之后的“新进入”调用）
 * </pre>
 * 先后关系一律由**独立轨迹断言**（事件的相对次序）检查，不用测试名称或打印文案充当证据。
 *
 * <p>本类不修改 src/main、不连接数据库、不启动服务、不执行真实 halt/kill、不使用随机等待。
 */
class G0K1OrderingModelTest {

    // ------------------------------------------------------------------ 轨迹与门

    /** 有序事件轨迹。 */
    static final class Trace {
        private final List<String> events = new ArrayList<>();
        void add(String event) { events.add(event); }
        List<String> events() { return List.copyOf(events); }
        String joined() { return String.join(" | ", events); }
        int indexOfPrefix(String prefix) {
            for (int i = 0; i < events.size(); i++) if (events.get(i).startsWith(prefix)) return i;
            return -1;
        }
    }

    /**
     * 本机层效果门。线性化点＝本调用对围栏标志的读取；{@code recheck} 打开时在读标志之后再读一次。
     * {@code guardEnabled=false} 仅用于负控（应能检出反例）。
     */
    static final class LocalGate {
        private final Trace trace;
        private final boolean guardEnabled;
        private final boolean recheck;
        private boolean fenced;
        private int violations;

        LocalGate(Trace trace, boolean guardEnabled, boolean recheck) {
            this.trace = trace; this.guardEnabled = guardEnabled; this.recheck = recheck;
        }

        void fence(String where) { fenced = true; trace.add("F1@" + where); }
        int violations() { return violations; }

        /** true＝允许委托（放行或在途），false＝拒绝。 */
        boolean enterEffect(String callId, Runnable afterFirstRead, Runnable afterFinalRead) {
            boolean first = fenced;
            trace.add("gate(" + callId + ") read1=" + first);
            if (guardEnabled && first) { trace.add("refuse(" + callId + ")"); return false; }
            if (!guardEnabled && first) { violations++; trace.add("VIOLATION(" + callId + ")"); }
            if (afterFirstRead != null) afterFirstRead.run();
            if (guardEnabled && recheck) {
                boolean second = fenced;
                trace.add("gate(" + callId + ") read2=" + second);
                if (second) { trace.add("refuseAfterRecheck(" + callId + ")"); return false; }
            }
            if (afterFinalRead != null) afterFinalRead.run();
            trace.add("delegate(" + callId + ")");
            return true;
        }
    }

    /** 跨进程围栏通道的状态：{@code held} 只能由 F1′ 转为 true。 */
    static final class FenceLock {
        private final Trace trace;
        private boolean held;
        FenceLock(Trace trace) { this.trace = trace; }
        void acquireShared() { held = true; trace.add("fenceLock.acquireShared"); }
        /** true＝观察瞬间没有围栏共享锁；false＝有（门的独占 tryLock 必失败）。 */
        boolean observeFree(String at) {
            trace.add("fenceLock.observeFree@" + at + " free=" + !held);
            return !held;
        }
        boolean held() { return held; }
    }

    /** 逐记录孤儿扫描：记录 r1..rn，t_i ＝ offset+i+1，t_n ＝ offset+n+1。 */
    static final class Scan {
        record Outcome(String verdict, List<String> events) { }

        static Outcome scan(int records, int offset, IntPredicate aliveAtEvaluation, int scanNumber) {
            var events = new ArrayList<String>();
            for (int i = 1; i <= records; i++) {
                int t = offset + i + 1;
                boolean alive = aliveAtEvaluation.test(i);
                events.add("scan" + scanNumber + ":eval(r" + i + ")@" + t + " alive=" + alive);
                if (!alive) {
                    events.add("scan" + scanNumber + ":ORPHAN(r" + i + ")");
                    return new Outcome("ORPHAN", List.copyOf(events));
                }
            }
            events.add("scan" + scanNumber + ":scan_end@" + (offset + records + 1));
            return new Outcome("CLEAR", List.copyOf(events));
        }
    }

    /** 属主在 deathTimes[record-1] 死亡；在 t_i ＝ offset+i+1 判定“存活”当且仅当 deathTime > t_i。 */
    private static IntPredicate deathsAt(int offset, int... deathTimes) {
        return record -> deathTimes[record - 1] > offset + record + 1;
    }

    // ------------------------------------------------------------------ 轨迹用例

    @Test void newCallEnteringAfterFenceIsRefusedWithTraceOrder() {
        var trace = new Trace();
        var gate = new LocalGate(trace, true, false);

        gate.fence("before-call");
        assertThat(gate.enterEffect("c2", null, null)).isFalse();

        assertThat(trace.indexOfPrefix("F1@")).isLessThan(trace.indexOfPrefix("gate(c2) read1"));
        assertThat(trace.indexOfPrefix("gate(c2) read1")).isLessThan(trace.indexOfPrefix("refuse(c2)"));
        assertThat(trace.joined()).contains("gate(c2) read1=true").doesNotContain("delegate(c2)");
    }

    @Test void recheckCatchesFenceBetweenFirstReadAndRecheck() {
        var trace = new Trace();
        var gate = new LocalGate(trace, true, true);

        assertThat(gate.enterEffect("c1", () -> gate.fence("after-read1"), null)).isFalse();

        assertThat(trace.indexOfPrefix("gate(c1) read1=false")).isLessThan(trace.indexOfPrefix("F1@"));
        assertThat(trace.indexOfPrefix("F1@")).isLessThan(trace.indexOfPrefix("gate(c1) read2=true"));
        assertThat(trace.indexOfPrefix("gate(c1) read2=true")).isLessThan(trace.indexOfPrefix("refuseAfterRecheck(c1)"));
        assertThat(trace.joined()).doesNotContain("delegate(c1)");
        System.out.println("[G0K1-order] recheck closes the first-read window: " + trace.joined());
    }

    @Test void windowAfterTheFinalReadIsRecordedAsInFlightNotAsAViolation() {
        var trace = new Trace();
        var gate = new LocalGate(trace, true, true);

        // 调度点：最后一次读取之后、委托开始之前发生 F1
        boolean allowed = gate.enterEffect("c1", null, () -> gate.fence("after-final-read"));

        assertThat(allowed).as("最终读取早于 F1 写 ⇒ 在途调用，允许完成委托").isTrue();
        assertThat(trace.indexOfPrefix("gate(c1) read2=false")).isLessThan(trace.indexOfPrefix("F1@"));
        assertThat(trace.indexOfPrefix("F1@")).isLessThan(trace.indexOfPrefix("delegate(c1)"));
        assertThat(gate.violations()).as("在途调用不是违反：违反只在“F1 之后新进入”时计").isZero();
        // 之后的新调用必须被拒
        assertThat(gate.enterEffect("c3", null, null)).isFalse();
        System.out.println("[G0K1-order] irreducible window after final read (in-flight): " + trace.joined());
    }

    @Test void violationsAreCountedOnlyWhenTheGuardIsRemoved() {
        var removed = new Trace();
        var unguarded = new LocalGate(removed, false, false);
        unguarded.fence("before-call");
        assertThat(unguarded.enterEffect("c2", null, null)).isTrue();
        assertThat(unguarded.violations()).isEqualTo(1);
        assertThat(removed.joined()).contains("VIOLATION(c2)").contains("delegate(c2)");

        var restored = new Trace();
        var guarded = new LocalGate(restored, true, false);
        guarded.fence("before-call");
        assertThat(guarded.enterEffect("c2", null, null)).isFalse();
        assertThat(guarded.violations()).isZero();
        System.out.println("[G0K1-order] negative control: " + removed.joined() + " | restored: " + restored.joined());
    }

    @Test void crossProcessFenceObservationIsAStateTransitionNotAConstant() {
        var trace = new Trace();
        var lock = new FenceLock(trace);

        boolean beforeAcquire = lock.observeFree("t1");
        assertThat(beforeAcquire).isTrue();
        lock.acquireShared();
        boolean afterAcquire = lock.observeFree("t2");
        assertThat(afterAcquire).as("F1′ 之后同一通道的观察必须失败").isFalse();
        assertThat(beforeAcquire).as("早先的观察不被追溯改写，但它只覆盖观察瞬间").isTrue();
        assertThat(lock.held()).isTrue();
        assertThat(trace.joined()).contains("fenceLock.observeFree@t1 free=true")
            .contains("fenceLock.acquireShared").contains("fenceLock.observeFree@t2 free=false");
        System.out.println("[G0K1-order] fence-channel transitions: " + trace.joined());
    }

    @Test void unconfirmedFenceSignalYieldsNoCrossProcessBasis() {
        var trace = new Trace();
        var gate = new LocalGate(trace, true, false);
        gate.fence("F1");
        var lock = new FenceLock(trace);            // F1′ 未执行 ⇒ held=false

        assertThat(lock.held()).isFalse();
        boolean crossProcessBasis = !lock.observeFree("after-F1");
        assertThat(crossProcessBasis).isFalse();
        assertThat(crossProcessBasis ? "BLOCKED" : "UNCONFIRMED_NO_CROSS_PROCESS_BASIS")
            .isEqualTo("UNCONFIRMED_NO_CROSS_PROCESS_BASIS");
    }

    // ------------------------------------------------------------------ 扫描窗口用例

    @Test void ownerDeathBeforeItsRecordEvaluationIsDetectedAsOrphan() {
        var outcome = Scan.scan(2, 0, deathsAt(0, 1, 99), 1);
        assertThat(outcome.verdict()).isEqualTo("ORPHAN");
        assertThat(outcome.events()).anyMatch(event -> event.startsWith("scan1:ORPHAN(r1)"));
    }

    @Test void ownerDeathInsideWindowWIsMissedByThatScanAndCaughtByTheNext() {
        var first = Scan.scan(2, 0, deathsAt(0, 3, 99), 1);
        assertThat(first.verdict()).as("窗口 W：属主在其记录判定之后、扫描结束之前死亡").isEqualTo("CLEAR");
        assertThat(first.events()).anyMatch(event -> event.startsWith("scan1:scan_end"));
        System.out.println("[G0K1-order] GAP_W: " + String.join(" | ", first.events()));

        var second = Scan.scan(2, 10, deathsAt(10, 3, 99), 2);
        assertThat(second.verdict()).isEqualTo("ORPHAN");
        assertThat(second.events()).anyMatch(event -> event.startsWith("scan2:ORPHAN(r1)"));
    }

    @Test void ownerDeathAfterScanEndIsPostDecisionAndCaughtByTheNextScan() {
        var first = Scan.scan(2, 0, deathsAt(0, 5, 99), 1);
        assertThat(first.verdict()).isEqualTo("CLEAR");
        var later = Scan.scan(2, 10, deathsAt(10, 5, 99), 2);
        assertThat(later.verdict()).isEqualTo("ORPHAN");
    }
}
