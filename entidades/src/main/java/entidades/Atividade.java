package entidades;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
public class Atividade {

    public int id;
    public LocalDateTime dataEnvio;
    public List<Questao> questoes;
    public Aluno aluno;
    public Correcao correcao;

    public Atividade(Aluno aluno, int id, LocalDateTime dataEnvio, Correcao correcao) {
        this.aluno = aluno;
        this.id = id;
        this.correcao = correcao;
        this.dataEnvio = dataEnvio;
        this.correcao = correcao;
    }
}

