package entidades;
import java.util.List;

public class Monitor extends Aluno{
    public List<Sala> salasDoMonitor;

    public Monitor(Aluno aluno,Sala sala){
        super(aluno);
        salasDoMonitor.add(sala);
    }

}
