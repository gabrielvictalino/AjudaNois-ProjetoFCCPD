# Bancos de dados

Cada microsserviço tem o seu próprio banco MySQL, e cada banco roda num container separado. Todos sobem juntos pelo `docker-compose.yml` que fica na pasta raiz do projeto. Para subir, testar ou começar do zero, veja o [RODAR-DOCKER.md](RODAR-DOCKER.md).

O sistema foi separado em 8 funcionalidades, uma por microsserviço. As 7 primeiras vieram da divisão do grupo, e a 8ª foi adicionada para o aluno se matricular numa sala, digitando o ID dela. Cada banco fica numa pasta própria aqui dentro e **deve ser revisto pelo membro responsável por aquela funcionalidade**. Pois por enquanto, o que tem não é o oficial, mais uma base apenas, que deve ser alterado pelo membro responsável pela funcionalidade.:

| Funcionalidade | Pasta | Responsável |
| --- | --- | --- |
| 1. Coordenador gerencia professores e alunos | `ms1-coordenador` | David |
| 2. Professor cria salas | `ms2-salas` | Lívia |
| 3. Professor publica processo seletivo e aceita monitores | `ms3-processo-seletivo` | Lívia |
| 4. Aluno se inscreve em processo seletivo | `ms4-inscricao` | Gabriel |
| 5. Monitor cria e corrige atividade, com ranking | `ms5-atividades` | David |
| 6. Aluno responde a atividade | `ms6-respostas` | Matheus |
| 7. Login | `ms7-login` | Matheus |
| 8. Aluno se matricula numa sala | `ms8-matricula` | A definir |

## Como cada banco é organizado

Todo banco é dividido em três partes, sempre na mesma ordem e com esses rótulos no arquivo `01-schema.sql`.

A primeira parte são as **cópias**. São dados que pertencem a outro serviço, mas que este precisa ler. Por exemplo, o banco de atividades guarda uma cópia dos alunos, que pertencem ao coordenador. As cópias servem só para leitura: o serviço nunca altera uma cópia por conta própria. Elas só mudam quando chega um evento do serviço dono avisando que algo mudou.

A segunda parte é o **dono**. São os dados que só este serviço cria e altera. Quando ele muda um desses dados e os outros serviços precisam saber, ele publica um evento.

A terceira parte é a **mensageria**: a tabela `eventos_outbox`. Quando o serviço altera um dado, ele grava o evento nessa tabela junto com o dado. Depois, um processo lê os eventos pendentes, publica no RabbitMQ e marca como publicados. Se o RabbitMQ estiver fora do ar, o evento fica esperando na tabela e é publicado quando ele voltar, então nada se perde. Todos os bancos usam exatamente essa mesma tabela.

Resumindo o caminho de um dado: o coordenador cadastra um aluno, o banco do coordenador (dono) grava o aluno e o evento, o evento vai para o RabbitMQ, e os outros serviços recebem e atualizam as suas cópias.

## Como adicionar um microsserviço novo

Copie a pasta `_modelo` com o nome do novo serviço, por exemplo `ms9-notificacoes`. No `01-schema.sql`, troque as tabelas de exemplo pelas cópias e pelas tabelas de dono do serviço, e deixe a parte de mensageria como está. Se o serviço precisar de dados iniciais, use o `02-seed.sql`; se não, apague esse arquivo.

Depois, adicione o banco no `docker-compose.yml` da raiz, copiando o bloco de um banco que já existe e trocando o nome, a porta (3319 para o microsserviço 9) e a pasta. Adicione também o banco na lista do `testar-bancos.ps1` e uma linha na tabela acima. Por fim, apague tudo e suba de novo, como está no [RODAR-DOCKER.md](RODAR-DOCKER.md), e rode o teste.
