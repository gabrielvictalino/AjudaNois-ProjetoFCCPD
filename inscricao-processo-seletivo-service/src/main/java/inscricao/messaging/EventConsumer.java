package inscricao.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.BuiltinExchangeType;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.DeliverCallback;
import inscricao.config.AppConfig;
import inscricao.exception.RegraDeNegocioException;
import inscricao.model.Eventos.AlunoSyncEvent;
import inscricao.model.Eventos.ProcessoSeletivoSyncEvent;
import inscricao.model.Eventos.SalaSyncEvent;
import inscricao.service.InscricaoService;

import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Consumidor assíncrono RabbitMQ do Microsserviço 4.
 *
 * <p>Consome eventos de sincronização de {@code Aluno} (MS1), {@code Sala} (MS2) e
 * {@code ProcessoSeletivo} (MS3) com:
 * <ul>
 *   <li>Filas duráveis ligadas ao exchange {@code ajudanois.events};</li>
 *   <li>ACK manual ({@code autoAck = false}) e {@code basicQos(10)};</li>
 *   <li>Encaminhamento de mensagens malformadas para Dead Letter Queue ({@code ms4.dlq});</li>
 *   <li>Reenfileiramento ({@code requeue = true}) em erros transitórios de banco ou dependência de ordem;</li>
 *   <li>Loop automático de reconexão caso o RabbitMQ fique indisponível no startup.</li>
 * </ul>
 */
public class EventConsumer implements AutoCloseable {

    private static final long RECONNECT_DELAY_MS = 5_000L;

    private final AppConfig config;
    private final InscricaoService service;
    private final ObjectMapper mapper;
    private final AtomicBoolean rodando = new AtomicBoolean(false);

    private Thread workerThread;
    private volatile Connection connection;
    private volatile Channel channel;

    public EventConsumer(AppConfig config, InscricaoService service, ObjectMapper mapper) {
        this.config = config;
        this.service = service;
        this.mapper = mapper;
    }

    public void iniciar() {
        if (!rodando.compareAndSet(false, true)) {
            return;
        }
        workerThread = new Thread(this::conectarEConsumirLoop, "ms4-rabbitmq-consumer");
        workerThread.setDaemon(true);
        workerThread.start();
    }

    private void conectarEConsumirLoop() {
        while (rodando.get()) {
            try {
                ConnectionFactory factory = new ConnectionFactory();
                factory.setHost(config.rabbitHost());
                factory.setPort(config.rabbitPort());
                factory.setUsername(config.rabbitUser());
                factory.setPassword(config.rabbitPassword());
                factory.setAutomaticRecoveryEnabled(true);
                factory.setNetworkRecoveryInterval(3_000);
                factory.setConnectionTimeout(5_000);

                connection = factory.newConnection("ms4-inscricao-consumer");
                channel = connection.createChannel();
                channel.basicQos(10);

                declararTopologia(channel);
                registrarConsumidores(channel);

                System.out.println("[RABBITMQ-CONSUMER] Conectado e escutando filas de sincronização do MS4.");
                return; // O RabbitMQ client mantém a thread de dispatch e faz recovery automático
            } catch (Exception e) {
                if (!rodando.get()) {
                    return;
                }
                System.out.printf(
                        "[RABBITMQ-CONSUMER] Broker indisponível (%s). Nova tentativa em %ds...%n",
                        e.getMessage(), RECONNECT_DELAY_MS / 1000
                );
                try {
                    Thread.sleep(RECONNECT_DELAY_MS);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }
    }

    private void declararTopologia(Channel ch) throws Exception {
        ch.exchangeDeclare(RabbitMQConfig.EXCHANGE_EVENTOS, BuiltinExchangeType.TOPIC, true);
        ch.exchangeDeclare(RabbitMQConfig.EXCHANGE_DLX, BuiltinExchangeType.DIRECT, true);
        ch.queueDeclare(RabbitMQConfig.QUEUE_DLQ, true, false, false, null);
        ch.queueBind(RabbitMQConfig.QUEUE_DLQ, RabbitMQConfig.EXCHANGE_DLX, RabbitMQConfig.ROUTING_KEY_DLQ);

        ch.queueDeclare(RabbitMQConfig.QUEUE_ALUNOS_SYNC, true, false, false, RabbitMQConfig.dlqArguments());
        ch.queueBind(RabbitMQConfig.QUEUE_ALUNOS_SYNC, RabbitMQConfig.EXCHANGE_EVENTOS, RabbitMQConfig.BINDING_ALUNO);

        ch.queueDeclare(RabbitMQConfig.QUEUE_SALAS_SYNC, true, false, false, RabbitMQConfig.dlqArguments());
        ch.queueBind(RabbitMQConfig.QUEUE_SALAS_SYNC, RabbitMQConfig.EXCHANGE_EVENTOS, RabbitMQConfig.BINDING_SALA);

        ch.queueDeclare(RabbitMQConfig.QUEUE_PROCESSOS_SYNC, true, false, false, RabbitMQConfig.dlqArguments());
        ch.queueBind(
                RabbitMQConfig.QUEUE_PROCESSOS_SYNC,
                RabbitMQConfig.EXCHANGE_EVENTOS,
                RabbitMQConfig.BINDING_PROCESSO_SELETIVO
        );
    }

    private void registrarConsumidores(Channel ch) throws Exception {
        DeliverCallback callback = (consumerTag, delivery) -> {
            long tag = delivery.getEnvelope().getDeliveryTag();
            String routingKey = delivery.getEnvelope().getRoutingKey();
            String body = new String(delivery.getBody(), StandardCharsets.UTF_8);

            try {
                processarMensagem(routingKey, body);
                ch.basicAck(tag, false);
                System.out.printf("[RABBITMQ-CONSUMER] Evento '%s' sincronizado e confirmado (ACK).%n", routingKey);
            } catch (JsonProcessingException | IllegalArgumentException malformed) {
                System.out.printf(
                        "[RABBITMQ-CONSUMER] Mensagem inválida em '%s' enviada para DLQ: %s%n",
                        routingKey, malformed.getMessage()
                );
                ch.basicNack(tag, false, false);
            } catch (RegraDeNegocioException | SQLException transientErr) {
                boolean jaRedelivered = delivery.getEnvelope().isRedeliver();
                boolean requeue = !jaRedelivered;
                System.out.printf(
                        "[RABBITMQ-CONSUMER] Erro ao processar '%s' (redeliver=%s, requeue=%s): %s%n",
                        routingKey, jaRedelivered, requeue, transientErr.getMessage()
                );
                ch.basicNack(tag, false, requeue);
            }
        };

        ch.basicConsume(RabbitMQConfig.QUEUE_ALUNOS_SYNC, false, callback, tag -> {});
        ch.basicConsume(RabbitMQConfig.QUEUE_SALAS_SYNC, false, callback, tag -> {});
        ch.basicConsume(RabbitMQConfig.QUEUE_PROCESSOS_SYNC, false, callback, tag -> {});
    }

    /**
     * Roteia e aplica uma mensagem de sincronização recebida do broker.
     * Exposto publicamente para permitir testes determinísticos sem broker externo.
     */
    public void processarMensagem(String routingKey, String payloadJson)
            throws JsonProcessingException, SQLException {
        if (routingKey == null || routingKey.isBlank()) {
            throw new IllegalArgumentException("Routing key vazia.");
        }

        if (routingKey.startsWith("aluno.")) {
            AlunoSyncEvent event = mapper.readValue(payloadJson, AlunoSyncEvent.class);
            service.sincronizarAluno(event);
        } else if (routingKey.startsWith("sala.")) {
            SalaSyncEvent event = mapper.readValue(payloadJson, SalaSyncEvent.class);
            service.sincronizarSala(event);
        } else if (routingKey.startsWith("processo_seletivo.")) {
            ProcessoSeletivoSyncEvent event = mapper.readValue(payloadJson, ProcessoSeletivoSyncEvent.class);
            service.sincronizarProcessoSeletivo(event);
        } else {
            throw new IllegalArgumentException("Routing key não suportada pelo MS4: " + routingKey);
        }
    }

    @Override
    public void close() {
        rodando.set(false);
        if (workerThread != null) {
            workerThread.interrupt();
        }
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
    }
}
