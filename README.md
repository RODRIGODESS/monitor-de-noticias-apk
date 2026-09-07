# Monitor de Notícias Android

Aplicativo Android em Kotlin/Jetpack Compose para monitoramento de notícias, termos, demandas e veículos de imprensa.

## Versão atual
**2.2.0**

### Principais recursos
- Busca no Google Notícias via RSS
- Detecção de notícias realmente novas
- Demandas por veículo + assunto
- Histórico local e exportação CSV
- Monitoramento automático com WorkManager
- Intervalo de monitoramento persistente
- Notificações para novas notícias e demandas
- Navegação superior com abas deslizáveis
- Seleção persistente de fontes
- Busca por veículo/grupo e filtros por região/estado
- Selecionar/desmarcar as fontes visíveis
- Modo `Buscar em todos os veículos` para busca aberta, inclusive veículos menores
- 13 veículos nacionais predefinidos
- Catálogo estadual inicial com 4 veículos de referência por UF
- Pesquisa por período com data **e hora** inicial/final persistentes
- Atalhos Hoje / 24 horas / 7 dias / 30 dias
- Validação de períodos inválidos

## Fontes nacionais incluídas
O Globo, Correio Braziliense, G1, R7, Revista Oeste, Folha de S.Paulo, Estadão, Valor Econômico, O Antagonista, CNN Brasil, Jovem Pan, Estado de Minas e Metrópoles.

> O catálogo estadual é uma curadoria inicial de veículos de grande relevância/alcance em cada UF; ele pode ser revisado e ampliado sem alterar o motor de busca. O modo de busca aberta continua disponível para encontrar veículos fora desse catálogo.

## Build
O projeto usa Android Gradle Plugin 8.7.3, Kotlin 2.0.21, Java 17, compileSdk 35 e targetSdk 35.

A automação em `.github/workflows/release.yml` compila o APK no GitHub Actions. O build de pull requests é usado para validação; pushes aprovados no `main` publicam a GitHub Release com o APK anexado.
