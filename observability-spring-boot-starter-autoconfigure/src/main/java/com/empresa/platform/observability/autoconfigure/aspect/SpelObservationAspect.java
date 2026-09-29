package com.empresa.platform.observability.autoconfigure.aspect;

import com.empresa.platform.observability.core.annotation.ObservationTag;
import com.empresa.platform.observability.core.annotation.ObservationTags;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;

import java.lang.reflect.Method;

@Aspect
@Order(20)
public class SpelObservationAspect {

    private static final Logger log = LoggerFactory.getLogger(SpelObservationAspect.class);
    private final ObservationRegistry observationRegistry;
    private final ExpressionParser parser = new SpelExpressionParser();

    public SpelObservationAspect(ObservationRegistry observationRegistry) {
        this.observationRegistry = observationRegistry;
    }

    @Around("@annotation(com.empresa.platform.observability.core.annotation.ObservationTag) || @annotation(com.empresa.platform.observability.core.annotation.ObservationTags)")
    public Object processObservationTags(ProceedingJoinPoint joinPoint) throws Throwable {
        Observation observation = observationRegistry != null ? observationRegistry.getCurrentObservation() : null;
        if (observation == null) {
            return joinPoint.proceed();
        }

        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        Object[] args = joinPoint.getArgs();
        String[] paramNames = signature.getParameterNames();

        StandardEvaluationContext context = new StandardEvaluationContext();
        if (paramNames != null) {
            for (int i = 0; i < paramNames.length; i++) {
                context.setVariable(paramNames[i], args[i]);
            }
        }

        ObservationTag[] tags = method.getAnnotationsByType(ObservationTag.class);

        for (ObservationTag tag : tags) {
            if (!tag.expression().contains("#result")) {
                evaluateAndTag(observation, context, tag);
            }
        }

        Object result;
        try {
            result = joinPoint.proceed();
        } catch (Throwable t) {
            context.setVariable("error", t);
            throw t;
        }

        context.setVariable("result", result);
        for (ObservationTag tag : tags) {
            if (tag.expression().contains("#result")) {
                evaluateAndTag(observation, context, tag);
            }
        }

        return result;
    }

    private static final java.util.Set<String> DANGEROUS_METRIC_KEYS = java.util.Set.of(
            "trace_id", "traceid", "correlation_id", "correlationid",
            "account_id", "accountid", "order_id", "orderid"
    );

    private void evaluateAndTag(Observation observation, EvaluationContext context, ObservationTag tag) {
        try {
            String value = parser.parseExpression(tag.expression()).getValue(context, String.class);
            if (value != null) {
                boolean forceHigh = tag.highCardinality() || DANGEROUS_METRIC_KEYS.contains(tag.key().toLowerCase());
                if (forceHigh) {
                    observation.highCardinalityKeyValue(tag.key(), value);
                } else {
                    observation.lowCardinalityKeyValue(tag.key(), value);
                }
            }
        } catch (Exception e) {
            log.warn("Failed to evaluate SpEL expression [{}] for Observation tag [{}]: {}", tag.expression(), tag.key(), e.getMessage());
        }
    }
}
