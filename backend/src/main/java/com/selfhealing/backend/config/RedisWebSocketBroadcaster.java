package com.selfhealing.backend.config;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.data.redis.listener.adapter.MessageListenerAdapter;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;

/**
 * RedisWebSocketBroadcaster — Cross-replica WebSocket message relay via Redis Pub/Sub.
 *
 * Problem solved: Spring's enableSimpleBroker("/topic") is JVM-local.
 * An alert published by Replica 2 never reaches WebSocket clients connected to Replica 1.
 *
 * Solution: Every WebSocket message is published to a Redis Pub/Sub channel.
 * All replicas subscribe to the same channel and forward incoming messages
 * to their local STOMP subscribers. A replicaId field prevents echo loops
 * (a replica ignores messages it published itself).
 */
@Component
public class RedisWebSocketBroadcaster implements MessageListener {

    private static final Logger log = LoggerFactory.getLogger(RedisWebSocketBroadcaster.class);
    private static final String CHANNEL = "shbs:ws:broadcast";

    // Unique ID for this JVM instance — prevents echo loops
    private final String replicaId = UUID.randomUUID().toString().substring(0, 8);

    private final StringRedisTemplate redis;
    private final SimpMessagingTemplate messagingTemplate;
    private final RedisMessageListenerContainer listenerContainer;
    private final ObjectMapper objectMapper;

    public RedisWebSocketBroadcaster(StringRedisTemplate redis,
                                      SimpMessagingTemplate messagingTemplate,
                                      RedisMessageListenerContainer listenerContainer,
                                      ObjectMapper objectMapper) {
        this.redis = redis;
        this.messagingTemplate = messagingTemplate;
        this.listenerContainer = listenerContainer;
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    public void subscribe() {
        listenerContainer.addMessageListener(this, new ChannelTopic(CHANNEL));
        log.info("WebSocket broadcaster subscribed to Redis channel '{}' (replicaId={})", CHANNEL, replicaId);
    }

    /**
     * Broadcast a message to all replicas via Redis Pub/Sub.
     * Every replica (including this one) will receive it and forward to local STOMP subscribers.
     *
     * @param destination STOMP destination, e.g. "/topic/alerts"
     * @param payload     the message payload (will be JSON-serialized)
     */
    public void broadcast(String destination, Object payload) {
        try {
            BroadcastEnvelope envelope = new BroadcastEnvelope(replicaId, destination, payload);
            String json = objectMapper.writeValueAsString(envelope);
            redis.convertAndSend(CHANNEL, json);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize broadcast message: {}", e.getMessage());
            // Fallback: send locally only
            messagingTemplate.convertAndSend(destination, payload);
        } catch (Exception e) {
            log.warn("Redis unavailable for broadcast, sending locally only: {}", e.getMessage());
            messagingTemplate.convertAndSend(destination, payload);
        }
    }

    /**
     * Receive a message from Redis Pub/Sub channel.
     * Called on all replicas (including the publisher).
     */
    @Override
    public void onMessage(Message message, byte[] pattern) {
        try {
            String json = new String(message.getBody(), StandardCharsets.UTF_8);
            BroadcastEnvelope envelope = objectMapper.readValue(json, BroadcastEnvelope.class);

            // Skip messages published by this replica (prevent echo)
            if (replicaId.equals(envelope.replicaId())) {
                // Still deliver locally since we didn't send via messagingTemplate yet
                messagingTemplate.convertAndSend(envelope.destination(), envelope.payload());
                return;
            }

            // Forward to local STOMP subscribers
            messagingTemplate.convertAndSend(envelope.destination(), envelope.payload());
            log.debug("Relayed WebSocket message from replica '{}' to '{}'", envelope.replicaId(), envelope.destination());

        } catch (Exception e) {
            log.error("Failed to process Redis broadcast message: {}", e.getMessage());
        }
    }

    /**
     * Envelope wrapping every broadcast message with origin metadata.
     */
    public record BroadcastEnvelope(String replicaId, String destination, Object payload) {}
}
