# Guia de Capacidades do Starter (Capabilities Guide)

Este documento descreve detalhadamente cada uma das capacidades do starter, explicando como utilizá-las na prática e demonstrando exemplos de código em estrita conformidade com a especificação técnica corporativa **Candidate Architecture v2**.

> 💡 **Para um passo a passo completo de como e por que usar cada funcionalidade em um microsserviço real, consulte o [Tutorial Completo de Adoção](TUTORIAL.md).**

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
* **`LegType`**: Define o sentido arquitetural e natureza da perna auditada:
  - `INBOUND`: Requisições recebidas pela aplicação (controladores HTTP/REST, endpoints gRPC).
  - `OUTBOUND`: Chamadas síncronas enviadas para microsserviços ou terceiros (clientes HTTP/Feign, WebClient).
  - `CONFIG`: Carregamento e resolução de configurações internas/remotas, segredos e Feature Flags (Vault, Consul, Spring Cloud Config, Unleash).
  - `DATABASE`: Operações de persistência e consultas a bancos relacionais e NoSQL (JPA, JDBC, MongoDB, DynamoDB).
  - `MESSAGING`: Operações de publicação e consumo assíncrono em brokers de mensageria (Kafka, RabbitMQ, SQS).
  - `CACHE`: Leituras, gravações e invalidações em camadas de cache (Redis, Memcached, Caffeine).
  - `INTERNAL`: Computação, transformações pesadas ou processamentos internos críticos em memória.
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

## 4. Enriquecimento de Contexto: Logs (@MDC) vs Métricas e Traces (@ObservationTag)

Para assegurar uma separação arquitetural cristalina de responsabilidades, o starter divide a telemetria em três pilares complementares, cada um com sua anotação dedicada:

| Anotação | Finalidade | Destino / Efeito | Ciclo de Vida |
|---|---|---|---|
| **[`@MDC`](file:///Users/gleidsonfersanp/workspace/observability-spring-boot-starter-project/observability-api/src/main/java/com/empresa/platform/observability/core/annotation/MDC.java)** | **Contexto de Logging (SLF4J MDC)** | Injeta variáveis nos logs estruturados e console. Elimina 100% dos `MDC.put` e `MDC.remove` do código de negócio. | Empilhado (*stack semantics*) no início do método e removido/restaurado em `finally`. |
| **[`@ObservationTag`](file:///Users/gleidsonfersanp/workspace/observability-spring-boot-starter-project/observability-api/src/main/java/com/empresa/platform/observability/core/annotation/ObservationTag.java)** | **Métricas e Tracing Spans (Micrometer)** | Injeta tags na `Observation` ativa (`lowCardinality` em timers Prometheus/Datadog; `highCardinality` em spans OpenTelemetry/Datadog APM). | Associado ao ciclo de vida da `Observation` do Micrometer. |
| **[`@FlowDimension`](file:///Users/gleidsonfersanp/workspace/observability-spring-boot-starter-project/observability-api/src/main/java/com/empresa/platform/observability/core/annotation/FlowDimension.java)** | **Dimensão de Negócio do Fluxo** | Dimensões macro anexadas ao `@TrackFlow`, propagadas automaticamente para métricas de fluxo e MDC. | Durante todo o escopo do `@TrackFlow`. |

---

### 4.1 `@MDC`: Eliminando 100% dos `MDC.put(...)` Manuais

A anotação `@MDC` foi criada especificamente para que as classes de serviço, controladores e adaptadores nunca precisem importar `org.slf4j.MDC`. Ela pode ser aplicada tanto em **nível de método** (com SpEL ou valores estáticos) quanto em **nível de parâmetro**:

```java
// 1. Extração direta de parâmetro
public void processOrder(@MDC("orderId") String orderId, @MDC("tenantId") String tenant) {
    log.info("Processando pedido"); // 'orderId' e 'tenantId' já presentes nos logs!
}

// 2. Extração dinâmica via SpEL a partir de objetos complexos
@MDC(key = "userId", expression = "#request.customer.id")
@MDC(key = "plan", expression = "#request.subscription.planType")
public void subscribe(SubscriptionRequest request) {
    log.info("Registrando assinatura"); // 'userId' e 'plan' presentes nos logs!
}

// 3. Valor estático declarado
@MDC(key = "layer", value = "entrypoint")
public void handle() {
    log.info("Requisição recebida");
}
```

#### Ciclo de Vida e Isolamento Seguro no MDC (Stack Semantics)
* **Preservação de Contexto e Escopos Aninhados**: Se o método `A` define `@MDC("tenant", "empresa-1")` e chama o método `B` que define `@MDC("tenant", "empresa-2")`, o `MdcAspect` armazena o valor anterior em uma pilha in-JVM na thread. Ao término de `B`, o valor `"empresa-1"` é restaurado com precisão cirúrgica.
* **Limpeza Garantida em `finally`**: Mesmo se o método lançar uma exceção de negócio ou erro de infraestrutura, o bloco `finally` do aspecto garante a restauração dos valores ou o `MDC.remove()`.
* **Zero Leakage em Thread Pools**: Garante que threads reutilizadas (`Tomcat Workers`, `@Async`, pools de mensageria) não retenham chaves residuais de requisições anteriores.

---

### 4.2 `@ObservationTag`: Tags em Métricas e Spans de Tracing

Dedicada estritamente ao Micrometer `Observation`:

```java
@Observed(name = "order.payment")
@ObservationTag(key = "payment_method", expression = "#details.method", highCardinality = false)
@ObservationTag(key = "transaction_id", expression = "#details.transactionId", highCardinality = true)
public PaymentResult executePayment(PaymentDetails details) {
    return paymentProcessor.pay(details);
}
```

* **`highCardinality = false`**: Roteado para `lowCardinalityKeyValue`, convertendo-se em tags dimensionais para séries temporais (Prometheus / Datadog Metrics).
* **`highCardinality = true`**: Roteado para `highCardinalityKeyValue`, sendo anexado estritamente aos atributos do Span OpenTelemetry / Datadog APM, prevenindo explosão de cardinalidade e vazamento de memória no TSDB.

---

### 4.3 Arquitetura Canônica de um Fluxo de Negócio Completo

Veja como um fluxo real é instrumentado do Controller HTTP às integrações externas, combinando as anotações sem nenhuma linha manual de telemetria ou logging boiler-plate:

```java
// ==============================================================================
// 1. ENTRYPOINT: Controller REST
// ==============================================================================
@RestController
@RequestMapping("/api/v1/orders")
public class OrderController {

    private final OrderService orderService;

    @PostMapping
    @TrackFlow("order-checkout")
    @LogLeg(target = "order-ingress", type = LegType.INBOUND, includePayload = true,
            mask = { @MaskField(expression = "#request.cardNumber", pattern = MaskPattern.CARD_PARTIAL) })
    @MDC(key = "tenant_id", expression = "#request.tenantId")
    public ResponseEntity<OrderResponse> createOrder(@RequestBody OrderRequest request) {
        log.info("Iniciando checkout de pedido"); 
        // MDC contém: correlation_id, traceId, spanId, flow="order-checkout", tenant_id="acme"
        return ResponseEntity.ok(orderService.checkout(request));
    }
}

// ==============================================================================
// 2. DOMAIN ORCHESTRATION: Serviço de Negócio
// ==============================================================================
@Service
public class OrderService {

    private final InventoryClient inventoryClient;
    private final PaymentClient paymentClient;
    private final OrderRepository orderRepository;

    @TrackStep(name = "order-checkout-orchestration", type = ComponentType.BUSINESS)
    @MDC(key = "order_id", expression = "#request.orderId")
    public OrderResponse checkout(OrderRequest request) {
        log.info("Validando estoque");
        inventoryClient.reserveStock(request.orderId(), request.items());

        log.info("Processando pagamento");
        PaymentReceipt receipt = paymentClient.charge(request.orderId(), request.totalAmount());

        log.info("Persistindo pedido confirmado");
        return orderRepository.save(new Order(request, receipt));
    }
}

// ==============================================================================
// 3. EXTERNAL INTEGRATION: Cliente Feign / REST
// ==============================================================================
@Component
public class PaymentClient {

    private final PaymentFeignClient feignClient;

    @TrackStep(name = "payment-gateway-charge", type = ComponentType.FEIGN)
    @LogLeg(target = "payment-gateway", type = LegType.OUTBOUND)
    public PaymentReceipt charge(@MDC("order_id") String orderId, BigDecimal amount) {
        log.info("Enviando cobrança ao gateway externo"); 
        // Automaticamente gera span child, perna de auditoria OUTBOUND com latência e status,
        // e propaga X-Correlation-Id e W3C traceparent nos headers HTTP downstream.
        return feignClient.authorize(new ChargeRequest(orderId, amount));
    }
}
```

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

---

## 8. Configuração Centralizada de Logging (Logback & `logback.yml`)

O starter fornece uma configuração corporativa padronizada para o Logback, eliminando a necessidade de duplicar arquivos complexos de formatação de log em cada microsserviço.

### 8.1 Carregamento Automático via `logback.yml`

Através do [`ObservabilityLoggingEnvironmentPostProcessor`](file:///Users/gleidsonfersanp/workspace/observability-spring-boot-starter-project/observability-autoconfigure/src/main/java/com/empresa/platform/observability/autoconfigure/logging/ObservabilityLoggingEnvironmentPostProcessor.java), o starter injeta na inicialização do Spring Boot as propriedades padrão de logging definidas em `logback.yml` com prioridade padrão (*lowest precedence*). Isso significa que qualquer microsserviço pode sobrescrever qualquer propriedade em seu próprio `application.yml`:

* **Padrão de Console Colorido (Dev/Local)**:
  Exibe timestamp, thread, nível, logger, e os identificadores canônicos de observabilidade:
  ```text
  %clr(%d{yyyy-MM-dd HH:mm:ss.SSS}){faint} %clr([%15.15t]){faint} %clr(%-5p) %clr(%-40.40logger{39}){cyan} %clr([cid=%X{correlation_id:-none}]){magenta} %clr([%X{traceId:-},%X{spanId:-}]){faint} %clr([flow=%X{flow:-none},step=%X{step:-none}]){yellow} - %m%n%wEx
  ```
* **MDC Transversal**:
  Todas as chaves adicionadas via `@MDC`, `@TrackFlow` (`flow`), `@TrackStep` (`step`, `step.type`) e `@LogLeg` (`leg_*`) aparecem automaticamente.

### 8.2 Inclusão de Defaults XML no Projeto (`observability-logback-defaults.xml`)

Caso a aplicação utilize um `logback-spring.xml` próprio (por exemplo, para configurar appenders customizados para Loki, Splunk ou Datadog Agent), basta importar as definições canônicas do starter:

```xml
<configuration>
    <!-- Importa appenders padronizados e convenções semânticas corporativas -->
    <include resource="com/empresa/platform/observability/logback/observability-logback-defaults.xml"/>

    <!-- Seus appenders específicos podem estender ou reutilizar os padrões -->
    <root level="INFO">
        <appender-ref ref="CONSOLE"/>
    </root>
</configuration>
```

#### Comportamento Multi-Perfil do Logback:
1. **Perfil Local / Desenvolvimento (`!container & !prod`)**:
   Console ANSI colorido com `cid`, `traceId`, `spanId`, `flow` e `step` destacados para leitura ágil pelo desenvolvedor.
2. **Perfil Container / Cloud / Produção (`container | prod`)**:
   Console em JSON estruturado com todos os metadados do MDC indexados como chaves de primeiro nível (`correlation_id`, `trace_id`, `span_id`, `flow`, `step`, `step_type`, `leg_*`), pronto para ingestão nativa por Datadog Logs, Loki, Elasticsearch ou CloudWatch.

