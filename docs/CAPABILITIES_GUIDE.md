# Guia de Capacidades do Starter (Capabilities Guide)

Este documento descreve detalhadamente cada uma das capacidades do starter, explicando como utilizá-las na prática e demonstrando exemplos de código em estrita conformidade com a especificação técnica corporativa **Candidate Architecture v2**.

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

## 2. Rastreamento de Subprocessos (`@TrackStep`) e Catálogo de Componentes (`ComponentType`)

Permite fatiar e atribuir latência a etapas de processamento e dependências de infraestrutura de forma declarativa e não-intrusiva:

```java
@Component
public class PaymentGatewayClient {

    @TrackStep(value = "payment-gateway-charge", type = ComponentType.FEIGN)
    public ChargeResult charge(PaymentRequest request) {
        return feignClient.executeCharge(request);
    }
}
```

### 2.1 Catálogo Canônico de Tipos de Componentes (`ComponentType`):

O enum [`ComponentType`](file:///Users/gleidsonfersanp/workspace/observability-spring-boot-starter-project/observability-api/src/main/java/com/empresa/platform/observability/core/annotation/ComponentType.java) padroniza a categorização arquitetural dos subprocessos (Candidate Architecture v2 - Seção 50):

| Categoria | `ComponentType` | Descrição & Quando Usar |
|---|---|---|
| **HTTP & RPC** | `HTTP` | Clientes REST genéricos (`RestClient`, `WebClient`, `HttpClient` nativo do Java). |
| | `FEIGN` | Clientes declarativos Spring Cloud OpenFeign para APIs REST downstream. |
| | `GRPC` | Chamadas síncronas ou streaming de alta performance via Protocol Buffers / gRPC. |
| **Persistência & Cache** | `DATABASE` | Operações em bancos relacionais ou NoSQL (JPA/Hibernate, consultas SQL, JDBC, MongoDB). |
| | `CACHE` | Leituras e escritas em camadas de cache em memória ou distribuído (Redis, Caffeine, Hazelcast). |
| **Mensageria & Filas** | `KAFKA` | Mensageria genérica Apache Kafka (uso simplificado/unificado). |
| | `KAFKA_PRODUCER` | Publicação ativa de records/eventos em tópicos Kafka (`KafkaTemplate`). |
| | `KAFKA_CONSUMER` | Processamento e consumo de mensagens de tópicos Kafka (`@KafkaListener`). |
| | `SQS` | Mensageria genérica AWS Simple Queue Service. |
| | `SQS_PRODUCER` | Envio de mensagens para filas AWS SQS (`SqsTemplate`). |
| | `SQS_CONSUMER` | Consumo e processamento de mensagens de filas AWS SQS (`@SqsListener`). |
| | `SNS` | Publicação fan-out em tópicos do AWS Simple Notification Service. |
| | `JMS` | Mensageria corporativa empresarial legada (ActiveMQ, IBM MQ, Artemis). |
| **Execução Local & Negócio** | `BUSINESS` | **Padrão do `@TrackStep`**. Regras de negócio essenciais, validações de domínio e cálculos financeiros. |
| | `INTERNAL` | Processamento técnico pesado, transformações de dados in-memory, parsing, hashing ou criptografia. |
| | `EXECUTOR` | Tarefas assíncronas delegadas a thread-pools secundários, `CompletableFuture` ou workers de background. |
| **Padrões de Resiliência** | `RETRY` | Execuções repetidas e tentativas com políticas de backoff operacional. |
| | `CIRCUIT_BREAKER` | Subprocessos protegidos por disjuntores de falha (Resilience4j). |
| | `BULKHEAD` | Isolamento de concorrência e contenção de saturação de threads. |
| | `RATE_LIMITER` | Controle de vazão e estrangulamento de requisições por segundo. |
| **Extensibilidade** | `CUSTOM` | Etapas customizadas de domínio que extrapolam as categorias padronizadas acima. |

### 2.2 Métodos Utilitários do Enum `ComponentType`

O enum disponibiliza métodos utilitários para consultas semânticas em runtime:

```java
ComponentType type = ComponentType.KAFKA_PRODUCER;

type.isIntegration(); // true  (indica se é dependência externa / I/O de rede ou disco)
type.isMessaging();   // true  (indica se pertence ao ecossistema de mensageria assíncrona)
type.isResilience();  // false (indica se é barreira de resiliência: RETRY, CIRCUIT_BREAKER, etc.)
type.isLocal();       // false (indica se é processamento in-memory: BUSINESS, INTERNAL, EXECUTOR)
type.getCategory();   // "MESSAGING" (categoria macro para agrupamento em dashboards analíticos)
```

### 2.3 As 4 Grandes Utilidades Arquiteturais do `ComponentType`

1. **Atribuição Precisa no Latency Attribution Engine**:
   - Alimenta automaticamente a tag dimensional `type` nas métricas canônicas:
     - `observability.flow.component.work.duration` (esforço nominal)
     - `observability.flow.component.attributed.duration` (contribuição normalizada no wall-clock)
   - Permite consultas analíticas transversais em Prometheus/Grafana:
     ```promql
     # Distribuição da latência do fluxo por tipo de componente
     sum by (type) (rate(observability_flow_component_attributed_duration_seconds_sum{flow="OrderCheckout"}[5m]))
     ```
2. **Topologia Dinâmica no Datadog APM & Request Flow Maps**:
   - O `DatadogObservabilityEngine` injeta `step.type` como atributo canônico de span e tag de observação.
   - O Datadog Trace Explorer e o Service Map utilizam esse atributo para desenhar dependências e categorizar visualmente a borda do serviço.
3. **Parametrização Granular de SLAs**:
   - Permite que a esteira de monitoramento aplique limiares distintos de SLA baseados na categoria do componente (ex: tolerância de 20ms para `CACHE`, 300ms para `DATABASE`, 1500ms para `HTTP`).
4. **Proteção Rigorosa Contra Explosão de Cardinalidade**:
   - Por ser um enum fechado e finito, garante **risco zero** de estouro de memória no TSDB (Prometheus / Datadog Metrics), diferentemente de strings livres informadas manualmente por desenvolvedores.

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

### Enums de Suporte a Pernas de Auditoria:
* **`LegType`**: Define o sentido arquitetural da perna auditada:
  - `INBOUND`: Requisições recebidas pela aplicação (controladores HTTP, ouvintes de fila).
  - `OUTBOUND`: Chamadas enviadas para fora da aplicação (clientes HTTP/Feign, publicadores).
* **`LegPhase`**: Fases do ciclo de vida registradas estruturadamente:
  - `START` / `REQUEST`: Início da execução e dados de entrada.
  - `END` / `RESPONSE`: Conclusão com sucesso e dados de retorno.
  - `ERROR`: Falha ou exceção capturada durante a perna.

### Regras de Governança de Dados (LGPD / PCI-DSS):
* **`includePayload = false` por padrão**: Em conformidade com a especificação técnica Candidate v2, o starter **não** audita corpos de mensagens a menos que explicitado com `includePayload = true` ou configurado com `@MaskField`.
* **Padrões de Máscara (`MaskPattern`)**:
  - `CPF_PARTIAL`: `123.456.789-00` $\rightarrow$ `123.***.***-00`
  - `EMAIL_PARTIAL`: `john.doe@example.com` $\rightarrow$ `j***e@example.com`
  - `CARD_PARTIAL`: `4111222233334444` $\rightarrow$ `************4444`
  - `PASSWORD`: `minhasenha` $\rightarrow$ `********`
  - `FULL_MASK`: `dado-sensivel` $\rightarrow$ `***REDACTED***`

---

## 4. Enriquecimento Dinâmico de Tags e MDC com SpEL (`@ObservationTag` e `@FlowDimension`)

Elimina **100% dos `MDC.put(...)` manuais** do código de negócio. Permite extrair atributos de parâmetros de métodos e objetos de retorno usando Spring Expression Language (SpEL) ou ligação direta de parâmetros:

```java
@Observed(name = "order.payment")
@TrackStep(name = "process-payment", type = ComponentType.BUSINESS)
@ObservationTag(key = "tenant_id", expression = "#tenantId", highCardinality = false)
@ObservationTag(key = "order_id", expression = "#orderId", highCardinality = true)
@ObservationTag(key = "status", expression = "#result?.status()", highCardinality = false)
public PaymentResult executePayment(String tenantId, 
                                    String orderId, 
                                    @ObservationTag(key = "customer_cpf") String cpf,
                                    PaymentDetails details) {
    // 💡 ZERO linhas de MDC.put("order_id", ...) ou MDC.remove(...)!
    // As variáveis 'flow', 'step', 'step.type', 'tenant_id', 'order_id' e 'customer_cpf'
    // já estão ativas no MDC do SLF4J para todos os logs executados dentro deste escopo.
    log.info("Processando pagamento da transação");
    return paymentProcessor.pay(details);
}
```

### Ciclo de Vida e Isolamento Seguro no MDC (Stack Semantics)
* **Injeção Pré-Execução**: Parâmetros e expressões SpEL (sem `#result`) são avaliados antes do método e inseridos imediatamente no MDC do SLF4J (`tag.mdc() == true` por padrão).
* **Limpeza Garantida em `finally`**: Ao término da execução (mesmo em caso de `RuntimeException` ou erro de infraestrutura), o `SpelObservationAspect` e o `FlowTrackingAspect` restauram os valores anteriores do MDC ou executam `MDC.remove()`.
* **Segurança Concorrente em Thread Pools**: Garante que pools de threads (`@Async`, `ExecutorService`, TomCat Workers) nunca sofram contaminação de contexto (*context leakage*) entre diferentes requisições.
* **Escopos Aninhados**: Se um método interno sobrescrever temporariamente uma chave (ex: `tenant`), o escopo anterior é preservado e restaurado automaticamente assim que o método interno encerra.

### Proteção Contra Explosão de Cardinalidade
O starter inspeciona as chaves de tags dinâmicas. Chaves identificadas como potencialmente perigosas para séries temporais (`user_id`, `cpf`, `email`, `document`, `order_id`, `account_id`, etc.) são **automaticamente promovidas para tags de alta cardinalidade** (`getHighCardinalityKeyValues()`), sendo visíveis em traces e spans do OpenTelemetry e logs estruturados no MDC, mas protegendo o TSDB (Prometheus/Datadog) contra esgotamento de memória.

---

## 5. Propagação de Contexto e Correlation ID

* **Entrada**: O `CorrelationIdFilter` captura o cabeçalho `X-Correlation-Id` da requisição HTTP. Caso não enviado pelo cliente, um UUID canônico é gerado.
* **MDC**: O `correlation_id` e o `traceId` são injetados no MDC do SLF4J, aparecendo automaticamente em todas as linhas de log.
* **Saída (Feign)**: O `observabilityFeignRequestInterceptor` injeta transparentemente o `X-Correlation-Id` e o `x-trace-id` nas chamadas downstream.
* **Threads Assíncronas**: O `ObservabilityTaskDecorator` clona e restaura o MDC e os snapshots de observação através de *thread-pools* e tarefas `@Async`.

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

### Catálogo Completo de Tipos de Alertas (`AlertType`):
| `AlertType` | Severidade Padrão | Descrição do Evento |
|---|---|---|
| `CIRCUIT_BREAKER_OPEN` | `CRITICAL` | Disjuntor Resilience4j entrou em estado OPEN por taxa de falhas/lentas. |
| `CIRCUIT_BREAKER_DEGRADED` | `WARNING` | Disjuntor entrou em HALF_OPEN (tentando auto-recuperação parcial). |
| `FLOW_LATENCY_SLA_BREACH` | `WARNING` | Duração do fluxo ultrapassou o limiar de SLA fim a fim configurado. |
| `INTEGRATION_LATENCY_SLA_BREACH` | `WARNING` | Subprocesso ou integração externa violou o SLA individual configurado. |
| `FLOW_STEP_INTERRUPTION` | `CRITICAL` | Subprocesso sofreu falha não tratada ou interrupção abrupta no fluxo. |
| `DATABASE_POOL_STARVATION` | `CRITICAL` | Esgotamento crítico de conexões com threads bloqueadas no HikariCP. |
| `KAFKA_LAG_HIGH` | `WARNING` | Acúmulo de lag do grupo de consumidores Kafka acima do limiar. |
| `SQS_BACKLOG_HIGH` | `WARNING` | Fila SQS acumulou backlog de mensagens acima do limiar configurado. |

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
1. **Request Flow Maps Dinâmicos**: Injeta tags de span (`flow.name`, `flow.variant`, `flow.step`, `step.type`, `flow.status`, `feature.name`, `feature.variant`) que permitem ao Datadog Trace Explorer projetar e comparar visualmente caminhos de rotas e migrações operacionais.
2. **Supressão de Polling in-JVM com Data Streams Monitoring (DSM)**:
   - Reporta `requiresInJvmLagPolling() == false`.
   - Com o `dd-java-agent` ativo com `-Ddd.data.streams.enabled=true`, o Datadog monitora a latência de ponta a ponta (pathway latency) e o lag de mensageria diretamente nos brokers e filas, eliminando consultas repetitivas de polling in-JVM via `AdminClient` ou `GetQueueAttributes`.
3. **Ponte Não-Intrusiva OpenTelemetry**:
   - Injeta atributos de span via `OtelSpanBridge` capturados automaticamente pelo Datadog Java Agent via `DD_TRACE_OTEL_ENABLED=true`, sem exigir nenhum jar fechado ou proprietário no classpath da aplicação.
