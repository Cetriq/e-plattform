package se.eplatform.common.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import se.eplatform.audit.web.AuditInterceptor;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final AuditInterceptor auditInterceptor;

    public WebConfig(AuditInterceptor auditInterceptor) {
        this.auditInterceptor = auditInterceptor;
    }

    @Bean
    public ObjectMapper objectMapper(Jackson2ObjectMapperBuilder builder) {
        return builder
                .modules(new JavaTimeModule())
                .featuresToDisable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .build();
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(auditInterceptor).addPathPatterns("/api/**");
    }
}
