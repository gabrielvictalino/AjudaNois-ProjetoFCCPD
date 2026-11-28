-- ---------------------------------------------------------------------
-- 1. CÓPIAS
-- ---------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS professores (
    id   INT          PRIMARY KEY,
    nome VARCHAR(150) NOT NULL
);

CREATE TABLE IF NOT EXISTS processos_seletivos (
    id      INT PRIMARY KEY,
    sala_id INT NOT NULL
);

-- ---------------------------------------------------------------------
-- 2. DONO
-- ---------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS salas (
    id           INT AUTO_INCREMENT PRIMARY KEY,
    nome         VARCHAR(150) NOT NULL,
    professor_id INT          NOT NULL,
    CONSTRAINT fk_salas_professor FOREIGN KEY (professor_id) REFERENCES professores (id)
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
