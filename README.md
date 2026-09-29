# Observability Spring Boot Starter

[![Java](https://img.shields.io/badge/Java-21-orange.svg)](https://openjdk.org/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.2.3-green.svg)](https://spring.io/projects/spring-boot)
[![Micrometer](https://img.shields.io/badge/Micrometer-1.12.3-blue.svg)](https://micrometer.io/)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)

Starter corporativo padronizado para observabilidade completa em ecossistemas Spring Boot, implementando a especificação **Candidate v2**: **instrumentação automática por padrão** para infraestrutura e **instrumentação declarativa** apenas onde o framework não consegue inferir a semântica de negócio sozinho.

---

## 🎯 Filosofia e Objetivos

Nenhuma aplicação de negócio deve precisar gerenciar programaticamente:
- `MeterRegistry`, `Tracer`, `Timer.Sample`, `ObservationRegistry`
- Criação e abertura de `Span` ou manipulação manual de `MDC`
- Filtros manuais de extração e propagação de Correlation ID
- Binders customizados e polling de fila/tópico por JVM

### Princípio 80/20 de Observabilidade
```text
80-95% da observabilidade
          ↓
     Automática (HTTP, Feign, Kafka, SQS, JDBC, Hikari, Resilience4j, JVM)

5-20% da observabilidade
          ↓
  Semântica Declarativa (@TrackFlow, @TrackStep, @LogLeg, @ObservationTag)
```

---

## 🏗️ Arquitetura Multi-Módulo

O starter adota a convenção canônica oficial do Spring Boot para criação de starters:

```text
observability-spring-boot-starter-parent/
├── observability-spring-boot-starter-core/          # Módulo puro, agnóstico de Spring Boot Starter
│   ├── annotation/                                  # @TrackFlow, @TrackStep, @LogLeg, @ObservationTag
│   ├── flow/                                        # FlowContext, LatencyAttributionEngine
│   ├── leg/                                         # LegContext, SpelMaskingService
│   ├── correlation/                                 # CorrelationContext (W3C / MDC / HTTP Headers)
│   └── alerting/                                    # AlertDispatcher, AlertEvent, AlertNotifier
│
├── observability-spring-boot-starter-autoconfigure/ # Configurações automáticas e Beans condicionais
│   ├── aspect/                                      # FlowTrackingAspect, SpelObservationAspect, LegLoggingAspect
│   ├── alerting/                                    # LogAlertNotifier, WebhookAlertNotifier
│   ├── feign/                                       # FeignObservabilityAutoConfiguration (Interceptors)
│   ├── jdbc/                                        # JdbcObservabilityAutoConfiguration (HikariPoolAlertWatcher)
│   ├── kafka/                                       # KafkaObservabilityAutoConfiguration
│   ├── resilience/                                  # ResilienceObservabilityAutoConfiguration (CircuitBreakerAlertListener)
│   ├── sqs/                                         # SqsObservabilityAutoConfiguration
│   └── LoggingObservationHandler.java               # Formatação de logs estruturados de observation
│
└── observability-spring-boot-starter/               # Dependência única (Fat Starter) que as aplicações importam
```

---

## 🚀 Como Usar na Aplicação

### 1. Dependência Maven

Adicione no `pom.xml` da aplicação:

```xml
<dependency>
    <groupId>com.empresa.platform</groupId>
    <artifactId>observability-spring-boot-starter</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</dependency>
```

### 2. Configuração Básica (`application.yml`)

```yaml
observability:
  enabled: true
  naming:
    prefix: observability
  alerting:
    enabled: true
    webhook-url: "http://alert-manager.internal/webhook"
    thresholds:
      default-flow-sla-ms: 1000
      default-step-sla-ms: 500
      flow-slas:
        "POST /payments": 800
      step-slas:
        "payment-gateway": 400
      hikari-pending-threads: 5
      circuit-breaker-open: true
```

---

## 📊 Matriz de Cobertura Automática vs Declarativa

| Componente | Nível | O que é coletado automaticamente |
|---|---|---|
| **Spring MVC REST** | Automática | Requisições HTTP, latência, status, URI sanitizada, exemplars e correlation ID |
| **OpenFeign** | Automática | Latência externa, injeção de cabeçalhos de propagação (`X-Correlation-Id`, W3C traceparent) |
| **JDBC / Hibernate** | Automática | Query execution spans, transações de banco de dados |
| **HikariCP** | Automática | Conexões ativas, threads pendentes, timeout de conexão e alarmística reativa |
| **Kafka** | Automática | Spans de envio do `KafkaTemplate`, propagação de correlation ID nos records |
| **AWS SQS** | Automática | Spans de envio do `SqsTemplate`, propagação de correlation ID nas mensagens |
| **Resilience4j** | Automática | Circuit Breaker state changes (OPEN/HALF_OPEN), métricas de chamadas |
| **Subprocessos de Negócio** | Declarativa (`@TrackStep`) | Latência atribuída semântica, SLA guard, cálculo de tempo de trabalho vs espera |
| **Entrypoints Semânticos** | Declarativa (`@TrackFlow`) | Nome de fluxo de alto nível quando diferente do caminho REST cru |
| **Auditoria e LGPD** | Declarativa (`@LogLeg`) | Per-leg logging seguro com mascaramento granular via SpEL (Email, CPF, Cartão, Senha) |
| **Tags de Negócio** | Declarativa (`@ObservationTag`)| Extração dinâmica de tags de contexto a partir de argumentos ou retornos do método |

---

## 🏷️ Exemplos de Instrumentação Declarativa

### 1. Delimitação de Subprocesso com `@TrackStep`
```java
@Service
public class RiskService {

    @TrackStep("risk-calculation")
    public RiskAssessment calculateRisk(Customer customer) {
        // O starter mede a latência dessa etapa, calcula o tempo atribuído se houver paralelismo,
        // valida o SLA e emite alertas se a duração estourar o limiar.
        return doCalculate(customer);
    }
}
```

### 2. Enriquecimento de Contexto via SpEL com `@ObservationTag`
```java
@TrackStep("billing-verification")
@ObservationTag(key = "userId", expression = "#userId", highCardinality = true)
@ObservationTag(key = "plan", expression = "#result?.plan()")
public BillingDto getBilling(String userId) {
    return billingClient.fetch(userId);
}
```

### 3. Auditoria de Pernas e Mascaramento LGPD com `@LogLeg`
```java
@PostMapping("/users")
@TrackFlow("UserRegistration")
@LogLeg(
    target = "user-orchestrator",
    type = LegType.INBOUND,
    mask = {
        @MaskField(expression = "#request.email", pattern = MaskPattern.EMAIL_PARTIAL),
        @MaskField(expression = "#request.taxId", pattern = MaskPattern.CPF_PARTIAL)
    }
)
public ResponseEntity<UserResponse> register(@RequestBody UserRequest request) {
    return ResponseEntity.ok(userService.register(request));
}
```

---

## 🧮 Motor de Atribuição de Latência (`LatencyAttributionEngine`)

Para resolver a distorção matemática de agregação simples em fluxos concorrentes (`CompletableFuture.allOf`), o starter implementa o modelo canônico de três métricas de latência:

1. **`flow.duration` (Wall-clock time)**: Tempo real decorrido de ponta a ponta na perspectiva do usuário.
2. **`flow.component.work.duration`**: Soma estrita de CPU e trabalho real de cada componente ou thread.
3. **`flow.component.attributed.duration`**: Latência normalizada ponderada sobre o tempo de relógio do fluxo, garantindo que a decomposição percentual feche sempre exatamente em 100%.

---

## 🛡️ Alarmística Reativa In-App (`AlertDispatcher`)

O starter monitora anomalias em tempo sub-segundo diretamente no ciclo de vida da JVM:
- **`FLOW_LATENCY_SLA_BREACH`**: Quando a duração de um `@TrackFlow` viola o SLA configurado.
- **`INTEGRATION_LATENCY_SLA_BREACH`**: Quando um `@TrackStep` excede seu limiar individual.
- **`CIRCUIT_BREAKER_OPEN`**: Transições imediatas de disjuntores Resilience4j.
- **`HIKARICP_POOL_EXHAUSTION`**: Quando threads aguardando conexões no HikariCP superam o limite de segurança.

Os alertas são despachados de forma desacoplada para log estruturado JSON, webhooks corporativos assíncronos e exportados como contadores Prometheus (`alerts_triggered_total`).

---

## 📄 Licença
Distribuído sob a licença Apache 2.0.
