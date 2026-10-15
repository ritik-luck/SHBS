package com.selfhealing.backend;

import com.selfhealing.backend.model.CircuitBreakerState;
import com.selfhealing.backend.service.CircuitBreakerService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.ToxiproxyContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import com.redis.testcontainers.RedisContainer;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * ChaosIntegrationTest — Tests fail-open behavior when Redis becomes unresponsive
 * using Toxiproxy to simulate network partitions.
 */
@SpringBootTest
@Testcontainers
class ChaosIntegrationTest {

    private static final Network network = Network.newNetwork();

    @Container
    static final MySQLContainer<?> mySQLContainer = new MySQLContainer<>(DockerImageName.parse("mysql:8.0"))
            .withDatabaseName("shbs_test")
            .withUsername("test")
            .withPassword("test")
            .withNetwork(network);

    @Container
    static final RedisContainer redisContainer = new RedisContainer(DockerImageName.parse("redis:7.2.4-alpine"))
            .withNetwork(network)
            .withNetworkAliases("redis");

    @Container
    static final ToxiproxyContainer toxiproxy = new ToxiproxyContainer("ghcr.io/shopify/toxiproxy:2.5.0")
            .withNetwork(network);

    static ToxiproxyContainer.ContainerProxy redisProxy;

    @DynamicPropertySource
    static void registerDynamicProperties(DynamicPropertyRegistry registry) {
        // MySQL Properties
        registry.add("spring.datasource.url", mySQLContainer::getJdbcUrl);
        registry.add("spring.datasource.username", mySQLContainer::getUsername);
        registry.add("spring.datasource.password", mySQLContainer::getPassword);
        registry.add("spring.datasource.driver-class-name", mySQLContainer::getDriverClassName);
        registry.add("spring.flyway.url", mySQLContainer::getJdbcUrl);
        registry.add("spring.flyway.user", mySQLContainer::getUsername);
        registry.add("spring.flyway.password", mySQLContainer::getPassword);

        // Map Redis through Toxiproxy
        redisProxy = toxiproxy.getProxy(redisContainer, 6379);
        registry.add("spring.data.redis.host", redisProxy::getContainerIpAddress);
        registry.add("spring.data.redis.port", redisProxy::getProxyPort);
        
        // Use a short timeout so tests don't hang for long when simulating chaos
        registry.add("spring.data.redis.timeout", () -> "100ms");
    }

    @Autowired
    private CircuitBreakerService circuitBreakerService;

    @Test
    void testFailOpenWhenRedisIsDown() throws IOException {
        String testService = "chaos-service";

        // Initial state should be CLOSED (healthy)
        assertEquals(CircuitBreakerState.CLOSED, circuitBreakerService.getState(testService));

        // Inject Chaos: Cut the connection completely
        redisProxy.setConnectionCut(true);

        // Attempting to read state should fail-open and return CLOSED gracefully
        assertEquals(CircuitBreakerState.CLOSED, circuitBreakerService.getState(testService), 
                "Should fail-open and return CLOSED when Redis is unreachable");

        // Attempting to write failures should fail gracefully
        circuitBreakerService.recordFailure(testService);

        // Remove Chaos: Restore connection
        redisProxy.setConnectionCut(false);

        // Should be able to read normally again
        assertEquals(CircuitBreakerState.CLOSED, circuitBreakerService.getState(testService));
    }
}
