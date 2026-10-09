package se.eplatform.ops.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import se.eplatform.ops.domain.SystemEvent;
import se.eplatform.ops.repository.SystemEventRepository;

import java.util.Map;

/**
 * Writes the technical system log shown to IT/drift. Callers must not pass
 * personal data (names, e-mail, answers, IP addresses) in messages or details.
 */
@Service
public class SystemEventService {

    private static final Logger log = LoggerFactory.getLogger(SystemEventService.class);

    private final SystemEventRepository repository;

    public SystemEventService(SystemEventRepository repository) {
        this.repository = repository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(SystemEvent.Level level, String source, String message, Map<String, Object> details) {
        try {
            repository.save(new SystemEvent(level, source, message, details));
        } catch (Exception e) {
            log.error("Could not write system event {} {}", source, message, e);
        }
    }

    public void info(String source, String message, Map<String, Object> details) {
        record(SystemEvent.Level.INFO, source, message, details);
    }

    public void warn(String source, String message, Map<String, Object> details) {
        record(SystemEvent.Level.WARN, source, message, details);
    }

    public void error(String source, String message, Map<String, Object> details) {
        record(SystemEvent.Level.ERROR, source, message, details);
    }
}
