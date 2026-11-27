package database;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

//Fazer consultas dos professores
public class ProfessorRepository {

    public void listarProfessores() {

        String sql = "SELECT * FROM professores";

        try (
            Connection conexao = Conexao.conectar();
            PreparedStatement stmt = conexao.prepareStatement(sql);
            ResultSet resultado = stmt.executeQuery()
        ) {

            while (resultado.next()) {

                int id = resultado.getInt("id");
                String nome = resultado.getString("nome");

                System.out.println(id + " - " + nome);
            }

        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
    public List<Integer> retornarProfessores() {
        List<Integer> idProfessores = new ArrayList<>();
        String sql = "SELECT * FROM professores";

        try (
            Connection conexao = Conexao.conectar();
            PreparedStatement stmt = conexao.prepareStatement(sql);
            ResultSet resultado = stmt.executeQuery()
        ) {

            while (resultado.next()) {

                int id = resultado.getInt("id");
                idProfessores.add(id);
            }
            return idProfessores;
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }
}