package com.acrylic.outbound.e2averify;

import com.acrylic.outbound.common.BusinessException;
import com.acrylic.outbound.temporary.evidence.*;
import com.acrylic.outbound.temporary.protocol.E2aPageItem;
import java.util.ArrayList;
import org.springframework.context.ConfigurableApplicationContext;
import static org.assertj.core.api.Assertions.*;

/** Real stored objects/DB transactions exercise omissions that an immutable HTTP page cannot overwrite. */
final class E2aUnsubmittedNegativeChecks {
    private final E2aCrossSourceHttp http;
    private final E2aCrossSourceHttpSetup setup;
    private final SourceEvidenceIntakeBusinessFacts facts;
    private final SourceEvidenceIntakeTransactions transactions;

    E2aUnsubmittedNegativeChecks(E2aCrossSourceHttp http, E2aCrossSourceHttpSetup setup, ConfigurableApplicationContext context) {
        this.http = http; this.setup = setup;
        facts = context.getBean(SourceEvidenceIntakeBusinessFacts.class);
        transactions = context.getBean(SourceEvidenceIntakeTransactions.class);
    }

    void verify() {
        var items = new ArrayList<>(setup.items());
        var formalScan = E2aCrossSourceFixture.item("SCAN", setup.extraRecords.get(1));
        items.remove(formalScan);
        rejected(items, 11);
        rejected(setup.items(), 3);
        var missingCapture = new ArrayList<>(setup.items());
        missingCapture.remove(E2aCrossSourceFixture.item("CAPTURE", setup.extraRecords.getFirst()));
        assertThatThrownBy(() -> transactions.write(status -> facts.require(setup.fixture.binding(http.wire.openKey), missingCapture, 5, null, 11)))
            .isInstanceOf(BusinessException.class);
    }

    private void rejected(java.util.List<E2aPageItem> items, long auditThrough) {
        assertThatThrownBy(() -> transactions.write(status -> facts.require(setup.fixture.binding(http.wire.openKey), items, 5, null, auditThrough)))
            .isInstanceOfSatisfying(BusinessException.class,
                error -> assertThat(error.code()).isEqualTo("E2A_OTHER_UNSUBMITTED_UNVERIFIABLE"));
    }
}
