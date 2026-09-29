# Guia de Configuração (Reference Guide)

Este guia documenta todas as propriedades de configuração suportadas pelo **Observability Spring Boot Starter** sob o prefixo `observability.*`.

Todas as propriedades possuem suporte nativo a *IDE Autocompletion* e validação estática no IntelliJ, VS Code e Eclipse via `spring-boot-configuration-processor`.

---

## 1. Toggles Mestres e Granulares

| Propriedade | Tipo | Padrão | Descrição |
|---|---|---|---|
| `observability.enabled` | `boolean` | `true` | **Master Switch**. Quando `false`, inibe 100% dos aspectos, interceptores, filtros e listeners do starter na JVM. |
| `observability.engine` | `String` | `micrometer` | **Engine de Telemetria**. Seleciona o adaptador da SPI (`micrometer` \| `datadog` \| `opentelemetry`). Em produção sob Datadog APM/DSM, definir como `datadog`. Em local/CI, manter `micrometer`. |
| `observability.flow-tracking.enabled` | `boolean` | `true` | Ativa/desativa o aspecto `@TrackFlow` e a instrumentação de decomposição de latência do `FlowTrackingAspect`. |
| `observability.leg-logging.enabled` | `boolean` | `true` | Ativa/desativa a auditoria estruturada forense de pernas (`AUDIT_LEG_LOGGER`) e o aspecto `@LogLeg`. |
| `observability.spel-observation.enabled` | `boolean` | `true` | Ativa/desativa a extração dinâmica de tags via SpEL através do aspecto `SpelObservationAspect`. |
| `observability.correlation.enabled` | `boolean` | `true` | Ativa/desativa o filtro de Correlation ID para servlets (`CorrelationIdFilter`). |
| `observability.feign.enabled` | `boolean` | `true` | Ativa/desativa a injeção automática de headers W3C TraceContext e `X-Correlation-Id` em clientes Feign. |
| `observability.resilience.enabled` | `boolean` | `true` | Ativa/desativa o listener de transição de estado de Circuit Breakers Resilience4j. |
| `observability.jdbc.enabled` | `boolean` | `true` | Ativa/desativa o watchdog e métricas de starvation de conexões do HikariCP. |
| `observability.alerting.enabled` | `boolean` | `true` | Ativa/desativa o barramento e o despachador de eventos `AlertDispatcher`. |
| `observability.async-decorator.enabled` | `boolean` | `true` | Ativa/desativa o `ObservabilityTaskDecorator` para propagação de contexto em threads assíncronas. |
| `observability.observation-handler.enabled` | `boolean` | `true` | Ativa/desativa o log de ciclo de vida (`Starting/Finished operation`) do `LoggingObservationHandler`. |

---

## 2. Configurações do Sistema de Alertas (`observability.alerting.*`)

| Propriedade | Tipo | Padrão | Descrição |
|---|---|---|---|
| `observability.alerting.webhook-url` | `String` | `null` | URL HTTP para onde o `WebhookAlertNotifier` enviará alertas (ex: webhook do Alertmanager, Slack ou PagerDuty). Se nula, o notifier HTTP permanece desabilitado. |
| `observability.alerting.thresholds.default-flow-sla-ms` | `long` | `2000` | SLA padrão de duração do fluxo (em milissegundos). Estouros disparam alerta `FLOW_LATENCY_SLA_BREACH`. |
| `observability.alerting.thresholds.default-step-sla-ms` | `long` | `800` | SLA padrão de subprocessos/steps. Estouros disparam alerta `INTEGRATION_LATENCY_SLA_BREACH`. |
| `observability.alerting.thresholds.flow-slas.<nome-do-fluxo>` | `Map<String, Long>` | `{}` | SLAs customizados por nome de fluxo (ex: `"POST /payments": 500`). |
| `observability.alerting.thresholds.step-slas.<nome-do-step>` | `Map<String, Long>` | `{}` | SLAs customizados por nome de step (ex: `"API Customer": 300`). |
| `observability.alerting.thresholds.hikari-pending-threads` | `int` | `1` | Limiar de threads bloqueadas aguardando conexão no pool antes de disparar `DATABASE_POOL_STARVATION`. |
| `observability.alerting.thresholds.circuit-breaker-open` | `boolean` | `true` | Se `true`, dispara alerta `CIRCUIT_BREAKER_OPEN` com severidade `CRITICAL` imediatamente na abertura do disjuntor. |
| `observability.alerting.thresholds.kafka-lag-high` | `long` | `100` | Limiar de lag de consumidor antes de disparar `KAFKA_LAG_HIGH`. |
| `observability.alerting.thresholds.sqs-backlog-high` | `long` | `50` | Limiar de profundidade de fila antes de disparar `SQS_BACKLOG_HIGH`. |

---

## 3. Exemplo Completo de `application.yml`

```yaml
# ==============================================================================
# CONFIGURAÇÃO CORPORATIVA DO STARTER DE OBSERVABILIDADE
# ==============================================================================
observability:
  enabled: true # Master switch
  engine: micrometer # micrometer (dev/test) | datadog (prod) | opentelemetry

  # Toggles Granulares (opcional, todos são true por padrão)
  flow-tracking:
    enabled: true
  leg-logging:
    enabled: true
  spel-observation:
    enabled: true
  correlation:
    enabled: true
  feign:
    enabled: true
  resilience:
    enabled: true
  jdbc:
    enabled: true
  async-decorator:
    enabled: true
  observation-handler:
    enabled: true

  # Configuração de Alertas e SLAs
  alerting:
    enabled: true
    webhook-url: "http://alertmanager.empresa.internal/api/v1/alerts"
    thresholds:
      default-flow-sla-ms: 1200
      default-step-sla-ms: 600
      flow-slas:
        "GET /api/v1/orchestrator/users/{userId}": 1000
        "POST /api/v1/orchestrator/users": 500
      step-slas:
        "API Customer (GET /customers/{userId})": 400
        "API Billing (GET /billing/accounts/{userId})": 500
      hikari-pending-threads: 2
      circuit-breaker-open: true
      kafka-lag-high: 100
      sqs-backlog-high: 50
```

---

## 4. Desativação em Testes de Unidade

Para testes que desejam rodar de forma completamente isolada de observabilidade:

```java
@SpringBootTest
@TestPropertySource(properties = "observability.enabled=false")
class MinhaRegraDeNegocioTest {
    // A aplicação sobe sem nenhum aspecto ou overhead de observabilidade
}
```
