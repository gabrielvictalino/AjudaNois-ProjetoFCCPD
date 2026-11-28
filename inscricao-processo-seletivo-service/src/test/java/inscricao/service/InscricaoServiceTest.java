package inscricao.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import entidades.ProcessoSeletivo;
import inscricao.database.Conexao;
import inscricao.database.DatabaseInitializer;
import inscricao.database.InscricaoRepository;
import inscricao.exception.RegraDeNegocioException;
import inscricao.exception.RegraDeNegocioException.TipoErro;
import inscricao.messaging.EventPublisher;
import inscricao.messaging.OutboxRelay;
import inscricao.messaging.RabbitMQConfig;
import inscricao.model.EventoOutbox;
import inscricao.model.Eventos.AlunoInscritoEvent;
import inscricao.model.Eventos.AlunoSyncEvent;
import inscricao.model.Eventos.ProcessoSeletivoSyncEvent;
import inscricao.model.Eventos.SalaSyncEvent;
import inscricao.model.Inscricao;
import inscricao.service.InscricaoService.ResultadoTesteConcorrencia;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.*;

class InscricaoServiceTest {

    private InscricaoRepository repository;
    private FakePublisher publisher;
    private OutboxRelay outboxRelay;
    private InscricaoService service;
    private ObjectMapper mapper;

    @BeforeEach
    void setUp() throws Exception {
        String dbName = "ms4_test_" + UUID.randomUUID().toString().replace("-", "");
        Conexao conexao = new Conexao(
                "jdbc:h2:mem:" + dbName + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
                "sa",
                ""
        );
        DatabaseInitializer.inicializar(conexao);

        mapper = new ObjectMapper();
        repository = new InscricaoRepository(conexao);
        publisher = new FakePublisher();
        outboxRelay = new OutboxRelay(repository, publisher, 60_000);
        outboxRelay.iniciar();
        service = new InscricaoService(repository, outboxRelay, mapper);

        service.popularDadosExemplo();
    }

    @AfterEach
    void tearDown() {
        if (outboxRelay != null) {
            outboxRelay.close();
        }
    }

    @Test
    void deveInscreverAlunoEPublicarEventoNoRabbitMQ() throws Exception {
        Inscricao inscricao = service.inscreverAluno(1, 1001);

        assertTrue(inscricao.id() > 0);
        assertEquals(1, inscricao.alunoId());
        assertEquals("Ana Paula", inscricao.alunoNome());
        assertEquals(1001, inscricao.processoSeletivoId());
        assertEquals(10, inscricao.salaId());
        assertEquals("INSCRITO", inscricao.status());

        // Evento publicado de forma assíncrona e outbox zerado
        aguardarCondicao(() -> publisher.mensagens.size() == 1 && contarPendentesSemExcecao() == 0);
        assertEquals(0, service.contarOutboxPendentes());
        assertEquals(1, publisher.mensagens.size());
        MensagemPublicada msg = publisher.mensagens.get(0);
        assertEquals(RabbitMQConfig.EXCHANGE_EVENTOS, msg.exchange());
        assertEquals(RabbitMQConfig.RK_INSCRICAO_CRIADA, msg.routingKey());

        AlunoInscritoEvent payload = mapper.readValue(msg.payload(), AlunoInscritoEvent.class);
        assertEquals(inscricao.id(), payload.inscricaoId());
        assertEquals(1, payload.alunoId());
        assertEquals(1001, payload.processoSeletivoId());
        assertEquals(10, payload.salaId());

        // Integração com o módulo compartilhado entidades
        ProcessoSeletivo entidade = service.carregarProcessoComoEntidade(1001).orElseThrow();
        assertEquals(1001, entidade.id);
        assertEquals(10, entidade.sala.id);
        assertEquals(1, entidade.alunosInscritos.size());
        assertEquals(1, entidade.alunosInscritos.get(0).id);
    }

    @Test
    void deveRejeitarInscricaoDuplicada() throws Exception {
        service.inscreverAluno(1, 1001);

        RegraDeNegocioException ex = assertThrows(
                RegraDeNegocioException.class,
                () -> service.inscreverAluno(1, 1001)
        );
        assertEquals(TipoErro.INSCRICAO_DUPLICADA, ex.getTipoErro());
    }

    @Test
    void deveRejeitarInscricaoEmProcessoEncerrado() {
        RegraDeNegocioException ex = assertThrows(
                RegraDeNegocioException.class,
                () -> service.inscreverAluno(1, 1003)
        );
        assertEquals(TipoErro.PROCESSO_ENCERRADO, ex.getTipoErro());
    }

    @Test
    void deveRejeitarAlunoOuProcessoInexistente() {
        RegraDeNegocioException exAluno = assertThrows(
                RegraDeNegocioException.class,
                () -> service.inscreverAluno(999, 1001)
        );
        assertEquals(TipoErro.ALUNO_NAO_ENCONTRADO, exAluno.getTipoErro());

        RegraDeNegocioException exProcesso = assertThrows(
                RegraDeNegocioException.class,
                () -> service.inscreverAluno(1, 9999)
        );
        assertEquals(TipoErro.PROCESSO_NAO_ENCONTRADO, exProcesso.getTipoErro());
    }

    @Test
    void deveRespeitarLimiteDeVagasEPermitirNovaInscricaoAposCancelamento() throws Exception {
        // Processo 1001 tem maxVagas = 2
        service.inscreverAluno(1, 1001);
        service.inscreverAluno(2, 1001);

        RegraDeNegocioException lotado = assertThrows(
                RegraDeNegocioException.class,
                () -> service.inscreverAluno(3, 1001)
        );
        assertEquals(TipoErro.VAGAS_ESGOTADAS, lotado.getTipoErro());

        // Cancela aluno 2 -> libera vaga e publica inscricao.cancelada
        Inscricao cancelada = service.cancelarInscricao(2, 1001);
        assertEquals("CANCELADA", cancelada.status());
        aguardarCondicao(() -> publisher.mensagens.size() >= 3);
        assertEquals(RabbitMQConfig.RK_INSCRICAO_CANCELADA, publisher.mensagens.get(2).routingKey());

        // Agora aluno 3 consegue se inscrever
        Inscricao insc3 = service.inscreverAluno(3, 1001);
        assertEquals(3, insc3.alunoId());
        assertEquals(2, service.listarInscricoesPorProcesso(1001).size());
    }

    @Test
    void deveRejeitarCancelamentoEmProcessoEncerradoOuInexistente() throws Exception {
        service.inscreverAluno(1, 1001);

        // Encerra o processo seletivo 1001
        service.sincronizarProcessoSeletivo(new ProcessoSeletivoSyncEvent(
                1001, 10, 100, "Monitoria FCCPD 2026.2", "ENCERRADO", 2
        ));

        RegraDeNegocioException exEncerrado = assertThrows(
                RegraDeNegocioException.class,
                () -> service.cancelarInscricao(1, 1001)
        );
        assertEquals(TipoErro.PROCESSO_ENCERRADO, exEncerrado.getTipoErro());
        assertEquals(1, service.listarInscricoesPorProcesso(1001).size());

        RegraDeNegocioException exInexistente = assertThrows(
                RegraDeNegocioException.class,
                () -> service.cancelarInscricao(1, 9999)
        );
        assertEquals(TipoErro.PROCESSO_NAO_ENCONTRADO, exInexistente.getTipoErro());
    }

    @Test
    void devePermitirReinscricaoDoMesmoAlunoAposCancelamento() throws Exception {
        Inscricao primeira = service.inscreverAluno(1, 1001);
        assertEquals("INSCRITO", primeira.status());

        Inscricao cancelada = service.cancelarInscricao(1, 1001);
        assertEquals("CANCELADA", cancelada.status());
        assertTrue(service.listarInscricoesPorAluno(1).isEmpty());

        Inscricao reinscricao = service.inscreverAluno(1, 1001);
        assertTrue(reinscricao.id() > primeira.id());
        assertEquals("INSCRITO", reinscricao.status());
        assertEquals(1, service.listarInscricoesPorProcesso(1001).size());
        aguardarCondicao(() -> publisher.mensagens.size() == 3);
        assertEquals(3, publisher.mensagens.size());
        assertEquals(RabbitMQConfig.RK_INSCRICAO_CRIADA, publisher.mensagens.get(0).routingKey());
        assertEquals(RabbitMQConfig.RK_INSCRICAO_CANCELADA, publisher.mensagens.get(1).routingKey());
        assertEquals(RabbitMQConfig.RK_INSCRICAO_CRIADA, publisher.mensagens.get(2).routingKey());
    }

    @Test
    void deveGarantirConsistenciaForteEmInscricoesDuplicadasConcorrentes() throws Exception {
        ResultadoTesteConcorrencia resultado = service.simularConflitoInscricaoDuplicada(1, 1002, 10);

        assertEquals(10, resultado.totalTentativas());
        assertEquals(1, resultado.inscricoesConfirmadas());
        assertEquals(9, resultado.conflitosBloqueados());
        assertEquals(0, resultado.outrosErros());
        assertEquals(1, service.listarInscricoesPorProcesso(1002).size());
    }

    @Test
    void deveGarantirConsistenciaForteNoLimiteDeVagasSobConcorrencia() throws Exception {
        // Cadastra 8 alunos disputando processo 2001 com apenas 2 vagas
        service.sincronizarSala(new SalaSyncEvent(50, "Redes de Computadores", 200, "Prof. Alberto"));
        service.sincronizarProcessoSeletivo(new ProcessoSeletivoSyncEvent(
                2001, 50, 200, "Monitoria Redes", "ABERTO", 2
        ));
        for (int alunoId = 10; alunoId < 18; alunoId++) {
            service.sincronizarAluno(new AlunoSyncEvent(alunoId, "Aluno " + alunoId, "aluno" + alunoId));
        }

        int numAlunos = 8;
        ExecutorService pool = Executors.newFixedThreadPool(numAlunos);
        CountDownLatch pronto = new CountDownLatch(numAlunos);
        CountDownLatch largada = new CountDownLatch(1);
        AtomicInteger confirmados = new AtomicInteger();
        AtomicInteger esgotados = new AtomicInteger();
        List<Future<?>> futuros = new ArrayList<>();

        for (int alunoId = 10; alunoId < 18; alunoId++) {
            final int id = alunoId;
            futuros.add(pool.submit(() -> {
                pronto.countDown();
                try {
                    largada.await();
                    service.inscreverAluno(id, 2001);
                    confirmados.incrementAndGet();
                } catch (RegraDeNegocioException e) {
                    if (e.getTipoErro() == TipoErro.VAGAS_ESGOTADAS) {
                        esgotados.incrementAndGet();
                    } else {
                        fail("Erro inesperado: " + e.getTipoErro());
                    }
                } catch (Exception e) {
                    fail("Exceção inesperada: " + e.getMessage());
                }
            }));
        }

        pronto.await();
        largada.countDown();
        for (Future<?> f : futuros) {
            f.get();
        }
        pool.shutdownNow();

        assertEquals(2, confirmados.get());
        assertEquals(6, esgotados.get());
        assertEquals(2, service.listarInscricoesPorProcesso(2001).size());
    }

    @Test
    void deveTolerarQuedaDoBrokerEReprocessarOutboxQuandoVoltar() throws Exception {
        // Simula queda do RabbitMQ
        publisher.falhar.set(true);

        Inscricao inscricao = service.inscreverAluno(1, 1002);
        assertTrue(inscricao.id() > 0);
        assertEquals(1, service.listarInscricoesPorAluno(1).size());

        aguardarCondicao(() -> {
            try {
                List<EventoOutbox> recentes = service.listarOutboxRecentes(5);
                return !recentes.isEmpty() && recentes.get(0).tentativas() >= 1;
            } catch (Exception e) {
                return false;
            }
        });

        assertEquals(0, publisher.mensagens.size());
        assertEquals(1, service.contarOutboxPendentes());

        List<EventoOutbox> outbox = service.listarOutboxRecentes(5);
        assertEquals("PENDENTE", outbox.get(0).status());
        assertTrue(outbox.get(0).tentativas() >= 1);
        assertNotNull(outbox.get(0).ultimoErro());

        // Broker volta ao ar -> reprocessamento entrega o evento sem perda de dados
        publisher.falhar.set(false);
        int publicados = service.reprocessarOutboxAgora();

        assertEquals(1, publicados);
        assertEquals(0, service.contarOutboxPendentes());
        assertEquals(1, publisher.mensagens.size());
        assertEquals(RabbitMQConfig.RK_INSCRICAO_CRIADA, publisher.mensagens.get(0).routingKey());
    }

    @Test
    void naoDeveBloquearInscricaoQuandoBrokerEstiverTravado() throws Exception {
        CountDownLatch publicacaoIniciada = new CountDownLatch(1);
        CountDownLatch liberarBroker = new CountDownLatch(1);
        publisher.publicacaoIniciada = publicacaoIniciada;
        publisher.bloquearAte = liberarBroker;

        long inicioNs = System.nanoTime();
        service.inscreverAluno(1, 1001);
        assertTrue(publicacaoIniciada.await(2, TimeUnit.SECONDS));

        // Segunda inscrição ocorre enquanto a primeira tentativa de envio no broker ainda está bloqueada
        service.inscreverAluno(2, 1001);
        long duracaoMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - inicioNs);
        assertTrue(duracaoMs < 1_000, "Inscrição não deveria bloquear esperando o broker (levou " + duracaoMs + "ms)");

        liberarBroker.countDown();
        aguardarCondicao(() -> publisher.mensagens.size() == 2 && contarPendentesSemExcecao() == 0);
        assertEquals(2, publisher.mensagens.size());
        assertEquals(0, service.contarOutboxPendentes());
    }

    private int contarPendentesSemExcecao() {
        try {
            return service.contarOutboxPendentes();
        } catch (Exception e) {
            return -1;
        }
    }

    private static void aguardarCondicao(BooleanSupplier condicao) throws InterruptedException {
        long limite = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
        while (System.nanoTime() < limite) {
            if (condicao.getAsBoolean()) {
                return;
            }
            Thread.sleep(15);
        }
        assertTrue(condicao.getAsBoolean(), "Condição assíncrona não foi satisfeita dentro do tempo limite.");
    }

    private record MensagemPublicada(String exchange, String routingKey, String payload) {
    }

    private static final class FakePublisher implements EventPublisher {
        final AtomicBoolean falhar = new AtomicBoolean(false);
        final List<MensagemPublicada> mensagens = Collections.synchronizedList(new ArrayList<>());
        volatile CountDownLatch publicacaoIniciada;
        volatile CountDownLatch bloquearAte;

        @Override
        public void publicar(String exchange, String routingKey, String payloadJson) throws InterruptedException {
            CountDownLatch iniciada = publicacaoIniciada;
            if (iniciada != null) {
                iniciada.countDown();
            }
            CountDownLatch gate = bloquearAte;
            if (gate != null) {
                gate.await(5, TimeUnit.SECONDS);
            }
            if (falhar.get()) {
                throw new RuntimeException("Broker RabbitMQ indisponível (simulação de falha)");
            }
            mensagens.add(new MensagemPublicada(exchange, routingKey, payloadJson));
        }
    }
}
