package com.empresa.platform.observability.autoconfigure.async;

import io.micrometer.context.ContextSnapshotFactory;
import org.springframework.core.task.TaskDecorator;

/**
 * Decorator padronizado para propagação de contexto (MDC, Correlation ID, Tracing e Observações)
 * ao atravessar threads em ThreadPoolTaskExecutor e @Async (Candidate Architecture v2 - Seção 8).
 */
public class ObservabilityTaskDecorator implements TaskDecorator {

    private final ContextSnapshotFactory snapshotFactory = ContextSnapshotFactory.builder().build();

    @Override
    public Runnable decorate(Runnable runnable) {
        return snapshotFactory.captureAll().wrap(runnable);
    }
}
