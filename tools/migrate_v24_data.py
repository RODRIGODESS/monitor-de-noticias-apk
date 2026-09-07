#!/usr/bin/env python3
"""One-time local-data migration helper for Monitor de Notícias v2.4 -> v2.5.

Why this exists:
The historical v2.4 APK was signed by an ephemeral Android debug key. A new
permanent signing key cannot update that package in-place. This helper backs up
only the app-owned SQLite databases and SharedPreferences through `run-as`, then
restores them after the new APK is installed.

Requirements:
- Android Platform Tools (`adb`) installed and on PATH.
- USB debugging enabled and this computer authorized on the phone.
- The installed v2.4 build is debuggable (the GitHub release was assembleDebug).

Usage:
  python tools/migrate_v24_data.py backup
  # after backup succeeds, install the permanently signed v2.5 using migrate:
  python tools/migrate_v24_data.py migrate path/to/monitor-de-noticias-v2.5.0.apk

The `migrate` command refuses to continue unless the backup exists and is non-empty.
"""

from __future__ import annotations

import pathlib
import shutil
import subprocess
import sys

PACKAGE = "br.com.monitordenoticias.android"
BACKUP = pathlib.Path("monitor-noticias-v24-data.tar")


def run(args: list[str], **kwargs) -> subprocess.CompletedProcess:
    print("+", " ".join(args))
    return subprocess.run(args, check=True, **kwargs)


def require_adb() -> None:
    if shutil.which("adb") is None:
        raise SystemExit("ERRO: adb não encontrado. Instale o Android Platform Tools e adicione adb ao PATH.")
    run(["adb", "get-state"], stdout=subprocess.DEVNULL)


def ensure_run_as() -> None:
    probe = subprocess.run(
        ["adb", "shell", "run-as", PACKAGE, "pwd"],
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
        text=True,
    )
    if probe.returncode != 0:
        raise SystemExit(
            "ERRO: não foi possível acessar os dados do app com run-as. "
            "Confirme que a v2.4 do GitHub está instalada e que a depuração USB foi autorizada.\n"
            + probe.stderr.strip()
        )


def backup() -> None:
    require_adb()
    ensure_run_as()
    run(["adb", "shell", "am", "force-stop", PACKAGE])
    print(f"Criando backup em {BACKUP.resolve()} ...")
    with BACKUP.open("wb") as out:
        proc = subprocess.run(
            ["adb", "exec-out", "run-as", PACKAGE, "tar", "-cf", "-", "databases", "shared_prefs"],
            stdout=out,
            stderr=subprocess.PIPE,
        )
    if proc.returncode != 0:
        BACKUP.unlink(missing_ok=True)
        raise SystemExit("ERRO ao criar backup: " + proc.stderr.decode(errors="replace"))
    if not BACKUP.exists() or BACKUP.stat().st_size < 512:
        BACKUP.unlink(missing_ok=True)
        raise SystemExit("ERRO: o backup ficou vazio; a migração foi interrompida.")
    print(f"OK: backup criado ({BACKUP.stat().st_size} bytes). Nenhum dado foi apagado do telefone.")


def migrate(apk: pathlib.Path) -> None:
    require_adb()
    if not BACKUP.exists() or BACKUP.stat().st_size < 512:
        raise SystemExit("ERRO: faça primeiro: python tools/migrate_v24_data.py backup")
    if not apk.exists():
        raise SystemExit(f"ERRO: APK não encontrado: {apk}")

    answer = input(
        "Backup verificado. A próxima etapa removerá a v2.4, instalará a v2.5 e restaurará os dados.\n"
        "Digite MIGRAR para continuar: "
    ).strip()
    if answer != "MIGRAR":
        raise SystemExit("Migração cancelada; o app instalado não foi alterado.")

    run(["adb", "shell", "am", "force-stop", PACKAGE])
    run(["adb", "uninstall", PACKAGE])
    run(["adb", "install", str(apk)])
    run(["adb", "shell", "am", "force-stop", PACKAGE])

    print("Restaurando banco e preferências ...")
    with BACKUP.open("rb") as src:
        proc = subprocess.run(
            [
                "adb", "shell", "run-as", PACKAGE, "sh", "-c",
                f"cd /data/user/0/{PACKAGE} && tar -xf -",
            ],
            stdin=src,
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
        )
    if proc.returncode != 0:
        raise SystemExit(
            "ERRO durante a restauração. O arquivo de backup permanece intacto em "
            f"{BACKUP.resolve()}\n" + proc.stderr.decode(errors="replace")
        )

    print("OK: dados restaurados. Abra o Monitor de Notícias no telefone; o banco será migrado automaticamente para a nova versão.")


def main() -> None:
    if len(sys.argv) < 2 or sys.argv[1] not in {"backup", "migrate"}:
        raise SystemExit(
            "Uso:\n"
            "  python tools/migrate_v24_data.py backup\n"
            "  python tools/migrate_v24_data.py migrate caminho/para/v2.5.apk"
        )
    if sys.argv[1] == "backup":
        backup()
    else:
        if len(sys.argv) != 3:
            raise SystemExit("Informe o caminho do APK v2.5 no comando migrate.")
        migrate(pathlib.Path(sys.argv[2]))


if __name__ == "__main__":
    main()
