# Monitor de Notícias Android

Aplicativo Android em Kotlin/Jetpack Compose para monitoramento de notícias, termos e demandas.

## Versão atual
**2.0.0**

### Principais recursos
- Busca no Google Notícias via RSS
- Detecção de notícias realmente novas
- Demandas por veículo + assunto
- Histórico local
- Monitoramento automático com WorkManager
- Intervalo de monitoramento persistente
- Exportação CSV
- Notificações para novas notícias e demandas

## Build
O projeto usa Android Gradle Plugin 8.7.3, Kotlin 2.0.21, Java 17, compileSdk 35 e targetSdk 35.

A automação em `.github/workflows/release.yml` compila o APK no GitHub Actions. Tags `v*` também publicam uma GitHub Release com o APK anexado.
