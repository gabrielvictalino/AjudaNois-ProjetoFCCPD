package inscricao.database;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/**
 * Executa o script {@code schema.sql} de forma idempotente na inicialização do serviço.
 */
public final class DatabaseInitializer {

    private static final String SCHEMA_RESOURCE = "/schema.sql";

    private DatabaseInitializer() {
    }

    public static void inicializar(Conexao conexao) throws SQLException {
        List<String> statements = carregarComandosSql();
        try (Connection conn = conexao.conectar();
             Statement stmt = conn.createStatement()) {
            for (String sql : statements) {
                stmt.execute(sql);
            }
        }
    }

    private static List<String> carregarComandosSql() {
        InputStream stream = DatabaseInitializer.class.getResourceAsStream(SCHEMA_RESOURCE);
        if (stream == null) {
            throw new IllegalStateException("Recurso SQL não encontrado no classpath: " + SCHEMA_RESOURCE);
        }

        StringBuilder conteudoLimpo = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String linha;
            while ((linha = reader.readLine()) != null) {
                String trimmed = linha.trim();
                if (trimmed.isEmpty() || trimmed.startsWith("--")) {
                    continue;
                }
                conteudoLimpo.append(linha).append('\n');
            }
        } catch (IOException e) {
            throw new IllegalStateException("Falha ao ler " + SCHEMA_RESOURCE, e);
        }

        List<String> comandos = new ArrayList<>();
        for (String parte : conteudoLimpo.toString().split(";")) {
            String comando = parte.trim();
            if (!comando.isEmpty()) {
                comandos.add(comando);
            }
        }
        return comandos;
    }
}
