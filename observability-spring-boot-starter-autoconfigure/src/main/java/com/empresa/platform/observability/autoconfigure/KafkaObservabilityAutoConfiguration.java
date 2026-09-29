package com.empresa.platform.observability.autoconfigure;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.KafkaTemplate;

@AutoConfiguration
@ConditionalOnClass(KafkaTemplate.class)
@ConditionalOnProperty(prefix = "observability", name = "enabled", havingValue = "true", matchIfMissing = true)
public class KafkaObservabilityAutoConfiguration {

    @Bean
    public BeanPostProcessor kafkaObservabilityBeanPostProcessor() {
        return new BeanPostProcessor() {
            @Override
            public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
                if (bean instanceof KafkaTemplate<?, ?> template) {
                    template.setObservationEnabled(true);
                } else if (bean instanceof ConcurrentKafkaListenerContainerFactory<?, ?> factory) {
                    factory.getContainerProperties().setObservationEnabled(true);
                }
                return bean;
            }
        };
    }
}
