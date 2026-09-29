package com.empresa.platform.observability.core.flow;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Motor de Atribuição de Latência (Candidate Architecture v2 - Seções 4, 9, 10, 11, 12, 37, 38).
 * Garante as três dimensões temporais fundamentais do Flow:
 * 1. Wall-Clock Duration (tempo percebido externamente: observability.flow.duration)
 * 2. Work Duration (soma de todo trabalho executado: observability.flow.component.work.duration)
 * 3. Attributed Duration (latência matematicamente atribuída: observability.flow.component.attributed.duration)
 * Além das métricas analíticas:
 * - observability.flow.parallel.overlap.duration
 * - observability.flow.unattributed.duration
 */
public class LatencyAttributionEngine {

    public static void recordAttributions(
            String flowName,
            long wallClockNanos,
            Map<String, Long> stepDurations,
            Map<String, String> stepTypes,
            MeterRegistry registry
    ) {
        if (registry == null) {
            return;
        }

        long sumWorkNanos = 0;
        for (Long d : stepDurations.values()) {
            sumWorkNanos += d;
        }

        if (sumWorkNanos <= wallClockNanos) {
            // Cenário sequencial: a atribuição direta é idêntica à duração do trabalho
            for (Map.Entry<String, Long> entry : stepDurations.entrySet()) {
                String component = entry.getKey();
                long workDuration = entry.getValue();
                String type = stepTypes.getOrDefault(component, "INTERNAL");

                recordAttributedMetric(registry, flowName, component, type, workDuration);
            }

            long unattributedNanos = Math.max(0, wallClockNanos - sumWorkNanos);
            recordAttributedMetric(registry, flowName, "Internal & Framework", "INTERNAL", unattributedNanos);

            Timer.builder("observability.flow.unattributed.duration")
                    .tag("flow", flowName)
                    .description("Duração de latência ainda não explicada por subprocessos instrumentados no fluxo")
                    .register(registry)
                    .record(unattributedNanos, TimeUnit.NANOSECONDS);

        } else {
            // Cenário com paralelismo ou sobreposição temporal (Work > Wall-Clock):
            // Aplica normalização proporcional de contribuição para manter a invariante do gráfico de composição:
            // Σ attributed component duration ≈ flow wall-clock duration
            for (Map.Entry<String, Long> entry : stepDurations.entrySet()) {
                String component = entry.getKey();
                long workDuration = entry.getValue();
                String type = stepTypes.getOrDefault(component, "INTERNAL");

                double proportion = (double) workDuration / sumWorkNanos;
                long attributedNanos = Math.round(wallClockNanos * proportion);

                recordAttributedMetric(registry, flowName, component, type, attributedNanos);
            }

            long overlapNanos = sumWorkNanos - wallClockNanos;
            Timer.builder("observability.flow.parallel.overlap.duration")
                    .tag("flow", flowName)
                    .description("Duração de sobreposição de subprocessos paralelos no fluxo")
                    .register(registry)
                    .record(overlapNanos, TimeUnit.NANOSECONDS);
        }
    }

    private static void recordAttributedMetric(
            MeterRegistry registry,
            String flowName,
            String component,
            String type,
            long durationNanos
    ) {
        Timer.builder("observability.flow.component.attributed.duration")
                .tag("flow", flowName)
                .tag("component", component)
                .tag("type", type)
                .description("Duração de latência atribuída ao componente no gráfico de composição do fluxo")
                .register(registry)
                .record(durationNanos, TimeUnit.NANOSECONDS);
    }
}
