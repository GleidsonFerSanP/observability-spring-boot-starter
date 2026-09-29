# Esquema de Telemetria e Convenções Canônicas (Telemetry Schema)

Este documento especifica o catálogo unificado de telemetria produzido pelo **Observability Spring Boot Starter**, garantindo padronização corporativa transversal entre dezenas de microsserviços e compatibilidade nativa com backends analíticos modernos como **Prometheus**, **Grafana**, **Datadog**, **OpenTelemetry Collector**, **Splunk** e **Elasticsearch/Loki**.

---

## 1. Princípios de Governança de Telemetria

1. **Convenção Semântica OpenTelemetry & Micrometer**:
   - Métricas utilizam o padrão dimensional com nomes em notação de ponto (`observability.flow.*`) ou compatibilidade Prometheus com sufixos de unidade (`_seconds`, `_total`).
   - Tags/Dimensões são estritamente de **baixa cardinalidade** para proteger os bancos de séries temporais (TSDB) contra estouro de memória (*cardinality explosion*).
2. **Separação de Preocupações de Cardinalidade**:
   - **Métricas**: Agrupamentos finitos conhecidos em tempo de compilação ou configuração (nome do fluxo, status, componente, variante de deploy, tipo de integração).
   - **Logs e Traces (MDC / Span Attributes)**: Identificadores dinâmicos e de alta cardinalidade (`correlation_id`, `userId`, `accountNumber`, payloads mascarados).
3. **Consistência Semântica Corporativa**:
   - Um dashboard corporativo de "Flow Analytics" ou "Resilience" consome os mesmos nomes de métricas e tags independentemente da linguagem, tecnologia ou squad responsável pelo microsserviço.

---

## 2. Catálogo de Métricas Canônicas

### 2.1. Métricas do Latency Attribution Engine (Candidate Architecture v2)

| Métrica | Tipo | Unidade | Tags / Dimensões | Descrição Semântica |
|---|---|---|---|---|
| `observability.flow.duration` | Timer | Segundos | `flow`, `status`, `variant`, `feature` | Tempo total de relógio (*wall-clock*) percebido pelo cliente desde o início até o término do fluxo. |
| `observability.flow.component.work.duration` | Timer | Segundos | `flow`, `component`, `status`, `variant`, `feature` | Duração nominal de esforço gasto em um subprocesso/componente específico (soma de durações reais). |
| `observability.flow.component.attributed.duration` | Timer | Segundos | `flow`, `component`, `status`, `variant`, `feature` | Duração temporal atribuída a este componente na linha do tempo concorrente (*normalized wall-clock contribution*). |
| `observability.flow.unattributed.duration` | Timer | Segundos | `flow`, `variant`, `feature` | Latência interna não atribuída a nenhum `@TrackStep` (tempo de computação pura, serialização ou steps não instrumentados). |
| `observability.flow.parallel.overlap.duration` | Timer | Segundos | `flow`, `variant`, `feature` | Tempo economizado graças à concorrência / paralelismo (Diferença entre o esforço nominal bruto e o tempo de relógio consumido). |
| `observability.flow.interruption` | Counter | Unidades | `flow`, `step`, `error`, `variant`, `feature` | Quantidade de interrupções abruptas ou falhas não tratadas ocorridas durante a execução de fluxos de negócio. |

### 2.2. Métricas de Compatibilidade Retroativa (v1 Specification)

| Métrica | Tipo | Unidade | Tags / Dimensões | Descrição Semântica |
|---|---|---|---|---|
| `flow_total_duration_seconds` | Timer | Segundos | `flow`, `status`, `variant`, `feature` | Métrica legada do tempo total do fluxo (idêntica ao `observability.flow.duration`). |
| `flow_slice_duration_seconds` | Timer | Segundos | `flow`, `step`, `type`, `variant`, `feature` | Duração de cada fatia/etapa do fluxo registrada individualmente. |
| `flow_interruption_total` | Counter | Unidades | `flow`, `step`, `error_type` | Contador legado de interrupções de fluxo. |

### 2.3. Métricas de Alarmística e SLA

| Métrica | Tipo | Unidade | Tags / Dimensões | Descrição Semântica |
|---|---|---|---|---|
| `alerts_triggered_total` | Counter | Unidades | `type`, `severity`, `source`, `target` | Quantidade de alertas gerados e despachados pelo `AlertDispatcher` da plataforma. |

### 2.4. Métricas de Resiliência (Resilience4j)

| Métrica | Tipo | Unidade | Tags / Dimensões | Descrição Semântica |
|---|---|---|---|---|
| `resilience4j.circuitbreaker.calls` | Counter | Unidades | `name`, `kind`, `state` | Chamadas executadas pelo disjuntor (sucessos, falhas, bloqueios por estado OPEN). |
| `resilience4j.circuitbreaker.state` | Gauge | Estado ordinal | `name`, `state` | Estado operacional atual do Circuit Breaker (`0=CLOSED`, `1=OPEN`, `2=HALF_OPEN`, `3=DISABLED`, `4=METRICS_ONLY`). |
| `resilience4j.circuitbreaker.failure.rate` | Gauge | Porcentagem | `name` | Taxa de falha atual calculada na janela deslizante do disjuntor. |
| `resilience4j.circuitbreaker.slow.call.rate` | Gauge | Porcentagem | `name` | Taxa de chamadas lentas registradas na janela de amostragem. |

### 2.5. Métricas de Conectividade JDBC (HikariCP)

| Métrica | Tipo | Unidade | Tags / Dimensões | Descrição Semântica |
|---|---|---|---|---|
| `hikaricp.connections.active` | Gauge | Conexões | `pool` | Conexões atualmente em uso por transações ativas. |
| `hikaricp.connections.idle` | Gauge | Conexões | `pool` | Conexões estabelecidas e prontas para uso imediato no pool. |
| `hikaricp.connections.pending` | Gauge | Threads | `pool` | Quantidade de threads do servidor bloqueadas aguardando liberação de uma conexão. |
| `hikaricp.connections.timeout` | Counter | Unidades | `pool` | Conexões que atingiram timeout de obtenção (`connectionTimeout`). |

---

## 3. Esquema do MDC (Mapped Diagnostic Context)

O starter gerencia e propaga automaticamente chaves canônicas de contexto em todas as linhas de log produzidas pela aplicação e pelas threads de workers assíncronos (`ObservabilityTaskDecorator`).

### 3.1. Chaves de Contexto Global e de Rastreabilidade

| Chave MDC | Formato / Exemplo | Injetado Por | Descrição |
|---|---|---|---|
| `correlation_id` / `cid` | `c3a9f0e1-4b2e-41d1-b51f-6a7f98b1c4e2` | `CorrelationIdFilter` / Clientes HTTP / Mensageria | Identificador único de correlação fim a fim entre todos os microsserviços envolvidos na transação. |
| `traceId` | `4bf92f3577b34da6a3ce929d0e0e4736` | OpenTelemetry / W3C TraceContext | Identificador distribuído de rastreamento do OpenTelemetry (W3C standard). |
| `spanId` | `00f067aa0ba902b7` | OpenTelemetry / Micrometer Tracing | Identificador do span ativo na thread atual. |
| `flow` | `user-creation-flow` | `FlowTrackingAspect` | Nome do fluxo de negócio orquestrado ativo na thread. |
| `step` | `step-validate-user` | `FlowTrackingAspect` | Nome do subprocesso ou step atualmente em execução. |
| `variant` | `new` ou `legacy` | `FlowContext` / Feature Flags | Identificador da variante da arquitetura/código (útil em canary releases e migrações v1->v2). |
| `feature.name` | `payment-v2` | `FlowFeatureEvaluationListener` | Nome da feature flag associada à execução. |
| `feature.variant` | `new` ou `treatment` | `FlowFeatureEvaluationListener` | Variante avaliada da feature flag. |

### 3.2. Chaves Temporárias de Auditoria de Integração (`@LogLeg`)

Durante o ciclo de vida de uma perna de integração externa auditada por `@LogLeg`, o MDC é enriquecido transitoriamente e limpo no bloco `finally`:

| Chave MDC | Exemplo | Descrição |
|---|---|---|
| `leg_number` | `1`, `2` | Número sequencial da perna de integração dentro da thread atual. |
| `leg_parent` | `1` | Número da perna pai (em caso de chamadas encadeadas). |
| `leg_type` | `OUTBOUND`, `DATABASE`, `MESSAGING` | Categoria arquitetural da integração. |
| `leg_target` | `CustomerFeignClient` | Destino lógico ou físico invocado. |
| `leg_phase` | `REQUEST`, `RESPONSE` | Fase do ciclo de vida da chamada. |
| `leg_duration_ms` | `142` | Tempo decorrido em milissegundos da chamada externa. |
| `leg_status` | `SUCCESS`, `FAILED` | Resultado da operação. |

### 3.3. Convenção de Tags de Spans por Engine (Datadog APM vs Micrometer)

A tabela abaixo compara os atributos de spans injetados conforme o adaptador ativo da `ObservabilityEngine`:

| Conceito de Telemetria | `DatadogObservabilityEngine` (Produção) | `MicrometerObservabilityEngine` (Local / CI) | Finalidade no Observabilidade |
| :--- | :--- | :--- | :--- |
| Nome do Fluxo | `flow.name` e `flow` | `flow` | Identificador raiz do `@TrackFlow` no Datadog Request Flow Map e Prometheus. |
| Variante de Rota | `flow.variant` e `variant` | `variant` | Segregação de migrações operacionais e canaries (`legacy` vs `new`). |
| Nome do Subprocesso | `flow.step` e `step` | `step` | Nó filho do `@TrackStep` na árvore de execução. |
| Tipo de Integração | `step.type` | `step.type` | Categoria arquitetural (`INTEGRATION_HTTP`, `DATABASE`, `CACHE`, etc.). |
| Status de Conclusão | `flow.status` | `flow.status` | Estado final (`SUCCESS`, `DEGRADED_FALLBACK`, `INTERRUPTED`). |
| Feature Flag Nome | `feature.name` | `feature` | Nome da feature flag que conduziu a rota. |
| Feature Flag Variante | `feature.variant` | `variant` | Variante avaliada da feature flag. |
| Tipo de Exceção | `error.type` | `error.class` | Classe de erro capturada em falhas. |
| Mensagem de Erro | `error.message` | `error.message` | Mensagem detalhada da falha. |

---

## 4. Esquema de Logs Estruturados

### 4.1. Formato Padrão de Log Estruturado (JSON)

As aplicações configuradas com saída JSON emitem eventos estruturados no seguinte formato:

```json
{
  "timestamp": "2026-09-29T15:30:00.123Z",
  "level": "INFO",
  "logger": "com.empresa.service.UserService",
  "thread": "http-nio-8080-exec-3",
  "message": "Usuário criado com sucesso no banco de dados.",
  "context": {
    "correlation_id": "8f3b2361-b541-479c-9c71-2f7413693175",
    "traceId": "4bf92f3577b34da6a3ce929d0e0e4736",
    "spanId": "00f067aa0ba902b7",
    "flow": "user-creation-flow",
    "step": "step-save-database",
    "variant": "new",
    "feature.name": "user-v2",
    "feature.variant": "new"
  }
}
```

### 4.2. Logger Especializado de Auditoria Forense (`AUDIT_LEG_LOGGER`)

O logger dedicado `AUDIT_LEG_LOGGER` emite registros auditáveis de chamadas de borda com mascaramento automático de dados sensíveis (LGPD / PCI-DSS):

#### Evento de Envio (`LEG_REQUEST`):
```json
{
  "event": "LEG_REQUEST",
  "legNumber": 1,
  "type": "OUTBOUND",
  "phase": "REQUEST",
  "target": "CardAuthorizerClient",
  "method": "authorizePayment",
  "request": {
    "cardHolder": "Gleidson Santos",
    "cardNumber": "************1234",
    "cvv": "***",
    "amount": 250.00
  }
}
```

#### Evento de Retorno (`LEG_RESPONSE`):
```json
{
  "event": "LEG_RESPONSE",
  "legNumber": 1,
  "type": "OUTBOUND",
  "phase": "RESPONSE",
  "target": "CardAuthorizerClient",
  "status": "SUCCESS",
  "durationMs": 185,
  "response": {
    "authorizationCode": "AUTH-987654",
    "status": "APPROVED"
  }
}
```

---

## 5. Esquema do Evento de Alerta (`AlertEvent`)

Alertas despachados via webhook, fila ou log obedecem a uma estrutura JSON rigorosamente tipada:

```json
{
  "alertId": "e2831f24-5d53-4dc9-98cf-3c582f03fa19",
  "timestamp": "2026-09-29T15:30:45.890Z",
  "type": "CIRCUIT_BREAKER_OPEN",
  "severity": "CRITICAL",
  "source": "Resilience4j",
  "target": "externalPartnerClient",
  "message": "Disjuntor 'externalPartnerClient' abriu (OPEN) após taxa de falhas exceder o limiar de tolerância. Tráfego bloqueado.",
  "currentValue": 1.0,
  "thresholdValue": 0.0,
  "metadata": {
    "fromState": "HALF_OPEN",
    "toState": "OPEN",
    "failureRate": 75.0,
    "numberOfFailedCalls": 15,
    "correlation_id": "8f3b2361-b541-479c-9c71-2f7413693175"
  }
}
```

### Tipos Canônicos de Alerta (`AlertType`)

1. `CIRCUIT_BREAKER_OPEN` (Severidade: `CRITICAL`): Disjuntor abriu devido a falhas contínuas na integração remota.
2. `CIRCUIT_BREAKER_DEGRADED` (Severidade: `WARNING`): Disjuntor entrou em modo de teste (`HALF_OPEN`).
3. `INTEGRATION_LATENCY_SLA_BREACH` (Severidade: `WARNING`): Latência de uma chamada de integração ultrapassou o limiar de alerta configurado.
4. `FLOW_LATENCY_SLA_BREACH` (Severidade: `CRITICAL`): O tempo total do fluxo ponta a ponta excedeu o SLA corporativo.
5. `DATABASE_POOL_STARVATION` (Severidade: `CRITICAL`): Pool do HikariCP esgotado com threads aguardando na fila além do limiar.
6. `FLOW_STEP_INTERRUPTION` (Severidade: `CRITICAL`): Subprocesso de negócio falhou de forma não recuperável.
7. `KAFKA_LAG_HIGH` (Severidade: `WARNING`): Consumo de tópicos Kafka acumulando lag anormal.
8. `SQS_BACKLOG_HIGH` (Severidade: `WARNING`): Backlog de mensagens pendentes na fila SQS elevado.
