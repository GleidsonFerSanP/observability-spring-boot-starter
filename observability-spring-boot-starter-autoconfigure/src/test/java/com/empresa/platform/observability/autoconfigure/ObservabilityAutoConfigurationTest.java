package com.empresa.platform.observability.autoconfigure;

import com.empresa.platform.observability.autoconfigure.aspect.FlowTrackingAspect;
import com.empresa.platform.observability.autoconfigure.aspect.LegLoggingAspect;
import com.empresa.platform.observability.autoconfigure.aspect.SpelObservationAspect;
import com.empresa.platform.observability.autoconfigure.async.ObservabilityTaskDecorator;
import com.empresa.platform.observability.autoconfigure.CorrelationIdFilter;
import com.empresa.platform.observability.core.alerting.AlertDispatcher;
import com.empresa.platform.observability.core.leg.SpelMaskingService;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.observation.ObservationRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

class ObservabilityAutoConfigurationTest {

    private final org.springframework.boot.test.context.runner.WebApplicationContextRunner contextRunner = new org.springframework.boot.test.context.runner.WebApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ObservabilityAutoConfiguration.class))
            .withUserConfiguration(TestConfig.class);

    @Configuration
    static class TestConfig {
        @Bean
        MeterRegistry meterRegistry() {
            return new SimpleMeterRegistry();
        }

        @Bean
        ObservationRegistry observationRegistry() {
            return ObservationRegistry.create();
        }
    }

    @Test
    @DisplayName("Auto-Configuration ativa por padrão e instancia todos os componentes de observabilidade")
    void shouldRegisterObservabilityBeansByDefault() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(FlowTrackingAspect.class);
            assertThat(context).hasSingleBean(LegLoggingAspect.class);
            assertThat(context).hasSingleBean(SpelObservationAspect.class);
            assertThat(context).hasSingleBean(ObservabilityTaskDecorator.class);
            assertThat(context).hasSingleBean(CorrelationIdFilter.class);
            assertThat(context).hasSingleBean(AlertDispatcher.class);
            assertThat(context).hasSingleBean(SpelMaskingService.class);
        });
    }

    @Test
    @DisplayName("Auto-Configuration respeita flag observability.enabled=false desabilitando componentes")
    void shouldBackOffWhenObservabilityDisabled() {
        contextRunner.withPropertyValues("observability.enabled=false")
                .run(context -> {
                    assertThat(context).doesNotHaveBean(FlowTrackingAspect.class);
                    assertThat(context).doesNotHaveBean(LegLoggingAspect.class);
                    assertThat(context).doesNotHaveBean(SpelObservationAspect.class);
                    assertThat(context).doesNotHaveBean(ObservabilityTaskDecorator.class);
                    assertThat(context).doesNotHaveBean(CorrelationIdFilter.class);
                });
    }
}
