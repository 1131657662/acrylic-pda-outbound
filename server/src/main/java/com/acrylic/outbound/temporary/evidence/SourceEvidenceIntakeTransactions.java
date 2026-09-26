package com.acrylic.outbound.temporary.evidence;

import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

@Component
public class SourceEvidenceIntakeTransactions {
    private final TransactionTemplate read;
    private final TransactionTemplate write;

    public SourceEvidenceIntakeTransactions(PlatformTransactionManager manager) {
        read=template(manager,true);write=template(manager,false);
    }

    public <T> T read(TransactionCallback<T> callback) { return read.execute(callback); }
    public <T> T write(TransactionCallback<T> callback) { return write.execute(callback); }

    private TransactionTemplate template(PlatformTransactionManager manager,boolean readOnly) {
        var template=new TransactionTemplate(manager);
        template.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRED);
        template.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        template.setReadOnly(readOnly);return template;
    }
}
