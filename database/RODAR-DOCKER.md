# Como rodar os bancos no Docker

Rode tudo da **raiz do projeto**, com o Docker Desktop aberto.

## Subir

```powershell
docker compose up -d rabbitmq db-coordenador db-salas db-processo-seletivo db-inscricao-ps db-atividades db-respostas db-login db-matricula
```

Conferir se estão `healthy`:

```powershell
docker compose ps
```

## Mudou algum `.sql`? Apague tudo e comece do zero

Os arquivos `.sql` só rodam na **primeira vez** que o banco sobe. Então, **ao alterar qualquer parte do modelo do database, apague tudo e suba de novo**:

```powershell
docker compose down -v
```

Depois, rode o comando de **Subir** de novo. Se der `500 Internal Server Error` logo em seguida, espere alguns segundos e rode o **Subir** outra vez.

O `down -v` apaga os containers e **todos os dados** dos bancos e do RabbitMQ. Use só em desenvolvimento.

## Testar

```powershell
.\database\testar-bancos.ps1
```

Tem que terminar com `Todos os bancos passaram.`
