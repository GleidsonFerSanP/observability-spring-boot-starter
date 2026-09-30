package com.empresa.platform.observability.autoconfigure.aspect;

import com.empresa.platform.observability.core.annotation.ObservationTag;
import io.micrometer.observation.ObservationRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SpelObservationAspectTest {

    private ObservationRegistry observationRegistry;
    private SpelObservationAspect aspect;

    static class SampleService {
        private final AtomicReference<String> capturedUserId = new AtomicReference<>();
        private final AtomicReference<String> capturedTenant = new AtomicReference<>();

        @ObservationTag(key = "userId", expression = "#userId")
        @ObservationTag(key = "orderId", expression = "#order.id")
        public String processOrder(String userId, OrderDto order) {
            capturedUserId.set(MDC.get("userId"));
            return "SUCCESS";
        }

        @ObservationTag(key = "tenant", expression = "#tenant")
        public void outerMethod(String tenant, SampleService self, String innerTenant) {
            capturedTenant.set(MDC.get("tenant"));
            self.innerMethod(innerTenant);
            // After inner returns, outer should still see its own tenant
            capturedTenant.set(MDC.get("tenant"));
        }

        @ObservationTag(key = "tenant", expression = "#innerTenant")
        public void innerMethod(String innerTenant) {
            assertThat(MDC.get("tenant")).isEqualTo(innerTenant);
        }

        @ObservationTag(key = "secret", expression = "#secret", mdc = false)
        public void processWithoutMdc(String secret) {
            assertThat(MDC.get("secret")).isNull();
        }

        @ObservationTag(key = "userId", expression = "#userId")
        public void failMethod(String userId) {
            assertThat(MDC.get("userId")).isEqualTo(userId);
            throw new IllegalStateException("Simulated failure");
        }

        public void processWithParamTag(@ObservationTag(key = "customerId") String customerId) {
            capturedUserId.set(MDC.get("customerId"));
        }
    }

    record OrderDto(String id, double amount) {}

    @BeforeEach
    void setUp() {
        MDC.clear();
        observationRegistry = ObservationRegistry.create();
        aspect = new SpelObservationAspect(observationRegistry);
    }

    @AfterEach
    void tearDown() {
        MDC.clear();
    }

    private SampleService createProxy(SampleService target) {
        AspectJProxyFactory factory = new AspectJProxyFactory(target);
        factory.addAspect(aspect);
        return factory.getProxy();
    }

    @Test
    @DisplayName("Deve injetar tags SpEL no MDC durante execução do método e limpar após o término")
    void shouldInjectAndCleanMdcTags() {
        SampleService service = new SampleService();
        SampleService proxy = createProxy(service);

        String result = proxy.processOrder("user-123", new OrderDto("ord-999", 50.0));

        assertThat(result).isEqualTo("SUCCESS");
        assertThat(service.capturedUserId.get()).isEqualTo("user-123");
        // Após o término do método, MDC deve estar limpo
        assertThat(MDC.get("userId")).isNull();
        assertThat(MDC.get("orderId")).isNull();
    }

    @Test
    @DisplayName("Deve preservar valor anterior do MDC em chamadas aninhadas (stack semantics)")
    void shouldHandleNestedMdcInvocations() {
        SampleService service = new SampleService();
        SampleService proxy = createProxy(service);

        proxy.outerMethod("tenant-A", proxy, "tenant-B");

        assertThat(service.capturedTenant.get()).isEqualTo("tenant-A");
        assertThat(MDC.get("tenant")).isNull();
    }

    @Test
    @DisplayName("Não deve injetar no MDC quando mdc=false")
    void shouldNotInjectWhenMdcDisabled() {
        SampleService service = new SampleService();
        SampleService proxy = createProxy(service);

        proxy.processWithoutMdc("confidential-token");

        assertThat(MDC.get("secret")).isNull();
    }

    @Test
    @DisplayName("Deve limpar MDC garantidamente no bloco finally mesmo em caso de exceção")
    void shouldCleanMdcOnException() {
        SampleService service = new SampleService();
        SampleService proxy = createProxy(service);

        assertThatThrownBy(() -> proxy.failMethod("user-error"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Simulated failure");

        // MDC deve estar limpo mesmo com exceção
        assertThat(MDC.get("userId")).isNull();
    }

    @Test
    @DisplayName("Deve suportar @ObservationTag diretamente em parâmetros do método")
    void shouldSupportParamLevelObservationTag() {
        SampleService service = new SampleService();
        SampleService proxy = createProxy(service);

        proxy.processWithParamTag("cust-777");

        assertThat(service.capturedUserId.get()).isEqualTo("cust-777");
        assertThat(MDC.get("customerId")).isNull();
    }
}
