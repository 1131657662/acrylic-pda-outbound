package com.acrylic.outbound.reports;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;

class OperatorNamesTest {
    @ParameterizedTest @ValueSource(strings={"刘 颖","test_pda01","Liu Ying","张 三","O'Brien","Müller"})
    void acceptsRecordedNamesInAnyScript(String name) {
        assertTrue(OperatorNames.usable(name));
    }
    @ParameterizedTest @ValueSource(strings={""," ","   ","原人员ID:5052f470-906c-44e3-9e7a-af0e08ff93f7",
            "5052f470-906c-44e3-9e7a-af0e08ff93f7","5052F470906C44E39E7AAF0E08FF93F7"})
    void rejectsMissingAndMachineIdentifierNames(String name) {
        assertFalse(OperatorNames.usable(name));
    }
    @Test void rejectsNullName() {
        assertFalse(OperatorNames.usable(null));
    }
    @Test void rejectsNameEqualToOperatorIdOrAccount() {
        String id=UUID.randomUUID().toString();
        assertFalse(OperatorNames.usable(id,id,"test_pda01"));
        assertFalse(OperatorNames.usable(id.replace("-",""),id));
        assertTrue(OperatorNames.usable("test_pda01","01"));
    }
    @Test void rejectsAccountNameRegardlessOfCaseAndSpacing() {
        assertFalse(OperatorNames.usable("TEST_PDA01","test_pda01"));
        assertFalse(OperatorNames.usable("  test_pda01  ","test_pda01"));
    }
}
