package io.vigil.detection.config;

import io.vigil.common.kafka.Topics;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

/**
 * Declares the alerts topic this service produces to. KafkaAdmin creates it
 * at startup if the broker does not have it yet.
 */
@Configuration
public class KafkaTopicConfig {

    @Bean
    public NewTopic alertsTopic() {
        return TopicBuilder.name(Topics.ALERTS)
                .partitions(3)
                .replicas(1)
                .build();
    }
}
