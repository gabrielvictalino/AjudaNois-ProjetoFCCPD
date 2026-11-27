package entidades;
import java.util.ArrayList;
import java.util.List;

public class ProcessoSeletivo {
    public int id;
    public Professor professorQueCriouProcesso;
    public List<Aluno> alunosInscritos;
    public List<Monitor> monitoresAceitos;
    public Sala sala;

    public ProcessoSeletivo(int id, Professor professorQueCriouProcesso,Sala sala){
        this.id = id;
        this.professorQueCriouProcesso = professorQueCriouProcesso;
        this.alunosInscritos = new ArrayList<>();
        this.monitoresAceitos = new ArrayList<>();
        this.sala = sala;
    }

}
