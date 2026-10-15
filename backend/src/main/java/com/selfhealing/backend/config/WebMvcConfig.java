package com.selfhealing.backend.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * WebMvcConfig — Registers the RequestMetricsInterceptor for all endpoints.
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    private final RequestMetricsInterceptor requestMetricsInterceptor;

    public WebMvcConfig(RequestMetricsInterceptor requestMetricsInterceptor) {
        this.requestMetricsInterceptor = requestMetricsInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(requestMetricsInterceptor)
                .addPathPatterns("/**")
                .excludePathPatterns("/actuator/**", "/ws/**");
    }
}
