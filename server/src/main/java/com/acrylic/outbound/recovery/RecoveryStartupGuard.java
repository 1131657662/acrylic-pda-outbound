package com.acrylic.outbound.recovery;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.core.env.Environment;
import org.springframework.util.ClassUtils;
import static com.acrylic.outbound.recovery.RecoveryControlFormat.require;

/**
 * The startup guard of the admitted entry. It runs before any application bean exists and enforces
 * the two things a startup must not get wrong:
 *
 * <ol>
 *   <li>the framework's own session-expiry writer is disabled. That writer runs on its own thread
 *       outside every operation root, so an admitted instance that leaves it enabled would have an
 *       uncovered database writer: startup fails with {@code CONTROL_SESSION_CLEANUP_UNCOVERED}
 *       instead of running with one.</li>
 *   <li>every application bean is created after the initialization window exists
 *       ({@link RecoveryInitializationWindow}), so a startup consumer that legitimately reads the
 *       database — for example the recovery fence caching {@code server_state} — does so inside an
 *       explicit {@code INITIALIZATION} root instead of outside every root. The ordering is made
 *       explicit here rather than left to bean declaration order.</li>
 * </ol>
 *
 * <p>It deliberately does not touch infrastructure beans: their startup queries are covered by the
 * same disabled-cleanup rule, and touching them would risk ordering cycles around the very
 * configuration properties the window depends on.
 */
public final class RecoveryStartupGuard implements BeanFactoryPostProcessor {
    /** The framework property that schedules {@code cleanUpExpiredSessions()}. */
    static final String CLEANUP_PROPERTY = "spring.session.jdbc.cleanup-cron";
    private static final String CLEANUP_DISABLED = "-";
    private static final String APPLICATION_PACKAGE = "com.acrylic.outbound.";
    private static final String WINDOW = "recoveryInitializationWindow";
    private static final Set<String> EXCLUDED = Set.of(WINDOW, "recoveryOperationRegistry",
        "recoveryAdmissionConfiguration", "recoveryStartupGuard");

    private final Environment environment;

    public RecoveryStartupGuard(Environment environment) {
        this.environment = Objects.requireNonNull(environment);
    }

    /** The property value the guard accepted; observability for the startup transcript. */
    String cleanupCron() { return environment.getProperty(CLEANUP_PROPERTY); }

    @Override public void postProcessBeanFactory(ConfigurableListableBeanFactory factory) {
        require(CLEANUP_DISABLED.equals(environment.getProperty(CLEANUP_PROPERTY)), "CONTROL_SESSION_CLEANUP_UNCOVERED");
        require(factory.containsBeanDefinition(WINDOW), "CONTROL_STARTUP_WINDOW_MISSING");
        for (String name : factory.getBeanDefinitionNames()) {
            BeanDefinition definition = factory.getBeanDefinition(name);
            // Configuration properties are what the window itself is built from: ordering them after
            // it would be a cycle, and they never touch the database.
            if (name.startsWith("outbound.") || EXCLUDED.contains(name) || !applicationBean(factory, definition)) continue;
            definition.setDependsOn(merged(definition.getDependsOn()));
        }
    }

    private String[] merged(String[] existing) {
        if (existing == null || existing.length == 0) return new String[]{WINDOW};
        List<String> names = new ArrayList<>(List.of(existing));
        if (!names.contains(WINDOW)) names.add(WINDOW);
        return names.toArray(String[]::new);
    }

    /** Whether this definition is one of the application's own beans rather than framework wiring. */
    private boolean applicationBean(ConfigurableListableBeanFactory factory, BeanDefinition definition) {
        String name = definition.getBeanClassName();
        if (name == null && definition.getFactoryBeanName() != null) {
            BeanDefinition factoryDefinition = factory.getBeanDefinition(definition.getFactoryBeanName());
            name = factoryDefinition == null ? null : factoryDefinition.getBeanClassName();
        }
        if (name == null || !name.startsWith(APPLICATION_PACKAGE)) return false;
        try {
            Class<?> type = ClassUtils.forName(name, getClass().getClassLoader());
            return !BeanFactoryPostProcessor.class.isAssignableFrom(type)
                && !org.springframework.beans.factory.config.BeanPostProcessor.class.isAssignableFrom(type)
                && !type.isAnnotationPresent(org.springframework.boot.context.properties.ConfigurationProperties.class);
        } catch (ClassNotFoundException | LinkageError unavailable) {
            return false;
        }
    }
}
