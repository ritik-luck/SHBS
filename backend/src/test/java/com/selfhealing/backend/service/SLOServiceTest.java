package com.selfhealing.backend.service;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class SLOServiceTest {

    private MeterRegistry meterRegistry;
    private SLOService sloService;
    private org.springframework.data.redis.core.StringRedisTemplate redisTemplate;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        redisTemplate = org.mockito.Mockito.mock(org.springframework.data.redis.core.StringRedisTemplate.class);
        org.springframework.data.redis.core.ZSetOperations zSetOperations = org.mockito.Mockito.mock(org.springframework.data.redis.core.ZSetOperations.class);
        org.mockito.Mockito.when(redisTemplate.opsForZSet()).thenReturn(zSetOperations);
        org.mockito.Mockito.when(zSetOperations.zCard(org.mockito.ArgumentMatchers.eq("slo:success:/api/orders"))).thenReturn(1L);
        org.mockito.Mockito.when(zSetOperations.zCard(org.mockito.ArgumentMatchers.eq("slo:failure:/api/orders"))).thenReturn(0L);
        
        sloService = new SLOService(meterRegistry, redisTemplate);
    }

    @Test
    void testRecordSuccessUpdatesMetrics() {
        sloService.recordSuccess("/api/orders", 15.5);
        
        Map<String, Object> metrics = sloService.getMetrics("/api/orders");
        assertEquals(1L, metrics.get("windowSize"));
        assertEquals(0L, metrics.get("failures"));
        assertEquals("100.000%", metrics.get("currentSLI"));
    }

}
