package se.eplatform.ops.api;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.distribution.HistogramSnapshot;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;
import se.eplatform.ops.domain.SystemEvent;
import se.eplatform.ops.repository.SystemEventRepository;
import se.eplatform.ops.service.AppVersion;
import se.eplatform.ops.service.StartupRecorder;
import se.eplatform.storage.service.FileStorageService;

import java.lang.management.ManagementFactory;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * For IT/drift (OPERATIONS): health of the system and the technical log.
 * Nothing here contains personal data.
 */
@RestController
@RequestMapping("/api/v1/ops")
@Tag(name = "IT & drift", description = "Kräver rollen OPERATIONS. Innehåller inga personuppgifter.")
public class OperationsController {

    private final JdbcTemplate jdbc;
    private final SystemEventRepository systemEvents;
    private final StartupRecorder startup;
    private final FileStorageService fileStorage;
    private final MeterRegistry meters;
    private final Environment environment;
    private final boolean emailEnabled;
    private final String mailHost;

    public OperationsController(JdbcTemplate jdbc, SystemEventRepository systemEvents, StartupRecorder startup,
                                FileStorageService fileStorage, MeterRegistry meters, Environment environment,
                                @Value("${eplatform.email.enabled:false}") boolean emailEnabled,
                                @Value("${spring.mail.host:}") String mailHost) {
        this.jdbc = jdbc;
        this.systemEvents = systemEvents;
        this.startup = startup;
        this.fileStorage = fileStorage;
        this.meters = meters;
        this.environment = environment;
        this.emailEnabled = emailEnabled;
        this.mailHost = mailHost;
    }

    public record Component(String name, String status, String detail) {}

    public record Status(
            String overall,
            String version,
            String profiles,
            Instant instanceStartedAt,
            long uptimeSeconds,
            long heapUsedMb,
            long heapMaxMb,
            List<Component> components,
            RequestStats requests,
            long errorsLast24h,
            List<SystemEventDTO> lastJobRuns,
            List<SystemEventDTO> recentStartups) {}

    public record RequestStats(long count, long serverErrors, double meanMs, double p95Ms) {}

    public record SystemEventDTO(UUID id, Instant timestamp, SystemEvent.Level level, String source,
                                 String message, Map<String, Object> details) {
        static SystemEventDTO from(SystemEvent e) {
            return new SystemEventDTO(e.getId(), e.getTimestamp(), e.getLevel(), e.getSource(), e.getMessage(), e.getDetails());
        }
    }

    @Operation(summary = "Driftstatus")
    @GetMapping("/status")
    public Status status() {
        List<Component> components = new ArrayList<>();
        components.add(database());
        components.add(new Component("Fillagring", "UP",
                fileStorage.isExternalStorage() ? "Vercel Blob (privat)" : "MinIO/S3"));
        components.add(new Component("E-post", emailEnabled ? "UP" : "DISABLED",
                emailEnabled ? "SMTP " + mailHost : "Utskick avstängda"));

        String overall = components.stream().anyMatch(c -> "DOWN".equals(c.status())) ? "DOWN" : "UP";
        Runtime rt = Runtime.getRuntime();
        Instant since = Instant.now().minus(Duration.ofHours(24));

        return new Status(
                overall,
                AppVersion.of(environment),
                String.join(",", environment.getActiveProfiles()),
                startup.startedAt(),
                ManagementFactory.getRuntimeMXBean().getUptime() / 1000,
                (rt.totalMemory() - rt.freeMemory()) / (1024 * 1024),
                rt.maxMemory() / (1024 * 1024),
                components,
                requestStats(),
                systemEvents.countByLevelAndTimestampAfter(SystemEvent.Level.ERROR, since),
                systemEvents.latestPerSource("job:").stream().map(SystemEventDTO::from).toList(),
                systemEvents.search(null, "app", PageRequest.of(0, 5)).map(SystemEventDTO::from).getContent());
    }

    @Operation(summary = "Systemlogg", description = "Tekniska händelser: fel, jobbkörningar, starter.")
    @GetMapping("/events")
    public Page<SystemEventDTO> events(
            @RequestParam(required = false) SystemEvent.Level level,
            @RequestParam(defaultValue = "") String source,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        return systemEvents.search(level, source, PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 200)))
                .map(SystemEventDTO::from);
    }

    private Component database() {
        long start = System.nanoTime();
        try {
            String version = jdbc.queryForObject(
                    "SELECT version FROM flyway_schema_history WHERE success ORDER BY installed_rank DESC LIMIT 1",
                    String.class);
            long ms = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
            return new Component("Databas", "UP", "Svarstid " + ms + " ms, schemaversion V" + version);
        } catch (Exception e) {
            return new Component("Databas", "DOWN", e.getClass().getSimpleName());
        }
    }

    /** API requests handled by this instance since it started. */
    private RequestStats requestStats() {
        long count = 0;
        long errors = 0;
        double totalMs = 0;
        double p95 = 0;
        for (Timer timer : meters.find("http.server.requests").timers()) {
            long c = timer.count();
            count += c;
            totalMs += timer.totalTime(TimeUnit.MILLISECONDS);
            String status = timer.getId().getTag("status");
            if (status != null && status.startsWith("5")) {
                errors += c;
            }
            HistogramSnapshot snapshot = timer.takeSnapshot();
            for (var v : snapshot.percentileValues()) {
                if (v.percentile() == 0.95) p95 = Math.max(p95, v.value(TimeUnit.MILLISECONDS));
            }
        }
        return new RequestStats(count, errors, count == 0 ? 0 : totalMs / count, p95);
    }
}
