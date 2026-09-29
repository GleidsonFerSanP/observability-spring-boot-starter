package com.empresa.platform.observability.autoconfigure;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
@AutoConfiguration
@ConditionalOnClass(name = "io.awspring.cloud.sqs.operations.SqsTemplate")
public class SqsObservabilityAutoConfiguration {
}
