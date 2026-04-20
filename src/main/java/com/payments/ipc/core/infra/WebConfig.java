package com.payments.ipc.core.infra;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@RequiredArgsConstructor
public class WebConfig implements WebMvcConfigurer {

    private final IdempotencyInterceptor idempotencyInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // We only want to protect the transfer endpoint, not health checks or GET requests
        registry.addInterceptor(idempotencyInterceptor)
                .addPathPatterns("/api/v1/transfers");
    }
}