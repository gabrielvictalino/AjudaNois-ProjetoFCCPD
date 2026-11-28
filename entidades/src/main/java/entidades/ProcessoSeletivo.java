package entidades;
import java.util.ArrayList;
import java.util.List;

public class ProcessoSeletivo {
    public int id;
    public Professor professorQueCriouProcesso;
    public List<Aluno> alunosInscritos;
    public List<Monitor> monitoresAceitos;
    public Sala sala;
    public String titulo;
    public int maxVagas;
    public String status;

    public ProcessoSeletivo(int id, Professor professorQueCriouProcesso,Sala sala){
        this.id = id;
        this.professorQueCriouProcesso = professorQueCriouProcesso;
        this.alunosInscritos = new ArrayList<>();
        this.monitoresAceitos = new ArrayList<>();
        this.sala = sala;
        this.status = "ABERTO";
    }

    public ProcessoSeletivo(int id, Professor professorQueCriouProcesso, Sala sala, String titulo, int maxVagas, String status){
        this(id, professorQueCriouProcesso, sala);
        this.titulo = titulo;
        this.maxVagas = maxVagas;
        this.status = status;
    }

}
