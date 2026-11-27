import java.util.Scanner;
import entidades.*;
import database.*;
import java.util.List;

public class Main {

    public static void main(String[] args) {

        Scanner scanner = new Scanner(System.in);

        while (true) {

            System.out.println("\n================================");
            System.out.println("    GERENCIAMENTO DE ACESSOS");
            System.out.println("================================");
            System.out.println("1 - Criar acesso Professor");
            System.out.println("2 - Criar acesso Aluno");
            System.out.println("0 - Sair");
            System.out.println("================================");
            System.out.print("Escolha uma opção: ");

            int escolha = scanner.nextInt();
            scanner.nextLine();

            if (escolha == 0) {
                System.out.println("Encerrando sistema...");
                break;
            }

            if (escolha != 1 && escolha != 2) {
                System.out.println("Opção inválida!");
                continue;
            }

            System.out.print("Digite o ID: ");
            int id = scanner.nextInt();
            scanner.nextLine();

            System.out.print("Digite o nome: ");
            String nome = scanner.nextLine();

            System.out.print("Digite o login: ");
            String login = scanner.nextLine();

            System.out.print("Digite a senha: ");
            String senha = scanner.nextLine();

            switch (escolha) {

                case 1:
                    List<Integer> idProfessores = retornarProfessores();
                    if(idProfessores != null && idProfessores.contains(id)){
                        continue;
                    }
                    Professor professor = criarAcessoProfessor(
                        id, nome, login, senha
                    );
                    //Fazer registrar Professor
                    //Propagar no rabbitMQ a atualização
                    System.out.println("Professor criado com sucesso!");
                    break;

                case 2:
                    List<Integer> idAlunos = retornarAlunos();
                    if(idAlunos != null && idAlunos.contains(id)){
                        continue;
                    }
                    Aluno aluno = criarAcessoAluno(
                        id, nome, login, senha
                    );
                    //Fazer registrar Professor
                    //Propagar no rabbitMQ a atualização
                    System.out.println("Aluno criado com sucesso!");
                    break;
            }
        }

        scanner.close();
    }

    public static Professor criarAcessoProfessor(
        int id, String nome, String login, String senha
    ) {
        return new Professor(id, nome, login, senha);
    }

    public static Aluno criarAcessoAluno(
        int id, String nome, String login, String senha
    ) {
        return new Aluno(id, nome, login, senha);
    }
}