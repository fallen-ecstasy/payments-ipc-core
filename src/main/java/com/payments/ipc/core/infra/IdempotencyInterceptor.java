package com.payments.ipc.core.infra;

import com.payments.ipc.core.exception.ConcurrentRequestException;
import com.payments.ipc.core.exception.IdempotencyException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.time.Duration;

@Component
@RequiredArgsConstructor
public class IdempotencyInterceptor implements HandlerInterceptor {

    // Spring Boot auto-configures this because we added the Redis dependency
    private final StringRedisTemplate redisTemplate;

    @Override
    public boolean preHandle(HttpServletRequest request, @NonNull HttpServletResponse response, @NonNull Object handler) {
        // Only apply to POST requests (modifying state)
        if (!"POST".equalsIgnoreCase(request.getMethod())) {
            return true;
        }

        String idempotencyKey = request.getHeader("Idempotency-Key");
        if (idempotencyKey == null || idempotencyKey.trim().isEmpty()) {
            throw new IdempotencyException("Missing 'Idempotency-Key' header.");
        }

        String cacheKey = "idemp:" + idempotencyKey;

        // ATOMIC OPERATION: Try to set the key with a status of "IN_PROGRESS".
        // TTL (Time To Live) is 24 hours.
        Boolean isNewRequest = redisTemplate.opsForValue()
                .setIfAbsent(cacheKey, "IN_PROGRESS", Duration.ofHours(24));

        if (Boolean.FALSE.equals(isNewRequest)) {
            // The key already exists. What state is it in?
            String currentStatus = redisTemplate.opsForValue().get(cacheKey);

            if ("COMPLETED".equals(currentStatus)) {
                // In a true production system, you would fetch and return the cached JSON response here.
                // For this project, rejecting it with a clear message is sufficient proof of concept.
                throw new IdempotencyException("Request already processed for key: " + idempotencyKey);
            } else {
                // It's "IN_PROGRESS". The user double-clicked the submit button!
                throw new ConcurrentRequestException("Request is currently processing. Please wait.");
            }
        }

        // If we reach here, it's a brand new, valid request. Proceed to the Controller.
        return true;
    }
}