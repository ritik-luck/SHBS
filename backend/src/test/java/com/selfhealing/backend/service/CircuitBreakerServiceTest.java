package com.selfhealing.backend.service;

import com.selfhealing.backend.model.CircuitBreakerState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.ZSetOperations;

import java.time.Duration;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.context.ApplicationEventPublisher;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CircuitBreakerServiceTest {

    private AuditService auditService;
    private ConfigService configService;
    private AlertService alertService;
    private StringRedisTemplate redisTemplate;
    private ValueOperations<String, String> valueOps;
    private ZSetOperations<String, String> zSetOps;

    private CircuitBreakerService circuitBreakerService;

    @BeforeEach
    void setUp() {
        auditService = mock(AuditService.class);
        configService = mock(ConfigService.class);
        alertService = mock(AlertService.class);
        redisTemplate = mock(StringRedisTemplate.class);
        valueOps = mock(ValueOperations.class);
        zSetOps = mock(ZSetOperations.class);

        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(redisTemplate.opsForZSet()).thenReturn(zSetOps);
        
        when(configService.getInt(anyString(), anyInt())).thenAnswer(i -> i.getArgument(1));

        MeterRegistry meterRegistry = mock(MeterRegistry.class);
        Counter counter = mock(Counter.class);
        when(meterRegistry.counter(anyString())).thenReturn(counter);
        
        ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);

        circuitBreakerService = new CircuitBreakerService(
                auditService, configService, alertService, redisTemplate, meterRegistry, eventPublisher);
    }

    @Test
    void testAllowRequestWhenClosed() {
        when(valueOps.get(contains("state"))).thenReturn(CircuitBreakerState.CLOSED.name());
        when(zSetOps.count(anyString(), anyDouble(), anyDouble())).thenReturn(0L);

        assertTrue(circuitBreakerService.allowRequest("inventory-service"));
    }

    @Test
    void testAllowRequestWhenOpenAndRecoveryNotElapsed() {
        when(valueOps.get(contains("state"))).thenReturn(CircuitBreakerState.OPEN.name());
        when(valueOps.get(contains("last_failure"))).thenReturn(String.valueOf(System.currentTimeMillis()));
        when(configService.getInt(contains("cb_recovery_ms"), anyInt())).thenReturn(10000);

        assertFalse(circuitBreakerService.allowRequest("inventory-service"));
    }

    @Test
    void testAllowRequestWhenOpenAndRecoveryElapsed() {
        when(valueOps.get(contains("state"))).thenReturn(CircuitBreakerState.OPEN.name());
        when(valueOps.get(contains("last_failure"))).thenReturn(String.valueOf(System.currentTimeMillis() - 20000));
        when(configService.getInt(contains("cb_recovery_ms"), anyInt())).thenReturn(10000);

        when(redisTemplate.execute(any(org.springframework.data.redis.core.script.DefaultRedisScript.class), anyList(), any(Object[].class))).thenReturn(1L);

        assertTrue(circuitBreakerService.allowRequest("inventory-service"));
        verify(redisTemplate).execute(any(org.springframework.data.redis.core.script.DefaultRedisScript.class), anyList(), eq("OPEN"), eq("HALF_OPEN"), anyString());
    }
    
    @Test
    void testRecordFailureTripsBreaker() {
        when(valueOps.get(contains("state"))).thenReturn(CircuitBreakerState.CLOSED.name());
        when(zSetOps.count(anyString(), anyDouble(), anyDouble())).thenReturn(5L); // Threshold is 5
        
        when(redisTemplate.execute(any(org.springframework.data.redis.core.script.DefaultRedisScript.class), anyList(), any(Object[].class))).thenReturn(-5L); // Tripped breaker returns -count

        circuitBreakerService.recordFailure("inventory-service");
        
        verify(redisTemplate).execute(any(org.springframework.data.redis.core.script.DefaultRedisScript.class), anyList(), any(Object[].class));
        verify(alertService).sendAlert(anyString(), anyString(), anyString());
    }
}
