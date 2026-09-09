param(
    [switch]$Publish,
    [string]$TargetCommit = $env:GITHUB_SHA
)

$ErrorActionPreference = "Stop"
Set-StrictMode -Version Latest

$repoRoot = Split-Path $PSScriptRoot -Parent
Set-Location $repoRoot

function Invoke-Checked {
    param(
        [Parameter(Mandatory = $true)][string]$Command,
        [Parameter(Mandatory = $true)][string[]]$Arguments
    )
    & $Command @Arguments
    if ($LASTEXITCODE -ne 0) {
        throw "$Command falhou com exit code $LASTEXITCODE"
    }
}

Write-Host "=== Monitor de Notícias v4.0.3 • Windows build ==="
Write-Host "Repo: $repoRoot"

if (-not (Get-Command java -ErrorAction SilentlyContinue)) {
    throw "Java não encontrado no PATH. É necessário JDK 17."
}
if (-not (Get-Command gradle -ErrorAction SilentlyContinue)) {
    throw "Gradle não encontrado no PATH. É necessário Gradle 8.10.2 ou compatível."
}

$javaVersion = (& java -version 2>&1 | Select-Object -First 1)
Write-Host "Java: $javaVersion"
Write-Host "Gradle: $(& gradle --version | Select-String '^Gradle ' | Select-Object -First 1)"

$encoded = "desktop\src\main\resources\monitor_icon.ico.b64"
$icon = "desktop\src\main\resources\monitor_icon.ico"
if (!(Test-Path $encoded)) { throw "Fonte do ícone não encontrada: $encoded" }
$base64 = [IO.File]::ReadAllText($encoded).Trim()
[IO.File]::WriteAllBytes($icon, [Convert]::FromBase64String($base64))
if ((Get-Item $icon).Length -lt 1000) { throw "ICO gerado parece inválido." }
Write-Host "Ícone Windows preparado."

Write-Host "Compilando app image..."
Invoke-Checked -Command "gradle" -Arguments @(':desktop:createDistributable', '--stacktrace')

$root = "desktop\build\compose\binaries\main\app"
if (!(Test-Path $root)) { throw "App image não foi gerado: $root" }
$appDir = Get-ChildItem $root -Directory | Select-Object -First 1
if ($null -eq $appDir) { throw "Diretório do aplicativo portátil não encontrado." }

$runtimeBin = Join-Path $appDir.FullName "runtime\bin"
if (!(Test-Path $runtimeBin)) { throw "runtime/bin da JVM não encontrado: $runtimeBin" }

$nativeDlls = @("msvcp140.dll", "vcruntime140.dll", "vcruntime140_1.dll")
foreach ($dll in $nativeDlls) {
    $target = Join-Path $runtimeBin $dll
    if (!(Test-Path $target)) {
        $source = Join-Path $env:JAVA_HOME "bin\$dll"
        if (!(Test-Path $source)) { throw "Dependência nativa necessária não encontrada: $dll" }
        Copy-Item $source $target -Force
    }
}

$jvm = Join-Path $runtimeBin "server\jvm.dll"
if (!(Test-Path $jvm)) { throw "jvm.dll não encontrada: $jvm" }
$env:PATH = "$runtimeBin;$runtimeBin\server;$env:PATH"

Add-Type @'
using System;
using System.Runtime.InteropServices;
public static class NativeLibraryCheckLocal403 {
    [DllImport("kernel32", SetLastError=true, CharSet=CharSet.Unicode)]
    public static extern IntPtr LoadLibrary(string lpFileName);
    [DllImport("kernel32", SetLastError=true)]
    public static extern bool FreeLibrary(IntPtr hModule);
}
'@
$handle = [NativeLibraryCheckLocal403]::LoadLibrary($jvm)
if ($handle -eq [IntPtr]::Zero) {
    $code = [Runtime.InteropServices.Marshal]::GetLastWin32Error()
    throw "Windows não conseguiu carregar a JVM empacotada. Win32 error: $code"
}
[NativeLibraryCheckLocal403]::FreeLibrary($handle) | Out-Null
Write-Host "JVM empacotada validada."

$exe = Get-ChildItem $appDir.FullName -Filter *.exe -File | Select-Object -First 1
if ($null -eq $exe) { throw "Executável Windows não encontrado." }
$cfg = Get-ChildItem (Join-Path $appDir.FullName "app") -Filter *.cfg -File | Select-Object -First 1
if ($null -eq $cfg) { throw "Configuração do launcher jpackage não encontrada." }

$originalCfg = [IO.File]::ReadAllText($cfg.FullName)
if ($originalCfg -notmatch '(?m)^app\.mainclass=') { throw "app.mainclass não encontrada em $($cfg.FullName)" }
$selfTestCfg = [regex]::Replace(
    $originalCfg,
    '(?m)^app\.mainclass=.*$',
    'app.mainclass=br.com.monitordenoticias.desktop.SelfTestKt'
)

Write-Host "Executando SelfTest pelo executável empacotado..."
try {
    [IO.File]::WriteAllText($cfg.FullName, $selfTestCfg, [Text.UTF8Encoding]::new($false))
    $process = Start-Process -FilePath $exe.FullName -WorkingDirectory $appDir.FullName -PassThru
    if (!$process.WaitForExit(30000)) {
        Stop-Process -Id $process.Id -Force -ErrorAction SilentlyContinue
        throw "SelfTest do executável excedeu 30 segundos."
    }
    if ($process.ExitCode -ne 0) {
        throw "SelfTest do executável falhou com exit code $($process.ExitCode)."
    }
} finally {
    [IO.File]::WriteAllText($cfg.FullName, $originalCfg, [Text.UTF8Encoding]::new($false))
}

$restored = [IO.File]::ReadAllText($cfg.FullName)
if ($restored -notmatch '(?m)^app\.mainclass=br\.com\.monitordenoticias\.desktop\.MainV403Kt\s*$') {
    throw "MainV403Kt não foi restaurado depois do SelfTest."
}
Write-Host "SelfTest do MonitorDeNoticias.exe aprovado."

New-Item -ItemType Directory -Force -Path (Join-Path $appDir.FullName "data") | Out-Null
$zipName = "monitor-de-noticias-windows-portable-v4.0.3.zip"
$shaName = "monitor-de-noticias-windows-portable-v4.0.3.sha256"
$zip = Join-Path $repoRoot $zipName
$shaFile = Join-Path $repoRoot $shaName
if (Test-Path $zip) { Remove-Item $zip -Force }
Compress-Archive -Path "$($appDir.FullName)\*" -DestinationPath $zip -Force
$hash = (Get-FileHash $zip -Algorithm SHA256).Hash.ToLowerInvariant()
"$hash  $zipName" | Set-Content $shaFile -Encoding ascii

Write-Host "ZIP: $zip"
Write-Host "Tamanho: $((Get-Item $zip).Length) bytes"
Write-Host "SHA256: $hash"

if ($Publish) {
    if (-not (Get-Command gh -ErrorAction SilentlyContinue)) {
        throw "GitHub CLI (gh) não encontrado; não é possível publicar automaticamente."
    }
    if ([string]::IsNullOrWhiteSpace($TargetCommit)) {
        $TargetCommit = (& git rev-parse HEAD).Trim()
    }
    $tag = "windows-v4.0.3-portable"
    & gh release view $tag *> $null
    if ($LASTEXITCODE -ne 0) {
        Invoke-Checked -Command "gh" -Arguments @(
            'release', 'create', $tag, $zip, $shaFile,
            '--target', $TargetCommit,
            '--title', 'Monitor de Notícias v4.0.3 — Windows Portable',
            '--notes', 'Prérelease Windows v4.0.3 com novo layout operacional, busca em camadas, aliases regionais, cobertura complementar e diagnóstico de busca.',
            '--prerelease'
        )
    } else {
        Invoke-Checked -Command "gh" -Arguments @('release', 'upload', $tag, $zip, $shaFile, '--clobber')
        $releaseId = (& gh api "repos/$env:GITHUB_REPOSITORY/releases/tags/$tag" --jq '.id').Trim()
        if ($releaseId) {
            & gh api --method PATCH "repos/$env:GITHUB_REPOSITORY/releases/$releaseId" -f "target_commitish=$TargetCommit" *> $null
            if ($LASTEXITCODE -ne 0) { throw "Falha ao atualizar target_commitish da release." }
        }
    }
    Write-Host "Release $tag publicada/atualizada."
}

Write-Host "=== BUILD v4.0.3 CONCLUÍDA COM SUCESSO ==="
