package inscricao.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import inscricao.config.AppConfig;
import inscricao.database.Conexao;
import inscricao.database.DatabaseInitializer;
import inscricao.database.InscricaoRepository;
import inscricao.model.ProcessoSeletivoResumo;
import inscricao.model.StatusProcessoSeletivo;
import inscricao.service.InscricaoService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

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
        assertThrows(
                JsonProcessingException.class,
                () -> consumer.processarMensagem("aluno.criado", "{json-quebrado")
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> consumer.processarMensagem("desconhecido.evento", "{}")
        );
    }
}
