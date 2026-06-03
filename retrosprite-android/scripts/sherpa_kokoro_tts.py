#!/usr/bin/env python3
"""Generate Chinese/English speech with sherpa-onnx Kokoro.

This is a local TTS probe for RetroSprite promo narration and voice QA. It uses
the sherpa-onnx Kokoro multi-language model, which supports Chinese and English
and does not require an API key.
"""

from __future__ import annotations

import argparse
import hashlib
import json
import os
import time
from pathlib import Path

import sherpa_onnx
import soundfile as sf


DEFAULT_BASE = Path.home() / ".local/share/retrosprite/sherpa-onnx-tts"
DEFAULT_MODEL_DIR = DEFAULT_BASE / "models/kokoro-multi-lang-v1_1"


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Generate a local Kokoro wav prompt for RetroSprite.",
    )
    parser.add_argument("text_parts", nargs="*", help="Text to synthesize.")
    parser.add_argument("--text", default="", help="Text to synthesize.")
    parser.add_argument("--output", required=True, help="Output wav path.")
    parser.add_argument(
        "--metadata-output",
        default="",
        help="Optional JSON metadata output path.",
    )
    parser.add_argument(
        "--model-dir",
        default=os.environ.get("SHERPA_KOKORO_TTS_MODEL_DIR", str(DEFAULT_MODEL_DIR)),
        help="Directory containing model.onnx, voices.bin, tokens.txt, lexicons, and espeak-ng-data.",
    )
    parser.add_argument("--sid", type=int, default=58, help="Kokoro speaker id.")
    parser.add_argument(
        "--speed",
        type=float,
        default=1.0,
        help="Speech speed. Larger is faster; smaller is slower.",
    )
    parser.add_argument("--num-threads", type=int, default=2)
    parser.add_argument("--max-num-sentences", type=int, default=1)
    parser.add_argument("--silence-scale", type=float, default=0.2)
    args = parser.parse_args()
    args.text = args.text or " ".join(args.text_parts).strip()
    if not args.text:
        parser.error("text is required")
    return args


def required_file(model_dir: Path, name: str) -> str:
    path = model_dir / name
    if not path.is_file():
        raise FileNotFoundError(f"missing {name}: {path}")
    return str(path)


def existing_rule_fsts(model_dir: Path) -> str:
    names = ["phone-zh.fst", "date-zh.fst", "number-zh.fst"]
    paths = [str(model_dir / name) for name in names if (model_dir / name).is_file()]
    return ",".join(paths)


def sha256_file(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for chunk in iter(lambda: handle.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def main() -> None:
    args = parse_args()
    model_dir = Path(args.model_dir).expanduser().resolve()
    output = Path(args.output).expanduser().resolve()
    output.parent.mkdir(parents=True, exist_ok=True)

    config = sherpa_onnx.OfflineTtsConfig(
        model=sherpa_onnx.OfflineTtsModelConfig(
            kokoro=sherpa_onnx.OfflineTtsKokoroModelConfig(
                model=required_file(model_dir, "model.onnx"),
                voices=required_file(model_dir, "voices.bin"),
                tokens=required_file(model_dir, "tokens.txt"),
                data_dir=str(model_dir / "espeak-ng-data"),
                lexicon=",".join(
                    [
                        required_file(model_dir, "lexicon-us-en.txt"),
                        required_file(model_dir, "lexicon-zh.txt"),
                    ],
                ),
            ),
            provider="cpu",
            debug=False,
            num_threads=args.num_threads,
        ),
        rule_fsts=existing_rule_fsts(model_dir),
        max_num_sentences=args.max_num_sentences,
        silence_scale=args.silence_scale,
    )
    if not config.validate():
        raise ValueError(f"invalid sherpa-onnx Kokoro TTS config for {model_dir}")

    started = time.time()
    tts = sherpa_onnx.OfflineTts(config)
    audio = tts.generate(text=args.text, sid=args.sid, speed=args.speed)
    elapsed = time.time() - started

    if len(audio.samples) == 0:
        raise RuntimeError("sherpa-onnx returned empty audio")

    sf.write(output, audio.samples, samplerate=audio.sample_rate, subtype="PCM_16")
    duration = len(audio.samples) / audio.sample_rate
    metadata = {
        "backend": "sherpa_onnx_kokoro",
        "model": model_dir.name,
        "model_dir": str(model_dir),
        "speaker_id": args.sid,
        "speed": args.speed,
        "sample_rate": audio.sample_rate,
        "duration_seconds": round(duration, 3),
        "elapsed_seconds": round(elapsed, 3),
        "rtf": round(elapsed / duration, 3),
        "sha256": sha256_file(output),
        "output": str(output),
        "text": args.text,
    }
    if args.metadata_output:
        metadata_output = Path(args.metadata_output).expanduser().resolve()
        metadata_output.parent.mkdir(parents=True, exist_ok=True)
        metadata_output.write_text(
            json.dumps(metadata, ensure_ascii=False, indent=2) + "\n",
            encoding="utf-8",
        )
    print(json.dumps(metadata, ensure_ascii=False))


if __name__ == "__main__":
    main()
