package inscricao;

import com.fasterxml.jackson.databind.ObjectMapper;
import inscricao.database.Conexao;
import inscricao.database.DatabaseInitializer;
import inscricao.database.InscricaoRepository;
import inscricao.messaging.OutboxRelay;
import inscricao.service.InscricaoService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Scanner;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.*;

class MainTest {

    private InscricaoService service;

    @BeforeEach
    void setUp() throws Exception {
        String dbName = "ms4_main_" + UUID.randomUUID().toString().replace("-", "");
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
    }

    @Test
    void deveManterProcessoAtivoEmModoHeadlessQuandoStdinRetornarEofNoMenu() throws Exception {
        Scanner scanner = new Scanner(new ByteArrayInputStream(new byte[0]), StandardCharsets.UTF_8);
        CountDownLatch shutdownLatch = new CountDownLatch(1);
        ExecutorService executor = Executors.newSingleThreadExecutor();

        try {
            Future<?> execucao = executor.submit(() -> Main.executarMenuOuHeadless(scanner, service, shutdownLatch));

            // Deve permanecer bloqueado em modo headless sem lançar NoSuchElementException
            assertThrows(TimeoutException.class, () -> execucao.get(200, TimeUnit.MILLISECONDS));

            // Libera o latch simulando sinal de shutdown (SIGTERM)
            shutdownLatch.countDown();
            execucao.get(2, TimeUnit.SECONDS);
        } finally {
            scanner.close();
            executor.shutdownNow();
        }
    }

    @Test
    void deveManterProcessoAtivoEmModoHeadlessQuandoEofOcorrerDuranteLeituraDeTexto() throws Exception {
        // Entra na opção 7 (Cadastro manual) -> 1 (Aluno) -> ID 10 -> EOF antes de lerTexto() do nome
        byte[] entrada = "7\n1\n10\n".getBytes(StandardCharsets.UTF_8);
        Scanner scanner = new Scanner(new ByteArrayInputStream(entrada), StandardCharsets.UTF_8);
        CountDownLatch shutdownLatch = new CountDownLatch(1);
        ExecutorService executor = Executors.newSingleThreadExecutor();

        try {
            Future<?> execucao = executor.submit(() -> Main.executarMenuOuHeadless(scanner, service, shutdownLatch));

            assertThrows(TimeoutException.class, () -> execucao.get(200, TimeUnit.MILLISECONDS));

            shutdownLatch.countDown();
            execucao.get(2, TimeUnit.SECONDS);
            assertTrue(service.listarAlunos().isEmpty());
        } finally {
            scanner.close();
            executor.shutdownNow();
        }
    }

    @Test
    void deveEncerrarNormalmenteQuandoUsuarioEscolherOpcaoSair() throws Exception {
        byte[] entrada = "0\n".getBytes(StandardCharsets.UTF_8);
        Scanner scanner = new Scanner(new ByteArrayInputStream(entrada), StandardCharsets.UTF_8);
        CountDownLatch shutdownLatch = new CountDownLatch(1);
        ExecutorService executor = Executors.newSingleThreadExecutor();

        try {
            Future<?> execucao = executor.submit(() -> Main.executarMenuOuHeadless(scanner, service, shutdownLatch));
            // Deve encerrar imediatamente sem aguardar o latch
            execucao.get(2, TimeUnit.SECONDS);
            assertEquals(1, shutdownLatch.getCount());
        } finally {
            scanner.close();
            executor.shutdownNow();
        }
    }
}
