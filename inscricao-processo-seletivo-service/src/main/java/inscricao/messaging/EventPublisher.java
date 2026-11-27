package inscricao.messaging;

/**
 * Abstração para publicação de eventos no broker de mensageria,
 * permitindo substituição determinística em testes unitários e simulações de falha.
 */
public interface EventPublisher extends AutoCloseable {

    void publicar(String exchange, String routingKey, String payloadJson) throws Exception;

    @Override
    default void close() {
    }
}
