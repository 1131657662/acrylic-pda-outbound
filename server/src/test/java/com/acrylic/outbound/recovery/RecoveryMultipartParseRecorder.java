package com.acrylic.outbound.recovery;

import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.stream.Stream;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.web.multipart.MultipartResolver;

/**
 * Wraps the servlet multipart resolver so the run can observe the parse itself: the container stages
 * multipart parts while the request is being dispatched, and that staging must therefore happen
 * inside the request's operation root. Test-side only; the application wiring is untouched.
 */
final class RecoveryMultipartParseRecorder implements BeanPostProcessor {
    private final ObjectProvider<RecoveryOperationRegistry> operations;
    private final RecoveryRootObservation observation;
    private final Path location;

    RecoveryMultipartParseRecorder(ObjectProvider<RecoveryOperationRegistry> operations,
            RecoveryRootObservation observation, Path location) {
        this.operations = Objects.requireNonNull(operations);
        this.observation = Objects.requireNonNull(observation);
        this.location = location;
    }

    @Override public Object postProcessAfterInitialization(Object bean, String name) {
        if (!"multipartResolver".equals(name) || !(bean instanceof MultipartResolver)) return bean;
        return Proxy.newProxyInstance(MultipartResolver.class.getClassLoader(), new Class<?>[]{MultipartResolver.class},
            (proxy, method, args) -> {
                try {
                    return method.invoke(bean, args);
                } finally {
                    if (method.getName().equals("resolveMultipart")) observe();
                }
            });
    }

    private void observe() {
        RecoveryOperationRegistry registry = operations.getIfAvailable();
        boolean bound = false;
        if (registry != null) {
            RecoveryOperationToken token = registry.bound(Thread.currentThread());
            bound = token != null && registry.live(token) != null;
        }
        observation.parse(new RecoveryRootObservation.Parse(bound ? "BOUND" : "UNBOUND", stagedFiles()));
    }

    private int stagedFiles() {
        if (location == null || !Files.isDirectory(location)) return -1;
        try (Stream<Path> entries = Files.list(location)) {
            return (int) entries.count();
        } catch (java.io.IOException error) {
            return -1;
        }
    }
}
