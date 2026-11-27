package entidades;
import java.util.ArrayList;
import java.util.List;

public class Sala {
    public int id;
    public String nome;
    public Professor professor;
    public List<Aluno> alunos;
    public List<Monitor> listaDeMonitores;
    public List<Atividade> atividades;

    public Sala(int id, String nome, Professor professor){
        this.id = id;
        this.nome = nome;
        this.professor = professor;
        this.listaDeMonitores = new ArrayList<>();
        this.alunos = new ArrayList<>();
        this.atividades = new ArrayList<>();
    }

}
