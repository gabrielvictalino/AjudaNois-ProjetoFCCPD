package inscricao.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Contratos de eventos JSON trocados via RabbitMQ entre o Microsserviço 4
 * e os demais microsserviços do sistema AjudaNois.
 */
public final class Eventos {

    private Eventos() {
    }

    /**
     * Evento publicado pelo Microsserviço 1 (Coordenador) ao cadastrar/atualizar um aluno.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record AlunoSyncEvent(
            int id,
            String nome,
            String login
    ) {
    }

    /**
     * Evento publicado pelo Microsserviço 2 (Salas) ao criar/atualizar uma sala.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record SalaSyncEvent(
            int id,
            String nome,
            int professorId,
            String professorNome
    ) {
    }

    /**
     * Evento publicado pelo Microsserviço 3 (Processo Seletivo) ao publicar ou encerrar um processo seletivo.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ProcessoSeletivoSyncEvent(
            int id,
            int salaId,
            int professorId,
            String titulo,
            String status,
            int maxVagas
    ) {
    }

    /**
     * Evento publicado pelo Microsserviço 4 quando um aluno se inscreve em um processo seletivo.
     * Consumido pelo Microsserviço 3 para que o professor avalie e aceite monitores.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record AlunoInscritoEvent(
            long inscricaoId,
            int alunoId,
            String alunoNome,
            int processoSeletivoId,
            int salaId,
            String salaNome,
            String status,
            String dataInscricaoIso
    ) {
    }

    /**
     * Evento publicado pelo Microsserviço 4 quando um aluno cancela sua inscrição.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record InscricaoCanceladaEvent(
            long inscricaoId,
            int alunoId,
            int processoSeletivoId,
            int salaId,
            String canceladoEmIso
    ) {
    }
}
