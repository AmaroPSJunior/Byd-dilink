#!/usr/bin/env python3
"""Capture read-only BYD lighting-related Android logs over ADB."""
import argparse
import datetime as dt
import json
import re
import subprocess
import sys
from pathlib import Path

REMOTE_SESSIONS = "/sdcard/Download/BydDilink/Inspector/sessions"
DEFAULT_OUTPUT = Path.home() / "storage/shared/Download/BydDilink/Inspector/adb-captures"
SESSION_RE = re.compile(r"^\d{8}_\d{6}_\d{3}$")
LOG_RE = re.compile(r"^\s*(?P<epoch>\d+(?:\.\d+)?)\s+(?P<pid>\d+)\s+(?P<tid>\d+)\s+(?P<priority>[VDIWEF])\s+(?P<tag>[^:]+):\s?(?P<message>.*)$")
EVENT_RE = re.compile(r"eventType:(-?\d+),\s*eventValue:(-?\d+)")


def adb(serial, *args, capture=True):
    return subprocess.run(
        ["adb", "-s", serial, *args], check=True,
        stdout=subprocess.PIPE if capture else None,
        stderr=subprocess.PIPE if capture else None,
    )


def pick_device(requested):
    result = subprocess.run(["adb", "devices"], check=True, text=True, capture_output=True)
    devices = [line.split()[0] for line in result.stdout.splitlines()[1:] if len(line.split()) >= 2 and line.split()[1] == "device"]
    if requested:
        if requested not in devices:
            raise RuntimeError(f"ADB não vê {requested!r} como device. Rode adb devices e reconecte o carro.")
        return requested
    if len(devices) != 1:
        raise RuntimeError(f"Esperava exatamente um dispositivo ADB conectado; encontrei {len(devices)}. Use --serial.")
    return devices[0]


def read_session(serial, session_id):
    if not SESSION_RE.fullmatch(session_id):
        raise RuntimeError("ID de sessão inválido; esperado YYYYMMDD_HHMMSS_mmm.")
    remote = f"{REMOTE_SESSIONS}/{session_id}/metadata.json"
    result = adb(serial, "exec-out", "cat", remote)
    metadata = json.loads(result.stdout.decode("utf-8"))
    if metadata.get("mode") != "experimento":
        raise RuntimeError(f"A sessão {session_id} não está no modo experimento.")
    if metadata.get("endedAt") is not None:
        raise RuntimeError(f"A sessão {session_id} já foi finalizada; inicie a captura durante um novo experimento.")
    return metadata


def latest_active_experiment(serial):
    result = adb(serial, "shell", "ls", "-1t", REMOTE_SESSIONS)
    for raw_id in result.stdout.decode("utf-8", "replace").splitlines():
        session_id = raw_id.strip()
        if not SESSION_RE.fullmatch(session_id):
            continue
        try:
            return session_id, read_session(serial, session_id)
        except (subprocess.CalledProcessError, json.JSONDecodeError, RuntimeError):
            continue
    raise RuntimeError("Não encontrei experimento ativo do Inspector. Inicie um experimento primeiro.")


def pull_session_files(serial, session_id, output_dir):
    remote_dir = f"{REMOTE_SESSIONS}/{session_id}"
    for name in ("metadata.json", "events.jsonl"):
        try:
            result = adb(serial, "exec-out", "cat", f"{remote_dir}/{name}")
        except subprocess.CalledProcessError as error:
            detail = error.stderr.decode("utf-8", "replace").strip() if error.stderr else str(error)
            print(f"Não foi possível copiar {name}: {detail}", file=sys.stderr)
            continue
        (output_dir / name).write_bytes(result.stdout)


def main():
    parser = argparse.ArgumentParser(
        description="Captura logs OEM de iluminação em modo somente leitura e salva cópia local da sessão Inspector."
    )
    parser.add_argument("session_id", nargs="?", help="ID do experimento ativo; sem ele usa o experimento ativo mais recente.")
    parser.add_argument("--serial", help="Serial exibido por adb devices, por exemplo 172.20.227.181:5555.")
    parser.add_argument("--output-root", type=Path, default=DEFAULT_OUTPUT, help=f"Pasta local de saída (padrão: {DEFAULT_OUTPUT}).")
    args = parser.parse_args()

    try:
        serial = pick_device(args.serial)
        if args.session_id:
            session_id = args.session_id
            read_session(serial, session_id)
        else:
            session_id, _ = latest_active_experiment(serial)

        output_dir = args.output_root / session_id
        output_dir.mkdir(parents=True, exist_ok=True)
        capture_path = output_dir / "adb-logcat.jsonl"
        if capture_path.exists():
            raise RuntimeError(f"O arquivo já existe: {capture_path}; use --output-root diferente para preservar a captura.")

        print(f"Dispositivo: {serial}")
        print(f"Sessão: {session_id}")
        print(f"Arquivo local: {capture_path}")
        print("Capturando eventos OEM somente de leitura. Faça o ensaio e finalize a sessão no Inspector; depois pressione Ctrl+C. Não limpa o log do carro.")

        command = [
            "adb", "-s", serial, "logcat", "-v", "epoch",
            "LogUtils-AbsBYDAutoSettingListener:D",
            "AbsBYDAutoSettingListener:D",
            "AbsBYDAutoDevice:D",
            "BYDAutoSettingDevice:D",
            "BYDAutoDeviceManager:D",
            "*:S",
        ]
        process = subprocess.Popen(command, stdout=subprocess.PIPE, stderr=None, text=True, encoding="utf-8", errors="replace", bufsize=1)
        count = 0
        try:
            with capture_path.open("x", encoding="utf-8") as capture:
                assert process.stdout is not None
                for line in process.stdout:
                    match = LOG_RE.match(line.rstrip("\r\n"))
                    if not match:
                        continue
                    item = match.groupdict()
                    timestamp = float(item.pop("epoch"))
                    item["timestampMs"] = round(timestamp * 1000)
                    item["timestampUtc"] = dt.datetime.fromtimestamp(timestamp, dt.timezone.utc).isoformat(timespec="milliseconds")
                    item["pid"] = int(item["pid"])
                    item["tid"] = int(item["tid"])
                    event = EVENT_RE.search(item["message"])
                    if event:
                        event_type = int(event.group(1))
                        item["eventTypeDecimal"] = event_type
                        item["eventTypeHex"] = f"0x{event_type & 0xffffffff:08X}"
                        item["eventValue"] = int(event.group(2))
                    capture.write(json.dumps(item, ensure_ascii=False, separators=(",", ":")) + "\n")
                    capture.flush()
                    count += 1
        except KeyboardInterrupt:
            print("\nCaptura interrompida pelo usuário.")
        finally:
            if process.poll() is None:
                process.terminate()
                try:
                    process.wait(timeout=3)
                except subprocess.TimeoutExpired:
                    process.kill()
                    process.wait()

        pull_session_files(serial, session_id, output_dir)
        print(f"Linhas salvas: {count}")
        print(f"Arquivos do experimento copiados para: {output_dir}")
        return 0
    except (OSError, RuntimeError, subprocess.CalledProcessError, json.JSONDecodeError) as error:
        print(f"Erro: {error}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
