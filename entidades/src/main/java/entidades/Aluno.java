package entidades;
import java.util.ArrayList;
import java.util.List;

public class Aluno {
    public int id;
    public String nome;
    public List<Sala> salas;

    public Aluno(int id, String nome){
        this.id = id;
        this.nome = nome;
        this.salas = new ArrayList<>();
    }
    public Aluno(Aluno aluno){
        this.id = aluno.id;
        this.nome = aluno.nome;
        this.salas = aluno.salas;
    }
}
