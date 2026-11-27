package inscricao.database;

import inscricao.config.AppConfig;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

/**
 * Provedor de conexões JDBC desacoplado de variáveis de ambiente diretas
 * para permitir uso tanto com MySQL (produção/Docker) quanto com H2 (testes).
 */
public class Conexao {

    private final String url;
    private final String usuario;
    private final String senha;

    public Conexao(String url, String usuario, String senha) {
        this.url = url;
        this.usuario = usuario;
        this.senha = senha;
    }

    public static Conexao fromConfig(AppConfig config) {
        return new Conexao(config.dbUrl(), config.dbUser(), config.dbPassword());
    }

    public Connection conectar() throws SQLException {
        return DriverManager.getConnection(url, usuario, senha);
    }

    /**
     * Aguarda o banco ficar disponível com retry simples — útil quando o container
     * sobe em paralelo ao MySQL no Docker Compose.
     */
    public void aguardarDisponibilidade(int maxTentativas, long intervaloMs) throws SQLException {
        SQLException ultimoErro = null;
        for (int tentativa = 1; tentativa <= maxTentativas; tentativa++) {
            try (Connection conn = conectar()) {
                if (conn.isValid(2)) {
                    return;
                }
            } catch (SQLException e) {
                ultimoErro = e;
                System.out.printf(
                        "[DB] Aguardando banco de dados (tentativa %d/%d): %s%n",
                        tentativa, maxTentativas, e.getMessage()
                );
                try {
                    Thread.sleep(intervaloMs);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new SQLException("Espera pelo banco interrompida.", ie);
                }
            }
        }
        throw (ultimoErro != null)
                ? ultimoErro
                : new SQLException("Banco de dados indisponível após " + maxTentativas + " tentativas.");
    }
}
