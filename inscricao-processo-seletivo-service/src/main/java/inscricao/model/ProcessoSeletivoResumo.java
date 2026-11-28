package inscricao.model;

import entidades.Professor;
import entidades.Sala;

/**
 * Visão consolidada de um processo seletivo com os dados da sala associada
 * e contagem atual de inscritos.
 */
public record ProcessoSeletivoResumo(
        int id,
        int salaId,
        String salaNome,
        int professorId,
        String professorNome,
        String titulo,
        StatusProcessoSeletivo status,
        int maxVagas,
        int totalInscritos
) {

    public boolean possuiLimiteDeVagas() {
        return maxVagas > 0;
    }

    public boolean estaLotado() {
        return possuiLimiteDeVagas() && totalInscritos >= maxVagas;
    }

    public entidades.ProcessoSeletivo toEntidade() {
        Professor professor = new Professor(professorId, professorNome, "", "");
        Sala sala = new Sala(salaId, salaNome, professor);
        return new entidades.ProcessoSeletivo(id, professor, sala);
    }
}
