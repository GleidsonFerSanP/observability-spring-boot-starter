# Arquitetura do Observability Spring Boot Starter

Este documento detalha o desenho técnico, os padrões de engenharia de software e as decisões arquiteturais adotadas no **Observability Spring Boot Starter**, em estrita conformidade com a especificação técnica corporativa **Candidate Architecture v2**.

---

## 1. Princípios Arquiteturais

1. **80-95% Automático / 5-20% Declarativo**:
   - Toda a infraestrutura técnica (HTTP, RestClient, Feign, JDBC, HikariCP, Kafka, SQS, Resilience4j, JVM, Logs Estruturados, Context Propagation) é descoberta e instrumentada automaticamente.
   - Anotações são reservadas estritamente para enriquecimento semântico de negócio onde o framework não possui capacidade de inferência (`@TrackFlow`, `@TrackStep`, `@LogLeg`, `@ObservationTag`).
2. **Desacoplamento e Não-Intrusividade**:
   - Nenhuma classe de negócio manipula `MeterRegistry`, `Tracer`, `Span` ou `MDC` diretamente.
   - Desligar o starter (`observability.enabled=false`) remove 100% dos aspectos e filtros sem alterar o comportamento funcional das aplicações.
3. **Imutabilidade Matemática de Latência**:
   - Processos paralelos jamais são somados linearmente. A duração percebida pelo cliente (`wall_clock`) é o teto físico do fluxo.
4. **Proteção Nativa de Dados e Governança**:
   - Logs de payload operam como *opt-in* (`includePayload = false`), garantindo conformidade com LGPD e PCI-DSS.
   - Proteção de cardinalidade contra chaves de alta cardinalidade em TSDB.

---

## 2. Estrutura Modular Multi-Módulo

Seguindo o padrão de mercado oficial do Spring Boot (Spring Framework Reference Guide), o projeto é rigorosamente desacoplado em módulos com responsabilidades isoladas:

```text
observability-spring-boot-starter-project/
├── pom.xml                                          # Parent POM (BOM & gerência unificada de dependências)
│
├── observability-api/                               # MÓDULO API (Zero dependências externas, pure Java)
│   ├── annotation/                                  # Anotações de domínio: @TrackFlow, @TrackStep, @FlowDimension, @LogLeg, @ObservationTag
│   └── dimension/                                   # Contratos de dimensão: FlowDimensions
│
├── observability-core/                              # MÓDULO CORE (POJO / Framework-agnostic)
│   ├── flow/                                        # Modelos temporais: FlowExecution, StepExecution, LatencyAttributionEngine, FlowContext
│   ├── leg/                                         # Auditoria forense: SpelMaskingService, LegContext
│   ├── correlation/                                 # Contexto distribuído: CorrelationContext (W3C TraceContext, MDC)
│   ├── alerting/                                    # Barramento de eventos: AlertDispatcher, AlertEvent, AlertNotifier
│   ├── cardinality/                                 # Governança de cardinalidade: CardinalityPolicy
│   ├── engine/                                      # Engine SPI & adaptadores: ObservabilityEngine, DatadogObservabilityEngine, MicrometerObservabilityEngine
│   └── runtime/                                     # Detecção de agentes e engines: TracingRuntimeDetector
│
├── observability-autoconfigure/                     # MÓDULO AUTOCONFIGURE (Spring Boot AutoConfiguration)
│   ├── aspect/                                      # AOP: FlowTrackingAspect, SpelObservationAspect, LegLoggingAspect
│   ├── async/                                       # Propagação: ObservabilityTaskDecorator
│   ├── correlation/                                 # Filtro HTTP Servlet: CorrelationIdFilter
│   ├── feign/                                       # Interceptor Feign: FeignObservabilityAutoConfiguration
│   ├── kafka/                                       # BeanPostProcessors: KafkaObservabilityAutoConfiguration
│   ├── sqs/                                         # Auto-instrumentação: SqsObservabilityAutoConfiguration
│   ├── jdbc/                                        # Pool Watchdog: JdbcObservabilityAutoConfiguration
│   ├── resilience/                                  # Circuit Breaker: ResilienceObservabilityAutoConfiguration
│   ├── validator/                                   # Fail-fast de topologia: ObservabilityTopologyValidator
│   └── resources/META-INF/spring/
│       └── org.springframework.boot.autoconfigure.AutoConfiguration.imports
│
├── observability-spring-boot-starter/               # STARTER UMBRELLA (Dependency aggregator)
│   └── pom.xml                                      # Exporta api + core + autoconfigure + micrometer
│
├── observability-test/                              # MÓDULO DE TESTE (Harness e Asserções)
│   └── src/main/java/                               # ApplicationContextRunner utilities, TopologyAssertions
│
└── observability-legacy-compat/                     # COMPATIBILIDADE RETROATIVA
    └── src/main/java/                               # Bridges e adaptadores para versões de transição
```

---

## 3. Descoberta Automática de Topologia (Auto-Discovery)

O starter inspeciona o classpath em runtime utilizando `@ConditionalOnClass` e `@ConditionalOnWebApplication`. Se uma biblioteca não estiver presente, a autoconfiguração associada sequer é carregada:

```mermaid
flowchart TD
    App[Aplicação Spring Boot] --> Imports[AutoConfiguration.imports]
    Imports --> ObsAuto[ObservabilityAutoConfiguration]
    
    Imports --> CheckFeign{Feign no Classpath?}
    CheckFeign -- Sim --> FeignAuto[FeignObservabilityAutoConfiguration]
    CheckFeign -- Não --> SkipFeign[Ignora Feign]

    Imports --> CheckKafka{KafkaTemplate no Classpath?}
    CheckKafka -- Sim --> KafkaAuto[KafkaObservabilityAutoConfiguration]
    CheckKafka -- Não --> SkipKafka[Ignora Kafka]

    Imports --> CheckSQS{SqsTemplate no Classpath?}
    CheckSQS -- Sim --> SqsAuto[SqsObservabilityAutoConfiguration]
    CheckSQS -- Não --> SkipSQS[Ignora SQS]

    Imports --> CheckResilience{Resilience4j no Classpath?}
    CheckResilience -- Sim --> ResAuto[ResilienceObservabilityAutoConfiguration]
    CheckResilience -- Não --> SkipRes[Ignora Resilience]

    Imports --> CheckJDBC{HikariCP no Classpath?}
    CheckJDBC -- Sim --> JdbcAuto[JdbcObservabilityAutoConfiguration]
    CheckJDBC -- Não --> SkipJDBC[Ignora JDBC]
```

### Regras de Ativação e Desativação Condicional
* **Master Switch**: `@ConditionalOnProperty(prefix = "observability", name = "enabled", havingValue = "true", matchIfMissing = true)` presente em todas as classes `@AutoConfiguration`.
* **Granular Switches**: Cada `@Bean` sensível possui seu próprio toggle (ex: `observability.leg-logging.enabled`, `observability.flow-tracking.enabled`).
* **Extensibilidade (`@ConditionalOnMissingBean`)**: Se a aplicação cliente prover um `@Bean` customizado (ex: um `AlertDispatcher` conectado ao PagerDuty corporativo), o starter recua e preserva a definição do desenvolvedor.

---

## 4. Motor de Atribuição de Latência (Latency Attribution Engine)

### O Problema do Paralelismo
Em arquiteturas modernas com `CompletableFuture`, chamadas paralelas (ex: enriquecimento simultâneo em 3 APIs) distorcem gráficos de latência tradicionais:
- Se uma chamada dura 100ms e dispara 3 tarefas de 80ms em paralelo, a soma linear do trabalho é $240\text{ ms}$, excedendo os $100\text{ ms}$ de tempo de relógio (*wall-clock*).
- Starters ingênuos geram frações negativas ou usam `Math.max(0, ...)` que mascaram a latência e corrompem dashboards de composição (pie charts e stacked graphs).

### Invariantes Matemáticas Canônicas
O [`LatencyAttributionEngine`](file:///Users/gleidsonfersanp/workspace/observability-spring-boot-starter-project/observability-spring-boot-starter-core/src/main/java/com/empresa/platform/observability/core/flow/LatencyAttributionEngine.java) divide a medição em **três dimensões**:
1. **Wall-Clock Duration (`observability.flow.duration`)**: Latência real percebida pelo chamador.
2. **Work Duration (`observability.flow.component.work.duration`)**: Duração bruta consumida por cada subprocesso, independente de sobreposição.
3. **Attributed Duration (`observability.flow.component.attributed.duration`)**: Latência atribuída normalizada para composição de custo.

$$\text{Se } \sum \text{work}_i \le \text{wallClock}:$$
$$\text{attributed}_i = \text{work}_i$$
$$\text{unattributed} = \text{wallClock} - \sum \text{work}_i$$

$$\text{Se } \sum \text{work}_i > \text{wallClock}:$$
$$\text{attributed}_i = \text{round}\left(\text{wallClock} \times \frac{\text{work}_i}{\sum \text{work}_k}\right)$$
$$\text{parallel\_overlap} = \sum \text{work}_i - \text{wallClock}$$

**Garantia Arquitetural**: $\sum \text{attributed}_i \approx \text{wallClock}$ em 100% dos cenários.

---

## 5. Modelo de Execução Concorrente e Propagação de Contexto

Para evitar vazamentos de memória e perdas de contexto em threads assíncronas:

```mermaid
sequenceDiagram
    autonumber
    actor Caller as Cliente HTTP
    participant Filter as CorrelationIdFilter
    participant Decorator as ObservabilityTaskDecorator
    participant Pool as ThreadPool / Virtual Thread
    participant Feign as FeignCorrelationInterceptor
    participant Downstream as Serviço Externo

    Caller->>Filter: GET /resource (Header: X-Correlation-Id ou nulo)
    Filter->>Filter: Inicializa CorrelationContext & MDC
    Filter->>Decorator: Submete tarefa assíncrona
    Note over Decorator: Captura snapshot do ContextRegistry + Cópia do MDC Map
    Decorator->>Pool: Executa Runnable encapsulado
    Note over Pool: Aplica ContextSnapshot e MDC na thread trabalhadora
    Pool->>Feign: Chamada HTTP downstream
    Feign->>Downstream: Injeta X-Correlation-Id + traceparent (W3C)
    Downstream-->>Feign: Resposta
    Note over Pool: Limpa MDC e restaura thread no bloco finally
    Pool-->>Filter: Conclusão
    Filter-->>Caller: Retorna resposta com cabeçalho X-Correlation-Id
    Note over Filter: CorrelationContext.clear() limpa thread principal
```

* **`FlowExecution` & `StepExecution`**: Estruturados com coleções concorrentes (`ConcurrentLinkedQueue`), garantindo que dezenas de threads paralelas registrem etapas simultaneamente sem concorrência bloqueante ou *ConcurrentModificationException*.
* **`ObservabilityTaskDecorator`**: Implementa `org.springframework.core.task.TaskDecorator`, encapsulando tarefas via `ContextSnapshotFactory` e sincronizando cópia profunda do mapa MDC com restauração garantida em `finally`.

---

## 6. Arquitetura Hexagonal com Engine SPI e Datadog Oficial Corporativo

Para atender à diretriz corporativa de padronização do **Datadog** em ambientes produtivos sem gerar **Vendor Lock-in** e mantendo total autonomia de desenvolvimento local com ferramentas de código aberto (Prometheus, Jaeger, Grafana, Loki), o starter adota uma **Arquitetura Hexagonal (Ports and Adapters)**:

```mermaid
flowchart TD
    subgraph CoreDomain ["Core Domain & Anotações de Negócio"]
        FlowAnns["@TrackFlow, @TrackStep, @ObservationTag, @LogLeg"]
        FlowContext["FlowContext & FlowDimensions"]
        SPIPort["Port SPI: ObservabilityEngine"]
        FlowAnns --> FlowContext
        FlowContext --> SPIPort
    end

    subgraph Adapters ["Adaptadores Plugáveis"]
        DDEngine["DatadogObservabilityEngine (Oficial Corporativo)"]
        MicrEngine["MicrometerObservabilityEngine (Referência / Local Dev)"]
        SPIPort -.-> DDEngine
        SPIPort -.-> MicrEngine
    end

    subgraph TelemetryBackends ["Backends de Telemetria"]
        DDEngine -->|"Datadog APM & Request Flow Maps"| DDCloud["Datadog Cloud / dd-java-agent"]
        MicrEngine -->|"OTLP HTTP :4318 / Actuator Scrape"| LocalStack["Prometheus / Jaeger / Loki"]
    end
```

### Contrato da SPI (`ObservabilityEngine`)
Localizada em `com.empresa.platform.observability.core.engine`:
- `EngineCapabilities getCapabilities()`: Descoberta de recursos em tempo de execução (Service Map, Request Flow Map, DSM, in-JVM lag polling).
- `FlowScope startFlow(...)` e `void completeFlow(...)`: Gerenciamento do ciclo de vida e dimensões do fluxo.
- `StepScope startStep(...)` e `void completeStep(...)`: Medição e marcação de subprocessos.
- `void recordFlowInterruption(...)` e `void recordStepInterruption(...)`: Marcação precisa de falhas e causadores.
- `void tagAttribute(key, value)`: Enriquecimento contextual seguro.

### Capacidades dos Adaptadores
| Recurso | `DatadogObservabilityEngine` | `MicrometerObservabilityEngine` |
| :--- | :--- | :--- |
| **Padrão de Ativação** | Produção corporativa (`observability.engine=datadog`) | Local, CI e Testes (`observability.engine=micrometer`) |
| **Tags de Spans** | `flow.name`, `flow.variant`, `flow.step`, `flow.status`, `feature.name` | `flow`, `step`, `variant`, `feature` |
| **Request Flow Map** | Suportado nativamente no Trace Explorer | Via queries PromQL no Grafana |
| **Data Streams (DSM)** | Ativo via Datadog Agent (`requiresInJvmLagPolling() == false`) | Requer Binders in-JVM (`requiresInJvmLagPolling() == true`) |
| **Ponte de Rastreio** | OpenTelemetry API Bridge (`DD_TRACE_OTEL_ENABLED=true`) | Micrometer Observation API |
| **Zero Vendor Jars** | Sim (opera via padrões abertos e JVM agent) | Sim (open-source standard) |

---

## 7. Política "Single-Producer Per Signal" e OpenTelemetry API Pura

### 7.1 O Risco da Concorrência de Motores de Telemetria
Em ambientes corporativos com observabilidade distribuída, um dos erros mais comuns e danosos é a inclusão indiscriminada de SDKs de telemetria na mesma aplicação:
* **Concorrência de Tracers na JVM**: Se a aplicação empacotar `opentelemetry-sdk` ou `micrometer-tracing-bridge-otel` e rodar sob o `dd-java-agent`, dois motores de tracing competem pelos interceptadores de thread e headers HTTP/W3C. O resultado são traces quebrados (spans desconexos), perda do `parent_id` e sobrecarga de CPU de até 30%.
* **Duplicação de Ingestão e Explosão de Faturas SaaS**: Exportar as mesmas métricas para Datadog e Prometheus simultaneamente sem governança dobra o tráfego de rede e o custo de ingestão em plataformas de nuvem.

### 7.2 A Regra da OpenTelemetry API Pura
Para eliminar esse risco, o starter adota a seguinte diretriz de dependências:
1. O starter depende **exclusivamente da especificação aberta `io.opentelemetry:opentelemetry-api`**, sem embutir `opentelemetry-sdk` nem exportadores OTLP.
2. Em produção sob Datadog, o agente `-javaagent:dd-java-agent.jar` (com a flag `DD_TRACE_OTEL_ENABLED=true`) injeta a implementação canônica do Tracer. A aplicação manipula spans e atributos de forma agnóstica via API aberta, enquanto o agente garante o envio confiável sem overhead duplicado.
3. Em testes ou ambientes locais, bridges abertos podem ser ativados de forma controlada sem poluir o binário de produção.

### 7.3 Validação de Topologia e Trava Fail-Fast
O módulo `observability-autoconfigure` implementa o `ObservabilityTopologyValidator` e o `TracingRuntimeDetector`:
* **Detecção de Agentes Concorrentes**: Inspeciona os argumentos de runtime da JVM (`ManagementFactory.getRuntimeMXBean().getInputArguments()`). Caso múltiplos agentes incompatíveis (ex: Datadog Agent + OTel Java Agent) sejam detectados, o startup emite alertas imediatos.
* **Perfis de Métricas (`observability.profile`)**:
  - `datadog` (padrão): Ativa apenas o ecossistema Datadog.
  - `prometheus`: Ativa apenas o registro do Prometheus para scraping.
* **Dual-Export Guard**: Se a aplicação carregar acidentalmente múltiplos registries incompatíveis sem a permissão explícita `observability.metrics.allow-dual-export: true`, o inicializador lança `IllegalStateException` no boot (*fail-fast*), bloqueando a inicialização antes que custos indevidos sejam gerados.

---

## 8. Governança Estrita de Cardinalidade (`CardinalityPolicy`)

A explosão de cardinalidade (*cardinality bomb*) ocorre quando atributos com alta variabilidade (IDs de usuário, CPFs, tokens UUID, timestamps) são injetados indevidamente como tags de métricas em bancos de séries temporais (TSDB), causando esgotamento de memória (OOM) no Prometheus ou custos astronômicos de métricas customizadas no Datadog.

O starter estabelece a separação rígida implementada em [`CardinalityPolicy`](file:///Users/gleidsonfersanp/workspace/observability-spring-boot-starter-project/observability-core/src/main/java/com/empresa/platform/observability/core/cardinality/CardinalityPolicy.java):

```mermaid
flowchart LR
    Attr["Atributo / Tag de Negócio"] --> Check{Alta Cardinalidade? (IDs, Tokens, Mensagens)}
    Check -- Não (Baixa: status, variant, feature) --> Metrics["MeterRegistry (Prometheus / Datadog Metrics)"]
    Check -- Sim (Alta: userId, orderId) --> Spans["Span Attributes (OTel / Datadog Trace Explorer)"]
    Check -- Sim --> Logs["MDC / Structured Logs (Grafana Loki)"]
```

1. **Baixa Cardinalidade (Métricas / TSDB)**: Apenas dimensões finitas e previsíveis (`flow.name`, `flow.variant`, `step.name`, `status`, `feature.name`).
2. **Alta Cardinalidade (Tracing & Logs)**: Expressões SpEL avaliadas dinamicamente via `@ObservationTag(highCardinality = true)` são roteadas exclusivamente para os atributos de Span e campos de log estruturado, permitindo consultas forenses exatas sem degradar o TSDB.


