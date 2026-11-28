package inscricao.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.AMQP;
import inscricao.config.AppConfig;
import inscricao.database.Conexao;
import inscricao.database.DatabaseInitializer;
import inscricao.database.InscricaoRepository;
import inscricao.exception.RegraDeNegocioException;
import inscricao.exception.RegraDeNegocioException.TipoErro;
import inscricao.messaging.EventConsumer.AcaoFalha;
import inscricao.model.ProcessoSeletivoResumo;
import inscricao.model.StatusProcessoSeletivo;
import inscricao.service.InscricaoService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.SQLException;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class EventConsumerTest {

    private InscricaoService service;
    private EventConsumer consumer;

    @BeforeEach
    void setUp() throws Exception {
        String dbName = "ms4_consumer_" + UUID.randomUUID().toString().replace("-", "");
        Conexao conexao = new Conexao(
                "jdbc:h2:mem:" + dbName + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
                "sa",
                ""
        );
        DatabaseInitializer.inicializar(conexao);

        ObjectMapper mapper = new ObjectMapper();
        InscricaoRepository repository = new InscricaoRepository(conexao);
        OutboxRelay relay = new OutboxRelay(repository, (ex, rk, body) -> {}, 60_000);
        service = new InscricaoService(repository, relay, mapper);
        AppConfig dummyConfig = new AppConfig("", "", "", "localhost", 5672, "admin", "admin", 5_000);
        consumer = new EventConsumer(dummyConfig, service, mapper);
    }

    @Test
    void deveSincronizarEventosDeAlunoSalaEProcessoDeFormaIdempotente() throws Exception {
        String alunoJson = """
                {"id": 42, "nome": "Lucas Silva", "login": "lucas.silva"}
                """;
        String salaJson = """
                {"id": 7, "nome": "Compiladores", "professorId": 300, "professorNome": "Profa. Helena"}
                """;
        String processoAbertoJson = """
                {"id": 500, "salaId": 7, "professorId": 300, "titulo": "Monitoria Compiladores", "status": "ABERTO", "maxVagas": 3}
                """;

        consumer.processarMensagem("aluno.criado", alunoJson);
        // Reentrega duplicada do mesmo evento (idempotência)
        consumer.processarMensagem("aluno.criado", alunoJson);
        consumer.processarMensagem("sala.criada", salaJson);
        consumer.processarMensagem("processo_seletivo.publicado", processoAbertoJson);

        assertEquals(1, service.listarAlunos().size());
        assertEquals("Lucas Silva", service.listarAlunos().get(0).nome);
        assertEquals(1, service.listarSalas().size());
        assertEquals(1, service.listarProcessosAbertos().size());

        // Atualização de encerramento do processo seletivo
        String processoEncerradoJson = """
                {"id": 500, "salaId": 7, "professorId": 300, "titulo": "Monitoria Compiladores", "status": "ENCERRADO", "maxVagas": 3}
                """;
        consumer.processarMensagem("processo_seletivo.encerrado", processoEncerradoJson);

        assertTrue(service.listarProcessosAbertos().isEmpty());
        ProcessoSeletivoResumo resumo = service.listarTodosProcessos().get(0);
        assertEquals(StatusProcessoSeletivo.ENCERRADO, resumo.status());
    }

    @Test
    void deveLancarExcecaoParaPayloadMalformadoOuRoutingKeyInvalida() {
        JsonProcessingException jsonErr = assertThrows(
                JsonProcessingException.class,
                () -> consumer.processarMensagem("aluno.criado", "{json-quebrado")
        );
        assertEquals(AcaoFalha.ENVIAR_PARA_DLQ, EventConsumer.classificarFalha(jsonErr, 0));

        IllegalArgumentException rkErr = assertThrows(
                IllegalArgumentException.class,
                () -> consumer.processarMensagem("desconhecido.evento", "{}")
        );
        assertEquals(AcaoFalha.ENVIAR_PARA_DLQ, EventConsumer.classificarFalha(rkErr, 0));
    }

    @Test
    void deveEnviarDadosInvalidosDiretoParaDlqSemRetry() {
        String alunoInvalidoJson = """
                {"id": 0, "nome": "", "login": "invalido"}
                """;

        RegraDeNegocioException ex = assertThrows(
                RegraDeNegocioException.class,
                () -> consumer.processarMensagem("aluno.criado", alunoInvalidoJson)
        );
        assertEquals(TipoErro.DADOS_INVALIDOS, ex.getTipoErro());
        assertEquals(AcaoFalha.ENVIAR_PARA_DLQ, EventConsumer.classificarFalha(ex, 0));
    }

    @Test
    void deveAgendarRetryComLimiteParaEventoForaDeOrdemEErroDeBanco() throws Exception {
        String processoAntesDaSalaJson = """
                {"id": 501, "salaId": 77, "professorId": 300, "titulo": "Monitoria SO", "status": "ABERTO", "maxVagas": 2}
                """;
        String salaJson = """
                {"id": 77, "nome": "Sistemas Operacionais", "professorId": 300, "professorNome": "Prof. Carlos"}
                """;

        // Chegada fora de ordem: processo_seletivo antes de sala -> SALA_NAO_ENCONTRADA agenda retry
        RegraDeNegocioException exSala = assertThrows(
                RegraDeNegocioException.class,
                () -> consumer.processarMensagem("processo_seletivo.publicado", processoAntesDaSalaJson)
        );
        assertEquals(TipoErro.SALA_NAO_ENCONTRADA, exSala.getTipoErro());
        assertEquals(AcaoFalha.AGENDAR_RETRY, EventConsumer.classificarFalha(exSala, 0));
        assertEquals(AcaoFalha.AGENDAR_RETRY, EventConsumer.classificarFalha(exSala, RabbitMQConfig.MAX_RETRIES_SYNC - 1));
        assertEquals(AcaoFalha.ENVIAR_PARA_DLQ, EventConsumer.classificarFalha(exSala, RabbitMQConfig.MAX_RETRIES_SYNC));

        // Erro transitório de banco também respeita o limite de retries
        SQLException sqlErr = new SQLException("Lock wait timeout");
        assertEquals(AcaoFalha.AGENDAR_RETRY, EventConsumer.classificarFalha(sqlErr, 0));
        assertEquals(AcaoFalha.ENVIAR_PARA_DLQ, EventConsumer.classificarFalha(sqlErr, RabbitMQConfig.MAX_RETRIES_SYNC));

        // Quando a sala chega, o reprocessamento do evento do processo seletivo passa com sucesso
        consumer.processarMensagem("sala.criada", salaJson);
        consumer.processarMensagem("processo_seletivo.publicado", processoAntesDaSalaJson);
        assertEquals(1, service.listarProcessosAbertos().size());
        assertEquals(501, service.listarProcessosAbertos().get(0).id());
    }

    @Test
    void deveIncrementarHeaderDeRetryEPreservarPersistencia() {
        assertEquals(0, EventConsumer.extrairTentativasRetry(null));

        AMQP.BasicProperties originais = new AMQP.BasicProperties.Builder()
                .contentType("application/json")
                .headers(Map.of("origem", "ms3"))
                .build();

        AMQP.BasicProperties tentativa1 = EventConsumer.construirPropriedadesRetry(originais, 1);
        assertEquals(2, tentativa1.getDeliveryMode());
        assertEquals("application/json", tentativa1.getContentType());
        assertEquals("ms3", tentativa1.getHeaders().get("origem"));
        assertEquals(1, EventConsumer.extrairTentativasRetry(tentativa1));

        AMQP.BasicProperties tentativa2 = EventConsumer.construirPropriedadesRetry(tentativa1, 2);
        assertEquals(2, EventConsumer.extrairTentativasRetry(tentativa2));
    }
}
