-- ---------------------------------------------------------------------
-- 1. CÓPIAS
-- ---------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS copia_exemplos (
    id            INT          PRIMARY KEY,
    nome          VARCHAR(150) NOT NULL
);

-- ---------------------------------------------------------------------
-- 2. DONO
-- ---------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS exemplos (
    id               INT AUTO_INCREMENT PRIMARY KEY,
    nome             VARCHAR(150) NOT NULL,
    copia_exemplo_id INT          NOT NULL,
    status           VARCHAR(20)  NOT NULL DEFAULT 'ATIVO',
    CONSTRAINT uk_exemplos_nome   UNIQUE (nome),
    CONSTRAINT ck_exemplos_status CHECK (status IN ('ATIVO', 'INATIVO')),
    CONSTRAINT fk_exemplos_copia  FOREIGN KEY (copia_exemplo_id) REFERENCES copia_exemplos (id)
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
