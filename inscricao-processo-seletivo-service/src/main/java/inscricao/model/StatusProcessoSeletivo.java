package inscricao.model;

/**
 * Estado operacional de um processo seletivo na projeção do Microsserviço 4.
 */
public enum StatusProcessoSeletivo {
    ABERTO,
    ENCERRADO;

    public static StatusProcessoSeletivo fromString(String value) {
        if (value == null || value.isBlank()) {
            return ABERTO;
        }
        return StatusProcessoSeletivo.valueOf(value.trim().toUpperCase());
    }
}
