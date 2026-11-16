package com.cloudnative.ms_orders.config;

import java.time.Duration;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.common.config.TopicConfig;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

// ms-orders is the producer, so it owns the topology of the topics it writes to.
@Configuration
public class KafkaTopicsConfig {
    public static final String ORDERS_EVENTS = "orders.events";
    public static final String AUDIT_TIMELINE = "audit.timeline";

    @Bean
    public NewTopic ordersEventsTopic() {
        return TopicBuilder.name(ORDERS_EVENTS).partitions(3)
                .config(TopicConfig.CLEANUP_POLICY_CONFIG, TopicConfig.CLEANUP_POLICY_DELETE)
                .config(TopicConfig.RETENTION_MS_CONFIG, String.valueOf(Duration.ofDays(7).toMillis()))
                .build();
    }

    // Records are keyed by event id, so compaction never collapses two different events.
    @Bean
    public NewTopic auditTimelineTopic() {
        return TopicBuilder.name(AUDIT_TIMELINE).partitions(3)
                .config(TopicConfig.CLEANUP_POLICY_CONFIG, "compact,delete")
                .config(TopicConfig.RETENTION_MS_CONFIG, String.valueOf(Duration.ofDays(30).toMillis()))
                .build();
    }
}
