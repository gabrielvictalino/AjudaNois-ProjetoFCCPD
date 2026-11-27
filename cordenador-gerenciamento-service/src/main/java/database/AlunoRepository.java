package database;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class AlunoRepository {
        public void listarAlunos() {

        String sql = "SELECT * FROM alunos";

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
    public List<Integer> retornarAlunos() {
        List<Integer> idAlunos = new ArrayList<>();
        String sql = "SELECT * FROM alunos";

        try (
            Connection conexao = Conexao.conectar();
            PreparedStatement stmt = conexao.prepareStatement(sql);
            ResultSet resultado = stmt.executeQuery()
        ) {

            while (resultado.next()) {

                int id = resultado.getInt("id");
                idAlunos.add(id);
            }
            return idAlunos;
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }
}
