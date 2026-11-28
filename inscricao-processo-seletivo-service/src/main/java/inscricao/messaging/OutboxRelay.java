package inscricao.messaging;

import inscricao.database.InscricaoRepository;
import inscricao.model.EventoOutbox;

import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Worker de reprocessamento do Transactional Outbox.
 *
 * <p>Garante tolerância a falhas na comunicação com o RabbitMQ: se o broker cair durante uma
 * inscrição, o registro permanece em {@code eventos_outbox} com status {@code PENDENTE} e é
 * reenviado automaticamente assim que o broker voltar.
 */
public class OutboxRelay implements AutoCloseable {

    private static final int LOTE_PADRAO = 50;
    private static final long SHUTDOWN_GRACE_SECONDS = 2L;

    private final InscricaoRepository repository;
    private final EventPublisher publisher;
    private final long retryIntervalMs;
    private final Object lifecycleLock = new Object();
    private final Object dispatchLock = new Object();
    private final AtomicBoolean disparoAgendado = new AtomicBoolean(false);
    private ScheduledExecutorService scheduler;

    public OutboxRelay(InscricaoRepository repository, EventPublisher publisher, long retryIntervalMs) {
        this.repository = repository;
        this.publisher = publisher;
        this.retryIntervalMs = retryIntervalMs;
    }

    public void iniciar() {
        synchronized (lifecycleLock) {
            if (scheduler != null && !scheduler.isShutdown()) {
                return;
            }
            scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "ms4-outbox-relay");
                t.setDaemon(true);
                return t;
            });
            scheduler.scheduleWithFixedDelay(
                    this::despacharPendentesSilencioso,
                    retryIntervalMs,
                    retryIntervalMs,
                    TimeUnit.MILLISECONDS
            );
        }
    }

    /**
     * Solicita um despacho assíncrono imediato no scheduler do Outbox sem bloquear a thread
     * chamadora (evitando latência em cascata quando o RabbitMQ está indisponível).
     * Múltiplos chamados concorrentes são coalescidos em uma única tarefa pendente.
     */
    public void dispararAssincrono() {
        ScheduledExecutorService exec;
        synchronized (lifecycleLock) {
            exec = this.scheduler;
        }
        if (exec == null || exec.isShutdown()) {
            return;
        }
        if (!disparoAgendado.compareAndSet(false, true)) {
            return;
        }
        try {
            exec.execute(() -> {
                disparoAgendado.set(false);
                despacharPendentes();
            });
        } catch (RejectedExecutionException ignored) {
            disparoAgendado.set(false);
        }
    }

    /**
     * Tenta publicar todos os eventos pendentes no outbox.
     *
     * @return quantidade de eventos efetivamente confirmados pelo broker nesta execução.
     */
    public int despacharPendentes() {
        synchronized (dispatchLock) {
            int publicados = 0;
            try {
                List<EventoOutbox> pendentes = repository.buscarOutboxPendentes(LOTE_PADRAO);
                for (EventoOutbox evento : pendentes) {
                    try {
                        publisher.publicar(evento.exchangeName(), evento.routingKey(), evento.payload());
                        repository.marcarOutboxPublicado(evento.id());
                        publicados++;
                        System.out.printf(
                                "[OUTBOX] Evento #%d (%s) publicado e confirmado no RabbitMQ.%n",
                                evento.id(), evento.routingKey()
                        );
                    } catch (Exception falha) {
                        repository.registrarFalhaOutbox(evento.id(), falha.getMessage());
                        System.out.printf(
                                "[OUTBOX] Falha ao publicar evento #%d (%s) — permanecerá PENDENTE para retry: %s%n",
                                evento.id(), evento.routingKey(), falha.getMessage()
                        );
                        // Interrompe o lote se o broker estiver indisponível para tentar novamente no próximo ciclo
                        break;
                    }
                }
            } catch (Exception dbError) {
                System.out.println("[OUTBOX] Erro ao consultar eventos pendentes: " + dbError.getMessage());
            }
            return publicados;
        }
    }

    private void despacharPendentesSilencioso() {
        despacharPendentes();
    }

    @Override
    public void close() {
        ScheduledExecutorService exec;
        synchronized (lifecycleLock) {
            exec = this.scheduler;
            this.scheduler = null;
        }
        if (exec != null) {
            exec.shutdown();
            try {
                if (!exec.awaitTermination(SHUTDOWN_GRACE_SECONDS, TimeUnit.SECONDS)) {
                    exec.shutdownNow();
                }
            } catch (InterruptedException e) {
                exec.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }
}
