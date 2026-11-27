package inscricao.database;

import entidades.Aluno;
import entidades.Professor;
import entidades.Sala;
import inscricao.exception.RegraDeNegocioException;
import inscricao.exception.RegraDeNegocioException.TipoErro;
import inscricao.model.EventoOutbox;
import inscricao.model.Inscricao;
import inscricao.model.ProcessoSeletivoResumo;
import inscricao.model.StatusProcessoSeletivo;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLIntegrityConstraintViolationException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

/**
 * Repositório JDBC do Microsserviço 4.
 *
 * <p>Combina dois modelos de consistência:
 * <ul>
 *   <li><b>Consistência eventual</b> nas projeções locais ({@code alunos}, {@code salas},
 *       {@code processos_seletivos}), atualizadas de forma idempotente via eventos RabbitMQ.</li>
 *   <li><b>Consistência forte</b> nas inscrições ({@code inscricoes} + {@code eventos_outbox}),
 *       usando transação ACID, lock pessimista ({@code SELECT ... FOR UPDATE}) no processo seletivo
 *       e restrição {@code UNIQUE (aluno_id, processo_seletivo_id)}.</li>
 * </ul>
 */
public class InscricaoRepository {

    private final Conexao conexao;

    public InscricaoRepository(Conexao conexao) {
        this.conexao = conexao;
    }

    // -------------------------------------------------------------------------
    // Projeções locais (idempotentes — seguras para reentrega de mensagens)
    // -------------------------------------------------------------------------

    public void upsertAluno(Aluno aluno) throws SQLException {
        String updateSql = "UPDATE alunos SET nome = ?, login = ?, atualizado_em = CURRENT_TIMESTAMP WHERE id = ?";
        String insertSql = "INSERT INTO alunos (id, nome, login) VALUES (?, ?, ?)";

        try (Connection conn = conexao.conectar()) {
            try (PreparedStatement upd = conn.prepareStatement(updateSql)) {
                upd.setString(1, aluno.nome);
                upd.setString(2, aluno.login);
                upd.setInt(3, aluno.id);
                if (upd.executeUpdate() > 0) {
                    return;
                }
            }
            try (PreparedStatement ins = conn.prepareStatement(insertSql)) {
                ins.setInt(1, aluno.id);
                ins.setString(2, aluno.nome);
                ins.setString(3, aluno.login);
                ins.executeUpdate();
            } catch (SQLIntegrityConstraintViolationException race) {
                try (PreparedStatement upd = conn.prepareStatement(updateSql)) {
                    upd.setString(1, aluno.nome);
                    upd.setString(2, aluno.login);
                    upd.setInt(3, aluno.id);
                    upd.executeUpdate();
                }
            }
        }
    }

    public void upsertSala(Sala sala) throws SQLException {
        int professorId = (sala.professor != null) ? sala.professor.id : 0;
        String professorNome = (sala.professor != null && sala.professor.nome != null)
                ? sala.professor.nome
                : "Professor não informado";

        String updateSql = "UPDATE salas SET nome = ?, professor_id = ?, professor_nome = ?, "
                + "atualizado_em = CURRENT_TIMESTAMP WHERE id = ?";
        String insertSql = "INSERT INTO salas (id, nome, professor_id, professor_nome) VALUES (?, ?, ?, ?)";

        try (Connection conn = conexao.conectar()) {
            try (PreparedStatement upd = conn.prepareStatement(updateSql)) {
                upd.setString(1, sala.nome);
                upd.setInt(2, professorId);
                upd.setString(3, professorNome);
                upd.setInt(4, sala.id);
                if (upd.executeUpdate() > 0) {
                    return;
                }
            }
            try (PreparedStatement ins = conn.prepareStatement(insertSql)) {
                ins.setInt(1, sala.id);
                ins.setString(2, sala.nome);
                ins.setInt(3, professorId);
                ins.setString(4, professorNome);
                ins.executeUpdate();
            } catch (SQLIntegrityConstraintViolationException race) {
                try (PreparedStatement upd = conn.prepareStatement(updateSql)) {
                    upd.setString(1, sala.nome);
                    upd.setInt(2, professorId);
                    upd.setString(3, professorNome);
                    upd.setInt(4, sala.id);
                    upd.executeUpdate();
                }
            }
        }
    }

    public void upsertProcessoSeletivo(
            int id,
            int salaId,
            int professorId,
            String titulo,
            StatusProcessoSeletivo status,
            int maxVagas
    ) throws SQLException {
        String updateSql = "UPDATE processos_seletivos SET sala_id = ?, professor_id = ?, titulo = ?, "
                + "status = ?, max_vagas = ? WHERE id = ?";
        String insertSql = "INSERT INTO processos_seletivos (id, sala_id, professor_id, titulo, status, max_vagas) "
                + "VALUES (?, ?, ?, ?, ?, ?)";

        try (Connection conn = conexao.conectar()) {
            if (!existeSala(conn, salaId)) {
                throw new RegraDeNegocioException(
                        TipoErro.SALA_NAO_ENCONTRADA,
                        "Sala com ID " + salaId + " não encontrada para vincular ao processo seletivo " + id + "."
                );
            }

            try (PreparedStatement upd = conn.prepareStatement(updateSql)) {
                upd.setInt(1, salaId);
                upd.setInt(2, professorId);
                upd.setString(3, titulo);
                upd.setString(4, status.name());
                upd.setInt(5, Math.max(0, maxVagas));
                upd.setInt(6, id);
                if (upd.executeUpdate() > 0) {
                    return;
                }
            }
            try (PreparedStatement ins = conn.prepareStatement(insertSql)) {
                ins.setInt(1, id);
                ins.setInt(2, salaId);
                ins.setInt(3, professorId);
                ins.setString(4, titulo);
                ins.setString(5, status.name());
                ins.setInt(6, Math.max(0, maxVagas));
                ins.executeUpdate();
            } catch (SQLIntegrityConstraintViolationException race) {
                try (PreparedStatement upd = conn.prepareStatement(updateSql)) {
                    upd.setInt(1, salaId);
                    upd.setInt(2, professorId);
                    upd.setString(3, titulo);
                    upd.setString(4, status.name());
                    upd.setInt(5, Math.max(0, maxVagas));
                    upd.setInt(6, id);
                    upd.executeUpdate();
                }
            }
        }
    }

    // -------------------------------------------------------------------------
    // Consultas
    // -------------------------------------------------------------------------

    public List<Aluno> listarAlunos() throws SQLException {
        String sql = "SELECT id, nome, login FROM alunos ORDER BY id";
        List<Aluno> resultado = new ArrayList<>();
        try (Connection conn = conexao.conectar();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                resultado.add(new Aluno(
                        rs.getInt("id"),
                        rs.getString("nome"),
                        rs.getString("login"),
                        ""
                ));
            }
        }
        return resultado;
    }

    public Optional<Aluno> buscarAlunoPorId(int alunoId) throws SQLException {
        String sql = "SELECT id, nome, login FROM alunos WHERE id = ?";
        try (Connection conn = conexao.conectar();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, alunoId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(new Aluno(
                            rs.getInt("id"),
                            rs.getString("nome"),
                            rs.getString("login"),
                            ""
                    ));
                }
            }
        }
        return Optional.empty();
    }

    public List<Sala> listarSalas() throws SQLException {
        String sql = "SELECT id, nome, professor_id, professor_nome FROM salas ORDER BY id";
        List<Sala> resultado = new ArrayList<>();
        try (Connection conn = conexao.conectar();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                Professor prof = new Professor(
                        rs.getInt("professor_id"),
                        rs.getString("professor_nome"),
                        "",
                        ""
                );
                resultado.add(new Sala(rs.getInt("id"), rs.getString("nome"), prof));
            }
        }
        return resultado;
    }

    public List<ProcessoSeletivoResumo> listarProcessosSeletivos(boolean apenasAbertos) throws SQLException {
        String sql = """
                SELECT ps.id,
                       ps.sala_id,
                       s.nome AS sala_nome,
                       ps.professor_id,
                       s.professor_nome,
                       ps.titulo,
                       ps.status,
                       ps.max_vagas,
                       COUNT(i.id) AS total_inscritos
                  FROM processos_seletivos ps
                  JOIN salas s ON s.id = ps.sala_id
                  LEFT JOIN inscricoes i ON i.processo_seletivo_id = ps.id AND i.status = 'INSCRITO'
                """
                + (apenasAbertos ? " WHERE ps.status = 'ABERTO' " : " ")
                + """
                 GROUP BY ps.id, ps.sala_id, s.nome, ps.professor_id, s.professor_nome, ps.titulo, ps.status, ps.max_vagas
                 ORDER BY ps.id
                """;

        List<ProcessoSeletivoResumo> lista = new ArrayList<>();
        try (Connection conn = conexao.conectar();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                lista.add(mapearProcessoResumo(rs));
            }
        }
        return lista;
    }

    public Optional<ProcessoSeletivoResumo> buscarProcessoSeletivoPorId(int processoId) throws SQLException {
        String sql = """
                SELECT ps.id,
                       ps.sala_id,
                       s.nome AS sala_nome,
                       ps.professor_id,
                       s.professor_nome,
                       ps.titulo,
                       ps.status,
                       ps.max_vagas,
                       COUNT(i.id) AS total_inscritos
                  FROM processos_seletivos ps
                  JOIN salas s ON s.id = ps.sala_id
                  LEFT JOIN inscricoes i ON i.processo_seletivo_id = ps.id AND i.status = 'INSCRITO'
                 WHERE ps.id = ?
                 GROUP BY ps.id, ps.sala_id, s.nome, ps.professor_id, s.professor_nome, ps.titulo, ps.status, ps.max_vagas
                """;
        try (Connection conn = conexao.conectar();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, processoId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapearProcessoResumo(rs));
                }
            }
        }
        return Optional.empty();
    }

    public List<Inscricao> listarInscricoesPorAluno(int alunoId) throws SQLException {
        String sql = """
                SELECT i.id,
                       i.aluno_id,
                       a.nome AS aluno_nome,
                       i.processo_seletivo_id,
                       ps.titulo AS titulo_processo,
                       i.sala_id,
                       s.nome AS sala_nome,
                       i.status,
                       i.data_inscricao
                  FROM inscricoes i
                  JOIN alunos a ON a.id = i.aluno_id
                  JOIN processos_seletivos ps ON ps.id = i.processo_seletivo_id
                  JOIN salas s ON s.id = i.sala_id
                 WHERE i.aluno_id = ?
                 ORDER BY i.data_inscricao DESC, i.id DESC
                """;
        return listarInscricoesComParametro(sql, alunoId);
    }

    public List<Inscricao> listarInscricoesPorProcesso(int processoSeletivoId) throws SQLException {
        String sql = """
                SELECT i.id,
                       i.aluno_id,
                       a.nome AS aluno_nome,
                       i.processo_seletivo_id,
                       ps.titulo AS titulo_processo,
                       i.sala_id,
                       s.nome AS sala_nome,
                       i.status,
                       i.data_inscricao
                  FROM inscricoes i
                  JOIN alunos a ON a.id = i.aluno_id
                  JOIN processos_seletivos ps ON ps.id = i.processo_seletivo_id
                  JOIN salas s ON s.id = i.sala_id
                 WHERE i.processo_seletivo_id = ?
                 ORDER BY i.data_inscricao ASC, i.id ASC
                """;
        return listarInscricoesComParametro(sql, processoSeletivoId);
    }

    // -------------------------------------------------------------------------
    // Escrita transacional com consistência forte + Transactional Outbox
    // -------------------------------------------------------------------------

    /**
     * Inscreve um aluno em um processo seletivo dentro de uma única transação ACID:
     * <ol>
     *   <li>Valida a existência do aluno.</li>
     *   <li>Obtém lock exclusivo ({@code FOR UPDATE}) na linha do processo seletivo.</li>
     *   <li>Verifica status {@code ABERTO}, duplicidade e limite de vagas (quando configurado).</li>
     *   <li>Insere a inscrição em {@code inscricoes}.</li>
     *   <li>Registra o evento correspondente em {@code eventos_outbox} na mesma transação.</li>
     * </ol>
     */
    public Inscricao inscreverAlunoAtomico(
            int alunoId,
            int processoSeletivoId,
            String exchangeName,
            String routingKey,
            Function<Inscricao, String> payloadBuilder
    ) throws SQLException {
        try (Connection conn = conexao.conectar()) {
            conn.setAutoCommit(false);
            conn.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);

            try {
                // 1. Validar aluno
                String alunoNome = buscarNomeAluno(conn, alunoId);
                if (alunoNome == null) {
                    throw new RegraDeNegocioException(
                            TipoErro.ALUNO_NAO_ENCONTRADO,
                            "Aluno com ID " + alunoId + " não encontrado no Microsserviço 4."
                    );
                }

                // 2. Lock pessimista na linha do processo seletivo para serializar concorrência
                ProcessoLocked ps = bloquearProcessoSeletivo(conn, processoSeletivoId);
                if (ps == null) {
                    throw new RegraDeNegocioException(
                            TipoErro.PROCESSO_NAO_ENCONTRADO,
                            "Processo seletivo com ID " + processoSeletivoId + " não encontrado."
                    );
                }

                if (ps.status != StatusProcessoSeletivo.ABERTO) {
                    throw new RegraDeNegocioException(
                            TipoErro.PROCESSO_ENCERRADO,
                            "Processo seletivo " + processoSeletivoId + " está " + ps.status + " e não aceita inscrições."
                    );
                }

                // 3. Verificar duplicidade (reforçada pela constraint UNIQUE no banco)
                if (existeInscricao(conn, alunoId, processoSeletivoId)) {
                    throw new RegraDeNegocioException(
                            TipoErro.INSCRICAO_DUPLICADA,
                            "Aluno " + alunoId + " já está inscrito no processo seletivo " + processoSeletivoId + "."
                    );
                }

                // 4. Verificar limite de vagas (quando max_vagas > 0)
                if (ps.maxVagas > 0) {
                    int inscritosAtuais = contarInscritosAtivos(conn, processoSeletivoId);
                    if (inscritosAtuais >= ps.maxVagas) {
                        throw new RegraDeNegocioException(
                                TipoErro.VAGAS_ESGOTADAS,
                                "Processo seletivo " + processoSeletivoId + " atingiu o limite de " + ps.maxVagas + " vaga(s)."
                        );
                    }
                }

                // 5. Inserir inscrição
                Instant agora = Instant.now();
                long inscricaoId = inserirInscricao(conn, alunoId, processoSeletivoId, ps.salaId, agora);

                Inscricao inscricao = new Inscricao(
                        inscricaoId,
                        alunoId,
                        alunoNome,
                        processoSeletivoId,
                        ps.titulo,
                        ps.salaId,
                        ps.salaNome,
                        "INSCRITO",
                        agora
                );

                // 6. Registrar evento no outbox na mesma transação
                String payloadJson = payloadBuilder.apply(inscricao);
                inserirOutbox(conn, exchangeName, routingKey, payloadJson);

                conn.commit();
                return inscricao;
            } catch (SQLIntegrityConstraintViolationException dup) {
                conn.rollback();
                throw new RegraDeNegocioException(
                        TipoErro.INSCRICAO_DUPLICADA,
                        "Conflito detectado: aluno " + alunoId + " já está inscrito no processo seletivo " + processoSeletivoId + "."
                );
            } catch (RuntimeException | SQLException e) {
                conn.rollback();
                throw e;
            }
        }
    }

    /**
     * Cancela a inscrição de um aluno em um processo seletivo de forma atômica,
     * registrando o evento {@code inscricao.cancelada} no outbox.
     */
    public Inscricao cancelarInscricaoAtomico(
            int alunoId,
            int processoSeletivoId,
            String exchangeName,
            String routingKey,
            Function<Inscricao, String> payloadBuilder
    ) throws SQLException {
        try (Connection conn = conexao.conectar()) {
            conn.setAutoCommit(false);
            conn.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);

            try {
                bloquearProcessoSeletivo(conn, processoSeletivoId);

                Inscricao existente = buscarInscricaoUnica(conn, alunoId, processoSeletivoId);
                if (existente == null) {
                    throw new RegraDeNegocioException(
                            TipoErro.INSCRICAO_NAO_ENCONTRADA,
                            "Nenhuma inscrição encontrada para o aluno " + alunoId
                                    + " no processo seletivo " + processoSeletivoId + "."
                    );
                }

                try (PreparedStatement del = conn.prepareStatement(
                        "DELETE FROM inscricoes WHERE id = ?")) {
                    del.setLong(1, existente.id());
                    del.executeUpdate();
                }

                Inscricao cancelada = new Inscricao(
                        existente.id(),
                        existente.alunoId(),
                        existente.alunoNome(),
                        existente.processoSeletivoId(),
                        existente.tituloProcesso(),
                        existente.salaId(),
                        existente.salaNome(),
                        "CANCELADA",
                        existente.dataInscricao()
                );

                String payloadJson = payloadBuilder.apply(cancelada);
                inserirOutbox(conn, exchangeName, routingKey, payloadJson);

                conn.commit();
                return cancelada;
            } catch (RuntimeException | SQLException e) {
                conn.rollback();
                throw e;
            }
        }
    }

    // -------------------------------------------------------------------------
    // Transactional Outbox
    // -------------------------------------------------------------------------

    public List<EventoOutbox> buscarOutboxPendentes(int limite) throws SQLException {
        String sql = """
                SELECT id, exchange_name, routing_key, payload, status, tentativas, ultimo_erro, criado_em, publicado_em
                  FROM eventos_outbox
                 WHERE status = 'PENDENTE'
                 ORDER BY id ASC
                 LIMIT ?
                """;
        List<EventoOutbox> pendentes = new ArrayList<>();
        try (Connection conn = conexao.conectar();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, limite);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    pendentes.add(mapearOutbox(rs));
                }
            }
        }
        return pendentes;
    }

    public List<EventoOutbox> listarOutboxRecentes(int limite) throws SQLException {
        String sql = """
                SELECT id, exchange_name, routing_key, payload, status, tentativas, ultimo_erro, criado_em, publicado_em
                  FROM eventos_outbox
                 ORDER BY id DESC
                 LIMIT ?
                """;
        List<EventoOutbox> lista = new ArrayList<>();
        try (Connection conn = conexao.conectar();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, limite);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    lista.add(mapearOutbox(rs));
                }
            }
        }
        return lista;
    }

    public int contarOutboxPendentes() throws SQLException {
        String sql = "SELECT COUNT(*) FROM eventos_outbox WHERE status = 'PENDENTE'";
        try (Connection conn = conexao.conectar();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            return rs.next() ? rs.getInt(1) : 0;
        }
    }

    public void marcarOutboxPublicado(long outboxId) throws SQLException {
        String sql = """
                UPDATE eventos_outbox
                   SET status = 'PUBLICADO',
                       tentativas = tentativas + 1,
                       ultimo_erro = NULL,
                       publicado_em = CURRENT_TIMESTAMP
                 WHERE id = ?
                """;
        try (Connection conn = conexao.conectar();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setLong(1, outboxId);
            stmt.executeUpdate();
        }
    }

    public void registrarFalhaOutbox(long outboxId, String erro) throws SQLException {
        String erroTruncado = (erro != null && erro.length() > 480) ? erro.substring(0, 480) : erro;
        String sql = """
                UPDATE eventos_outbox
                   SET tentativas = tentativas + 1,
                       ultimo_erro = ?
                 WHERE id = ?
                """;
        try (Connection conn = conexao.conectar();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, erroTruncado);
            stmt.setLong(2, outboxId);
            stmt.executeUpdate();
        }
    }

    // -------------------------------------------------------------------------
    // Helpers internos
    // -------------------------------------------------------------------------

    private boolean existeSala(Connection conn, int salaId) throws SQLException {
        try (PreparedStatement stmt = conn.prepareStatement("SELECT 1 FROM salas WHERE id = ?")) {
            stmt.setInt(1, salaId);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next();
            }
        }
    }

    private String buscarNomeAluno(Connection conn, int alunoId) throws SQLException {
        try (PreparedStatement stmt = conn.prepareStatement("SELECT nome FROM alunos WHERE id = ?")) {
            stmt.setInt(1, alunoId);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next() ? rs.getString("nome") : null;
            }
        }
    }

    private ProcessoLocked bloquearProcessoSeletivo(Connection conn, int processoId) throws SQLException {
        String sql = """
                SELECT ps.id, ps.sala_id, ps.titulo, ps.status, ps.max_vagas, s.nome AS sala_nome
                  FROM processos_seletivos ps
                  JOIN salas s ON s.id = ps.sala_id
                 WHERE ps.id = ?
                   FOR UPDATE
                """;
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, processoId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }
                return new ProcessoLocked(
                        rs.getInt("id"),
                        rs.getInt("sala_id"),
                        rs.getString("sala_nome"),
                        rs.getString("titulo"),
                        StatusProcessoSeletivo.fromString(rs.getString("status")),
                        rs.getInt("max_vagas")
                );
            }
        }
    }

    private boolean existeInscricao(Connection conn, int alunoId, int processoId) throws SQLException {
        String sql = "SELECT 1 FROM inscricoes WHERE aluno_id = ? AND processo_seletivo_id = ?";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, alunoId);
            stmt.setInt(2, processoId);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next();
            }
        }
    }

    private int contarInscritosAtivos(Connection conn, int processoId) throws SQLException {
        String sql = "SELECT COUNT(*) FROM inscricoes WHERE processo_seletivo_id = ? AND status = 'INSCRITO'";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, processoId);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
    }

    private long inserirInscricao(
            Connection conn,
            int alunoId,
            int processoId,
            int salaId,
            Instant dataInscricao
    ) throws SQLException {
        String sql = """
                INSERT INTO inscricoes (aluno_id, processo_seletivo_id, sala_id, status, data_inscricao)
                VALUES (?, ?, ?, 'INSCRITO', ?)
                """;
        try (PreparedStatement stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            stmt.setInt(1, alunoId);
            stmt.setInt(2, processoId);
            stmt.setInt(3, salaId);
            stmt.setTimestamp(4, Timestamp.from(dataInscricao));
            stmt.executeUpdate();
            try (ResultSet keys = stmt.getGeneratedKeys()) {
                if (keys.next()) {
                    return keys.getLong(1);
                }
            }
        }
        throw new SQLException("Não foi possível obter o ID gerado para a inscrição.");
    }

    private void inserirOutbox(
            Connection conn,
            String exchangeName,
            String routingKey,
            String payloadJson
    ) throws SQLException {
        String sql = """
                INSERT INTO eventos_outbox (exchange_name, routing_key, payload, status, tentativas)
                VALUES (?, ?, ?, 'PENDENTE', 0)
                """;
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, exchangeName);
            stmt.setString(2, routingKey);
            stmt.setString(3, payloadJson);
            stmt.executeUpdate();
        }
    }

    private Inscricao buscarInscricaoUnica(Connection conn, int alunoId, int processoId) throws SQLException {
        String sql = """
                SELECT i.id,
                       i.aluno_id,
                       a.nome AS aluno_nome,
                       i.processo_seletivo_id,
                       ps.titulo AS titulo_processo,
                       i.sala_id,
                       s.nome AS sala_nome,
                       i.status,
                       i.data_inscricao
                  FROM inscricoes i
                  JOIN alunos a ON a.id = i.aluno_id
                  JOIN processos_seletivos ps ON ps.id = i.processo_seletivo_id
                  JOIN salas s ON s.id = i.sala_id
                 WHERE i.aluno_id = ? AND i.processo_seletivo_id = ?
                """;
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, alunoId);
            stmt.setInt(2, processoId);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next() ? mapearInscricao(rs) : null;
            }
        }
    }

    private List<Inscricao> listarInscricoesComParametro(String sql, int parametro) throws SQLException {
        List<Inscricao> lista = new ArrayList<>();
        try (Connection conn = conexao.conectar();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, parametro);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    lista.add(mapearInscricao(rs));
                }
            }
        }
        return lista;
    }

    private ProcessoSeletivoResumo mapearProcessoResumo(ResultSet rs) throws SQLException {
        return new ProcessoSeletivoResumo(
                rs.getInt("id"),
                rs.getInt("sala_id"),
                rs.getString("sala_nome"),
                rs.getInt("professor_id"),
                rs.getString("professor_nome"),
                rs.getString("titulo"),
                StatusProcessoSeletivo.fromString(rs.getString("status")),
                rs.getInt("max_vagas"),
                rs.getInt("total_inscritos")
        );
    }

    private Inscricao mapearInscricao(ResultSet rs) throws SQLException {
        Timestamp ts = rs.getTimestamp("data_inscricao");
        return new Inscricao(
                rs.getLong("id"),
                rs.getInt("aluno_id"),
                rs.getString("aluno_nome"),
                rs.getInt("processo_seletivo_id"),
                rs.getString("titulo_processo"),
                rs.getInt("sala_id"),
                rs.getString("sala_nome"),
                rs.getString("status"),
                ts != null ? ts.toInstant() : Instant.EPOCH
        );
    }

    private EventoOutbox mapearOutbox(ResultSet rs) throws SQLException {
        Timestamp criado = rs.getTimestamp("criado_em");
        Timestamp publicado = rs.getTimestamp("publicado_em");
        return new EventoOutbox(
                rs.getLong("id"),
                rs.getString("exchange_name"),
                rs.getString("routing_key"),
                rs.getString("payload"),
                rs.getString("status"),
                rs.getInt("tentativas"),
                rs.getString("ultimo_erro"),
                criado != null ? criado.toInstant() : Instant.EPOCH,
                publicado != null ? publicado.toInstant() : null
        );
    }

    private record ProcessoLocked(
            int id,
            int salaId,
            String salaNome,
            String titulo,
            StatusProcessoSeletivo status,
            int maxVagas
    ) {
    }
}
