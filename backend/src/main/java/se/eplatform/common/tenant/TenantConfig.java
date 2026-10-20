package se.eplatform.common.tenant;

import jakarta.persistence.EntityManagerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.orm.jpa.JpaTransactionManager;

@Configuration
public class TenantConfig {

    /** Replaces Spring Boot's transaction manager, so every transaction carries the context. */
    @Bean
    public JpaTransactionManager transactionManager(EntityManagerFactory entityManagerFactory) {
        return new TenantContextBinder(entityManagerFactory);
    }
}
