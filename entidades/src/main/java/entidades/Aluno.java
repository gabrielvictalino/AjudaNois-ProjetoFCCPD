package entidades;
import java.util.ArrayList;
import java.util.List;

public class Aluno {
    public int id;
    public String nome;
    public List<Sala> salas;
    public String login;
    public String senha;

    public Aluno(int id, String nome, String login,String senha){
        this.id = id;
        this.nome = nome;
        this.login = login;
        this.senha = senha;
        this.salas = new ArrayList<>();
    }
    public Aluno(Aluno aluno){
        this.id = aluno.id;
        this.nome = aluno.nome;
        this.login = aluno.login;
        this.senha = aluno.senha;
        this.salas = aluno.salas;
    }
}
