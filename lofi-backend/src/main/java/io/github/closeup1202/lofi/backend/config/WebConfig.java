package io.github.closeup1202.lofi.backend.config;

import io.github.closeup1202.lofi.backend.api.security.LofiAuthInterceptor;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Value("${lofi.backend.api-key:}")
    private String apiKey;

    @PostConstruct
    void validate() {
        if (!StringUtils.hasText(apiKey)) {
            throw new IllegalStateException(
                    "lofi.backend.api-key is required. Set it via application.yml or LOFI_API_KEY env var. " +
                            "See README 'Backend Mode' section for migration steps.");
        }
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new LofiAuthInterceptor(apiKey))
                .addPathPatterns("/lofi/ingest");
    }
}
