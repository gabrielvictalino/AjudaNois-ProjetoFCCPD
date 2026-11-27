package entidades;

public class Questao {
    public int id;
    public String enunciado;
    public String resposta;
    public double pontuacao;
    
    public Questao(int id, String enunciado, String resposta){
       this.id = id;
       this.enunciado = enunciado;
       this.resposta =resposta; 
    }
}
