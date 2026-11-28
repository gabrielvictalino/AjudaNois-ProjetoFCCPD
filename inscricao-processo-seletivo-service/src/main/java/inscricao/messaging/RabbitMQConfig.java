package inscricao.messaging;

import java.util.Map;

/**
 * Topologia de exchanges, filas e routing keys do Microsserviço 4 no RabbitMQ.
 */
public final class RabbitMQConfig {

    private RabbitMQConfig() {
    }

    /** Exchange principal de eventos de domínio (topic, durável). */
    public static final String EXCHANGE_EVENTOS = "ajudanois.events";

    /** Dead Letter Exchange para mensagens malformadas ou que esgotaram tentativas de retry. */
    public static final String EXCHANGE_DLX = "ajudanois.dlx";
    public static final String QUEUE_DLQ = "ms4.dlq";
    public static final String ROUTING_KEY_DLQ = "ms4.dlq";

    /** Topologia de retry com atraso (TTL) para eventos fora de ordem ou falhas transitórias. */
    public static final String EXCHANGE_RETRY = "ajudanois.retry";
    public static final String EXCHANGE_RETRY_RETURN = "ms4.retry.return";
    public static final String QUEUE_RETRY = "ms4.sync.retry";
    public static final String HEADER_RETRY_COUNT = "x-ms4-retry-count";
    public static final int RETRY_DELAY_MS = 2_000;
    public static final int MAX_RETRIES_SYNC = 3;

    // Filas consumidas pelo Microsserviço 4 (sincronização de projeções locais)
    public static final String QUEUE_ALUNOS_SYNC = "ms4.alunos.sync";
    public static final String BINDING_ALUNO = "aluno.*";

    public static final String QUEUE_SALAS_SYNC = "ms4.salas.sync";
    public static final String BINDING_SALA = "sala.*";

    public static final String QUEUE_PROCESSOS_SYNC = "ms4.processos-seletivos.sync";
    public static final String BINDING_PROCESSO_SELETIVO = "processo_seletivo.*";

    // Fila durável de saída para que eventos de inscrição não se percam mesmo se o MS3 estiver offline
    public static final String QUEUE_INSCRICOES_SAIDA = "ms3.inscricoes.processo-seletivo";
    public static final String BINDING_INSCRICAO = "inscricao.*";

    // Routing keys publicadas pelo Microsserviço 4
    public static final String RK_INSCRICAO_CRIADA = "inscricao.criada";
    public static final String RK_INSCRICAO_CANCELADA = "inscricao.cancelada";

    public static Map<String, Object> dlqArguments() {
        return Map.of(
                "x-dead-letter-exchange", EXCHANGE_DLX,
                "x-dead-letter-routing-key", ROUTING_KEY_DLQ
        );
    }

    public static Map<String, Object> retryQueueArguments() {
        return Map.of(
                "x-message-ttl", RETRY_DELAY_MS,
                "x-dead-letter-exchange", EXCHANGE_RETRY_RETURN
        );
    }
}
