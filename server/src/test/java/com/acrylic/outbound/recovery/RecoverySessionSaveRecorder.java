package com.acrylic.outbound.recovery;

import java.lang.reflect.Proxy;
import java.util.Objects;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.session.SessionRepository;

/**
 * Wraps the framework session repository so the run can observe the moment a session is saved: the
 * saving thread must be inside a live operation root then, because the save's own JDBC access goes
 * through the primary (root-requiring) data source. Test-side only; the application wiring is
 * untouched and no production bean reads this class.
 */
final class RecoverySessionSaveRecorder implements BeanPostProcessor {
    private final ObjectProvider<RecoveryOperationRegistry> operations;
    private final RecoveryRootObservation observation;

    RecoverySessionSaveRecorder(ObjectProvider<RecoveryOperationRegistry> operations, RecoveryRootObservation observation) {
        this.operations = Objects.requireNonNull(operations);
        this.observation = Objects.requireNonNull(observation);
    }

    @Override public Object postProcessAfterInitialization(Object bean, String name) {
        if (!"sessionRepository".equals(name) || !(bean instanceof SessionRepository<?>)) return bean;
        return Proxy.newProxyInstance(SessionRepository.class.getClassLoader(), new Class<?>[]{SessionRepository.class},
            (proxy, method, args) -> {
                if (method.getName().equals("save")) observe();
                return method.invoke(bean, args);
            });
    }

    private void observe() {
        RecoveryOperationRegistry registry = operations.getIfAvailable();
        if (registry == null) { observation.save(new RecoveryRootObservation.Save("NO_REGISTRY", -1)); return; }
        RecoveryOperationToken token = registry.bound(Thread.currentThread());
        boolean live = token != null && registry.live(token) != null;
        observation.save(new RecoveryRootObservation.Save(live ? "BOUND" : "UNBOUND", registry.liveRoots()));
    }
}
