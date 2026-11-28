package inscricao.model;

import java.time.Instant;

/**
 * Representa a inscrição confirmada de um aluno em um processo seletivo de uma sala.
 */
public record Inscricao(
        long id,
        int alunoId,
        String alunoNome,
        int processoSeletivoId,
        String tituloProcesso,
        int salaId,
        String salaNome,
        String status,
        Instant dataInscricao
) {
}
