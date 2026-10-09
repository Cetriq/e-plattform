package se.eplatform.ops.service;

import org.springframework.core.env.Environment;

/**
 * The deployed version: the Git commit on Vercel, otherwise "lokal".
 */
public final class AppVersion {

    private AppVersion() {}

    public static String of(Environment environment) {
        String sha = environment.getProperty("VERCEL_GIT_COMMIT_SHA", "");
        return sha.isBlank() ? "lokal" : sha.substring(0, Math.min(7, sha.length()));
    }
}
