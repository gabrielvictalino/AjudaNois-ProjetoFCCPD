package inscricao.exception;

/**
 * Exceção de domínio para violações de regra de negócio ou consistência
 * durante o fluxo de inscrição em processos seletivos.
 */
public class RegraDeNegocioException extends RuntimeException {

    public enum TipoErro {
        ALUNO_NAO_ENCONTRADO,
        SALA_NAO_ENCONTRADA,
        PROCESSO_NAO_ENCONTRADO,
        PROCESSO_ENCERRADO,
        INSCRICAO_DUPLICADA,
        VAGAS_ESGOTADAS,
        INSCRICAO_NAO_ENCONTRADA,
        DADOS_INVALIDOS
    }

    private final TipoErro tipoErro;

    public RegraDeNegocioException(TipoErro tipoErro, String message) {
        super(message);
        this.tipoErro = tipoErro;
    }

    public TipoErro getTipoErro() {
        return tipoErro;
    }
}
