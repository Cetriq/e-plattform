package se.eplatform.ops.service;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.lang.management.ManagementFactory;
import java.time.Instant;
import java.util.Map;

/**
 * Records each start of a backend instance, with how long it took. On hosts
 * that stop idle instances this shows how often users meet a cold start.
 */
@Component
public class StartupRecorder {

    private final SystemEventService systemEvents;
    private final Environment environment;
    private final Instant startedAt = Instant.now();

    public StartupRecorder(SystemEventService systemEvents, Environment environment) {
        this.systemEvents = systemEvents;
        this.environment = environment;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onReady() {
        long startupMs = ManagementFactory.getRuntimeMXBean().getUptime();
        systemEvents.info("app", "Instans startad på " + startupMs + " ms",
                Map.of("startupMs", startupMs,
                       "profiles", String.join(",", environment.getActiveProfiles()),
                       "version", AppVersion.of(environment)));
    }

    public Instant startedAt() {
        return startedAt;
    }
}
