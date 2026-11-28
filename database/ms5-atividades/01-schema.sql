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

CREATE TABLE IF NOT EXISTS monitores (
    aluno_id INT NOT NULL,
    sala_id  INT NOT NULL,
    PRIMARY KEY (aluno_id, sala_id)
);

-- ---------------------------------------------------------------------
-- 2. DONO
-- ---------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS atividades (
    id         INT AUTO_INCREMENT PRIMARY KEY,
    sala_id    INT       NOT NULL,
    monitor_id INT       NOT NULL,
    data_envio TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_atividades_monitor FOREIGN KEY (monitor_id, sala_id) REFERENCES monitores (aluno_id, sala_id)
);

CREATE TABLE IF NOT EXISTS questoes (
    id           INT AUTO_INCREMENT PRIMARY KEY,
    atividade_id INT           NOT NULL,
    enunciado    VARCHAR(2000) NOT NULL,
    resposta     VARCHAR(500)  NOT NULL,
    pontuacao    DECIMAL(6,2)  NOT NULL DEFAULT 1.00,
    CONSTRAINT fk_questoes_atividade FOREIGN KEY (atividade_id) REFERENCES atividades (id)
);

CREATE TABLE IF NOT EXISTS correcoes (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    atividade_id INT          NOT NULL,
    aluno_id     INT          NOT NULL,
    resposta_id  CHAR(36)     NOT NULL UNIQUE,
    enviada_em   TIMESTAMP(3) NOT NULL,
    correta      BOOLEAN      NOT NULL,
    posicao      INT          NULL,
    pontos       DECIMAL(6,2) NOT NULL DEFAULT 0,
    CONSTRAINT uk_correcoes_aluno   UNIQUE (atividade_id, aluno_id),
    CONSTRAINT uk_correcoes_posicao UNIQUE (atividade_id, posicao),
    CONSTRAINT fk_correcoes_atividade FOREIGN KEY (atividade_id) REFERENCES atividades (id)
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
