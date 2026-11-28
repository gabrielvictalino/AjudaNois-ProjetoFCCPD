# =====================================================================
# Testa os bancos de dados dos microsservicos.
#
#   .\database\testar-bancos.ps1           sobe os bancos do compose e confere
#   .\database\testar-bancos.ps1 -Limpar   no fim, para e apaga os bancos (volumes)
#
# Para cada banco confere: o container fica saudavel, os scripts .sql
# rodaram (todas as tabelas esperadas existem) e o usuario do servico so
# enxerga o proprio banco. Tambem roda o _modelo num MySQL temporario,
# porque ele nao esta no docker-compose.yml.
# Arquivo sem acentos de proposito (Windows PowerShell 5.1).
# =====================================================================
param([switch]$Limpar)

# 'Continue': o docker escreve progresso no stderr e o PowerShell 5.1 trataria
# isso como erro. O script confere cada resultado por conta propria.
$ErrorActionPreference = 'Continue'
$raiz = Split-Path -Parent $PSScriptRoot
Set-Location $raiz

# Servico do compose, container, banco, usuario e tabelas esperadas.
# Adicionou ou removeu um banco/tabela? Atualize esta lista.
$bancos = @(
    @{ Servico = 'db-coordenador'; Container = 'ajudanois-db-coordenador'; Banco = 'coordenador_db'; Usuario = 'coordenador'
       Tabelas = 'alunos coordenadores eventos_outbox professores' },
    @{ Servico = 'db-salas'; Container = 'ajudanois-db-salas'; Banco = 'salas_db'; Usuario = 'salas'
       Tabelas = 'eventos_outbox processos_seletivos professores salas' },
    @{ Servico = 'db-processo-seletivo'; Container = 'ajudanois-db-processo-seletivo'; Banco = 'processo_seletivo_db'; Usuario = 'processo_seletivo'
       Tabelas = 'alunos eventos_outbox inscricoes monitores processos_seletivos professores salas' },
    @{ Servico = 'db-inscricao-ps'; Container = 'ajudanois-db-inscricao-ps'; Banco = 'inscricao_ps_db'; Usuario = 'inscricao'
       Tabelas = 'alunos eventos_outbox inscricoes processos_seletivos salas' },
    @{ Servico = 'db-atividades'; Container = 'ajudanois-db-atividades'; Banco = 'atividades_db'; Usuario = 'atividades'
       Tabelas = 'alunos atividades correcoes eventos_outbox monitores questoes salas' },
    @{ Servico = 'db-respostas'; Container = 'ajudanois-db-respostas'; Banco = 'respostas_db'; Usuario = 'respostas'
       Tabelas = 'alunos atividades eventos_outbox itens_resposta matriculas questoes respostas resultados salas' },
    @{ Servico = 'db-login'; Container = 'ajudanois-db-login'; Banco = 'login_db'; Usuario = 'login'
       Tabelas = 'eventos_outbox usuarios' },
    @{ Servico = 'db-matricula'; Container = 'ajudanois-db-matricula'; Banco = 'matricula_db'; Usuario = 'matricula'
       Tabelas = 'alunos eventos_outbox matriculas salas' }
)

# Pastas que nao estao no compose: testadas num container temporario.
$avulsos = @(
    @{ Nome = '_modelo'; Pasta = 'database/_modelo'
       Tabelas = 'copia_exemplos eventos_outbox exemplos' }
)

$senha = if ($env:DB_SENHA) { $env:DB_SENHA } else { 'senha' }
$falhas = 0
function Write-Resultado([bool]$ok, [string]$texto) {
    if ($ok) { Write-Host "  [OK]    $texto" -ForegroundColor Green }
    else     { Write-Host "  [FALHA] $texto" -ForegroundColor Red; $script:falhas++ }
}

function Wait-Saudavel([string]$container) {
    for ($i = 0; $i -lt 90; $i++) {
        $estado = docker inspect -f '{{.State.Health.Status}}' $container 2>$null
        if ($estado -eq 'healthy') { return $true }
        Start-Sleep -Seconds 2
    }
    return $false
}

function Test-Tabelas([string]$container, [string]$usuario, [string]$senha, [string]$banco, [string]$esperado) {
    $obtidas = (docker exec $container mysql "-u$usuario" "-p$senha" -N -e 'SHOW TABLES' $banco 2>$null) -join ' '
    $a = ($esperado -split ' ' | Sort-Object) -join ' '
    $b = ($obtidas -split '\s+' | Where-Object { $_ } | Sort-Object) -join ' '
    Write-Resultado ($a -eq $b) "tabelas: $b"
    if ($a -ne $b) { Write-Host "          esperado: $a" }
}

Write-Host "`n== Subindo os bancos do docker-compose =="
docker compose up -d --quiet-pull ($bancos | ForEach-Object { $_.Servico }) 2>&1 | Out-Null
if ($LASTEXITCODE -ne 0) { Write-Host 'docker compose up falhou. Rode sem o script para ver o erro.' -ForegroundColor Red; exit 1 }

foreach ($b in $bancos) {
    Write-Host "`n== $($b.Servico) ($($b.Banco)) =="
    Write-Resultado (Wait-Saudavel $b.Container) 'container saudavel'
    Test-Tabelas $b.Container $b.Usuario $senha $b.Banco $b.Tabelas
    $visiveis = (docker exec $b.Container mysql "-u$($b.Usuario)" "-p$senha" -N -e 'SHOW DATABASES' 2>$null) -join ' '
    Write-Resultado ($visiveis -notmatch 'mysql') "usuario '$($b.Usuario)' so ve: $visiveis"
}

foreach ($t in $avulsos) {
    Write-Host "`n== $($t.Nome) (MySQL temporario) =="
    $nome = "ajudanois-teste-$($t.Nome.TrimStart('_'))"
    docker rm -fv $nome 2>$null | Out-Null
    $pasta = (Resolve-Path $t.Pasta).Path
    docker run -d --name $nome -e MYSQL_ROOT_PASSWORD=teste -e MYSQL_DATABASE=teste_db `
        -v "${pasta}:/docker-entrypoint-initdb.d:ro" `
        --health-cmd 'mysqladmin ping -h 127.0.0.1 -uroot -pteste --silent' --health-interval 5s `
        mysql:8.4 --performance-schema=OFF --innodb-buffer-pool-size=32M | Out-Null
    Write-Resultado (Wait-Saudavel $nome) 'container saudavel'
    Test-Tabelas $nome 'root' 'teste' 'teste_db' $t.Tabelas
    docker rm -fv $nome | Out-Null              # -v: apaga tambem o volume do MySQL temporario
}

if ($Limpar) {
    Write-Host "`n== Limpando: parando os bancos e apagando os volumes =="
    docker compose rm -sf ($bancos | ForEach-Object { $_.Servico }) | Out-Null
    $projeto = (Split-Path -Leaf $raiz).ToLower()
    'coordenador_data salas_data processo_seletivo_data inscricao_ps_data atividades_data respostas_data login_data matricula_data' -split ' ' |
        ForEach-Object { docker volume rm "${projeto}_$_" 2>$null | Out-Null }
}

Write-Host ''
if ($falhas -eq 0) { Write-Host 'Todos os bancos passaram.' -ForegroundColor Green; exit 0 }
else { Write-Host "$falhas verificacao(oes) falharam." -ForegroundColor Red; exit 1 }
