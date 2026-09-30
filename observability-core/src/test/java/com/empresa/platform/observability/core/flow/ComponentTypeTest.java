package com.empresa.platform.observability.core.flow;

import com.empresa.platform.observability.core.annotation.ComponentType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ComponentTypeTest {

    @Test
    @DisplayName("Garante classificação de integração e localidade")
    void testIntegrationAndLocalClassification() {
        assertThat(ComponentType.HTTP.isIntegration()).isTrue();
        assertThat(ComponentType.FEIGN.isIntegration()).isTrue();
        assertThat(ComponentType.GRPC.isIntegration()).isTrue();
        assertThat(ComponentType.DATABASE.isIntegration()).isTrue();
        assertThat(ComponentType.CACHE.isIntegration()).isTrue();
        assertThat(ComponentType.KAFKA.isIntegration()).isTrue();
        assertThat(ComponentType.SQS.isIntegration()).isTrue();

        assertThat(ComponentType.BUSINESS.isLocal()).isTrue();
        assertThat(ComponentType.INTERNAL.isLocal()).isTrue();
        assertThat(ComponentType.EXECUTOR.isLocal()).isTrue();
        assertThat(ComponentType.BUSINESS.isIntegration()).isFalse();
    }

    @Test
    @DisplayName("Garante classificação de mensageria e resiliência")
    void testMessagingAndResilienceClassification() {
        assertThat(ComponentType.KAFKA_PRODUCER.isMessaging()).isTrue();
        assertThat(ComponentType.KAFKA_CONSUMER.isMessaging()).isTrue();
        assertThat(ComponentType.SQS_PRODUCER.isMessaging()).isTrue();
        assertThat(ComponentType.SQS_CONSUMER.isMessaging()).isTrue();
        assertThat(ComponentType.SNS.isMessaging()).isTrue();
        assertThat(ComponentType.JMS.isMessaging()).isTrue();

        assertThat(ComponentType.CIRCUIT_BREAKER.isResilience()).isTrue();
        assertThat(ComponentType.RETRY.isResilience()).isTrue();
        assertThat(ComponentType.BULKHEAD.isResilience()).isTrue();
        assertThat(ComponentType.RATE_LIMITER.isResilience()).isTrue();
    }

    @Test
    @DisplayName("Garante mapeamento de categorias de alto nível")
    void testCategoryMapping() {
        assertThat(ComponentType.HTTP.getCategory()).isEqualTo("HTTP");
        assertThat(ComponentType.FEIGN.getCategory()).isEqualTo("HTTP");
        assertThat(ComponentType.GRPC.getCategory()).isEqualTo("HTTP");
        assertThat(ComponentType.DATABASE.getCategory()).isEqualTo("DATABASE");
        assertThat(ComponentType.CACHE.getCategory()).isEqualTo("CACHE");
        assertThat(ComponentType.KAFKA_PRODUCER.getCategory()).isEqualTo("MESSAGING");
        assertThat(ComponentType.CIRCUIT_BREAKER.getCategory()).isEqualTo("RESILIENCE");
        assertThat(ComponentType.BUSINESS.getCategory()).isEqualTo("BUSINESS");
        assertThat(ComponentType.INTERNAL.getCategory()).isEqualTo("INTERNAL");
        assertThat(ComponentType.CUSTOM.getCategory()).isEqualTo("CUSTOM");
    }
}
