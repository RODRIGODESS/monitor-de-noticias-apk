# Monitor de Notícias — Windows Portable v4.0.2

Esta edição porta a **v4.0.2** para Windows sem substituir nem reduzir o aplicativo Android.

## Paridade funcional

O módulo `desktop` compila diretamente o mesmo motor Kotlin/JVM usado no Android para:

- busca progressiva de Notícias e Vídeos, com resultados entrando na tela durante a varredura;
- busca das últimas 24 horas e busca por período;
- atalhos de período **Hoje**, **24 horas**, **7 dias** e **30 dias**;
- Termos de Notícias independentes dos Termos de Vídeos;
- Demandas por veículo + assunto, busca individual, varredura de todas as Demandas e consulta dos resultados armazenados por Demanda;
- catálogo completo de fontes de Notícias;
- catálogo completo de fontes de Vídeos;
- seleção de fontes por região/UF, pesquisa textual e seleção/limpeza somente das fontes visíveis;
- YouTube oficial;
- Globoplay por Edições, Trechos e Jarvis;
- telejornais nacionais e regionais;
- varredura por fonte/canal seguida de cruzamento local com Termos e Demandas;
- título, descrição, tags, keywords e demais metadados usados pelo motor v4.0.2;
- histórico persistente de Notícias e Vídeos;
- exportação do histórico de Notícias em CSV;
- abertura da matéria/vídeo no navegador;
- compartilhamento de Notícias pelo WhatsApp;
- deduplicação;
- preservação da primeira captura e regra do selo **NOVO** baseada no histórico;
- diagnóstico de fontes de vídeo instáveis;
- relatórios das execuções automáticas de Notícias, Demandas e Vídeos, com tentativa, conclusão, encontrados, novos e falhas.

Somente as camadas específicas do Android são substituídas: `SQLiteOpenHelper` por SQLite JDBC, `Context/SharedPreferences` por armazenamento portátil, `WorkManager/AlarmManager/receivers` por agendamento residente no tray e início com o Windows, e a UI móvel por uma UI desktop.

## Monitoramento automático no Windows

- Notícias: intervalo configurável de 15, 30, 45 ou 60 minutos.
- Demandas: uma vez por hora.
- Vídeos: 08h, 12h, 15h, 19h e 21h no horário local, iguais à rotina da v4.0.2 Android.
- Fechar a janela envia o programa para a bandeja do sistema; **Sair** no menu da bandeja encerra o monitoramento.
- **Iniciar com o Windows** usa apenas o perfil do usuário (`HKCU`), sem exigir administrador, e pode ser ligado/desligado nas Configurações.

## Dados portáteis

A distribuição cria uma pasta `data` junto ao aplicativo. Nela ficam:

- `news.db`;
- `videos.db`;
- preferências;
- termos;
- demandas;
- histórico;
- relatórios automáticos;
- exportações CSV.

Copiar a pasta completa do programa para outro computador preserva os dados da edição Windows.

## Compilar localmente no Windows

Requer JDK 17 e Gradle 8.10.2 (ou compatível):

```powershell
gradle :desktop:createDistributable
```

O app image fica em `desktop\build\compose\binaries\main\app\`.

## Distribuição automática

O workflow `Windows Portable v4.0.2` roda em `windows-latest`, cria o app image com o runtime Java incluído, valida a presença do `.exe`, gera o ZIP e o arquivo SHA-256 e publica ambos na prerelease `windows-v4.0.2-portable`.

Arquivos publicados:

- `monitor-de-noticias-windows-portable-v4.0.2.zip`;
- `monitor-de-noticias-windows-portable-v4.0.2.sha256`.
