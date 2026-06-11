package io.vigil.ingest.config;

import io.vigil.common.kafka.Topics;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

/**
 * Declares the raw events topic. Spring Boot's KafkaAdmin picks up every
 * NewTopic bean and creates it on the broker at startup if it does not exist,
 * so a fresh docker compose stack bootstraps its own topics.
 */
@Configuration
public class KafkaTopicConfig {

    @Bean
    public NewTopic eventsRawTopic() {
        // Three partitions allow parallel consumers later; replication factor 1
        // because the dev stack runs a single broker.
        return TopicBuilder.name(Topics.EVENTS_RAW)
                .partitions(3)
                .replicas(1)
                .build();
    }
}
