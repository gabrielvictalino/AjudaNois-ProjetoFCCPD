-- ---------------------------------------------------------------------
-- 1. CÓPIAS
-- ---------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS professores (
    id   INT          PRIMARY KEY,
    nome VARCHAR(150) NOT NULL
);

CREATE TABLE IF NOT EXISTS alunos (
    id   INT          PRIMARY KEY,
    nome VARCHAR(150) NOT NULL
);

CREATE TABLE IF NOT EXISTS salas (
    id           INT          PRIMARY KEY,
    nome         VARCHAR(150) NOT NULL,
    professor_id INT          NOT NULL
);

CREATE TABLE IF NOT EXISTS inscricoes (
    inscricao_id         BIGINT      PRIMARY KEY,
    aluno_id             INT         NOT NULL,
    processo_seletivo_id INT         NOT NULL,
    status               VARCHAR(20) NOT NULL
);

-- ---------------------------------------------------------------------
-- 2. DONO
-- ---------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS processos_seletivos (
    id           INT AUTO_INCREMENT PRIMARY KEY,
    sala_id      INT          NOT NULL,
    professor_id INT          NOT NULL,
    titulo       VARCHAR(200) NOT NULL,
    max_vagas    INT          NOT NULL,
    status       VARCHAR(20)  NOT NULL DEFAULT 'ABERTO',
    CONSTRAINT ck_processos_status CHECK (status IN ('ABERTO', 'ENCERRADO')),
    CONSTRAINT ck_processos_vagas  CHECK (max_vagas > 0),
    CONSTRAINT fk_processos_sala      FOREIGN KEY (sala_id)      REFERENCES salas (id),
    CONSTRAINT fk_processos_professor FOREIGN KEY (professor_id) REFERENCES professores (id)
);

CREATE TABLE IF NOT EXISTS monitores (
    aluno_id             INT NOT NULL,
    processo_seletivo_id INT NOT NULL,
    sala_id              INT NOT NULL,
    PRIMARY KEY (aluno_id, processo_seletivo_id),
    CONSTRAINT fk_monitores_processo FOREIGN KEY (processo_seletivo_id) REFERENCES processos_seletivos (id),
    CONSTRAINT fk_monitores_aluno    FOREIGN KEY (aluno_id)             REFERENCES alunos (id)
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
