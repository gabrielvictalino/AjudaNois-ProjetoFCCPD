package entidades;
import java.util.List;
import java.util.ArrayList;

public class Professor {
    public int id;
    public String nome;
    public List<Sala> salas;

    public Professor (int id, String nome){
        this.id = id;
        this.nome = nome;
        this.salas = new ArrayList<>();
    }
}
