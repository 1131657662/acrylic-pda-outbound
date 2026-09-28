package com.acrylic.outbound.recovery;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntPredicate;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * G0-K1 定向反例（测试侧第 2 类）：<b>确定性调度模型</b>。
 *
 * <p>本类只在测试源码内建立最小状态模型，用显式事件顺序验证候选规范；<b>不</b>修改 src/main、
 * <b>不</b>连接数据库、<b>不</b>启动服务、<b>不</b>执行真实 halt/kill、<b>不</b>使用随机等待。
 * 模型通过不等于产品实现通过。
 *
 * <p>模型语义：本机层效果门的线性化点＝本次调用对 volatile 围栏标志的**一次读取**；跨进程
 * 围栏通道＝对 {@code fenced.lock} 的**一次**独占 tryLock 观察；孤儿扫描＝逐记录在 t_i 判定属主
 * 存活、于 t_n 结束，因此存在命名窗口 W_i ＝ (t_i, t_n]。
 */
class G0K1OrderingModelTest {

    // ------------------------------------------------------------------ 轨迹与模型

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
     * 本机层效果门。{@code guardEnabled} 只用于负控：关闭守卫时模型应能检出反例。
     * {@code recheck} 打开时在读标志之后、委托之前再读一次（用于证明“重读只移动线性化点”）。
     */
    static final class LocalGate {
        private final Trace trace;
        private final boolean guardEnabled;
        private final boolean recheck;
        private volatile boolean fenced;
        private int violations;

        LocalGate(Trace trace, boolean guardEnabled, boolean recheck) {
            this.trace = trace; this.guardEnabled = guardEnabled; this.recheck = recheck;
        }

        void fence(String where) { fenced = true; trace.add("F1@" + where); }
        boolean isFenced() { return fenced; }
        int violations() { return violations; }

        /** 返回 true＝允许委托（放行或在途），false＝拒绝。 */
        boolean enterEffect(String callId, Runnable betweenReadAndDelegate) {
            boolean observed = fenced;
            trace.add("gate(" + callId + ") readFenced=" + observed);
            if (guardEnabled && observed) { trace.add("refuse(" + callId + ")"); return false; }
            if (!guardEnabled && observed) { violations++; trace.add("VIOLATION(" + callId + ")"); }
            if (betweenReadAndDelegate != null) betweenReadAndDelegate.run();
            if (guardEnabled && recheck) {
                boolean again = fenced;
                trace.add("gate(" + callId + ") recheckFenced=" + again);
                if (again) { trace.add("refuseAfterRecheck(" + callId + ")"); return false; }
            }
            trace.add("delegate(" + callId + ")");
            return true;
        }
    }

    /** 跨进程围栏通道模型：F1′ 取得的共享锁与门的独占 tryLock 观察。 */
    static final class FenceLock {
        private boolean held;
        void acquireShared() { held = true; }
        /** true＝观察到无冲突锁（观察瞬间没有 FENCED 进程）。 */
        boolean observeFree() { return !held; }
    }

    /** 逐记录孤儿扫描模型：记录 r1..rn，t_i ＝ offset+i+1，t_n ＝ offset+n+1。 */
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

    // ------------------------------------------------------------------ 时序用例

    @Test void callEnteringTheGateAfterFenceIsRefusedAndNeverDelegates() {
        var trace = new Trace();
        var gate = new LocalGate(trace, true, false);

        gate.fence("before-call");

        assertThat(gate.enterEffect("c2", null)).isFalse();
        assertThat(trace.indexOfPrefix("F1@")).isLessThan(trace.indexOfPrefix("gate(c2)"));
        assertThat(trace.indexOfPrefix("gate(c2)")).isLessThan(trace.indexOfPrefix("refuse(c2)"));
        assertThat(trace.joined()).contains("gate(c2) readFenced=true").contains("refuse(c2)").doesNotContain("delegate(c2)");
    }

    @Test void inFlightCallThatReadBeforeFenceDelegatesAndIsLabelledInFlight() {
        var trace = new Trace();
        var gate = new LocalGate(trace, true, false);

        // 调用 c1 在围栏之前读到未围栏；F1 在读与委托之间发生；c1 属“在途调用”。
        boolean allowed = gate.enterEffect("c1", () -> gate.fence("inside-c1"));

        assertThat(allowed).as("在途调用（读标志发生在 F1 写之前）允许完成其委托").isTrue();
        assertThat(trace.indexOfPrefix("gate(c1) readFenced=false")).isLessThan(trace.indexOfPrefix("F1@"));
        assertThat(trace.indexOfPrefix("F1@")).isLessThan(trace.indexOfPrefix("delegate(c1)"));
        // 之后的“新进入”调用必须被拒——这才是 F1 的保证对象。
        assertThat(gate.enterEffect("c3", null)).isFalse();
        System.out.println("[G0K1-order] in-flight vs new: " + trace.joined());
    }

    @Test void recheckNarrowsTheWindowButDoesNotRemoveTheLinearizationPoint() {
        var trace = new Trace();
        var gate = new LocalGate(trace, true, true);

        // 重读把“读—委托之间”的窗口关掉：围栏发生在读之后、重读之前 ⇒ 被拒。
        assertThat(gate.enterEffect("c1", () -> gate.fence("between-read-and-recheck"))).isFalse();
        assertThat(trace.joined()).contains("refuseAfterRecheck(c1)").doesNotContain("delegate(c1)");

        // 但围栏若发生在最后一次读之后，仍属在途：证明重读只移动线性化点，不能消除它。
        var trace2 = new Trace();
        var gate2 = new LocalGate(trace2, true, true);
        assertThat(gate2.enterEffect("c2", () -> { })).isTrue();
        gate2.fence("after-final-read");
        assertThat(trace2.joined()).contains("delegate(c2)");
    }

    @Test void crossProcessObservationBeforeFenceIsNotInvalidatedForTheLaterGate() {
        var lock = new FenceLock();
        boolean firstDecision = lock.observeFree();          // t_f：无 FENCED 进程
        assertThat(firstDecision).isTrue();
        lock.acquireShared();                                 // 之后才有进程进入 FENCED（F1′ 成功）
        assertThat(lock.observeFree())
            .as("fenced.lock 的成功只能证明观察瞬间没有冲突锁；之后的门必须观察到冲突")
            .isFalse();
    }

    @Test void unconfirmedFenceLockYieldsNoCrossProcessBasisAndIsNotCalledBlocked() {
        var trace = new Trace();
        var gate = new LocalGate(trace, true, false);
        gate.fence("F1");                       // F1 生效：本机停止无条件成立
        var lock = new FenceLock();             // F1′ 未确认 ⇒ 未取得共享锁

        boolean localStop = gate.isFenced();
        boolean crossProcessBasis = !lock.observeFree();
        String label = crossProcessBasis ? "BLOCKED" : "UNCONFIRMED_NO_CROSS_PROCESS_BASIS";

        assertThat(localStop).as("本机效果停止由内存标志保证").isTrue();
        assertThat(label).isEqualTo("UNCONFIRMED_NO_CROSS_PROCESS_BASIS");
        assertThat(label).isNotEqualTo("BLOCKED");
    }

    @Test void ownerDeathBeforeItsRecordEvaluationIsDetectedAsOrphan() {
        // 记录 r1 属主在 t=1 死亡，其判定时刻 t_1=2 ⇒ 判孤儿
        var outcome = Scan.scan(2, 0, deathsAt(0, 1, 99), 1);
        assertThat(outcome.verdict()).isEqualTo("ORPHAN");
        assertThat(outcome.events()).anyMatch(event -> event.startsWith("scan1:ORPHAN(r1)"));
    }

    @Test void ownerDeathInsideWindowWIsMissedByThatScanAndCaughtByTheNext() {
        // r1 属主在 t=3 死亡：t_1=2 时仍存活（3>2），t_n=3 —— 死亡落在 W_1=(2,3] ⇒ 本次扫描仍 CLEAR
        var first = Scan.scan(2, 0, deathsAt(0, 3, 99), 1);
        assertThat(first.verdict()).as("命名缺口 W：属主在其记录判定之后、扫描结束之前死亡").isEqualTo("CLEAR");
        assertThat(first.events()).anyMatch(event -> event.startsWith("scan1:scan_end"));
        System.out.println("[G0K1-order] GAP_W: " + String.join(" | ", first.events()));

        // 后一次扫描（t_1=12）必然判孤儿
        var second = Scan.scan(2, 10, deathsAt(10, 3, 99), 2);
        assertThat(second.verdict()).isEqualTo("ORPHAN");
        assertThat(second.events()).anyMatch(event -> event.startsWith("scan2:ORPHAN(r1)"));
    }

    @Test void ownerDeathAfterScanEndIsPostDecisionAndCaughtByTheNextScan() {
        // r1 属主在 t=5 死亡，晚于本次扫描结束 t_n=3 ⇒ 本次 CLEAR（判定后类）
        var first = Scan.scan(2, 0, deathsAt(0, 5, 99), 1);
        assertThat(first.verdict()).isEqualTo("CLEAR");
        assertThat(first.events()).anyMatch(event -> event.startsWith("scan1:scan_end"));
        // 后一次扫描（t_1=12）发生在死亡之后 ⇒ ORPHAN
        var later = Scan.scan(2, 10, deathsAt(10, 5, 99), 2);
        assertThat(later.verdict()).isEqualTo("ORPHAN");
    }

    // ------------------------------------------------------------------ 负控（守卫是否承重）

    @Test void guardRemovedModelDetectsTheCounterexampleAndRestoringItRemovesIt() {
        var removedTrace = new Trace();
        var unguarded = new LocalGate(removedTrace, false, false);
        unguarded.fence("before-call");
        boolean delegated = unguarded.enterEffect("c2", null);

        assertThat(delegated).as("撤去守卫后反例可达（这正是守卫承重的证据）").isTrue();
        assertThat(unguarded.violations()).isEqualTo(1);
        assertThat(removedTrace.joined()).contains("VIOLATION(c2)").contains("delegate(c2)");
        System.out.println("[G0K1-order] negative control (guard removed): " + removedTrace.joined());

        var restoredTrace = new Trace();
        var guarded = new LocalGate(restoredTrace, true, false);
        guarded.fence("before-call");
        assertThat(guarded.enterEffect("c2", null)).isFalse();
        assertThat(guarded.violations()).isZero();
        System.out.println("[G0K1-order] restored run (guard enabled): " + restoredTrace.joined());
    }
}
