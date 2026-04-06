package se.eplatform.statistics.service;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import se.eplatform.audit.domain.AuditEvent;
import se.eplatform.audit.repository.AuditEventRepository;
import se.eplatform.cases.domain.Case;
import se.eplatform.cases.repository.CaseRepository;
import se.eplatform.flow.repository.FlowRepository;
import se.eplatform.statistics.api.StatisticsController.*;
import se.eplatform.user.repository.UserRepository;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class StatisticsService {

    private final CaseRepository caseRepository;
    private final FlowRepository flowRepository;
    private final UserRepository userRepository;
    private final AuditEventRepository auditRepository;

    public StatisticsService(
            CaseRepository caseRepository,
            FlowRepository flowRepository,
            UserRepository userRepository,
            AuditEventRepository auditRepository) {
        this.caseRepository = caseRepository;
        this.flowRepository = flowRepository;
        this.userRepository = userRepository;
        this.auditRepository = auditRepository;
    }

    public OverviewStats getOverviewStats() {
        long totalCases = caseRepository.count();
        long pendingCases = caseRepository.countByStatusName("SUBMITTED") +
                           caseRepository.countByStatusName("IN_PROGRESS");
        long completedCases = caseRepository.countByStatusName("COMPLETED");

        long totalFlows = flowRepository.count();
        long publishedFlows = flowRepository.countByStatus(se.eplatform.flow.domain.FlowStatus.PUBLISHED);

        long totalUsers = userRepository.count();

        // Cases this month
        LocalDate startOfMonth = LocalDate.now().withDayOfMonth(1);
        Instant monthStart = startOfMonth.atStartOfDay(ZoneId.systemDefault()).toInstant();
        long casesThisMonth = caseRepository.countByCreatedAtAfter(monthStart);

        // Average processing time (simplified - would need more complex query in production)
        double avgProcessingDays = calculateAverageProcessingDays();

        return new OverviewStats(
                totalCases,
                pendingCases,
                completedCases,
                totalFlows,
                publishedFlows,
                totalUsers,
                casesThisMonth,
                avgProcessingDays
        );
    }

    public List<StatusCount> getCasesByStatus() {
        List<Object[]> results = caseRepository.countGroupByStatus();
        return results.stream()
                .map(row -> new StatusCount((String) row[0], (Long) row[1]))
                .collect(Collectors.toList());
    }

    public List<FlowCount> getCasesByFlow() {
        List<Object[]> results = caseRepository.countGroupByFlow();
        return results.stream()
                .map(row -> new FlowCount(
                        row[0].toString(),
                        (String) row[1],
                        (Long) row[2]))
                .limit(10)
                .collect(Collectors.toList());
    }

    public List<TimelineEntry> getCaseTimeline(int days) {
        LocalDate endDate = LocalDate.now();
        LocalDate startDate = endDate.minusDays(days);

        Map<LocalDate, Long> submissions = new HashMap<>();
        Map<LocalDate, Long> completions = new HashMap<>();

        // Initialize all dates with 0
        for (LocalDate date = startDate; !date.isAfter(endDate); date = date.plusDays(1)) {
            submissions.put(date, 0L);
            completions.put(date, 0L);
        }

        // Get submission counts
        Instant startInstant = startDate.atStartOfDay(ZoneId.systemDefault()).toInstant();
        List<Case> cases = caseRepository.findByCreatedAtAfter(startInstant);

        for (Case c : cases) {
            LocalDate createdDate = c.getCreatedAt()
                    .atZone(ZoneId.systemDefault())
                    .toLocalDate();
            if (!createdDate.isBefore(startDate) && !createdDate.isAfter(endDate)) {
                submissions.merge(createdDate, 1L, Long::sum);
            }

            if (c.getCompletedAt() != null) {
                LocalDate completedDate = c.getCompletedAt()
                        .atZone(ZoneId.systemDefault())
                        .toLocalDate();
                if (!completedDate.isBefore(startDate) && !completedDate.isAfter(endDate)) {
                    completions.merge(completedDate, 1L, Long::sum);
                }
            }
        }

        List<TimelineEntry> timeline = new ArrayList<>();
        for (LocalDate date = startDate; !date.isAfter(endDate); date = date.plusDays(1)) {
            timeline.add(new TimelineEntry(
                    date,
                    submissions.getOrDefault(date, 0L),
                    completions.getOrDefault(date, 0L)
            ));
        }

        return timeline;
    }

    public List<AuditSummary> getRecentAuditEvents(int limit) {
        var page = auditRepository.findAll(PageRequest.of(0, limit,
                org.springframework.data.domain.Sort.by(
                        org.springframework.data.domain.Sort.Direction.DESC, "timestamp")));

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
                .withZone(ZoneId.systemDefault());

        return page.getContent().stream()
                .map(event -> new AuditSummary(
                        formatter.format(event.getTimestamp()),
                        event.getAction().name(),
                        event.getUserId(),
                        event.getUserName(),
                        event.getEntityType(),
                        truncate(event.getDetails(), 100)
                ))
                .collect(Collectors.toList());
    }

    public List<ActionCount> getAuditActionCounts(int days) {
        Instant since = Instant.now().minus(days, ChronoUnit.DAYS);
        List<Object[]> results = auditRepository.countActionsSince(since);

        return results.stream()
                .map(row -> new ActionCount(row[0].toString(), (Long) row[1]))
                .sorted((a, b) -> Long.compare(b.count(), a.count()))
                .limit(10)
                .collect(Collectors.toList());
    }

    private double calculateAverageProcessingDays() {
        // Simplified - in production you'd want a proper query
        List<Case> completedCases = caseRepository.findByStatusName("COMPLETED");
        if (completedCases.isEmpty()) {
            return 0.0;
        }

        double totalDays = 0;
        int count = 0;

        for (Case c : completedCases) {
            if (c.getCompletedAt() != null && c.getCreatedAt() != null) {
                long days = ChronoUnit.DAYS.between(c.getCreatedAt(), c.getCompletedAt());
                totalDays += days;
                count++;
            }
        }

        return count > 0 ? totalDays / count : 0.0;
    }

    private String truncate(String value, int maxLength) {
        if (value == null) return null;
        return value.length() > maxLength ? value.substring(0, maxLength) + "..." : value;
    }
}
