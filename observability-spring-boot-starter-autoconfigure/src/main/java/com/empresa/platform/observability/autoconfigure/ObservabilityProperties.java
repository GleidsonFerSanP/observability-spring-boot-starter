package com.empresa.platform.observability.autoconfigure;

import com.empresa.platform.observability.core.alerting.AlertingProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;

@ConfigurationProperties(prefix = "observability")
public class ObservabilityProperties {

    private boolean enabled = true;

    @NestedConfigurationProperty
    private AlertingProperties alerting = new AlertingProperties();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public AlertingProperties getAlerting() {
        return alerting;
    }

    public void setAlerting(AlertingProperties alerting) {
        this.alerting = alerting;
    }
}
