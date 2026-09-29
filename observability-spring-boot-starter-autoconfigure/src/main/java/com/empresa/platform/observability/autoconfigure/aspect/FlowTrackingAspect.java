package com.empresa.platform.observability.autoconfigure.aspect;

import com.empresa.platform.observability.core.alerting.AlertDispatcher;
import com.empresa.platform.observability.core.alerting.AlertEvent;
import com.empresa.platform.observability.core.alerting.AlertSeverity;
import com.empresa.platform.observability.core.alerting.AlertType;
import com.empresa.platform.observability.core.alerting.AlertingProperties;
import com.empresa.platform.observability.core.annotation.TrackFlow;
import com.empresa.platform.observability.core.annotation.TrackStep;
import com.empresa.platform.observability.core.engine.FlowScope;
import com.empresa.platform.observability.core.engine.MicrometerObservabilityEngine;
import com.empresa.platform.observability.core.engine.ObservabilityEngine;
import com.empresa.platform.observability.core.engine.StepScope;
import com.empresa.platform.observability.core.flow.FlowContext;
import com.empresa.platform.observability.core.flow.FlowExecution;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.observation.ObservationRegistry;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.MDC;
import org.springframework.core.annotation.Order;

import java.util.Map;
import java.util.concurrent.TimeUnit;

@Aspect
@Order(1)
public class FlowTrackingAspect {

    private final ObservabilityEngine observabilityEngine;
    private final MeterRegistry meterRegistry;
    private final ObservationRegistry observationRegistry;
    private final AlertDispatcher alertDispatcher;
    private final AlertingProperties alertingProperties;

    public FlowTrackingAspect(MeterRegistry meterRegistry,
                              ObservationRegistry observationRegistry,
                              AlertDispatcher alertDispatcher,
                              AlertingProperties alertingProperties) {
        this(new MicrometerObservabilityEngine(observationRegistry, meterRegistry),
                meterRegistry, observationRegistry, alertDispatcher, alertingProperties);
    }

    public FlowTrackingAspect(ObservabilityEngine observabilityEngine,
                              MeterRegistry meterRegistry,
                              ObservationRegistry observationRegistry,
                              AlertDispatcher alertDispatcher,
                              AlertingProperties alertingProperties) {
        this.observabilityEngine = observabilityEngine != null
                ? observabilityEngine
                : new MicrometerObservabilityEngine(observationRegistry, meterRegistry);
        this.meterRegistry = meterRegistry;
        this.observationRegistry = observationRegistry != null ? observationRegistry : ObservationRegistry.NOOP;
        this.alertDispatcher = alertDispatcher;
        this.alertingProperties = alertingProperties != null ? alertingProperties : new AlertingProperties();
    }

    @Around("@annotation(trackFlow)")
    public Object trackFlowExecution(ProceedingJoinPoint joinPoint, TrackFlow trackFlow) throws Throwable {
        long startNanos = System.nanoTime();
        String flowName = trackFlow.value();
        if (flowName == null || flowName.isBlank()) {
            flowName = joinPoint.getSignature().toShortString();
        }

        FlowContext.start(flowName);
        FlowScope flowScope = observabilityEngine.startFlow(flowName, trackFlow.type(), FlowContext.getCurrentDimensions());

        try {
            Object result = joinPoint.proceed();
            if (FlowContext.hasInterruption()) {
                flowScope.markDegraded(FlowContext.getFailedStep());
            } else {
                flowScope.markSuccess();
            }
            return result;
        } catch (Throwable t) {
            observabilityEngine.recordFlowInterruption(flowName, FlowContext.getFailedStep(), t, FlowContext.getCurrentDimensions(), flowScope);
            throw t;
        } finally {
            FlowExecution execution = FlowContext.getCurrentExecution();
            observabilityEngine.completeFlow(execution, flowScope);

            MDC.remove("variant");
            MDC.remove("feature.name");
            MDC.remove("feature.variant");

            long totalDurationMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos);
            long flowThresholdMs = alertingProperties.getThresholdForFlow(flowName);

            if (alertDispatcher != null && totalDurationMs > flowThresholdMs) {
                alertDispatcher.dispatch(AlertEvent.of(
                        AlertType.FLOW_LATENCY_SLA_BREACH,
                        AlertSeverity.CRITICAL,
                        "FlowEntrypoint",
                        flowName,
                        String.format("Duração total do fluxo '%s' (%d ms) violou o SLA end-to-end de %d ms.",
                                flowName, totalDurationMs, flowThresholdMs),
                        totalDurationMs,
                        flowThresholdMs,
                        Map.of("flow", flowName, "durationMs", totalDurationMs, "thresholdMs", flowThresholdMs)
                ));
            }
        }
    }

    @Around("@annotation(trackStep)")
    public Object trackStepExecution(ProceedingJoinPoint joinPoint, TrackStep trackStep) throws Throwable {
        long startNanos = System.nanoTime();
        String stepName = trackStep.value();
        if (stepName == null || stepName.isBlank()) {
            stepName = joinPoint.getSignature().toShortString();
        }
        String currentFlow = FlowContext.getCurrentFlowName();

        StepScope stepScope = observabilityEngine.startStep(currentFlow, stepName, trackStep.type(), FlowContext.getCurrentDimensions());

        try {
            return joinPoint.proceed();
        } catch (Throwable t) {
            observabilityEngine.recordStepInterruption(currentFlow, stepName, t, FlowContext.getCurrentDimensions(), stepScope);

            if (alertDispatcher != null) {
                alertDispatcher.dispatch(AlertEvent.of(
                        AlertType.FLOW_STEP_INTERRUPTION,
                        AlertSeverity.CRITICAL,
                        "FlowStep",
                        stepName,
                        String.format("Fluxo '%s' interrompido no step '%s' devido a %s: %s",
                                currentFlow, stepName, t.getClass().getSimpleName(), t.getMessage()),
                        1.0,
                        0.0,
                        Map.of(
                                "flow", currentFlow,
                                "step", stepName,
                                "errorType", t.getClass().getSimpleName(),
                                "errorMessage", t.getMessage() != null ? t.getMessage() : "null"
                        )
                ));
            }

            throw t;
        } finally {
            long duration = System.nanoTime() - startNanos;
            observabilityEngine.completeStep(currentFlow, stepName, trackStep.type(), duration, FlowContext.getCurrentDimensions(), stepScope);

            long stepDurationMs = TimeUnit.NANOSECONDS.toMillis(duration);
            long stepThresholdMs = alertingProperties.getThresholdForStep(stepName);

            if (alertDispatcher != null && stepDurationMs > stepThresholdMs) {
                alertDispatcher.dispatch(AlertEvent.of(
                        AlertType.INTEGRATION_LATENCY_SLA_BREACH,
                        AlertSeverity.WARNING,
                        "FlowStep",
                        stepName,
                        String.format("Latência do subprocesso '%s' (%d ms) excedeu o SLA estipulado de %d ms.",
                                stepName, stepDurationMs, stepThresholdMs),
                        stepDurationMs,
                        stepThresholdMs,
                        Map.of("step", stepName, "durationMs", stepDurationMs, "thresholdMs", stepThresholdMs)
                ));
            }
        }
    }
}
