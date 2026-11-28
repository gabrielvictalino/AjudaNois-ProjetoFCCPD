-- ---------------------------------------------------------------------
-- 2. DONO
-- ---------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS usuarios (
    id            INT AUTO_INCREMENT PRIMARY KEY,
    login         VARCHAR(100) NOT NULL UNIQUE,
    senha         VARCHAR(100) NOT NULL,
    tipo          VARCHAR(20)  NOT NULL,
    referencia_id INT          NOT NULL,
    CONSTRAINT ck_usuarios_tipo CHECK (tipo IN ('ALUNO', 'PROFESSOR', 'COORDENADOR')),
    CONSTRAINT uk_usuarios_referencia UNIQUE (tipo, referencia_id)
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
