import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;

public class Cliente {

    private static final String FILA = "atividades";

    public static void main(String[] args) throws Exception {

        // Verifica se foram informados os 5 dados
        if (args.length != 5) {

            System.out.println(
                    "Uso: java Cliente <aluno> <posicao> <Q1> <Q2> <Q3>"
            );

            return;
        }

        String aluno = args[0];
        int posicao = Integer.parseInt(args[1]);
        String Q1 = args[2];
        String Q2 = args[3];
        String Q3 = args[4];

        // Conecta ao RabbitMQ
        ConnectionFactory factory = new ConnectionFactory();

        factory.setHost("localhost");
        
        factory.setUsername("admin");
        factory.setPassword("admin");
        
        Connection conexao = factory.newConnection();
        Channel canal = conexao.createChannel();

        // Cria a fila caso ela ainda não exista
        canal.queueDeclare(
                FILA,
                true,
                false,
                false,
                null
        );

        // Cria a atividade
        Atividade atividade = new Atividade(
                aluno,
                posicao,
                Q1,
                Q2,
                Q3
        );

        // Converte a atividade para JSON
        ObjectMapper mapper = new ObjectMapper();

        String json = mapper.writeValueAsString(atividade);

        // Envia UMA atividade para a fila
        canal.basicPublish(
                "",
                FILA,
                null,
                json.getBytes()
        );

        System.out.println("Atividade enviada:");
        System.out.println(json);

        canal.close();
        conexao.close();
    }
}