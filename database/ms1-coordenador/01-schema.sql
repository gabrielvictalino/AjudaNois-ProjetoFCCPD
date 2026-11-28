-- ---------------------------------------------------------------------
-- 2. DONO
-- ---------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS coordenadores (
    id    INT AUTO_INCREMENT PRIMARY KEY,
    nome  VARCHAR(150) NOT NULL,
    login VARCHAR(100) NOT NULL UNIQUE
);

CREATE TABLE IF NOT EXISTS professores (
    id    INT AUTO_INCREMENT PRIMARY KEY,
    nome  VARCHAR(150) NOT NULL,
    login VARCHAR(100) NOT NULL UNIQUE
);

CREATE TABLE IF NOT EXISTS alunos (
    id    INT AUTO_INCREMENT PRIMARY KEY,
    nome  VARCHAR(150) NOT NULL,
    login VARCHAR(100) NOT NULL UNIQUE
);

-- ---------------------------------------------------------------------
-- 3. MENSAGERIA
-- ---------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS eventos_outbox (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    exchange_name VARCHAR(120) NOT NULL,
    routing_key VARCHAR(120) NOT NULL,
    payload TEXT NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDENTE',
    tentativas INT NOT NULL DEFAULT 0,
    ultimo_erro VARCHAR(500),
    criado_em TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    publicado_em TIMESTAMP NULL
);
