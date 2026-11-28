-- ---------------------------------------------------------------------
-- 1. CÓPIAS
-- ---------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS alunos (
    id   INT          PRIMARY KEY,
    nome VARCHAR(150) NOT NULL
);

CREATE TABLE IF NOT EXISTS salas (
    id   INT          PRIMARY KEY,
    nome VARCHAR(150) NOT NULL
);

-- ---------------------------------------------------------------------
-- 2. DONO
-- ---------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS matriculas (
    aluno_id INT NOT NULL,
    sala_id  INT NOT NULL,
    PRIMARY KEY (aluno_id, sala_id),
    CONSTRAINT fk_matriculas_aluno FOREIGN KEY (aluno_id) REFERENCES alunos (id),
    CONSTRAINT fk_matriculas_sala  FOREIGN KEY (sala_id)  REFERENCES salas (id)
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
