package inscricao;

import com.fasterxml.jackson.databind.ObjectMapper;
import entidades.Aluno;
import entidades.Sala;
import inscricao.config.AppConfig;
import inscricao.database.Conexao;
import inscricao.database.DatabaseInitializer;
import inscricao.database.InscricaoRepository;
import inscricao.exception.RegraDeNegocioException;
import inscricao.messaging.EventConsumer;
import inscricao.messaging.OutboxRelay;
import inscricao.messaging.RabbitMQEventPublisher;
import inscricao.model.EventoOutbox;
import inscricao.model.Eventos.AlunoSyncEvent;
import inscricao.model.Eventos.ProcessoSeletivoSyncEvent;
import inscricao.model.Eventos.SalaSyncEvent;
import inscricao.model.Inscricao;
import inscricao.model.ProcessoSeletivoResumo;
import inscricao.service.InscricaoService;
import inscricao.service.InscricaoService.ResultadoTesteConcorrencia;

import java.sql.SQLException;
import java.util.List;
import java.util.Scanner;

/**
 * Ponto de entrada do Microsserviço 4: Inscrição de Alunos em Processo Seletivo.
 */
public class Main {

    public static void main(String[] args) {
        AppConfig config = AppConfig.fromEnv();
        Conexao conexao = Conexao.fromConfig(config);
        ObjectMapper mapper = new ObjectMapper();

        try {
            System.out.println("[MS4] Conectando ao banco de dados: " + config.dbUrl());
            conexao.aguardarDisponibilidade(15, 2_000);
            DatabaseInitializer.inicializar(conexao);
            System.out.println("[MS4] Schema do banco verificado com sucesso.");
        } catch (SQLException e) {
            System.err.println("[MS4] Falha fatal ao inicializar banco de dados: " + e.getMessage());
            System.exit(1);
            return;
        }

        InscricaoRepository repository = new InscricaoRepository(conexao);
        RabbitMQEventPublisher publisher = new RabbitMQEventPublisher(config);
        OutboxRelay outboxRelay = new OutboxRelay(repository, publisher, config.outboxRetryIntervalMs());
        InscricaoService service = new InscricaoService(repository, outboxRelay, mapper);
        EventConsumer consumer = new EventConsumer(config, service, mapper);

        outboxRelay.iniciar();
        consumer.iniciar();

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            consumer.close();
            outboxRelay.close();
            publisher.close();
        }, "ms4-shutdown"));

        try (Scanner scanner = new Scanner(System.in)) {
            executarMenu(scanner, service);
        } finally {
            consumer.close();
            outboxRelay.close();
            publisher.close();
        }
    }

    private static void executarMenu(Scanner scanner, InscricaoService service) {
        while (true) {
            System.out.println("\n====================================================");
            System.out.println("  MS4 - INSCRIÇÃO EM PROCESSO SELETIVO (AjudaNois)");
            System.out.println("====================================================");
            System.out.println("1 - Listar processos seletivos abertos");
            System.out.println("2 - Inscrever aluno em processo seletivo");
            System.out.println("3 - Cancelar inscrição de aluno");
            System.out.println("4 - Consultar inscrições de um aluno");
            System.out.println("5 - Consultar inscritos de um processo seletivo");
            System.out.println("6 - Popular dados de exemplo (seed local)");
            System.out.println("7 - Cadastro manual local (Aluno / Sala / Processo)");
            System.out.println("8 - Status do Outbox RabbitMQ / forçar reprocessamento");
            System.out.println("9 - Simular concorrência de inscrição (teste consistência)");
            System.out.println("0 - Sair");
            System.out.println("====================================================");

            int escolha = lerInteiro(scanner, "Escolha uma opção: ");
            if (escolha == 0) {
                System.out.println("Encerrando Microsserviço 4...");
                break;
            }

            try {
                switch (escolha) {
                    case 1 -> exibirProcessosSeletivos(service, true);
                    case 2 -> realizarInscricao(scanner, service);
                    case 3 -> realizarCancelamento(scanner, service);
                    case 4 -> exibirInscricoesDoAluno(scanner, service);
                    case 5 -> exibirInscritosDoProcesso(scanner, service);
                    case 6 -> {
                        service.popularDadosExemplo();
                        System.out.println("Dados de exemplo populados com sucesso (Alunos 1..3, Salas 10/20, Processos 1001..1003).");
                    }
                    case 7 -> menuCadastroLocal(scanner, service);
                    case 8 -> exibirEReprocessarOutbox(scanner, service);
                    case 9 -> executarSimulacaoConcorrencia(scanner, service);
                    default -> System.out.println("Opção inválida!");
                }
            } catch (RegraDeNegocioException e) {
                System.out.printf("[REGRA DE NEGÓCIO - %s] %s%n", e.getTipoErro(), e.getMessage());
            } catch (SQLException e) {
                System.out.println("[ERRO DE BANCO] " + e.getMessage());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                System.out.println("Operação interrompida.");
            }
        }
    }

    private static void exibirProcessosSeletivos(InscricaoService service, boolean apenasAbertos) throws SQLException {
        List<ProcessoSeletivoResumo> processos = apenasAbertos
                ? service.listarProcessosAbertos()
                : service.listarTodosProcessos();

        if (processos.isEmpty()) {
            System.out.println("Nenhum processo seletivo encontrado. Use a opção 6 para popular dados de exemplo.");
            return;
        }

        System.out.println("\n--- PROCESSOS SELETIVOS ---");
        for (ProcessoSeletivoResumo ps : processos) {
            String vagas = ps.possuiLimiteDeVagas()
                    ? ps.totalInscritos() + "/" + ps.maxVagas()
                    : ps.totalInscritos() + "/ilimitadas";
            System.out.printf(
                    "ID: %d | Título: %s | Sala: [%d] %s | Prof: %s | Status: %s | Inscritos: %s%n",
                    ps.id(), ps.titulo(), ps.salaId(), ps.salaNome(),
                    ps.professorNome(), ps.status(), vagas
            );
        }
    }

    private static void realizarInscricao(Scanner scanner, InscricaoService service) throws SQLException {
        exibirAlunos(service);
        exibirProcessosSeletivos(service, true);

        int alunoId = lerInteiro(scanner, "Digite o ID do aluno: ");
        int processoId = lerInteiro(scanner, "Digite o ID do processo seletivo: ");

        Inscricao inscricao = service.inscreverAluno(alunoId, processoId);
        System.out.printf(
                "Inscrição #%d confirmada! Aluno '%s' (ID %d) inscrito em '%s' (Sala: %s).%n",
                inscricao.id(),
                inscricao.alunoNome(),
                inscricao.alunoId(),
                inscricao.tituloProcesso(),
                inscricao.salaNome()
        );
    }

    private static void realizarCancelamento(Scanner scanner, InscricaoService service) throws SQLException {
        int alunoId = lerInteiro(scanner, "Digite o ID do aluno: ");
        int processoId = lerInteiro(scanner, "Digite o ID do processo seletivo: ");

        Inscricao cancelada = service.cancelarInscricao(alunoId, processoId);
        System.out.printf(
                "Inscrição #%d cancelada com sucesso (Aluno %d, Processo %d).%n",
                cancelada.id(), cancelada.alunoId(), cancelada.processoSeletivoId()
        );
    }

    private static void exibirInscricoesDoAluno(Scanner scanner, InscricaoService service) throws SQLException {
        int alunoId = lerInteiro(scanner, "Digite o ID do aluno: ");
        List<Inscricao> inscricoes = service.listarInscricoesPorAluno(alunoId);

        if (inscricoes.isEmpty()) {
            System.out.println("Nenhuma inscrição encontrada para o aluno " + alunoId + ".");
            return;
        }

        System.out.println("\n--- INSCRIÇÕES DO ALUNO " + alunoId + " ---");
        for (Inscricao i : inscricoes) {
            System.out.printf(
                    "Inscrição #%d | Processo: [%d] %s | Sala: [%d] %s | Status: %s | Data: %s%n",
                    i.id(), i.processoSeletivoId(), i.tituloProcesso(),
                    i.salaId(), i.salaNome(), i.status(), i.dataInscricao()
            );
        }
    }

    private static void exibirInscritosDoProcesso(Scanner scanner, InscricaoService service) throws SQLException {
        int processoId = lerInteiro(scanner, "Digite o ID do processo seletivo: ");
        List<Inscricao> inscricoes = service.listarInscricoesPorProcesso(processoId);

        if (inscricoes.isEmpty()) {
            System.out.println("Nenhum aluno inscrito no processo seletivo " + processoId + ".");
            return;
        }

        System.out.println("\n--- INSCRITOS NO PROCESSO SELETIVO " + processoId + " ---");
        for (Inscricao i : inscricoes) {
            System.out.printf(
                    "Inscrição #%d | Aluno: [%d] %s | Sala: %s | Status: %s | Data: %s%n",
                    i.id(), i.alunoId(), i.alunoNome(), i.salaNome(), i.status(), i.dataInscricao()
            );
        }
    }

    private static void menuCadastroLocal(Scanner scanner, InscricaoService service) throws SQLException {
        System.out.println("\n--- CADASTRO MANUAL LOCAL (MODO ISOLADO) ---");
        System.out.println("1 - Cadastrar/Atualizar Aluno");
        System.out.println("2 - Cadastrar/Atualizar Sala");
        System.out.println("3 - Cadastrar/Atualizar Processo Seletivo");
        System.out.println("4 - Listar Alunos e Salas");

        int sub = lerInteiro(scanner, "Escolha: ");
        switch (sub) {
            case 1 -> {
                int id = lerInteiro(scanner, "ID do aluno: ");
                String nome = lerTexto(scanner, "Nome do aluno: ");
                String login = lerTexto(scanner, "Login do aluno: ");
                service.sincronizarAluno(new AlunoSyncEvent(id, nome, login));
                System.out.println("Aluno sincronizado localmente.");
            }
            case 2 -> {
                int id = lerInteiro(scanner, "ID da sala: ");
                String nome = lerTexto(scanner, "Nome da sala: ");
                int profId = lerInteiro(scanner, "ID do professor responsável: ");
                String profNome = lerTexto(scanner, "Nome do professor responsável: ");
                service.sincronizarSala(new SalaSyncEvent(id, nome, profId, profNome));
                System.out.println("Sala sincronizada localmente.");
            }
            case 3 -> {
                int id = lerInteiro(scanner, "ID do processo seletivo: ");
                int salaId = lerInteiro(scanner, "ID da sala: ");
                int profId = lerInteiro(scanner, "ID do professor: ");
                String titulo = lerTexto(scanner, "Título do processo seletivo: ");
                String status = lerTexto(scanner, "Status (ABERTO ou ENCERRADO): ");
                int maxVagas = lerInteiro(scanner, "Limite de vagas (0 = ilimitado): ");
                service.sincronizarProcessoSeletivo(
                        new ProcessoSeletivoSyncEvent(id, salaId, profId, titulo, status, maxVagas)
                );
                System.out.println("Processo seletivo sincronizado localmente.");
            }
            case 4 -> {
                exibirAlunos(service);
                exibirSalas(service);
                exibirProcessosSeletivos(service, false);
            }
            default -> System.out.println("Opção inválida!");
        }
    }

    private static void exibirEReprocessarOutbox(Scanner scanner, InscricaoService service) throws SQLException {
        int pendentes = service.contarOutboxPendentes();
        System.out.println("\n--- TRANSACTIONAL OUTBOX (RabbitMQ) ---");
        System.out.println("Eventos PENDENTES de publicação: " + pendentes);

        List<EventoOutbox> recentes = service.listarOutboxRecentes(10);
        for (EventoOutbox e : recentes) {
            System.out.printf(
                    "#%d | RK: %s | Status: %s | Tentativas: %d | Erro: %s%n",
                    e.id(), e.routingKey(), e.status(), e.tentativas(),
                    e.ultimoErro() == null ? "-" : e.ultimoErro()
            );
        }

        if (pendentes > 0) {
            String resp = lerTexto(scanner, "Deseja forçar tentativa de reprocessamento agora? (s/N): ");
            if (resp.equalsIgnoreCase("s")) {
                int enviados = service.reprocessarOutboxAgora();
                System.out.println("Eventos publicados e confirmados nesta tentativa: " + enviados);
            }
        }
    }

    private static void executarSimulacaoConcorrencia(Scanner scanner, InscricaoService service)
            throws InterruptedException {
        int alunoId = lerInteiro(scanner, "ID do aluno para o teste concorrente: ");
        int processoId = lerInteiro(scanner, "ID do processo seletivo: ");
        int threads = lerInteiro(scanner, "Quantidade de threads simultâneas (ex.: 8): ");

        ResultadoTesteConcorrencia res = service.simularConflitoInscricaoDuplicada(alunoId, processoId, threads);
        System.out.println("\n--- RESULTADO DO TESTE DE CONCORRÊNCIA ---");
        System.out.println("Tentativas simultâneas : " + res.totalTentativas());
        System.out.println("Inscrições confirmadas : " + res.inscricoesConfirmadas());
        System.out.println("Conflitos bloqueados   : " + res.conflitosBloqueados());
        System.out.println("Outros erros           : " + res.outrosErros());
    }

    private static void exibirAlunos(InscricaoService service) throws SQLException {
        List<Aluno> alunos = service.listarAlunos();
        System.out.println("\n--- ALUNOS ---");
        if (alunos.isEmpty()) {
            System.out.println("(nenhum aluno cadastrado)");
            return;
        }
        for (Aluno a : alunos) {
            System.out.printf("ID: %d | Nome: %s | Login: %s%n", a.id, a.nome, a.login);
        }
    }

    private static void exibirSalas(InscricaoService service) throws SQLException {
        List<Sala> salas = service.listarSalas();
        System.out.println("\n--- SALAS ---");
        if (salas.isEmpty()) {
            System.out.println("(nenhuma sala cadastrada)");
            return;
        }
        for (Sala s : salas) {
            String prof = s.professor != null ? s.professor.nome : "-";
            System.out.printf("ID: %d | Nome: %s | Professor: %s%n", s.id, s.nome, prof);
        }
    }

    private static int lerInteiro(Scanner scanner, String prompt) {
        while (true) {
            System.out.print(prompt);
            String linha = scanner.nextLine().trim();
            try {
                return Integer.parseInt(linha);
            } catch (NumberFormatException e) {
                System.out.println("Digite um número inteiro válido.");
            }
        }
    }

    private static String lerTexto(Scanner scanner, String prompt) {
        System.out.print(prompt);
        return scanner.nextLine().trim();
    }
}
