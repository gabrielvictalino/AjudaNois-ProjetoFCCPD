import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.GetResponse;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class Servidor {

    private static final String FILA = "atividades";

    // Quantidade de threads da pool
    private static final int NUM_THREADS = 3;

    // Respostas corretas
    private static final String RESPOSTA_Q1 = "A";
    private static final String RESPOSTA_Q2 = "B";
    private static final String RESPOSTA_Q3 = "C";

    public static void main(String[] args) throws Exception {

        // --------------------------------
        // 1. Conectar ao RabbitMQ
        // --------------------------------

        ConnectionFactory factory = new ConnectionFactory();

        factory.setHost("rabbitmq");

        Connection conexao = factory.newConnection();
        Channel canal = conexao.createChannel();

        canal.queueDeclare(
                FILA,
                true,
                false,
                false,
                null
        );

        ObjectMapper mapper = new ObjectMapper();

        // --------------------------------
        // 2. Retirar todas as mensagens
        // --------------------------------

        List<Atividade> atividades = new ArrayList<>();

        while (true) {

            GetResponse resposta = canal.basicGet(
                    FILA,
                    true
            );

            // Se não houver mais mensagens,
            // termina a retirada
            if (resposta == null) {
                break;
            }

            String json = new String(
                    resposta.getBody()
            );

            Atividade atividade =
                    mapper.readValue(
                            json,
                            Atividade.class
                    );

            atividades.add(atividade);

            System.out.println(
                    "Mensagem recebida: " + json
            );
        }

        System.out.println(
                "\nTotal de atividades recebidas: "
                        + atividades.size()
        );

        // --------------------------------
        // 3. Criar a pool de threads
        // --------------------------------

        ExecutorService pool =
                Executors.newFixedThreadPool(
                        NUM_THREADS
                );

        List<Future<Resultado>> resultados =
                new ArrayList<>();

        // --------------------------------
        // 4. Distribuir atividades
        //    entre as threads
        // --------------------------------

        for (Atividade atividade : atividades) {

            Future<Resultado> resultado =
                    pool.submit(
                            () -> processar(atividade)
                    );

            resultados.add(resultado);
        }

        // --------------------------------
        // 5. Esperar os resultados
        // --------------------------------

        List<Resultado> resultadosFinais =
                new ArrayList<>();

        for (Future<Resultado> resultado : resultados) {

            Resultado resultadoFinal =
                    resultado.get();

            resultadosFinais.add(resultadoFinal);
        }

        // Encerra a pool
        pool.shutdown();

        // --------------------------------
        // 6. Mostrar resultados
        // --------------------------------

        mostrarResultados(resultadosFinais);

        // --------------------------------
        // 7. Montar pódio
        // --------------------------------

        montarPodio(resultadosFinais);

        canal.close();
        conexao.close();
    }

    // =================================================
    // Processa uma atividade
    // =================================================

    public static Resultado processar(
            Atividade atividade) {

        boolean correta =
                atividade.getQ1().equals(RESPOSTA_Q1)
                        && atividade.getQ2().equals(RESPOSTA_Q2)
                        && atividade.getQ3().equals(RESPOSTA_Q3);

        double pontos = 0;

        if (correta) {

            if (atividade.getPosicao() == 1) {
                pontos = 1.0;
            }
            else if (atividade.getPosicao() == 2) {
                pontos = 0.75;
            }
            else if (atividade.getPosicao() == 3) {
                pontos = 0.5;
            }
        }

        System.out.println(
                "Thread: "
                        + Thread.currentThread().getName()
                        + " | Aluno: "
                        + atividade.getAluno()
                        + " | Correta: "
                        + correta
        );

        return new Resultado(
                atividade.getAluno(),
                atividade.getPosicao(),
                correta,
                pontos
        );
    }

    // =================================================
    // Mostra o resultado de cada atividade
    // =================================================

    public static void mostrarResultados(
            List<Resultado> resultados) {

        System.out.println(
                "\n===== RESULTADOS ====="
        );

        for (Resultado resultado : resultados) {

            System.out.println(
                    "Aluno: "
                            + resultado.getAluno()
                            + " | Posição: "
                            + resultado.getPosicao()
                            + " | Correta: "
                            + resultado.isCorreta()
                            + " | Pontos: "
                            + resultado.getPontos()
            );
        }
    }

    // =================================================
    // Monta o pódio
    // =================================================

    public static void montarPodio(
            List<Resultado> resultados) {

        List<Resultado> corretos =
                new ArrayList<>();

        // Pega somente atividades corretas
        for (Resultado resultado : resultados) {

            if (resultado.isCorreta()) {
                corretos.add(resultado);
            }
        }

        // Ordena pela posição de envio
        corretos.sort(
                Comparator.comparingInt(
                        Resultado::getPosicao
                )
        );

        System.out.println(
                "\n===== PÓDIO ====="
        );

        int limite = Math.min(
                3,
                corretos.size()
        );

        for (int i = 0; i < limite; i++) {

            Resultado resultado =
                    corretos.get(i);

            double pontos;

            if (i == 0) {
                pontos = 1.0;
            }
            else if (i == 1) {
                pontos = 0.75;
            }
            else {
                pontos = 0.5;
            }

            System.out.println(
                    (i + 1)
                            + "º lugar - "
                            + resultado.getAluno()
                            + " - "
                            + pontos
                            + " ponto(s)"
            );
        }
    }
}