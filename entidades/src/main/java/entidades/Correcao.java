package entidades;

public class Correcao {

    public String aluno;
    public int posicao;
    public boolean correta;
    public double pontos;
    public Atividade atividade;

    public Correcao(
            String aluno,
            int posicao,
            boolean correta,
            double pontos,
            Atividade atividade) {

        this.aluno = aluno;
        this.posicao = posicao;
        this.correta = correta;
        this.pontos = pontos;
        this.atividade = atividade;
    }

    public String getAluno() {
        return aluno;
    }

    public int getPosicao() {
        return posicao;
    }

    public boolean isCorreta() {
        return correta;
    }

    public double getPontos() {
        return pontos;
    }
}