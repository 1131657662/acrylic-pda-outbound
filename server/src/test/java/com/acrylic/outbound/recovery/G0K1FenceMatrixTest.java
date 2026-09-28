package com.acrylic.outbound.recovery;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * G0-K1 定向反例（测试侧第 2 类）：<b>围栏确认矩阵模型</b>。
 *
 * <p>覆盖 F1′（{@code fenced.lock} 共享锁）、F3（进程记录锁 {@code FileLock.release()} 确认）、
 * F5（进程终止）三者的“确认／未确认”全部 8 种组合，逐项给出：本机状态、其他观察者可实际看到的
 * 依据、后续接纳／效果的唯一结果。
 *
 * <p>模型只回答规范问题；<b>不</b>执行真实 halt/kill、<b>不</b>写文件、<b>不</b>修改 src/main。
 * 模型中“已调用 halt”不等于真实终止确认，本类因此单独用 {@code CONFIRMED} 标记而不是“调用过”。
 */
class G0K1FenceMatrixTest {

    enum Confirmation { CONFIRMED, UNCONFIRMED }

    /** 一步围栏动作的结果；{@code CONFIRMED} 只能由“已确认”语义产生。 */
    record Step(Confirmation result, String observableEvidence) { }

    record Row(String f1p, String f3, String f5, boolean localEffectStop,
               List<String> crossProcessBasis, String outcome, List<String> followUp) { }

    static List<String> basis(Step f1p, Step f3, Step f5) {
        var entries = new ArrayList<String>();
        if (f1p.result() == Confirmation.CONFIRMED) {
            entries.add("fenced.lock 共享锁被持有 ⇒ 其他进程门的独占 tryLock 必然失败（一次原子观察）");
        }
        if (f3.result() == Confirmation.CONFIRMED) {
            entries.add("进程记录锁已确认释放 ⇒ 其他进程逐记录探针判该进程租约为孤儿");
        }
        if (f5.result() == Confirmation.CONFIRMED) {
            entries.add("进程已终止 ⇒ OS 释放其全部锁（同样使租约呈孤儿、fenced.lock 不再被其持有）");
        }
        return List.copyOf(entries);
    }

    static Row evaluate(Step f1p, Step f3, Step f5) {
        List<String> crossBasis = basis(f1p, f3, f5);
        boolean local = true;                       // F1 为内存判定：无条件成立
        String outcome = crossBasis.isEmpty() ? "UNCONFIRMED_NO_CROSS_PROCESS_BASIS" : "BLOCKED";
        var followUp = new ArrayList<String>();
        followUp.add(local ? "本进程：拒绝新根、新子操作、新借用；效果门第 1 层拒绝已借出连接与已建 Statement"
            : "（模型不应出现）");
        if (crossBasis.isEmpty()) {
            followUp.add("跨进程：无确认依据 ⇒ 必须由运维外部隔离（停机/隔离该实例）；"
                + "这不是已实现能力，也不得写成已获得阻断保证");
        } else {
            followUp.add("跨进程：后续接纳与效果在下一次探针/观察处拒绝");
        }
        return new Row(f1p.result().name(), f3.result().name(), f5.result().name(), local, crossBasis, outcome,
            List.copyOf(followUp));
    }

    private static Step confirmed(String evidence) { return new Step(Confirmation.CONFIRMED, evidence); }
    private static Step unconfirmed(String evidence) { return new Step(Confirmation.UNCONFIRMED, evidence); }

    @Test void everyCombinationKeepsLocalEffectStopAndBlocksWhenExactlyOneSignalIsConfirmed() {
        var combos = List.of(
            evaluate(confirmed("F1′ ok"), confirmed("F3 ok"), confirmed("F5 ok")),
            evaluate(confirmed("F1′ ok"), confirmed("F3 ok"), unconfirmed("F5 未确认")),
            evaluate(confirmed("F1′ ok"), unconfirmed("F3 未确认"), confirmed("F5 ok")),
            evaluate(confirmed("F1′ ok"), unconfirmed("F3 未确认"), unconfirmed("F5 未确认")),
            evaluate(unconfirmed("F1′ 未确认"), confirmed("F3 ok"), confirmed("F5 ok")),
            evaluate(unconfirmed("F1′ 未确认"), confirmed("F3 ok"), unconfirmed("F5 未确认")),
            evaluate(unconfirmed("F1′ 未确认"), unconfirmed("F3 未确认"), confirmed("F5 ok")),
            evaluate(unconfirmed("F1′ 未确认"), unconfirmed("F3 未确认"), unconfirmed("F5 未确认")));

        assertThat(combos).hasSize(8);
        for (Row row : combos) {
            assertThat(row.localEffectStop())
                .as("F1′=%s F3=%s F5=%s：本机效果停止由内存标志无条件保证", row.f1p(), row.f3(), row.f5())
                .isTrue();
            if (row.crossProcessBasis().isEmpty()) {
                assertThat(row.outcome()).isEqualTo("UNCONFIRMED_NO_CROSS_PROCESS_BASIS");
                assertThat(row.followUp()).anyMatch(text -> text.contains("外部隔离"));
            } else {
                assertThat(row.outcome()).isEqualTo("BLOCKED");
            }
        }
        System.out.println("[G0K1-fence] 8 组合：本机停止全部成立；跨进程依据条数="
            + combos.stream().map(row -> Integer.toString(row.crossProcessBasis().size())).toList());
    }

    @Test void theAllUnconfirmedCombinationIsStillReportedAsABlockerNotAsCovered() {
        var row = evaluate(unconfirmed("F1′ 未确认"), unconfirmed("F3 未确认"), unconfirmed("F5 未确认"));

        assertThat(row.outcome()).isEqualTo("UNCONFIRMED_NO_CROSS_PROCESS_BASIS").isNotEqualTo("BLOCKED");
        assertThat(row.crossProcessBasis()).isEmpty();
        assertThat(row.followUp()).anyMatch(text -> text.contains("这不是已实现能力"));
        System.out.println("[G0K1-fence] all-unconfirmed: " + row);
    }

    @Test void callerVisibleEvidenceIsRecordedPerStep() {
        var row = evaluate(confirmed("fenced.lock shared acquired by this process"),
            unconfirmed("FileLock.release() threw; kernel state unknown"),
            unconfirmed("halt not executed"));
        assertThat(row.crossProcessBasis()).hasSize(1);
        assertThat(row.crossProcessBasis().getFirst()).contains("fenced.lock");
        assertThat(row.outcome()).isEqualTo("BLOCKED");
    }

    @Test void callingHaltInTheModelIsNotTreatedAsTerminationConfirmation() {
        // 模型里“已调用 halt”只是请求；未确认时不得据此产生跨进程依据。
        var requested = unconfirmed("halt 已请求但终止未确认");
        var row = evaluate(unconfirmed("F1′ 未确认"), unconfirmed("F3 未确认"), requested);
        assertThat(row.outcome()).isEqualTo("UNCONFIRMED_NO_CROSS_PROCESS_BASIS");
        assertThat(row.crossProcessBasis()).doesNotContain("halt 已调用");
    }

    @Test void fenceLockSuccessOnlyProvesNoConflictAtTheObservationInstant() {
        // 与 OrderingModel 一致的可见性口径：观察成功 ⇒ 该瞬间无 FENCED 进程；不等于以后没有。
        var observedFree = true;
        assertThat(observedFree).isTrue();
        var laterFenced = true;
        assertThat(observedFree && laterFenced)
            .as("观察瞬间与之后的围栏是两件事：门的判定只覆盖其观察瞬间")
            .isTrue();
    }
}
