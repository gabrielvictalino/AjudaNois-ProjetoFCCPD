# AjudaNois-ProjetoFCCPD

Projeto destinado à disciplina de **Fundamentos de Computação Concorrente, Paralela e Distribuída (FCCPD)**.

## Estrutura do Repositório

Projeto multi-módulo em **Java 21 + Maven**, utilizando **RabbitMQ**, **MySQL** e **Docker Compose**:

- `entidades/` — Modelos de domínio compartilhados (`Aluno`, `Atividade`, `Coordenador`, `Correcao`, `Monitor`, `ProcessoSeletivo`, `Professor`, `Questao`, `Sala`).
- `cliente/` — Aplicação cliente que publica atividades no RabbitMQ.
- `servidor/` — Servidor concorrente que consome atividades da fila e processa correções em pool de threads.
- `cordenador-gerenciamento-service/` — Serviço de gerenciamento de acessos integrado ao MySQL e RabbitMQ (Microsserviço 1).
- `inscricao-processo-seletivo-service/` — Serviço de inscrição de alunos em processos seletivos com MySQL dedicado, consistência forte, Transactional Outbox e integração RabbitMQ (Microsserviço 4 — veja [docs/ms4-inscricao-processo-seletivo.md](docs/ms4-inscricao-processo-seletivo.md)).
- `docker-compose.yml` — Orquestração local dos serviços (`rabbitmq`, `db-coordenador`, `coordenador`, `db-inscricao-ps`, `inscricao-processo-seletivo`).

## Colaboração e Fluxo de Git

Antes de criar sua primeira branch ou abrir um Pull Request, leia o guia completo:

- [Fluxo de Git & Guia de Colaboração](docs/git-workflow.md)

### Resumo das regras

- Branches protegidas: `main` (entregas revisadas) e `dev` (integração contínua).
- Todo trabalho acontece em branches `<tipo>/<descricao-curta>` criadas a partir de `dev` e integradas via Pull Request com aprovação de review.
- Commits e títulos de PR seguem **Conventional Commits** (`<tipo>(<escopo>): <resumo>`).
- **Documentação viva:** se um PR alterar decisões de arquitetura, contratos de mensageria/banco ou regras de fluxo, atualize a documentação correspondente no mesmo PR.
