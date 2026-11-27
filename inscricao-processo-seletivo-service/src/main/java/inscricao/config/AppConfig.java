package inscricao.config;

/**
 * Configuração do Microsserviço 4 carregada a partir de variáveis de ambiente
 * com valores padrão adequados para execução local ou via Docker Compose.
 */
public record AppConfig(
        String dbUrl,
        String dbUser,
        String dbPassword,
        String rabbitHost,
        int rabbitPort,
        String rabbitUser,
        String rabbitPassword,
        long outboxRetryIntervalMs
) {

    public static AppConfig fromEnv() {
        return new AppConfig(
                envOrDefault("DB_URL", "jdbc:mysql://localhost:3307/inscricao_ps_db?useSSL=false&allowPublicKeyRetrieval=true"),
                envOrDefault("DB_USER", "inscricao"),
                envOrDefault("DB_PASSWORD", "senha"),
                envOrDefault("RABBITMQ_HOST", "localhost"),
                Integer.parseInt(envOrDefault("RABBITMQ_PORT", "5672")),
                envOrDefault("RABBITMQ_USER", "admin"),
                envOrDefault("RABBITMQ_PASS", "admin"),
                Long.parseLong(envOrDefault("OUTBOX_RETRY_MS", "5000"))
        );
    }

    private static String envOrDefault(String key, String defaultValue) {
        String value = System.getenv(key);
        return (value == null || value.isBlank()) ? defaultValue : value;
    }
}
