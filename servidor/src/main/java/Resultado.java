public class Resultado {

    private String aluno;
    private int posicao;
    private boolean correta;
    private double pontos;

    public Resultado(
            String aluno,
            int posicao,
            boolean correta,
            double pontos) {

        this.aluno = aluno;
        this.posicao = posicao;
        this.correta = correta;
        this.pontos = pontos;
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