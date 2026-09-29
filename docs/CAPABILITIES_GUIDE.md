# Guia de Capacidades do Starter (Capabilities Guide)

Este documento descreve detalhadamente cada uma das capacidades do starter, explicando como utilizá-las na prática e demonstrando exemplos de código.

---

## 1. Inteligência de Fluxo (`@TrackFlow`)

### Quando usar?
Endpoints HTTP REST (`@GetMapping`, `@PostMapping`), `@KafkaListener` e `@SqsListener` são identificados automaticamente pelo starter. A anotação `@TrackFlow` é reservada para **substituir o nome técnico por um nome semântico de negócio** ou para delimitar fluxos orquestrados complexos.

```java
@RestController
@RequestMapping("/api/v1/orders")
public class OrderController {

    // Substitui o nome padrão pelo fluxo corporativo "OrderCheckout"
    @TrackFlow("OrderCheckout")
    @PostMapping("/{orderId}/checkout")
    public OrderResponse checkout(@PathVariable String orderId, @RequestBody OrderRequest request) {
        return orderService.processCheckout(orderId, request);
    }
}
```

O starter automaticamente:
1. Inicia um cronômetro de alta precisão para a duração do fluxo (*wall-clock*).
2. Cria e encerra uma `Observation` do Micrometer vinculada ao Trace atual.
3. Calcula a fatia de **Processamento Interno & Regras de Negócio** subtraindo as integrações externas do tempo total.
4. Emite a métrica canônica `observability.flow.duration`.

---

## 2. Rastreamento de Subprocessos (`@TrackStep`)

Permite fatiar e atribuir latência a etapas e dependências de infraestrutura:

```java
@Component
public class PaymentGatewayClient {

    @TrackStep(value = "payment-gateway-charge", type = ComponentType.FEIGN_HTTP)
    public ChargeResult charge(PaymentRequest request) {
        return feignClient.executeCharge(request);
    }
}
```

### Tipos de Componentes (`ComponentType`):
- `FEIGN_HTTP`: Clientes REST e integrações HTTP externas.
- `DATABASE`: Operações de persistência, consultas SQL e transações.
- `KAFKA_PRODUCER` / `KAFKA_CONSUMER`: Mensageria Kafka.
- `SQS_PRODUCER` / `SQS_CONSUMER`: Filas AWS SQS.
- `REDIS`: Cache e operações em memória.
- `RESILIENCE`: Fallbacks e retries.
- `INTERNAL`: Subprocessos pesados e algoritmos de cálculo locais.

---

## 3. Auditoria Forense de Pernas (`@LogLeg`) e Mascaramento SpEL

O `@LogLeg` produz logs estruturados para auditoria forense de entradas e saídas de rede no logger canônico `AUDIT_LEG_LOGGER`.

```java
@TrackFlow("UserProvisioning")
@LogLeg(
    target = "user-orchestrator",
    type = LegType.INBOUND,
    includePayload = true,
    mask = {
        @MaskField(expression = "#request.password", pattern = MaskPattern.PASSWORD),
        @MaskField(expression = "#request.cpf", pattern = MaskPattern.CPF_PARTIAL),
        @MaskField(expression = "#result?.email()", pattern = MaskPattern.EMAIL_PARTIAL)
    }
)
@PostMapping("/users")
public UserResponse register(@RequestBody UserRequest request) {
    return userService.register(request);
}
```

### Regras de Governança de Dados (LGPD / PCI-DSS):
* **`includePayload = false` por padrão**: Em conformidade com a especificação técnica Candidate v2, o starter **não** audita corpos de mensagens a menos que explicitado com `includePayload = true` ou configurado com `@MaskField`.
* **Padrões de Máscara (`MaskPattern`)**:
  - `CPF_PARTIAL`: `123.456.789-00` $\rightarrow$ `123.***.***-00`
  - `EMAIL_PARTIAL`: `john.doe@example.com` $\rightarrow$ `j***e@example.com`
  - `CARD_PARTIAL`: `4111222233334444` $\rightarrow$ `************4444`
  - `PASSWORD`: `minhasenha` $\rightarrow$ `********`
  - `FULL_MASK`: `dado-sensivel` $\rightarrow$ `***REDACTED***`

---

## 4. Enriquecimento Dinâmico de Tags com SpEL (`@ObservationTag`)

Permite enriquecer observações e traces em tempo de execução usando Spring Expression Language (SpEL):

```java
@Observed(name = "order.payment")
@ObservationTag(key = "tenant_id", expression = "#tenantId", highCardinality = false)
@ObservationTag(key = "order_id", expression = "#orderId", highCardinality = true)
@ObservationTag(key = "status", expression = "#result?.status()", highCardinality = false)
public PaymentResult executePayment(String tenantId, String orderId, PaymentDetails details) {
    return paymentProcessor.pay(details);
}
```

### Proteção Contra Explosão de Cardinalidade
O starter inspeciona as chaves de tags dinâmicas. Chaves identificadas como potencialmente perigosas para séries temporais (`user_id`, `cpf`, `email`, `document`, `order_id`, `account_id`, etc.) são **automaticamente promovidas para tags de alta cardinalidade** (`getHighCardinalityKeyValues()`), sendo visíveis em traces e spans do OpenTelemetry, mas protegendo o TSDB (Prometheus/Datadog) contra esgotamento de memória.

---

## 5. Propagação de Contexto e Correlation ID

* **Entrada**: O [`CorrelationIdFilter`](file:///Users/gleidsonfersanp/workspace/observability-spring-boot-starter-project/observability-spring-boot-starter-autoconfigure/src/main/java/com/empresa/platform/observability/autoconfigure/CorrelationIdFilter.java) captura o cabeçalho `X-Correlation-Id` da requisição HTTP. Caso não enviado pelo cliente, um UUID canônico é gerado.
* **MDC**: O `correlation_id` e o `traceId` são injetados no MDC do SLF4J, aparecendo automaticamente em todas as linhas de log.
* **Saída (Feign)**: O `observabilityFeignRequestInterceptor` injeta transparentemente o `X-Correlation-Id` e o `x-trace-id` nas chamadas downstream.
* **Threads Assíncronas**: O [`ObservabilityTaskDecorator`](file:///Users/gleidsonfersanp/workspace/observability-spring-boot-starter-project/observability-spring-boot-starter-autoconfigure/src/main/java/com/empresa/platform/observability/autoconfigure/async/ObservabilityTaskDecorator.java) clona e restaura o MDC e os snapshots de observação através de *thread-pools* e tarefas `@Async`.

---

## 6. Sistema de Alertas em Tempo Real (`AlertDispatcher`)

O starter possui um barramento de eventos de anomalia integrado ao Micrometer:

```java
@Autowired
private AlertDispatcher alertDispatcher;

// Disparo de alerta customizado de negócio
alertDispatcher.dispatch(AlertEvent.of(
    AlertType.FLOW_STEP_INTERRUPTION,
    AlertSeverity.CRITICAL,
    "PaymentModule",
    "payment-gateway",
    "Gateway externo indisponível há mais de 3 tentativas",
    3.0,
    1.0,
    Map.of("gateway", "cielo", "status", "503")
));
```

### Tipos de Alertas Nativos:
- `CIRCUIT_BREAKER_OPEN`: Disjuntor Resilience4j entrou em estado OPEN.
- `FLOW_LATENCY_SLA_BREACH`: Duração do fluxo ultrapassou o limiar de SLA configurado.
- `INTEGRATION_LATENCY_SLA_BREACH`: Dependência externa ultrapassou o SLA de step.
- `DATABASE_POOL_STARVATION`: Esgotamento de conexões pendentes no HikariCP.
- `KAFKA_LAG_HIGH`: Lag do grupo de consumidores ultrapassou o limite.
- `SQS_BACKLOG_HIGH`: Fila SQS acumulou backlog acima do limiar.

### Criando um Notificador Customizado (Ex: Slack/Teams):
Basta registrar um `@Bean` implementando `AlertNotifier`:

```java
@Component
public class SlackAlertNotifier implements AlertNotifier {
    @Override
    public void notify(AlertEvent event) {
        if (event.severity() == AlertSeverity.CRITICAL) {
            // envia mensagem para o canal do time de SRE
        }
    }
}
```

---

## 7. Arquitetura Hexagonal de Engines (`ObservabilityEngine` & Datadog Oficial)

O starter adota o padrão **Ports & Adapters**, desacoplando anotações e modelos da plataforma dos backends de telemetria específicos.

### Seleção Declarativa de Engine:
No `application.yml` de produção (ou via variável de ambiente `OBSERVABILITY_ENGINE=datadog`):

```yaml
observability:
  engine: datadog # datadog (produção) | micrometer (local/testes)
```

### O que o `DatadogObservabilityEngine` entrega nativamente:
1. **Request Flow Maps Dinâmicos**: Injeta tags de span (`flow.name`, `flow.variant`, `flow.step`, `flow.status`, `feature.name`, `feature.variant`) que permitem ao Datadog Trace Explorer projetar e comparar visualmente caminhos de rotas e migrações operacionais.
2. **Supressão de Polling in-JVM com Data Streams Monitoring (DSM)**:
   - Reporta `requiresInJvmLagPolling() == false`.
   - Com o `dd-java-agent` ativo com `-Ddd.data.streams.enabled=true`, o Datadog monitora a latência de ponta a ponta (pathway latency) e o lag de mensageria diretamente nos brokers e filas, eliminando consultas repetitivas de polling in-JVM via `AdminClient` ou `GetQueueAttributes`.
3. **Ponte Não-Intrusiva OpenTelemetry**:
   - Injeta atributos de span via `OtelSpanBridge` capturados automaticamente pelo Datadog Java Agent via `DD_TRACE_OTEL_ENABLED=true`, sem exigir nenhum jar fechado ou proprietário no classpath da aplicação.

