package se.eplatform.auth.api;

import io.swagger.v3.oas.annotations.Hidden;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import se.eplatform.auth.service.DemoCleanupJob;
import se.eplatform.privacy.service.RetentionService;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Map;

/**
 * Scheduled jobs triggered from outside, for hosts where the backend is not
 * kept running (Vercel Cron calls these with "Authorization: Bearer CRON_SECRET").
 */
@Hidden
@RestController
@RequestMapping("/api/v1/internal/cron")
public class CronController {

    private final DemoCleanupJob demoCleanupJob;
    private final RetentionService retentionService;
    private final byte[] cronSecret;

    public CronController(DemoCleanupJob demoCleanupJob, RetentionService retentionService,
                          @Value("${eplatform.cron.secret:}") String cronSecret) {
        this.demoCleanupJob = demoCleanupJob;
        this.retentionService = retentionService;
        this.cronSecret = cronSecret.getBytes(StandardCharsets.UTF_8);
    }

    @GetMapping("/demo-cleanup")
    public Map<String, String> demoCleanup(@RequestHeader(value = "Authorization", required = false) String authorization) {
        requireCronSecret(authorization);
        demoCleanupJob.removeExpiredDemoUsers();
        return Map.of("status", "ok");
    }

    @GetMapping("/retention")
    public RetentionService.Result retention(@RequestHeader(value = "Authorization", required = false) String authorization) {
        requireCronSecret(authorization);
        return retentionService.run();
    }

    private void requireCronSecret(String authorization) {
        byte[] given = authorization == null ? new byte[0] : authorization.getBytes(StandardCharsets.UTF_8);
        byte[] expected = ("Bearer " + new String(cronSecret, StandardCharsets.UTF_8)).getBytes(StandardCharsets.UTF_8);
        // No secret configured means the endpoint is disabled
        if (cronSecret.length == 0 || !MessageDigest.isEqual(given, expected)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Ogiltig cron-nyckel");
        }
    }
}
