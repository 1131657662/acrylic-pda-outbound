package com.acrylic.outbound.handover;

import com.acrylic.outbound.common.BusinessException;
import com.acrylic.outbound.submissions.SubmissionJson;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.*;

class HandoverContinuationInputTest {
    @ParameterizedTest
    @ValueSource(strings={"1", "101", "9007199254740991", "\"1\"", "\"101\""})
    void acceptsContractIntegersAndRetainedLegacyDecimalStrings(String value) {
        var input=SubmissionJson.parse("{\"nextOrderScanSeq\":"+value+"}");
        assertThat(HandoverContinuationService.nextSequence(input)).isEqualTo(Long.parseLong(value.replace("\"", "")));
    }

    @ParameterizedTest
    @ValueSource(strings={"1.5", "1.0", "true", "null", "[]", "{}", "9007199254740992", "9223372036854775808", "\"1.0\"", "\"+1\""})
    void refusesFractionsNonNumbersAndOverflow(String value) {
        assertThatThrownBy(()->HandoverContinuationService.nextSequence(
            SubmissionJson.parse("{\"nextOrderScanSeq\":"+value+"}"))).isInstanceOf(BusinessException.class);
    }
}
