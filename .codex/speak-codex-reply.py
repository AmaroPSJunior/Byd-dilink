#!/data/data/com.termux/files/usr/bin/python
"""Speak completed Portuguese Codex replies through Android's Termux TTS."""

import json
import re
import subprocess
import sys


def is_portuguese(text: str) -> bool:
    normalized = text.casefold()
    if re.search(r"[ãõç]", normalized):
        return True
    markers = {
        "agora", "ainda", "arquivo", "bem", "com", "como", "da", "das",
        "de", "dentro", "desde", "ela", "ele", "em", "então", "está",
        "estão", "estava", "eu", "foi", "isso", "já", "mais", "mas",
        "meu", "minha", "muito", "não", "nosso", "obrigado", "onde",
        "para", "pela", "pelo", "podemos", "posso", "porque", "por", "qual",
        "quando", "que", "seu", "sua", "também", "tem", "toda", "tudo",
        "uma", "você", "vocês", "vou", "estamos", "resposta", "pasta", "sim",
        "claro", "certo", "pronto", "feito", "pode", "segue", "aqui", "entendi",
    }
    words = set(re.findall(r"[a-zà-ÿ]+", normalized))
    hits = words & markers
    return len(hits) >= 2 or (len(words) <= 4 and bool(hits))


def clean_for_speech(text: str) -> str:
    text = re.sub(r"```.*?```", " bloco de código omitido. ", text, flags=re.S)
    text = re.sub(r"`([^`]+)`", r"\1", text)
    text = re.sub(r"!?\[([^\]]*)\]\([^)]*\)", r"\1", text)
    text = re.sub(r"https?://\S+", "", text)
    text = re.sub(r"^\s{0,3}#{1,6}\s*", "", text, flags=re.M)
    text = re.sub(r"^\s*[-*+]\s+", "", text, flags=re.M)
    text = re.sub(r"^\s*\d+\.\s+", "", text, flags=re.M)
    text = re.sub(r"[*_~>#]", "", text)
    return re.sub(r"\s+", " ", text).strip()


def main() -> int:
    try:
        event = json.loads(sys.argv[1])
    except (IndexError, json.JSONDecodeError):
        return 0
    if event.get("type") != "agent-turn-complete":
        return 0
    reply = event.get("last-assistant-message") or ""
    if not is_portuguese(reply):
        return 0
    spoken = clean_for_speech(reply)
    if spoken:
        subprocess.run(
            ["termux-tts-speak", "-l", "pt", "-n", "BR", spoken],
            check=False,
            stdout=subprocess.DEVNULL,
            stderr=subprocess.DEVNULL,
        )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
