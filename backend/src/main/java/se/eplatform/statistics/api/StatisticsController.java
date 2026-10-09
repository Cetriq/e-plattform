package se.eplatform.statistics.api;

import org.springframework.web.bind.annotation.*;
import se.eplatform.statistics.service.StatisticsService;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/admin/statistics")
public class StatisticsController {

    private final StatisticsService statisticsService;

    public StatisticsController(StatisticsService statisticsService) {
        this.statisticsService = statisticsService;
    }

    /**
     * Get overview statistics for the dashboard.
     */
    @GetMapping("/overview")
    public OverviewStats getOverview() {
        return statisticsService.getOverviewStats();
    }

    /**
     * Get case statistics grouped by status.
     */
    @GetMapping("/cases/by-status")
    public List<StatusCount> getCasesByStatus() {
        return statisticsService.getCasesByStatus();
    }

    /**
     * Get case statistics grouped by flow.
     */
    @GetMapping("/cases/by-flow")
    public List<FlowCount> getCasesByFlow() {
        return statisticsService.getCasesByFlow();
    }

    /**
     * Get case submissions over time.
     */
    @GetMapping("/cases/timeline")
    public List<TimelineEntry> getCaseTimeline(
            @RequestParam(defaultValue = "30") int days) {
        return statisticsService.getCaseTimeline(days);
    }

    // DTOs

    public record OverviewStats(
            long totalCases,
            long pendingCases,
            long completedCases,
            long totalFlows,
            long publishedFlows,
            long totalUsers,
            long casesThisMonth,
            double avgProcessingDays
    ) {}

    public record StatusCount(String status, long count) {}

    public record FlowCount(String flowId, String flowName, long count) {}

    public record TimelineEntry(LocalDate date, long submissions, long completions) {}
}
