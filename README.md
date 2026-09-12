# SGC_Sistema_de_Gerenciamento_de_Construcao

#Mapeamento dos Requisitos

Geração de script de construção: Gera target/build_reproduce.sh contendo comandos diretos de compilação e empacotamento para reproduzir a build independentemente da JVM.

Integração com o SCV: Valida e ancora o processo na linha de base/tag (vcsTagOrCommit), garantindo rastreabilidade entre versão de código e binário.

Re-compilação mínima: Calcula hashes SHA-256 de cada arquivo em src/, persistindo em .build_state. Somente fontes novas ou alteradas são repassadas ao compilador.

Criação do executável: Monta um arquivo .jar com o MANIFEST.MF configurado apontando para a classe de entrada principal (Main-Class).

Automação de testes: Integrada na etapa runTests() antes do empacotamento, impedindo a geração do executável em caso de falha.

Geração de documentação: Invoca a API padrão do Javadoc (DocumentationTool) para gerar os manuais HTML em target/docs.

Relatórios: Escreve um sumário consolidado com status de cada fase em target/reports/build-summary.txt.


