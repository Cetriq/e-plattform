package se.eplatform;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

// No form/basic login: users authenticate with bearer tokens only, so Spring's
// default in-memory user (with a generated password in the log) is not needed
@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
@EnableCaching
@EnableAsync
@EnableScheduling
public class EplatformApplication {

    public static void main(String[] args) {
        SpringApplication.run(EplatformApplication.class, args);
    }
}
