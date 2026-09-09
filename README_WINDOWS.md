# Monitor de Notícias — Windows Portable v4.0.3

A **v4.0.3** é a evolução exclusiva da edição Windows Portable. A versão Android permanece separada e não é modificada por esta branch.

## Interface operacional v4.0.3

A interface desktop foi redesenhada a partir dos mockups aprovados, com identidade própria inspirada em uma central naval de inteligência de mídia:

- azul-marinho, azul operacional, branco e detalhes dourados;
- menu lateral com estado da operação;
- cabeçalhos com situação de Proxy e Automação;
- atualização reativa das telas sem necessidade de trocar de aba;
- dashboard inicial com indicadores, ações rápidas, agendamentos, resumo das últimas 24 horas, fontes mais relevantes, atividades recentes e dicas;
- busca global local por Notícias, Vídeos e Demandas;
- notificações dos itens realmente novos da execução atual;
- estados vazios profissionais e mensagens de operação em tempo real.

## Notícias

- busca progressiva das últimas 24 horas;
- atalhos **Hoje**, **24 horas**, **7 dias** e **30 dias**;
- período personalizado;
- filtro **Só demandas**;
- busca textual local na janela carregada;
- ordenação por **Mais recentes**, **Mais antigos**, **Por fonte** ou **A-Z**;
- resultados atualizados durante a própria execução;
- selo **Nova** somente para matéria inserida na execução corrente;
- **Abrir notícia**, **WhatsApp** e **Copiar link**;
- histórico persistente e deduplicação preservando a primeira captura.

### Busca em camadas

A v4.0.3 reduz a dependência de um único caminho de descoberta:

1. Google Notícias RSS continua sendo a camada principal;
2. aliases editoriais e regionais normalizam nomes diferentes para o mesmo veículo;
3. consultas por fontes selecionadas reforçam a descoberta;
4. coleta direta complementar é usada em rotas Windows configuradas;
5. resultados são deduplicados por identidade histórica e por veículo canônico + título.

Exemplo de regressão protegida: **Folha PE**, **FolhaPE** e `folhape.com.br` são reconhecidos como **Folha de Pernambuco**.

### Diagnóstico de cobertura

A aba Notícias exibe a auditoria da última execução:

- quantidade de consultas Google;
- itens brutos recebidos;
- itens dentro do período;
- itens aceitos pelo filtro de fonte;
- erros do Google;
- fontes varridas diretamente;
- páginas/listagens examinadas;
- links candidatos;
- candidatos com termos/demandas;
- matérias aceitas pela cobertura direta;
- erros da cobertura direta;
- total final encontrado e total realmente novo.

Isso permite identificar se uma matéria foi perdida por período, fonte, termo, indexação ou indisponibilidade de uma rota direta.

## Cobertura direta Windows

A camada complementar possui rotas para:

- Folha de Pernambuco;
- Defesa em Foco;
- Defesa Aérea & Naval;
- DefesaNet;
- Tecnologia & Defesa / Tecnodefesa;
- Zona Militar;
- Click Petróleo e Gás;
- Poder Naval;
- GBN Defense / GBN News.

Uma falha em uma rota direta não interrompe a busca principal; ela é contabilizada no diagnóstico.

## Termos estabelecidos

A migração Windows adiciona, sem apagar termos personalizados, os seguintes termos a Notícias e Vídeos:

- CAPITANIA FLUVIAL
- FRAGATA
- SUBMARINO
- COMANDANTE DA MARINHA
- EXÉRCITO
- FAB
- MARINHA
- MINISTRO DA DEFESA
- MINISTÉRIO DA DEFESA
- MAIOR NAVIO DA AMERICA LATINA
- PROSUB

## Vídeos

A edição Windows mantém o motor de vídeo compartilhado da v4.0.2:

- YouTube oficial;
- Globoplay por Edições, Trechos e Jarvis;
- telejornais nacionais e regionais;
- cruzamento local com Termos e Demandas;
- título, descrição, tags, keywords e demais metadados;
- filtros, períodos e ordenação;
- histórico persistente;
- diagnóstico de fontes instáveis;
- selo **Novo** somente para item realmente novo na execução;
- **Abrir vídeo** e **Copiar link**.

## Demandas

- Demandas por **veículo + assunto**;
- seletor de veículo baseado no catálogo Windows;
- busca individual e de todas as Demandas;
- status, última execução, encontrados, novos e erros;
- diálogo de resultados armazenados por Demanda;
- atualização da tela em tempo real.

## Fontes

- fontes nacionais e regionais;
- catálogo de Vídeos;
- **8 mídias especializadas Windows**;
- filtros por região, UF e texto;
- selecionar/limpar somente fontes visíveis;
- selecionar todas ou nenhuma;
- aliases regionais automáticos e aliases editoriais específicos.

## Proxy autenticado

A configuração fica dentro do aplicativo. O proxy padrão desta edição pode ser configurado com servidor, porta, usuário, domínio e senha.

- autenticação HTTP/HTTPS;
- suporte ao domínio corporativo/NTLM quando aplicável;
- botão **Testar conexão**;
- senha protegida localmente pelo **Windows DPAPI** via API nativa/JNA;
- senha não é armazenada no GitHub;
- automações aguardam quando o proxy está ativado mas ainda não está pronto.

## Buscas automáticas

Há um controle geral e controles independentes:

- **Notícias:** ativar/desativar e escolher frequência;
- **Demandas:** ativar/desativar e escolher frequência;
- **Vídeos:** ativar/desativar e cadastrar horários `HH:mm`;
- cada bloco apresenta última execução, próxima execução e **Executar agora**;
- as preferências são persistidas localmente;
- **Iniciar com o Windows** continua usando o perfil do usuário, sem exigir administrador.

O monitoramento automático funciona enquanto o aplicativo estiver aberto ou minimizado na bandeja. Escolher **Sair** encerra o processo e, portanto, o agendamento residente.

## Histórico e dados portáteis

A distribuição utiliza uma pasta `data` junto ao aplicativo. Nela ficam bancos, preferências, termos, demandas, histórico, relatórios automáticos e exportações.

O Histórico é observável pela UI: limpar Notícias ou Vídeos atualiza a tela imediatamente. O histórico de Notícias pode ser exportado em CSV.

## Autotestes de regressão

Antes da publicação, o executável empacotado deve passar por um SelfTest que verifica, entre outros pontos:

- inicialização do controlador e bancos locais;
- catálogo de Notícias e Vídeos não vazio;
- equivalência **Folha PE = Folha de Pernambuco**;
- domínio `folhape.com.br` resolvido para a fonte correta;
- presença das 8 mídias especializadas Windows;
- migração integral dos 11 termos estabelecidos para Notícias e Vídeos.

## Compilar localmente no Windows

Requer JDK 17 e Gradle 8.10.2 (ou compatível):

```powershell
gradle :desktop:createDistributable
```

O app image fica em:

```text
desktop\build\compose\binaries\main\app\
```

## Distribuição v4.0.3

O workflow `Windows Portable v4.0.3` está preparado para:

1. compilar a aplicação;
2. criar a distribuição Windows com JVM incluída;
3. validar o `jvm.dll` empacotado;
4. executar o SelfTest usando o próprio `MonitorDeNoticias.exe`;
5. restaurar `MainV403Kt` como entrypoint de produção;
6. gerar ZIP + SHA-256;
7. publicar a prerelease `windows-v4.0.3-portable`.

Arquivos previstos:

- `monitor-de-noticias-windows-portable-v4.0.3.zip`;
- `monitor-de-noticias-windows-portable-v4.0.3.sha256`.

A release v4.0.3 só deve ser considerada validada depois que a compilação, o runtime empacotado e o SelfTest do executável terminarem com sucesso.
