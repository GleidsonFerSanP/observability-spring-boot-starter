package com.empresa.platform.observability.autoconfigure;

import com.empresa.platform.observability.autoconfigure.alerting.LogAlertNotifier;
import com.empresa.platform.observability.autoconfigure.alerting.WebhookAlertNotifier;
import com.empresa.platform.observability.autoconfigure.aspect.FlowTrackingAspect;
import com.empresa.platform.observability.autoconfigure.aspect.LegLoggingAspect;
import com.empresa.platform.observability.autoconfigure.aspect.SpelObservationAspect;
import com.empresa.platform.observability.core.alerting.AlertDispatcher;
import com.empresa.platform.observability.core.alerting.AlertNotifier;
import com.empresa.platform.observability.core.leg.SpelMaskingService;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.observation.ObservationRegistry;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;

import java.util.List;

@AutoConfiguration
@ConditionalOnProperty(prefix = "observability", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(ObservabilityProperties.class)
public class ObservabilityAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public SpelMaskingService spelMaskingService(@Autowired(required = false) ObjectMapper objectMapper) {
        return new SpelMaskingService(objectMapper != null ? objectMapper : new ObjectMapper());
    }

    @Bean
    @ConditionalOnMissingBean
    public AlertDispatcher alertDispatcher(
            ObservabilityProperties properties,
            @Autowired(required = false) List<AlertNotifier> notifiers,
            @Autowired(required = false) MeterRegistry meterRegistry
    ) {
        return new AlertDispatcher(properties.getAlerting(), notifiers, meterRegistry);
    }

    @Bean
    @ConditionalOnMissingBean(name = "logAlertNotifier")
    public LogAlertNotifier logAlertNotifier() {
        return new LogAlertNotifier();
    }

    @Bean
    @ConditionalOnMissingBean(name = "webhookAlertNotifier")
    @ConditionalOnProperty(prefix = "observability.alerting", name = "webhook-url")
    public WebhookAlertNotifier webhookAlertNotifier(
            ObservabilityProperties properties,
            @Autowired(required = false) RestTemplateBuilder builder
    ) {
        return new WebhookAlertNotifier(properties.getAlerting(), builder);
    }

    @Bean
    @ConditionalOnMissingBean
    public FlowTrackingAspect flowTrackingAspect(
            @Autowired(required = false) MeterRegistry meterRegistry,
            @Autowired(required = false) ObservationRegistry observationRegistry,
            @Autowired(required = false) AlertDispatcher alertDispatcher,
            ObservabilityProperties properties
    ) {
        return new FlowTrackingAspect(
                meterRegistry,
                observationRegistry != null ? observationRegistry : ObservationRegistry.NOOP,
                alertDispatcher,
                properties.getAlerting()
        );
    }

    @Bean
    @ConditionalOnMissingBean
    public SpelObservationAspect spelObservationAspect(@Autowired(required = false) ObservationRegistry observationRegistry) {
        return new SpelObservationAspect(observationRegistry != null ? observationRegistry : ObservationRegistry.NOOP);
    }

    @Bean
    @ConditionalOnMissingBean
    public LegLoggingAspect legLoggingAspect(
            SpelMaskingService spelMaskingService,
            @Autowired(required = false) ObjectMapper objectMapper
    ) {
        return new LegLoggingAspect(spelMaskingService, objectMapper);
    }

    @Bean
    @ConditionalOnMissingBean
    public LoggingObservationHandler loggingObservationHandler() {
        return new LoggingObservationHandler();
    }

    @Bean
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    @ConditionalOnMissingBean
    public CorrelationIdFilter correlationIdFilter() {
        return new CorrelationIdFilter();
    }
}
