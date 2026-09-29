package com.empresa.platform.observability.core.feature;

import com.empresa.platform.observability.core.flow.FlowContext;
import com.empresa.platform.observability.core.flow.FlowExecution;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import java.util.Map;

/**
 * Listener padrão da plataforma que traduz eventos de Feature Flag para Flow Dimensions,
 * tags de Observation (traces) e chaves de MDC (logs).
 */
public class FlowFeatureEvaluationListener implements FeatureEvaluationListener {

    private static final Logger log = LoggerFactory.getLogger(FlowFeatureEvaluationListener.class);
    private final ObservationRegistry observationRegistry;

    public FlowFeatureEvaluationListener() {
        this(null);
    }

    public FlowFeatureEvaluationListener(ObservationRegistry observationRegistry) {
        this.observationRegistry = observationRegistry;
    }

    @Override
    public void onFeatureEvaluated(String featureName, String variant, Map<String, String> metadata) {
        if (featureName == null || variant == null) {
            return;
        }

        // 1. Enriquecer o FlowExecution ativo com dimensões de primeira classe
        FlowExecution execution = FlowContext.getCurrentExecution();
        if (execution != null) {
            execution.setFeature(featureName);
            execution.setVariant(variant);
            if (metadata != null) {
                metadata.forEach((k, v) -> {
                    if (k != null && v != null && !"enabled".equalsIgnoreCase(k)) {
                        execution.setDimension(k, v);
                    }
                });
            }
        }

        // 2. Propagar para o contexto estruturado de logs (MDC)
        MDC.put("variant", variant);
        MDC.put("feature.name", featureName);
        MDC.put("feature.variant", variant);

        // 3. Propagar para a Observation corrente se disponível
        if (observationRegistry != null) {
            Observation currentObservation = observationRegistry.getCurrentObservation();
            if (currentObservation != null) {
                currentObservation.lowCardinalityKeyValue("variant", variant);
                currentObservation.lowCardinalityKeyValue("feature", featureName);
            }
        }

        log.debug("Feature flag evaluated: feature={}, variant={}", featureName, variant);
    }
}
