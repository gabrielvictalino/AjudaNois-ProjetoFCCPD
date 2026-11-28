package inscricao.model;

import java.time.Instant;

/**
 * Registro na tabela de outbox transacional para entrega garantida (at-least-once) ao RabbitMQ.
 */
public record EventoOutbox(
        long id,
        String exchangeName,
        String routingKey,
        String payload,
        String status,
        int tentativas,
        String ultimoErro,
        Instant criadoEm,
        Instant publicadoEm
) {
}
