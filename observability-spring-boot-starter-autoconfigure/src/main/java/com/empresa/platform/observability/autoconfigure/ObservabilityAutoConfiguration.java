package com.empresa.platform.observability.autoconfigure;

import com.empresa.platform.observability.autoconfigure.alerting.LogAlertNotifier;
import com.empresa.platform.observability.autoconfigure.alerting.WebhookAlertNotifier;
import com.empresa.platform.observability.autoconfigure.aspect.FlowTrackingAspect;
import com.empresa.platform.observability.autoconfigure.aspect.LegLoggingAspect;
import com.empresa.platform.observability.autoconfigure.aspect.SpelObservationAspect;
import com.empresa.platform.observability.core.alerting.AlertDispatcher;
import com.empresa.platform.observability.core.alerting.AlertNotifier;
import com.empresa.platform.observability.core.alerting.AlertingProperties;
import com.empresa.platform.observability.core.leg.SpelMaskingService;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.observation.ObservationRegistry;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.client.RestTemplateBuilder;
import com.empresa.platform.observability.core.feature.FeatureEvaluationListener;
import com.empresa.platform.observability.core.feature.FlowFeatureEvaluationListener;
import org.springframework.context.annotation.Bean;

import java.util.List;

@AutoConfiguration
@ConditionalOnProperty(prefix = "observability", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(ObservabilityProperties.class)
public class ObservabilityAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    @ConfigurationProperties(prefix = "app.observability.alerting")
    public AlertingProperties alertingProperties(ObservabilityProperties properties) {
        AlertingProperties ap = properties.getAlerting();
        return ap != null ? ap : new AlertingProperties();
    }

    @Bean
    @ConditionalOnMissingBean
    public SpelMaskingService spelMaskingService(@Autowired(required = false) ObjectMapper objectMapper) {
        return new SpelMaskingService(objectMapper != null ? objectMapper : new ObjectMapper());
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "observability.alerting", name = "enabled", havingValue = "true", matchIfMissing = true)
    public AlertDispatcher alertDispatcher(
            AlertingProperties alertingProperties,
            @Autowired(required = false) List<AlertNotifier> notifiers,
            @Autowired(required = false) MeterRegistry meterRegistry
    ) {
        return new AlertDispatcher(alertingProperties, notifiers, meterRegistry);
    }

    @Bean
    @ConditionalOnMissingBean(name = "logAlertNotifier")
    @ConditionalOnProperty(prefix = "observability.alerting", name = "enabled", havingValue = "true", matchIfMissing = true)
    public LogAlertNotifier logAlertNotifier() {
        return new LogAlertNotifier();
    }

    @Bean
    @ConditionalOnMissingBean(name = "webhookAlertNotifier")
    @ConditionalOnProperty(prefix = "observability.alerting", name = "webhook-url")
    public WebhookAlertNotifier webhookAlertNotifier(
            AlertingProperties alertingProperties,
            @Autowired(required = false) RestTemplateBuilder builder
    ) {
        return new WebhookAlertNotifier(alertingProperties, builder);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "observability.flow-tracking", name = "enabled", havingValue = "true", matchIfMissing = true)
    public FlowTrackingAspect flowTrackingAspect(
            @Autowired(required = false) MeterRegistry meterRegistry,
            @Autowired(required = false) ObservationRegistry observationRegistry,
            @Autowired(required = false) AlertDispatcher alertDispatcher,
            AlertingProperties alertingProperties
    ) {
        return new FlowTrackingAspect(
                meterRegistry,
                observationRegistry != null ? observationRegistry : ObservationRegistry.NOOP,
                alertDispatcher,
                alertingProperties
        );
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "observability.spel-observation", name = "enabled", havingValue = "true", matchIfMissing = true)
    public SpelObservationAspect spelObservationAspect(@Autowired(required = false) ObservationRegistry observationRegistry) {
        return new SpelObservationAspect(observationRegistry != null ? observationRegistry : ObservationRegistry.NOOP);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "observability.leg-logging", name = "enabled", havingValue = "true", matchIfMissing = true)
    public LegLoggingAspect legLoggingAspect(
            SpelMaskingService spelMaskingService,
            @Autowired(required = false) ObjectMapper objectMapper
    ) {
        return new LegLoggingAspect(spelMaskingService, objectMapper);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "observability.observation-handler", name = "enabled", havingValue = "true", matchIfMissing = true)
    public LoggingObservationHandler loggingObservationHandler() {
        return new LoggingObservationHandler();
    }

    @Bean
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "observability.correlation", name = "enabled", havingValue = "true", matchIfMissing = true)
    public CorrelationIdFilter correlationIdFilter() {
        return new CorrelationIdFilter();
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "observability.async-decorator", name = "enabled", havingValue = "true", matchIfMissing = true)
    public com.empresa.platform.observability.autoconfigure.async.ObservabilityTaskDecorator observabilityTaskDecorator() {
        return new com.empresa.platform.observability.autoconfigure.async.ObservabilityTaskDecorator();
    }

    @Bean
    @ConditionalOnMissingBean(FeatureEvaluationListener.class)
    public FlowFeatureEvaluationListener flowFeatureEvaluationListener(
            @Autowired(required = false) ObservationRegistry observationRegistry) {
        return new FlowFeatureEvaluationListener(observationRegistry);
    }
}
