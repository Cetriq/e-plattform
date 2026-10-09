package se.eplatform.ops.domain;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * A technical event for IT/drift: errors, job runs, startups. Contains no
 * personal data.
 */
@Entity
@Table(name = "system_events")
public class SystemEvent {

    public enum Level { INFO, WARN, ERROR }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private Instant timestamp = Instant.now();

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Level level;

    @Column(nullable = false, length = 100)
    private String source;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String message;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> details = new HashMap<>();

    protected SystemEvent() {}

    public SystemEvent(Level level, String source, String message, Map<String, Object> details) {
        this.level = level;
        this.source = source;
        this.message = message;
        if (details != null) {
            this.details = new HashMap<>(details);
        }
    }

    public UUID getId() { return id; }
    public Instant getTimestamp() { return timestamp; }
    public Level getLevel() { return level; }
    public String getSource() { return source; }
    public String getMessage() { return message; }
    public Map<String, Object> getDetails() { return details; }
}
