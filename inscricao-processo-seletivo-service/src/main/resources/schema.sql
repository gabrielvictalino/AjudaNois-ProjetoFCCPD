-- Schema do Microsserviço 4 (Inscrição de Alunos em Processo Seletivo)
-- Compatível com MySQL 8.4 e H2 (MODE=MySQL) para testes unitários/integração.

CREATE TABLE IF NOT EXISTS alunos (
    id INT PRIMARY KEY,
    nome VARCHAR(150) NOT NULL,
    login VARCHAR(100) NOT NULL UNIQUE,
    atualizado_em TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS salas (
    id INT PRIMARY KEY,
    nome VARCHAR(150) NOT NULL,
    professor_id INT NOT NULL,
    professor_nome VARCHAR(150) NOT NULL,
    atualizado_em TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS processos_seletivos (
    id INT PRIMARY KEY,
    sala_id INT NOT NULL,
    professor_id INT NOT NULL,
    titulo VARCHAR(200) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ABERTO',
    max_vagas INT NOT NULL DEFAULT 0,
    criado_em TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_ps_sala FOREIGN KEY (sala_id) REFERENCES salas (id)
);

CREATE TABLE IF NOT EXISTS inscricoes (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    aluno_id INT NOT NULL,
    processo_seletivo_id INT NOT NULL,
    sala_id INT NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'INSCRITO',
    data_inscricao TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_aluno_processo UNIQUE (aluno_id, processo_seletivo_id),
    CONSTRAINT fk_insc_aluno FOREIGN KEY (aluno_id) REFERENCES alunos (id),
    CONSTRAINT fk_insc_ps FOREIGN KEY (processo_seletivo_id) REFERENCES processos_seletivos (id),
    CONSTRAINT fk_insc_sala FOREIGN KEY (sala_id) REFERENCES salas (id)
);

-- Padrão Transactional Outbox para tolerância a falhas na publicação RabbitMQ
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
