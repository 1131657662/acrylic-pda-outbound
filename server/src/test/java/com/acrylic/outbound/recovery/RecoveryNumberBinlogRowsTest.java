package com.acrylic.outbound.recovery;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import static org.assertj.core.api.Assertions.*;

class RecoveryNumberBinlogRowsTest {
    @Test void ignoresOnlyUndeclaredBitmapPaddingBitsUsedByRealMysql() {
        var f=new RecoveryNumberBinlogFixture(); f.columnsBitmap=255; f.nullBitmap=252;
        f.insert(0).update(0,1);
        assertThat(RecoveryNumberBinlogRows.replay(f.request(),Map.of()).counters()).containsExactlyEntriesOf(Map.of(f.date,1));
    }
    @Test void retainsUnreferencedPriorAllocationsAcrossGlobalHistory() {
        var f=new RecoveryNumberBinlogFixture(); f.update(40,41).update(41,42);
        var previousDay=f.date.minusDays(1);
        var result=RecoveryNumberBinlogRows.replay(f.request(),Map.of(f.date,40,previousDay,99));
        assertThat(result.counters()).containsExactlyInAnyOrderEntriesOf(Map.of(f.date,42,previousDay,99));
        assertThat(result.allocations()).isEqualTo(2);
    }

    @Test void acceptsNewDateOnlyFromZeroThenSequentialAllocations() {
        var f=new RecoveryNumberBinlogFixture(); f.insert(0).update(0,1).update(1,2);
        var result=RecoveryNumberBinlogRows.replay(f.request(),Map.of());
        assertThat(result.counters()).containsExactlyEntriesOf(Map.of(f.date,2)); assertThat(result.allocations()).isEqualTo(2);
    }

    @ParameterizedTest @CsvSource({"41,42","40,40","40,39","40,42","40,-1"})
    void rejectsNoncontinuousAllocationImages(int before,int after) {
        var f=new RecoveryNumberBinlogFixture(); f.update(before,after);
        assertThatThrownBy(()->RecoveryNumberBinlogRows.replay(f.request(),Map.of(f.date,40))).isInstanceOf(RecoveryControlException.class);
    }

    @Test void rejectsUpdateWithoutItsOriginalCounter() {
        var f=new RecoveryNumberBinlogFixture(); f.update(0,1);
        assertThatThrownBy(()->RecoveryNumberBinlogRows.replay(f.request(),Map.of())).hasMessage("RECOVERY_NUMBER_BINLOG_DISCONTINUITY");
    }

    @Test void rejectsDeleteEvenWhenEndpointCouldHideIt() {
        var f=new RecoveryNumberBinlogFixture(); f.event(32,0,40,f.date);
        assertThatThrownBy(()->RecoveryNumberBinlogRows.replay(f.request(),Map.of(f.date,40))).hasMessage("RECOVERY_NUMBER_BINLOG_DELETE");
    }

    @Test void rejectsMovingCounterToAnotherDate() {
        var f=new RecoveryNumberBinlogFixture(); f.event(31,40,41,f.date.plusDays(1));
        assertThatThrownBy(()->RecoveryNumberBinlogRows.replay(f.request(),Map.of(f.date,40))).hasMessage("RECOVERY_NUMBER_BINLOG_DISCONTINUITY");
    }

    @Test void rejectsNonzeroNewCounter() {
        var f=new RecoveryNumberBinlogFixture(); f.insert(1);
        assertThatThrownBy(()->RecoveryNumberBinlogRows.replay(f.request(),Map.of())).hasMessage("RECOVERY_NUMBER_BINLOG_INSERT");
    }

    @Test void rejectsDuplicateDateInsertion() {
        var f=new RecoveryNumberBinlogFixture(); f.insert(0);
        assertThatThrownBy(()->RecoveryNumberBinlogRows.replay(f.request(),Map.of(f.date,0))).hasMessage("RECOVERY_NUMBER_BINLOG_INSERT");
    }

    @ParameterizedTest @CsvSource({"layout","bitmap","null","truncated"})
    void rejectsUnsupportedOrIncompleteRowImages(String corruption) {
        var f=new RecoveryNumberBinlogFixture();
        switch(corruption) {
            case "layout" -> f.dateType=3;
            case "bitmap" -> f.columnsBitmap=1;
            case "null" -> f.nullBitmap=1;
            case "truncated" -> f.truncate=true;
            default -> throw new AssertionError();
        }
        f.update(40,41);
        assertThatThrownBy(()->RecoveryNumberBinlogRows.replay(f.request(),Map.of(f.date,40))).isInstanceOf(RecoveryControlException.class);
    }
}
