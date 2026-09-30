# Guia de Configuração (Reference Guide)

Este guia documenta todas as propriedades de configuração suportadas pelo **Observability Spring Boot Starter** sob o prefixo `observability.*`.

Todas as propriedades possuem suporte nativo a *IDE Autocompletion* e validação estática no IntelliJ, VS Code e Eclipse via `spring-boot-configuration-processor`.

---

## 1. Configurações Globais, Perfis e Single-Producer

| Propriedade | Tipo | Padrão | Descrição |
|---|---|---|---|
| `observability.enabled` | `boolean` | `true` | **Master Switch**. Quando `false`, inibe 100% dos aspectos, interceptores, filtros e listeners do starter na JVM. |
| `observability.profile` | `String` | `datadog` | **Perfil de Telemetria Corporativo**. Opções: `datadog` (padrão corporativo), `prometheus` (scraping local/Grafana), ou `custom`. Determina as convenções semânticas e o registry ativo. |
| `observability.engine` | `String` | `datadog` | **Engine da SPI**. Seleciona o adaptador (`datadog` \| `micrometer` \| `opentelemetry`). Em produção sob Datadog APM/DSM, manter `datadog`. Em local/CI, pode ser alternado para `micrometer`. |
| `observability.metrics.allow-dual-export` | `boolean` | `false` | **Guarda de Exportação Duplicada**. Se `false`, o `ObservabilityTopologyValidator` aborta o boot (*fail-fast*) caso detecte múltiplos registries incompatíveis (ex: Datadog + Prometheus) ativos simultaneamente, prevenindo faturas SaaS duplicadas. |
| `observability.metrics.exporters` | `List<String>` | `["datadog"]` | Lista de exportadores de métricas autorizados. |
| `observability.tracing.engine` | `String` | `auto` | Modo do motor de tracing: `auto` (detecta `-javaagent`), `datadog-agent`, `otel-agent` ou `none`. |
| `observability.tracing.duplicate-policy` | `String` | `fail` | Política ao detectar múltiplos agentes de tracing na JVM: `fail` (*fail-fast* com exceção) ou `warn` (apenas log de alerta). |
| `observability.validation.mode` | `String` | `fail-fast` | Modo do validador de topologia: `fail-fast` (lança `IllegalStateException` no boot) ou `warn` (apenas emite logs de warning). |

---

## 2. Toggles Granulares de Infraestrutura e Aspectos

| Propriedade | Tipo | Padrão | Descrição |
|---|---|---|---|
| `observability.flow.enabled` / `observability.flow-tracking.enabled` | `boolean` | `true` | Ativa/desativa o aspecto `@TrackFlow` e a instrumentação de decomposição de latência do `FlowTrackingAspect`. |
| `observability.correlation.enabled` | `boolean` | `true` | Ativa/desativa o filtro de Correlation ID para servlets (`CorrelationIdFilter`). |
| `observability.leg-logging.enabled` | `boolean` | `true` | Ativa/desativa a auditoria estruturada forense de pernas (`AUDIT_LEG_LOGGER`) e o aspecto `@LogLeg`. |
| `observability.spel-observation.enabled` | `boolean` | `true` | Ativa/desativa a extração dinâmica de tags via SpEL através do aspecto `SpelObservationAspect`. |
| `observability.feign.enabled` | `boolean` | `true` | Ativa/desativa a injeção automática de headers W3C TraceContext e `X-Correlation-Id` em clientes Feign. |
| `observability.resilience.enabled` | `boolean` | `true` | Ativa/desativa o listener de transição de estado de Circuit Breakers Resilience4j. |
| `observability.jdbc.enabled` | `boolean` | `true` | Ativa/desativa o watchdog e métricas de starvation de conexões do HikariCP. |
| `observability.async-decorator.enabled` | `boolean` | `true` | Ativa/desativa o `ObservabilityTaskDecorator` para propagação de contexto em threads assíncronas. |
| `observability.observation-handler.enabled` | `boolean` | `false` | Ativa/desativa o log de ciclo de vida (`Starting/Finished operation`) do `LoggingObservationHandler` (desabilitado por padrão para evitar ruído). |
| `observability.infrastructure.kafka-lag.enabled` | `boolean` | `false` | Ativa polling in-JVM de lag Kafka via `AdminClient`. Em produção sob Datadog DSM, manter `false`. |
| `observability.infrastructure.sqs-polling.enabled` | `boolean` | `false` | Ativa polling in-JVM de profundidade de filas SQS via AWS SDK. Em produção sob Datadog DSM, manter `false`. |
| `observability.logs.structured` | `boolean` | `true` | Formata logs em JSON estruturado com campos semânticos para indexação. |
| `observability.logs.payload` | `boolean` | `false` | **Opt-in de Auditoria de Payloads**. Por padrão `false` (conformidade com LGPD/PCI-DSS). |

---

## 3. Configurações do Sistema de Alertas (`observability.alerting.*`)

As propriedades de alarmística vinculam-se à classe `AlertingProperties` sob o prefixo `observability.alerting.*`:

| Propriedade | Tipo | Padrão | Descrição |
|---|---|---|---|
| `observability.alerting.enabled` | `boolean` | `true` | Master switch do despachador de eventos de alerta in-app (`AlertDispatcher`). |
| `observability.alerting.webhook-url` | `String` | `null` | URL HTTP para envio de alertas via `WebhookAlertNotifier` (ex: Webhook Slack, Teams ou Alertmanager). |
| `observability.alerting.default-flow-sla-ms` | `long` | `3000` | SLA padrão de duração do fluxo (em milissegundos). Violações disparam alerta `FLOW_LATENCY_SLA_BREACH`. |
| `observability.alerting.default-step-sla-ms` | `long` | `1500` | SLA padrão de subprocessos/steps. Violações disparam alerta `INTEGRATION_LATENCY_SLA_BREACH`. |
| `observability.alerting.flow-sla-ms.<nome-do-fluxo>` | `Map<String, Long>` | `{}` | SLAs customizados por nome de fluxo (ex: `flow-sla-ms.OrderCheckout: 1200`). |
| `observability.alerting.step-sla-ms.<nome-do-step>` | `Map<String, Long>` | `{}` | SLAs customizados por nome de step (ex: `step-sla-ms.payment-gateway: 400`). |
| `observability.alerting.hikari-pending-threshold` | `int` | `1` | Limiar de threads bloqueadas aguardando conexão no pool antes de disparar `DATABASE_POOL_STARVATION`. |
| `observability.alerting.kafka-lag-threshold` | `long` | `100` | Limiar de lag de consumidor antes de disparar `KAFKA_LAG_HIGH`. |
| `observability.alerting.sqs-depth-threshold` | `int` | `50` | Limiar de profundidade de fila antes de disparar `SQS_BACKLOG_HIGH`. |

---

## 4. Exemplos Prontos de Configuração

### 4.1 Perfil de Produção Corporativo (Datadog Default)

```yaml
# ==============================================================================
# PRODUÇÃO: Datadog APM, DSM e Métricas (Sem Polling in-JVM redundante)
# ==============================================================================
observability:
  enabled: true
  profile: datadog
  engine: datadog

  metrics:
    allow-dual-export: false # Proteção ativa contra custo duplicado
    exporters:
      - datadog

  tracing:
    engine: auto # Detecta dd-java-agent injetado no container
    duplicate-policy: fail

  validation:
    mode: fail-fast # Bloqueia startup se houver conflito de topologia

  alerting:
    enabled: true
    webhook-url: "https://alertmanager.internal/alerts"
    default-flow-sla-ms: 2500
    default-step-sla-ms: 1000
    flow-sla-ms:
      OrderCheckout: 1200
      UserProvisioning: 800
    step-sla-ms:
      payment-gateway-charge: 500
    hikari-pending-threshold: 2

  infrastructure:
    kafka-lag:
      enabled: false # Delegado nativamente ao Datadog Data Streams Monitoring
    sqs-polling:
      enabled: false # Delegado nativamente ao Datadog Data Streams Monitoring
```

### 4.2 Perfil de Desenvolvimento Local / CI (Prometheus / Grafana)

```yaml
# ==============================================================================
# DESENVOLVIMENTO / CI: Prometheus Scrape Local & Grafana
# ==============================================================================
observability:
  enabled: true
  profile: prometheus
  engine: micrometer

  metrics:
    allow-dual-export: false
    exporters:
      - prometheus

  alerting:
    enabled: true
    default-flow-sla-ms: 3000
    default-step-sla-ms: 1500

  infrastructure:
    kafka-lag:
      enabled: true # Polling in-JVM ativo para alimentar queries PromQL locais
    sqs-polling:
      enabled: true

management:
  endpoints:
    web:
      exposure:
        include: health, info, prometheus
```

---

## 5. Desativação em Testes de Unidade

Para testes que desejam rodar de forma completamente isolada de observabilidade:

```java
@SpringBootTest
@TestPropertySource(properties = "observability.enabled=false")
class MinhaRegraDeNegocioTest {
    // A aplicação sobe sem nenhum aspecto ou overhead de observabilidade
}
```
