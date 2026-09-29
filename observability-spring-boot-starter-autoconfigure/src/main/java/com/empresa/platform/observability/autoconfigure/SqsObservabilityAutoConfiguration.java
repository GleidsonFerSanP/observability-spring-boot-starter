package com.empresa.platform.observability.autoconfigure;

import io.awspring.cloud.sqs.operations.SqsTemplate;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

@AutoConfiguration
@ConditionalOnClass(SqsTemplate.class)
@ConditionalOnProperty(prefix = "observability", name = "enabled", havingValue = "true", matchIfMissing = true)
public class SqsObservabilityAutoConfiguration {
}
