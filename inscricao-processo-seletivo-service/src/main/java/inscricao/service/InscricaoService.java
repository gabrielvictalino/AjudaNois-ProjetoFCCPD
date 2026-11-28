package inscricao.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import entidades.Aluno;
import entidades.ProcessoSeletivo;
import entidades.Professor;
import entidades.Sala;
import inscricao.database.InscricaoRepository;
import inscricao.exception.RegraDeNegocioException;
import inscricao.exception.RegraDeNegocioException.TipoErro;
import inscricao.messaging.OutboxRelay;
import inscricao.messaging.RabbitMQConfig;
import inscricao.model.EventoOutbox;
import inscricao.model.Eventos.AlunoInscritoEvent;
import inscricao.model.Eventos.AlunoSyncEvent;
import inscricao.model.Eventos.InscricaoCanceladaEvent;
import inscricao.model.Eventos.ProcessoSeletivoSyncEvent;
import inscricao.model.Eventos.SalaSyncEvent;
import inscricao.model.Inscricao;
import inscricao.model.ProcessoSeletivoResumo;
import inscricao.model.StatusProcessoSeletivo;

import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Serviço de aplicação do Microsserviço 4 (Inscrição de Alunos em Processo Seletivo).
 */
public class InscricaoService {

    private final InscricaoRepository repository;
    private final OutboxRelay outboxRelay;
    private final ObjectMapper mapper;

    public InscricaoService(InscricaoRepository repository, OutboxRelay outboxRelay, ObjectMapper mapper) {
        this.repository = repository;
        this.outboxRelay = outboxRelay;
        this.mapper = mapper;
    }

    // -------------------------------------------------------------------------
    // Caso de uso principal: inscrição e cancelamento em processo seletivo
    // -------------------------------------------------------------------------

    public Inscricao inscreverAluno(int alunoId, int processoSeletivoId) throws SQLException {
        Inscricao inscricao = repository.inscreverAlunoAtomico(
                alunoId,
                processoSeletivoId,
                RabbitMQConfig.EXCHANGE_EVENTOS,
                RabbitMQConfig.RK_INSCRICAO_CRIADA,
                this::serializarEventoInscricao
        );

        // Disparo assíncrono imediato; caso o broker esteja fora ou lento, não bloqueia a inscrição
        outboxRelay.dispararAssincrono();
        return inscricao;
    }

    public Inscricao cancelarInscricao(int alunoId, int processoSeletivoId) throws SQLException {
        Inscricao cancelada = repository.cancelarInscricaoAtomico(
                alunoId,
                processoSeletivoId,
                RabbitMQConfig.EXCHANGE_EVENTOS,
                RabbitMQConfig.RK_INSCRICAO_CANCELADA,
                this::serializarEventoCancelamento
        );

        outboxRelay.dispararAssincrono();
        return cancelada;
    }

    // -------------------------------------------------------------------------
    // Sincronização de projeções (eventos de MS1, MS2, MS3 ou cadastro local)
    // -------------------------------------------------------------------------

    public void sincronizarAluno(AlunoSyncEvent event) throws SQLException {
        if (event.id() <= 0 || isBlank(event.nome()) || isBlank(event.login())) {
            throw new RegraDeNegocioException(TipoErro.DADOS_INVALIDOS, "Dados inválidos para sincronizar aluno.");
        }
        Aluno aluno = new Aluno(event.id(), event.nome().trim(), event.login().trim(), "");
        repository.upsertAluno(aluno);
    }

    public void sincronizarSala(SalaSyncEvent event) throws SQLException {
        if (event.id() <= 0 || isBlank(event.nome())) {
            throw new RegraDeNegocioException(TipoErro.DADOS_INVALIDOS, "Dados inválidos para sincronizar sala.");
        }
        String profNome = isBlank(event.professorNome()) ? "Professor #" + event.professorId() : event.professorNome().trim();
        Professor professor = new Professor(event.professorId(), profNome, "", "");
        Sala sala = new Sala(event.id(), event.nome().trim(), professor);
        repository.upsertSala(sala);
    }

    public void sincronizarProcessoSeletivo(ProcessoSeletivoSyncEvent event) throws SQLException {
        if (event.id() <= 0 || event.salaId() <= 0 || isBlank(event.titulo())) {
            throw new RegraDeNegocioException(
                    TipoErro.DADOS_INVALIDOS,
                    "Dados inválidos para sincronizar processo seletivo."
            );
        }
        StatusProcessoSeletivo status = StatusProcessoSeletivo.fromString(event.status());
        repository.upsertProcessoSeletivo(
                event.id(),
                event.salaId(),
                event.professorId(),
                event.titulo().trim(),
                status,
                event.maxVagas()
        );
    }

    // -------------------------------------------------------------------------
    // Consultas e integração com o módulo compartilhado entidades
    // -------------------------------------------------------------------------

    public List<Aluno> listarAlunos() throws SQLException {
        return repository.listarAlunos();
    }

    public List<Sala> listarSalas() throws SQLException {
        return repository.listarSalas();
    }

    public List<ProcessoSeletivoResumo> listarProcessosAbertos() throws SQLException {
        return repository.listarProcessosSeletivos(true);
    }

    public List<ProcessoSeletivoResumo> listarTodosProcessos() throws SQLException {
        return repository.listarProcessosSeletivos(false);
    }

    public Optional<ProcessoSeletivo> carregarProcessoComoEntidade(int processoId) throws SQLException {
        Optional<ProcessoSeletivoResumo> resumoOpt = repository.buscarProcessoSeletivoPorId(processoId);
        if (resumoOpt.isEmpty()) {
            return Optional.empty();
        }

        ProcessoSeletivo entidade = resumoOpt.get().toEntidade();
        List<Inscricao> inscricoes = repository.listarInscricoesPorProcesso(processoId);
        for (Inscricao insc : inscricoes) {
            repository.buscarAlunoPorId(insc.alunoId()).ifPresent(entidade.alunosInscritos::add);
        }
        return Optional.of(entidade);
    }

    public List<Inscricao> listarInscricoesPorAluno(int alunoId) throws SQLException {
        return repository.listarInscricoesPorAluno(alunoId);
    }

    public List<Inscricao> listarInscricoesPorProcesso(int processoSeletivoId) throws SQLException {
        return repository.listarInscricoesPorProcesso(processoSeletivoId);
    }

    public List<EventoOutbox> listarOutboxRecentes(int limite) throws SQLException {
        return repository.listarOutboxRecentes(limite);
    }

    public int contarOutboxPendentes() throws SQLException {
        return repository.contarOutboxPendentes();
    }

    public int reprocessarOutboxAgora() {
        return outboxRelay.despacharPendentes();
    }

    // -------------------------------------------------------------------------
    // Modo seed isolado e demonstração de consistência sob concorrência
    // -------------------------------------------------------------------------

    /**
     * Popula dados de exemplo idempotentes (alunos, salas e processos seletivos)
     * para testes e demonstrações independentes enquanto MS1/MS2/MS3 estiverem offline.
     */
    public void popularDadosExemplo() throws SQLException {
        sincronizarAluno(new AlunoSyncEvent(1, "Ana Paula", "ana.paula"));
        sincronizarAluno(new AlunoSyncEvent(2, "Bruno Costa", "bruno.costa"));
        sincronizarAluno(new AlunoSyncEvent(3, "Carla Mendes", "carla.mendes"));

        sincronizarSala(new SalaSyncEvent(10, "Sistemas Distribuídos - FCCPD", 100, "Prof. Ricardo Lima"));
        sincronizarSala(new SalaSyncEvent(20, "Estruturas de Dados", 101, "Profa. Marina Souza"));

        sincronizarProcessoSeletivo(new ProcessoSeletivoSyncEvent(
                1001, 10, 100, "Monitoria FCCPD 2026.2", "ABERTO", 2
        ));
        sincronizarProcessoSeletivo(new ProcessoSeletivoSyncEvent(
                1002, 20, 101, "Monitoria Estruturas de Dados 2026.2", "ABERTO", 0
        ));
        sincronizarProcessoSeletivo(new ProcessoSeletivoSyncEvent(
                1003, 10, 100, "Monitoria FCCPD 2026.1 (Encerrada)", "ENCERRADO", 1
        ));
    }

    /**
     * Executa {@code numThreads} tentativas simultâneas de inscrição do mesmo aluno no mesmo
     * processo seletivo usando {@link CountDownLatch} para comprovar consistência forte.
     */
    public ResultadoTesteConcorrencia simularConflitoInscricaoDuplicada(
            int alunoId,
            int processoSeletivoId,
            int numThreads
    ) throws InterruptedException {
        int threads = Math.max(2, numThreads);
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch pronto = new CountDownLatch(threads);
        CountDownLatch largada = new CountDownLatch(1);

        AtomicInteger sucessos = new AtomicInteger();
        AtomicInteger conflitos = new AtomicInteger();
        AtomicInteger outrosErros = new AtomicInteger();
        List<Future<?>> tarefas = new ArrayList<>();

        for (int i = 0; i < threads; i++) {
            tarefas.add(pool.submit(() -> {
                pronto.countDown();
                try {
                    largada.await();
                    inscreverAluno(alunoId, processoSeletivoId);
                    sucessos.incrementAndGet();
                } catch (RegraDeNegocioException e) {
                    if (e.getTipoErro() == TipoErro.INSCRICAO_DUPLICADA
                            || e.getTipoErro() == TipoErro.VAGAS_ESGOTADAS) {
                        conflitos.incrementAndGet();
                    } else {
                        outrosErros.incrementAndGet();
                    }
                } catch (Exception e) {
                    outrosErros.incrementAndGet();
                }
            }));
        }

        pronto.await();
        largada.countDown();

        for (Future<?> f : tarefas) {
            try {
                f.get();
            } catch (Exception ignored) {
            }
        }
        pool.shutdownNow();

        return new ResultadoTesteConcorrencia(threads, sucessos.get(), conflitos.get(), outrosErros.get());
    }

    public record ResultadoTesteConcorrencia(
            int totalTentativas,
            int inscricoesConfirmadas,
            int conflitosBloqueados,
            int outrosErros
    ) {
    }

    // -------------------------------------------------------------------------
    // Serialização de eventos
    // -------------------------------------------------------------------------

    private String serializarEventoInscricao(Inscricao inscricao) {
        AlunoInscritoEvent event = new AlunoInscritoEvent(
                inscricao.id(),
                inscricao.alunoId(),
                inscricao.alunoNome(),
                inscricao.processoSeletivoId(),
                inscricao.salaId(),
                inscricao.salaNome(),
                inscricao.status(),
                inscricao.dataInscricao().toString()
        );
        return toJson(event);
    }

    private String serializarEventoCancelamento(Inscricao cancelada) {
        InscricaoCanceladaEvent event = new InscricaoCanceladaEvent(
                cancelada.id(),
                cancelada.alunoId(),
                cancelada.processoSeletivoId(),
                cancelada.salaId(),
                Instant.now().toString()
        );
        return toJson(event);
    }

    private String toJson(Object obj) {
        try {
            return mapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Falha ao serializar evento JSON.", e);
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
