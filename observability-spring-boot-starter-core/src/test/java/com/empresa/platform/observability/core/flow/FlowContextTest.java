package com.empresa.platform.observability.core.flow;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class FlowContextTest {

    private MeterRegistry meterRegistry;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        FlowContext.clear();
    }

    @AfterEach
    void tearDown() {
        FlowContext.clear();
    }

    @Test
    @DisplayName("Deve registrar fluxo sequencial com decomposição de steps e processamento interno")
    void shouldRecordSequentialFlowAndSlices() {
        FlowContext.start("test-sequential-flow");
        FlowContext.recordStep("step-database", TimeUnit.MILLISECONDS.toNanos(150));
        FlowContext.complete(meterRegistry);

        Timer totalTimer = meterRegistry.find("flow_total_duration_seconds")
                .tag("flow", "test-sequential-flow")
                .timer();
        assertThat(totalTimer).isNotNull();

        Timer stepTimer = meterRegistry.find("flow_slice_duration_seconds")
                .tag("flow", "test-sequential-flow")
                .tag("step", "step-database")
                .timer();
        assertThat(stepTimer).isNotNull();
        assertThat(stepTimer.totalTime(TimeUnit.MILLISECONDS)).isEqualTo(150.0);
    }

    @Test
    @DisplayName("Deve registrar métricas canônicas e legadas de interrupção de fluxo")
    void shouldRecordInterruptionMetrics() {
        FlowContext.start("test-interrupted-flow");
        FlowContext.recordInterruption("step-payment", new RuntimeException("HTTP_500_TIMEOUT"), meterRegistry);

        // Métrica canônica (Candidate v2 Sec 28, 37)
        Counter canonicalCounter = meterRegistry.find("observability.flow.interruption")
                .tag("flow", "test-interrupted-flow")
                .tag("failed_step", "step-payment")
                .tag("error_type", "RuntimeException")
                .counter();
        assertThat(canonicalCounter).isNotNull();
        assertThat(canonicalCounter.count()).isEqualTo(1.0);

        // Métrica legada
        Counter legacyCounter = meterRegistry.find("flow_interruption_total")
                .tag("flow", "test-interrupted-flow")
                .tag("failed_step", "step-payment")
                .tag("error_type", "RuntimeException")
                .counter();
        assertThat(legacyCounter).isNotNull();
        assertThat(legacyCounter.count()).isEqualTo(1.0);
    }
}
