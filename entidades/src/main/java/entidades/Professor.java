package entidades;
import java.util.List;
import java.util.ArrayList;

public class Professor {
    public int id;
    public String nome;
    public List<Sala> salas;
    public String login;
    public String senha;

    public Professor (int id, String nome,String login,String senha){
        this.id = id;
        this.nome = nome;
        this.login = login;
        this.senha = senha;
        this.salas = new ArrayList<>();
    }
}
