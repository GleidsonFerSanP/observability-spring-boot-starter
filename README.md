# Observability Spring Boot Starter

[![Java](https://img.shields.io/badge/Java-21-orange.svg)](https://openjdk.org/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.2.3+-green.svg)](https://spring.io/projects/spring-boot)
[![Micrometer](https://img.shields.io/badge/Micrometer-1.12.3+-blue.svg)](https://micrometer.io/)
[![Architecture](https://img.shields.io/badge/Spec-Candidate%20v2-purple.svg)](docs/ARCHITECTURE.md)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)

Starter corporativo padronizado para observabilidade unificada em ecossistemas Spring Boot 3.x, desenvolvido sob a especificação técnica **Candidate Architecture v2**: **instrumentação automática por padrão** para toda infraestrutura técnica e **instrumentação declarativa** exclusivamente onde o framework não consegue inferir a semântica de negócio.

---

## 📚 Documentação Especializada

| Documento | Descrição |
|---|---|
| 🏛️ **[Arquitetura do Starter](docs/ARCHITECTURE.md)** | Princípios de design, estrutura multi-módulo (`core`, `autoconfigure`, `starter`), auto-discovery condicional, diagramas de sequência de propagação de contexto assíncrono e formulação matemática da atribuição de latência. |
| ⚙️ **[Referência de Configuração](docs/CONFIGURATION_REFERENCE.md)** | Catálogo completo de propriedades `observability.*`, chaves mestras e granulares (`observability.<feature>.enabled`), parametrização de SLAs, limiares de alarmística e exemplo de `application.yml`. |
| 💡 **[Guia de Capacidades e Uso Prático](docs/CAPABILITIES_GUIDE.md)** | Guia de uso das anotações `@TrackFlow`, `@TrackStep`, `@LogLeg`, `@MaskField`, `@ObservationTag`, propagação de contexto assíncrono, barramento de eventos de alerta (`AlertDispatcher`) e segregação dimensional de feature flags. |
| 📊 **[Esquema de Telemetria (Telemetry Schema)](docs/TELEMETRY_SCHEMA.md)** | Catálogo canônico de métricas dimensionais (`observability.flow.*`, `resilience4j.*`, `hikaricp.*`), chaves padronizadas de MDC (`correlation_id`, `traceId`, `variant`), esquemas de logs estruturados (`AUDIT_LEG_LOGGER`) e eventos de alerta. |

---

## 🎯 Filosofia e Princípios

Nenhuma aplicação de microsserviço de negócio deve precisar implementar:
- Instanciação de `MeterRegistry`, `Tracer`, `Timer.Sample` ou `ObservationRegistry`.
- Manipulação manual de `Span`, abertura de escopos ou limpeza de `MDC`.
- Filtros manuais de extração e injeção de `X-Correlation-Id` ou W3C `traceparent`.
- Binders customizados de monitoramento de filas, bancos de dados ou disjuntores.

### O Princípio 80/20 de Observabilidade
```text
80-95% da observabilidade
          ↓
     Automática (HTTP, OpenFeign, Kafka, SQS, JDBC, HikariCP, Redis, Resilience4j, JVM, Logs)

5-20% da observabilidade
          ↓
   Semântica Declarativa (@TrackFlow, @TrackStep, @LogLeg, @ObservationTag)
```

---

## 🏗️ Estrutura Multi-Módulo (Padrão Spring Boot)

```text
observability-spring-boot-starter-project/
├── pom.xml                                          # Parent POM (BOM & dependências unificadas)
│
├── observability-spring-boot-starter-core/          # MÓDULO CORE (POJO / Framework-agnostic)
│   ├── annotation/                                  # @TrackFlow, @TrackStep, @LogLeg, @ObservationTag, @MaskField
│   ├── flow/                                        # FlowContext, LatencyAttributionEngine, FlowExecution, FlowDimensions
│   ├── leg/                                         # LegContext, SpelMaskingService
│   ├── correlation/                                 # CorrelationContext (W3C / MDC / HTTP Headers)
│   ├── feature/                                     # FlowFeatureEvaluationListener (Feature flag SPI)
│   └── alerting/                                    # AlertDispatcher, AlertEvent, AlertNotifier
│
├── observability-spring-boot-starter-autoconfigure/ # MÓDULO AUTOCONFIGURE (Spring Boot AutoConfiguration)
│   ├── aspect/                                      # FlowTrackingAspect, SpelObservationAspect, LegLoggingAspect
│   ├── async/                                       # ObservabilityTaskDecorator (MDC & ContextSnapshot propagation)
│   ├── alerting/                                    # LogAlertNotifier, WebhookAlertNotifier
│   ├── feign/                                       # FeignObservabilityAutoConfiguration
│   ├── jdbc/                                        # JdbcObservabilityAutoConfiguration (HikariPoolAlertWatcher)
│   ├── kafka/                                       # KafkaObservabilityAutoConfiguration
│   ├── resilience/                                  # ResilienceObservabilityAutoConfiguration (CircuitBreakerAlertListener)
│   ├── sqs/                                         # SqsObservabilityAutoConfiguration
│   └── LoggingObservationHandler.java               # Formatação e logging estruturado de Observation
│
└── observability-spring-boot-starter/               # STARTER AGREGADOR (Dependência única importada pelas apps)
    └── pom.xml
```

---

## 🚀 Como Usar na Aplicação

### 1. Dependência Maven

Basta adicionar a dependência agregadora no `pom.xml` da aplicação:

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
        "UserRegistration": 800
      step-slas:
        "step-validate-user": 200
      hikari-pending-threads: 5
      circuit-breaker-open: true
```

---

## 📊 Matriz de Cobertura de Capacidades

| Componente | Nível | O que é coletado automaticamente |
|---|---|---|
| **Spring MVC REST** | Automática | Requisições HTTP, status, latência percentil (P95/P99), Correlation ID, propagação W3C TraceContext. |
| **OpenFeign** | Automática | Latência externa, injeção transparente de headers de correlação (`X-Correlation-Id`, `traceparent`). |
| **JDBC / Hibernate** | Automática | Spans de execução SQL e tempo transacional. |
| **HikariCP** | Automática | Métricas de conexões ativas, idle e sentinela de esgotamento (`DATABASE_POOL_STARVATION`). |
| **Kafka (Producer/Consumer)** | Automática | Métricas de envio e consumo, injeção de correlation ID nos records. |
| **AWS SQS** | Automática | Interceptor de mensagens, métricas de envio e correlação. |
| **Resilience4j** | Automática | Monitoramento de transições de estado do Circuit Breaker (`OPEN`, `HALF_OPEN`) com alertas imediatos. |
| **Subprocessos de Negócio** | Declarativa (`@TrackStep`) | Latência de esforço nominal vs atribuída no tempo de relógio, controle de SLA de subprocesso. |
| **Fluxos Semânticos** | Declarativa (`@TrackFlow`) | Delimitação de orquestrações de negócio ponta a ponta e governança de interrupções. |
| **Auditoria Forense & LGPD** | Declarativa (`@LogLeg`) | Per-leg logging com mascaramento automático de dados sensíveis via SpEL (`@MaskField`). |
| **Tags Dinâmicas** | Declarativa (`@ObservationTag`) | Extração dinâmica de dimensões semânticas a partir de argumentos e retornos com SpEL. |

---

## 🧮 Motor de Atribuição de Latência (`LatencyAttributionEngine`)

Para resolver distorções matemáticas em fluxos que disparam subprocessos paralelos (`CompletableFuture.allOf`), o starter implementa o modelo de 3 métricas canônicas:

```mermaid
flowchart TD
    Total["observability.flow.duration (Wall-Clock Total)"]
    Attr["observability.flow.component.attributed.duration (Normalizado <= 100%)"]
    Work["observability.flow.component.work.duration (Esforço Nominal Bruto)"]
    Unattr["observability.flow.unattributed.duration (Processamento interno / Overhead)"]
    Overlap["observability.flow.parallel.overlap.duration (Tempo poupado por concorrência)"]

    Total --> Attr
    Total --> Unattr
    Work --> Overlap
```

- **Invariante Matemática do Wall-Clock**:
  $$\sum \text{attributed\_duration} + \text{unattributed\_duration} = \text{wall\_clock\_duration}$$
- Em fluxos com subprocessos concorrentes, a soma simples dos tempos nominais ultrapassa o tempo de relógio. O motor calcula a sobreposição paralela (`parallel.overlap.duration`) e normaliza a contribuição atribuída de cada componente.

---

## 🛡️ Desativação Limpa (Master Switch & Toggles)

O starter pode ser totalmente desativado ou desabilitado seletivamente sem que nenhuma linha de código da aplicação sofra alteração:

```yaml
# Desliga 100% dos aspectos, filtros e watchers:
observability:
  enabled: false
```

Ou com controle granular por funcionalidade:

```yaml
observability:
  enabled: true
  feign:
    enabled: false      # Desabilita apenas interceptor Feign
  leg-logging:
    enabled: false      # Desabilita apenas auditoria @LogLeg
  alerting:
    enabled: false      # Desabilita apenas despacho de alarmística
```

---

## 🧪 Suporte a Testes & Ambientes Locais

Para suíte de testes de integração (`@SpringBootTest`), o starter respeita a injeção condicional de mocks e desabilitações seletivas, garantindo tempo de boot ultrarrápido sem dependência de coletores externos.

---

## 📄 Licença

Distribuído sob a licença [Apache 2.0](LICENSE).
