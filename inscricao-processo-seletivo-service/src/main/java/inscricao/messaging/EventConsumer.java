package inscricao.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.BuiltinExchangeType;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.DeliverCallback;
import inscricao.config.AppConfig;
import inscricao.exception.RegraDeNegocioException;
import inscricao.exception.RegraDeNegocioException.TipoErro;
import inscricao.model.Eventos.AlunoSyncEvent;
import inscricao.model.Eventos.ProcessoSeletivoSyncEvent;
import inscricao.model.Eventos.SalaSyncEvent;
import inscricao.service.InscricaoService;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Consumidor assíncrono RabbitMQ do Microsserviço 4.
 *
 * <p>Consome eventos de sincronização de {@code Aluno} (MS1), {@code Sala} (MS2) e
 * {@code ProcessoSeletivo} (MS3) com:
 * <ul>
 *   <li>Filas duráveis ligadas ao exchange {@code ajudanois.events};</li>
 *   <li>ACK manual ({@code autoAck = false}) e {@code basicQos(10)};</li>
 *   <li>Encaminhamento imediato de mensagens malformadas ou com {@code DADOS_INVALIDOS} para
 *       Dead Letter Queue ({@code ms4.dlq});</li>
 *   <li>Retry com atraso via fila com TTL ({@code ms4.sync.retry}) em erros transitórios de banco
 *       ou eventos fora de ordem (ex.: processo seletivo antes da sala), com limite de tentativas;</li>
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

    public enum AcaoFalha {
        AGENDAR_RETRY,
        ENVIAR_PARA_DLQ
    }

    private void declararTopologia(Channel ch) throws Exception {
        ch.exchangeDeclare(RabbitMQConfig.EXCHANGE_EVENTOS, BuiltinExchangeType.TOPIC, true);
        ch.exchangeDeclare(RabbitMQConfig.EXCHANGE_DLX, BuiltinExchangeType.DIRECT, true);
        ch.exchangeDeclare(RabbitMQConfig.EXCHANGE_RETRY, BuiltinExchangeType.TOPIC, true);
        ch.exchangeDeclare(RabbitMQConfig.EXCHANGE_RETRY_RETURN, BuiltinExchangeType.TOPIC, true);

        ch.queueDeclare(RabbitMQConfig.QUEUE_DLQ, true, false, false, null);
        ch.queueBind(RabbitMQConfig.QUEUE_DLQ, RabbitMQConfig.EXCHANGE_DLX, RabbitMQConfig.ROUTING_KEY_DLQ);

        ch.queueDeclare(RabbitMQConfig.QUEUE_RETRY, true, false, false, RabbitMQConfig.retryQueueArguments());
        ch.queueBind(RabbitMQConfig.QUEUE_RETRY, RabbitMQConfig.EXCHANGE_RETRY, "#");

        ch.queueDeclare(RabbitMQConfig.QUEUE_ALUNOS_SYNC, true, false, false, RabbitMQConfig.dlqArguments());
        ch.queueBind(RabbitMQConfig.QUEUE_ALUNOS_SYNC, RabbitMQConfig.EXCHANGE_EVENTOS, RabbitMQConfig.BINDING_ALUNO);
        ch.queueBind(RabbitMQConfig.QUEUE_ALUNOS_SYNC, RabbitMQConfig.EXCHANGE_RETRY_RETURN, RabbitMQConfig.BINDING_ALUNO);

        ch.queueDeclare(RabbitMQConfig.QUEUE_SALAS_SYNC, true, false, false, RabbitMQConfig.dlqArguments());
        ch.queueBind(RabbitMQConfig.QUEUE_SALAS_SYNC, RabbitMQConfig.EXCHANGE_EVENTOS, RabbitMQConfig.BINDING_SALA);
        ch.queueBind(RabbitMQConfig.QUEUE_SALAS_SYNC, RabbitMQConfig.EXCHANGE_RETRY_RETURN, RabbitMQConfig.BINDING_SALA);

        ch.queueDeclare(RabbitMQConfig.QUEUE_PROCESSOS_SYNC, true, false, false, RabbitMQConfig.dlqArguments());
        ch.queueBind(
                RabbitMQConfig.QUEUE_PROCESSOS_SYNC,
                RabbitMQConfig.EXCHANGE_EVENTOS,
                RabbitMQConfig.BINDING_PROCESSO_SELETIVO
        );
        ch.queueBind(
                RabbitMQConfig.QUEUE_PROCESSOS_SYNC,
                RabbitMQConfig.EXCHANGE_RETRY_RETURN,
                RabbitMQConfig.BINDING_PROCESSO_SELETIVO
        );
    }

    private void registrarConsumidores(Channel ch) throws Exception {
        DeliverCallback callback = (consumerTag, delivery) -> {
            long tag = delivery.getEnvelope().getDeliveryTag();
            String routingKey = delivery.getEnvelope().getRoutingKey();
            String body = new String(delivery.getBody(), StandardCharsets.UTF_8);
            int tentativasAtuais = extrairTentativasRetry(delivery.getProperties());

            try {
                processarMensagem(routingKey, body);
                ch.basicAck(tag, false);
                System.out.printf("[RABBITMQ-CONSUMER] Evento '%s' sincronizado e confirmado (ACK).%n", routingKey);
            } catch (JsonProcessingException | IllegalArgumentException | RegraDeNegocioException | SQLException erro) {
                AcaoFalha acao = classificarFalha(erro, tentativasAtuais);
                if (acao == AcaoFalha.AGENDAR_RETRY) {
                    int proximaTentativa = tentativasAtuais + 1;
                    AMQP.BasicProperties retryProps = construirPropriedadesRetry(
                            delivery.getProperties(),
                            proximaTentativa
                    );
                    try {
                        ch.basicPublish(
                                RabbitMQConfig.EXCHANGE_RETRY,
                                routingKey,
                                retryProps,
                                delivery.getBody()
                        );
                        ch.basicAck(tag, false);
                        System.out.printf(
                                "[RABBITMQ-CONSUMER] Falha transitória em '%s' (tentativa %d/%d). Reagendado na fila de retry (%dms): %s%n",
                                routingKey,
                                proximaTentativa,
                                RabbitMQConfig.MAX_RETRIES_SYNC,
                                RabbitMQConfig.RETRY_DELAY_MS,
                                erro.getMessage()
                        );
                    } catch (IOException publishErr) {
                        boolean requeue = !delivery.getEnvelope().isRedeliver();
                        System.out.printf(
                                "[RABBITMQ-CONSUMER] Falha ao encaminhar '%s' para fila de retry (requeue=%s): %s%n",
                                routingKey, requeue, publishErr.getMessage()
                        );
                        ch.basicNack(tag, false, requeue);
                    }
                } else {
                    System.out.printf(
                            "[RABBITMQ-CONSUMER] Mensagem rejeitada em '%s' (tentativas=%d) enviada para DLQ: %s%n",
                            routingKey, tentativasAtuais, erro.getMessage()
                    );
                    ch.basicNack(tag, false, false);
                }
            }
        };

        ch.basicConsume(RabbitMQConfig.QUEUE_ALUNOS_SYNC, false, callback, tag -> {});
        ch.basicConsume(RabbitMQConfig.QUEUE_SALAS_SYNC, false, callback, tag -> {});
        ch.basicConsume(RabbitMQConfig.QUEUE_PROCESSOS_SYNC, false, callback, tag -> {});
    }

    /**
     * Decide se uma falha no consumo deve ser reagendada na fila de retry com TTL
     * ou enviada imediatamente para a DLQ.
     */
    public static AcaoFalha classificarFalha(Throwable erro, int tentativasRetryAtuais) {
        if (erro instanceof JsonProcessingException || erro instanceof IllegalArgumentException) {
            return AcaoFalha.ENVIAR_PARA_DLQ;
        }
        if (erro instanceof RegraDeNegocioException regra) {
            if (regra.getTipoErro() == TipoErro.DADOS_INVALIDOS) {
                return AcaoFalha.ENVIAR_PARA_DLQ;
            }
            return tentativasRetryAtuais < RabbitMQConfig.MAX_RETRIES_SYNC
                    ? AcaoFalha.AGENDAR_RETRY
                    : AcaoFalha.ENVIAR_PARA_DLQ;
        }
        if (erro instanceof SQLException) {
            return tentativasRetryAtuais < RabbitMQConfig.MAX_RETRIES_SYNC
                    ? AcaoFalha.AGENDAR_RETRY
                    : AcaoFalha.ENVIAR_PARA_DLQ;
        }
        return AcaoFalha.ENVIAR_PARA_DLQ;
    }

    static int extrairTentativasRetry(AMQP.BasicProperties properties) {
        if (properties == null || properties.getHeaders() == null) {
            return 0;
        }
        Object valor = properties.getHeaders().get(RabbitMQConfig.HEADER_RETRY_COUNT);
        if (valor instanceof Number numero) {
            return Math.max(0, numero.intValue());
        }
        return 0;
    }

    static AMQP.BasicProperties construirPropriedadesRetry(
            AMQP.BasicProperties originais,
            int proximaTentativa
    ) {
        Map<String, Object> headers = new HashMap<>();
        if (originais != null && originais.getHeaders() != null) {
            headers.putAll(originais.getHeaders());
        }
        headers.put(RabbitMQConfig.HEADER_RETRY_COUNT, proximaTentativa);

        AMQP.BasicProperties.Builder builder = originais != null
                ? originais.builder()
                : new AMQP.BasicProperties.Builder();
        return builder
                .deliveryMode(2)
                .headers(headers)
                .build();
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
