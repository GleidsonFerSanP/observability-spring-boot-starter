package com.empresa.platform.observability.autoconfigure;

import com.empresa.platform.observability.core.alerting.AlertingProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;

@ConfigurationProperties(prefix = "observability")
public class ObservabilityProperties {

    /**
     * Master switch for the corporate observability starter. Default is true.
     */
    private boolean enabled = true;

    private boolean flowTrackingEnabled = true;
    private boolean legLoggingEnabled = true;
    private boolean spelObservationEnabled = true;
    private boolean correlationEnabled = true;
    private boolean alertingEnabled = true;
    private boolean asyncDecoratorEnabled = true;
    private boolean observationHandlerEnabled = true;
    private boolean feignEnabled = true;
    private boolean resilienceEnabled = true;
    private boolean jdbcEnabled = true;

    @NestedConfigurationProperty
    private AlertingProperties alerting = new AlertingProperties();

    /**
     * Engine de observabilidade ativa (micrometer | datadog | opentelemetry). Padrão é micrometer.
     */
    private String engine = "micrometer";

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getEngine() {
        return engine;
    }

    public void setEngine(String engine) {
        this.engine = engine;
    }

    public boolean isFlowTrackingEnabled() {
        return flowTrackingEnabled;
    }

    public void setFlowTrackingEnabled(boolean flowTrackingEnabled) {
        this.flowTrackingEnabled = flowTrackingEnabled;
    }

    public boolean isLegLoggingEnabled() {
        return legLoggingEnabled;
    }

    public void setLegLoggingEnabled(boolean legLoggingEnabled) {
        this.legLoggingEnabled = legLoggingEnabled;
    }

    public boolean isSpelObservationEnabled() {
        return spelObservationEnabled;
    }

    public void setSpelObservationEnabled(boolean spelObservationEnabled) {
        this.spelObservationEnabled = spelObservationEnabled;
    }

    public boolean isCorrelationEnabled() {
        return correlationEnabled;
    }

    public void setCorrelationEnabled(boolean correlationEnabled) {
        this.correlationEnabled = correlationEnabled;
    }

    public boolean isAlertingEnabled() {
        return alertingEnabled;
    }

    public void setAlertingEnabled(boolean alertingEnabled) {
        this.alertingEnabled = alertingEnabled;
    }

    public boolean isAsyncDecoratorEnabled() {
        return asyncDecoratorEnabled;
    }

    public void setAsyncDecoratorEnabled(boolean asyncDecoratorEnabled) {
        this.asyncDecoratorEnabled = asyncDecoratorEnabled;
    }

    public boolean isObservationHandlerEnabled() {
        return observationHandlerEnabled;
    }

    public void setObservationHandlerEnabled(boolean observationHandlerEnabled) {
        this.observationHandlerEnabled = observationHandlerEnabled;
    }

    public boolean isFeignEnabled() {
        return feignEnabled;
    }

    public void setFeignEnabled(boolean feignEnabled) {
        this.feignEnabled = feignEnabled;
    }

    public boolean isResilienceEnabled() {
        return resilienceEnabled;
    }

    public void setResilienceEnabled(boolean resilienceEnabled) {
        this.resilienceEnabled = resilienceEnabled;
    }

    public boolean isJdbcEnabled() {
        return jdbcEnabled;
    }

    public void setJdbcEnabled(boolean jdbcEnabled) {
        this.jdbcEnabled = jdbcEnabled;
    }

    public AlertingProperties getAlerting() {
        return alerting;
    }

    public void setAlerting(AlertingProperties alerting) {
        this.alerting = alerting;
    }
}
