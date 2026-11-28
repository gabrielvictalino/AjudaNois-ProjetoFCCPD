package inscricao.messaging;

import com.rabbitmq.client.BuiltinExchangeType;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.MessageProperties;
import com.rabbitmq.client.Return;
import inscricao.config.AppConfig;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Publicador RabbitMQ com:
 * <ul>
 *   <li>Exchange e fila de saída duráveis;</li>
 *   <li>Mensagens persistentes ({@code deliveryMode = 2});</li>
 *   <li>Publisher Confirms ({@code confirmSelect} + {@code waitForConfirmsOrDie}) e
 *       {@code ReturnListener} para detectar mensagens não roteáveis ({@code mandatory = true})
 *       antes da marcação do outbox como publicado.</li>
 * </ul>
 */
public class RabbitMQEventPublisher implements EventPublisher {

    private static final long CONFIRM_TIMEOUT_MS = 5_000L;

    private final ConnectionFactory factory;
    private final AtomicReference<Return> mensagemRetornada = new AtomicReference<>();
    private Connection connection;
    private Channel channel;

    public RabbitMQEventPublisher(AppConfig config) {
        this.factory = new ConnectionFactory();
        this.factory.setHost(config.rabbitHost());
        this.factory.setPort(config.rabbitPort());
        this.factory.setUsername(config.rabbitUser());
        this.factory.setPassword(config.rabbitPassword());
        this.factory.setAutomaticRecoveryEnabled(true);
        this.factory.setNetworkRecoveryInterval(3_000);
        this.factory.setConnectionTimeout(5_000);
    }

    @Override
    public synchronized void publicar(String exchange, String routingKey, String payloadJson)
            throws IOException, TimeoutException, InterruptedException {
        garantirCanalConectado();
        mensagemRetornada.set(null);

        channel.basicPublish(
                exchange,
                routingKey,
                true,
                MessageProperties.PERSISTENT_TEXT_PLAIN,
                payloadJson.getBytes(StandardCharsets.UTF_8)
        );
        channel.waitForConfirmsOrDie(CONFIRM_TIMEOUT_MS);

        // No RabbitMQ, basic.return chega antes de basic.ack quando mandatory=true falha no roteamento
        Return retorno = mensagemRetornada.getAndSet(null);
        validarRetornoRoteamento(retorno, exchange, routingKey);
    }

    static void validarRetornoRoteamento(Return retorno, String exchange, String routingKey) throws IOException {
        if (retorno != null) {
            throw new IOException(String.format(
                    "Mensagem não roteável (mandatory=true): code=%d (%s), exchange='%s', routingKey='%s'",
                    retorno.getReplyCode(),
                    retorno.getReplyText(),
                    exchange,
                    routingKey
            ));
        }
    }

    private void garantirCanalConectado() throws IOException, TimeoutException {
        if (connection != null && connection.isOpen() && channel != null && channel.isOpen()) {
            return;
        }
        fecharSilenciosamente();

        connection = factory.newConnection("ms4-inscricao-publisher");
        channel = connection.createChannel();
        channel.confirmSelect();
        channel.addReturnListener(retorno -> mensagemRetornada.compareAndSet(null, retorno));

        // Garante que exchange e fila durável de saída existam para evitar descarte silencioso
        channel.exchangeDeclare(RabbitMQConfig.EXCHANGE_EVENTOS, BuiltinExchangeType.TOPIC, true);
        channel.queueDeclare(RabbitMQConfig.QUEUE_INSCRICOES_SAIDA, true, false, false, null);
        channel.queueBind(
                RabbitMQConfig.QUEUE_INSCRICOES_SAIDA,
                RabbitMQConfig.EXCHANGE_EVENTOS,
                RabbitMQConfig.BINDING_INSCRICAO
        );
    }

    private void fecharSilenciosamente() {
        try {
            if (channel != null && channel.isOpen()) {
                channel.close();
            }
        } catch (Exception ignored) {
        }
        try {
            if (connection != null && connection.isOpen()) {
                connection.close();
            }
        } catch (Exception ignored) {
        }
        channel = null;
        connection = null;
    }

    @Override
    public synchronized void close() {
        fecharSilenciosamente();
    }
}
