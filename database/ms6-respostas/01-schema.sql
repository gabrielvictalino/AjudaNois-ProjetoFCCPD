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

CREATE TABLE IF NOT EXISTS matriculas (
    aluno_id INT NOT NULL,
    sala_id  INT NOT NULL,
    PRIMARY KEY (aluno_id, sala_id)
);

CREATE TABLE IF NOT EXISTS atividades (
    id      INT PRIMARY KEY,
    sala_id INT NOT NULL
);

CREATE TABLE IF NOT EXISTS questoes (
    id           INT           PRIMARY KEY,
    atividade_id INT           NOT NULL,
    enunciado    VARCHAR(2000) NOT NULL,
    pontuacao    DECIMAL(6,2)  NOT NULL
);

CREATE TABLE IF NOT EXISTS resultados (
    resposta_id CHAR(36)     PRIMARY KEY,
    correta     BOOLEAN      NOT NULL,
    posicao     INT          NULL,
    pontos      DECIMAL(6,2) NOT NULL DEFAULT 0
);

-- ---------------------------------------------------------------------
-- 2. DONO
-- ---------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS respostas (
    id           CHAR(36)     PRIMARY KEY,
    atividade_id INT          NOT NULL,
    aluno_id     INT          NOT NULL,
    enviada_em   TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    CONSTRAINT uk_respostas_aluno UNIQUE (atividade_id, aluno_id),
    CONSTRAINT fk_respostas_atividade FOREIGN KEY (atividade_id) REFERENCES atividades (id),
    CONSTRAINT fk_respostas_aluno     FOREIGN KEY (aluno_id)     REFERENCES alunos (id)
);

CREATE TABLE IF NOT EXISTS itens_resposta (
    resposta_id CHAR(36)     NOT NULL,
    questao_id  INT          NOT NULL,
    resposta    VARCHAR(500) NOT NULL,
    PRIMARY KEY (resposta_id, questao_id),
    CONSTRAINT fk_itens_resposta FOREIGN KEY (resposta_id) REFERENCES respostas (id),
    CONSTRAINT fk_itens_questao  FOREIGN KEY (questao_id)  REFERENCES questoes (id)
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
