package com.empresa.platform.observability.core.flow;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class FlowContext {

    private static final ThreadLocal<Deque<FlowContext>> CURRENT_FLOW = ThreadLocal.withInitial(ArrayDeque::new);

    private final String flowName;
    private final long startNanos;
    private final Map<String, Long> stepDurations = new LinkedHashMap<>();
    private final Map<String, String> stepTypes = new LinkedHashMap<>();
    private String failedStep;
    private Throwable failureError;

    private FlowContext(String flowName) {
        this.flowName = flowName;
        this.startNanos = System.nanoTime();
    }

    public static void start(String flowName) {
        CURRENT_FLOW.get().push(new FlowContext(flowName));
    }

    public static String getCurrentFlowName() {
        Deque<FlowContext> stack = CURRENT_FLOW.get();
        return stack.isEmpty() ? "unknown" : stack.peek().flowName;
    }

    public static void recordStep(String stepName, long durationNanos) {
        recordStep(stepName, "INTERNAL", durationNanos);
    }

    public static void recordStep(String stepName, String type, long durationNanos) {
        Deque<FlowContext> stack = CURRENT_FLOW.get();
        if (!stack.isEmpty()) {
            FlowContext current = stack.peek();
            current.stepDurations.merge(stepName, durationNanos, Long::sum);
            current.stepTypes.putIfAbsent(stepName, type != null ? type : "INTERNAL");
        }
    }

    public static void recordInterruption(String stepName, Throwable t, MeterRegistry registry) {
        Deque<FlowContext> stack = CURRENT_FLOW.get();
        String currentFlow = "unknown";
        if (!stack.isEmpty()) {
            FlowContext current = stack.peek();
            current.failedStep = stepName;
            current.failureError = t;
            currentFlow = current.flowName;
        }

        String errorType = (t != null) ? t.getClass().getSimpleName() : "UnknownError";

        if (registry != null) {
            Counter.builder("flow_interruption_total")
                    .tag("flow", currentFlow)
                    .tag("failed_step", stepName)
                    .tag("error_type", errorType)
                    .description("Contador de interrupções de fluxo por etapa causadora e tipo de erro")
                    .register(registry)
                    .increment();
        }
    }

    public static boolean hasInterruption() {
        Deque<FlowContext> stack = CURRENT_FLOW.get();
        return !stack.isEmpty() && stack.peek().failedStep != null;
    }

    public static String getFailedStep() {
        Deque<FlowContext> stack = CURRENT_FLOW.get();
        return (!stack.isEmpty()) ? stack.peek().failedStep : null;
    }

    public static void complete(MeterRegistry registry) {
        Deque<FlowContext> stack = CURRENT_FLOW.get();
        if (stack.isEmpty()) {
            return;
        }

        FlowContext context = stack.pop();
        if (stack.isEmpty()) {
            CURRENT_FLOW.remove();
        }

        long totalNanos = System.nanoTime() - context.startNanos;
        long sumStepsNanos = 0;

        for (Map.Entry<String, Long> entry : context.stepDurations.entrySet()) {
            String stepName = entry.getKey();
            long stepDurationNanos = entry.getValue();
            String stepType = context.stepTypes.getOrDefault(stepName, "INTERNAL");
            sumStepsNanos += stepDurationNanos;

            if (registry != null) {
                // Legacy slice duration metric
                Timer.builder("flow_slice_duration_seconds")
                        .tag("flow", context.flowName)
                        .tag("step", stepName)
                        .description("Duração de cada fatia/subprocesso dentro do fluxo")
                        .register(registry)
                        .record(stepDurationNanos, TimeUnit.NANOSECONDS);

                // Candidate v2 work duration metric
                Timer.builder("observability.flow.component.work.duration")
                        .tag("flow", context.flowName)
                        .tag("component", stepName)
                        .tag("type", stepType)
                        .description("Duração do trabalho total do componente dentro do fluxo")
                        .register(registry)
                        .record(stepDurationNanos, TimeUnit.NANOSECONDS);
            }
        }

        if (registry != null) {
            // Legacy internal processing metric
            long internalNanos = Math.max(0, totalNanos - sumStepsNanos);
            Timer.builder("flow_slice_duration_seconds")
                    .tag("flow", context.flowName)
                    .tag("step", "Processamento Interno & Regras")
                    .description("Tempo de processamento interno e regras de negócio da aplicação")
                    .register(registry)
                    .record(internalNanos, TimeUnit.NANOSECONDS);

            // Legacy total flow metric
            Timer.builder("flow_total_duration_seconds")
                    .tag("flow", context.flowName)
                    .description("Duração total end-to-end do fluxo de entrada")
                    .register(registry)
                    .record(totalNanos, TimeUnit.NANOSECONDS);

            // Candidate v2 wall clock metric
            String status = (context.failedStep != null) ? "INTERRUPTED" : "SUCCESS";
            Timer.builder("observability.flow.duration")
                    .tag("flow", context.flowName)
                    .tag("status", status)
                    .description("Duração wall-clock do fluxo end-to-end")
                    .register(registry)
                    .record(totalNanos, TimeUnit.NANOSECONDS);

            // Candidate v2 Latency Attribution Engine
            LatencyAttributionEngine.recordAttributions(context.flowName, totalNanos, context.stepDurations, context.stepTypes, registry);
        }
    }
}
