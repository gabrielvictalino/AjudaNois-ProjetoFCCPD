public class Atividade {

    private String aluno;
    private int posicao;
    private String Q1;
    private String Q2;
    private String Q3;

    public Atividade() {
    }

    public Atividade(String aluno, int posicao, String Q1, String Q2, String Q3) {
        this.aluno = aluno;
        this.posicao = posicao;
        this.Q1 = Q1;
        this.Q2 = Q2;
        this.Q3 = Q3;
    }

    public String getAluno() {
        return aluno;
    }

    public int getPosicao() {
        return posicao;
    }

    public String getQ1() {
        return Q1;
    }

    public String getQ2() {
        return Q2;
    }

    public String getQ3() {
        return Q3;
    }
    public void setAluno(String aluno) {
    this.aluno = aluno;
}

    public void setPosicao(int posicao) {
        this.posicao = posicao;
    }

    public void setQ1(String Q1) {
        this.Q1 = Q1;
    }

    public void setQ2(String Q2) {
        this.Q2 = Q2;
    }

    public void setQ3(String Q3) {
        this.Q3 = Q3;
    }
}