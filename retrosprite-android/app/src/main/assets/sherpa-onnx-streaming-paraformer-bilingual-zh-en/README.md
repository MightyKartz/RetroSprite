# sherpa-onnx streaming Paraformer bilingual zh-en

Bundled for RetroSprite local Android ASR.

- Upstream model: `csukuangfj/sherpa-onnx-streaming-paraformer-bilingual-zh-en`
- Source: https://huggingface.co/csukuangfj/sherpa-onnx-streaming-paraformer-bilingual-zh-en
- Release package: https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/sherpa-onnx-streaming-paraformer-bilingual-zh-en.tar.bz2
- License: Apache-2.0
- Imported: 2026-05-24

Included runtime files:

- `encoder.int8.onnx`
- `decoder.int8.onnx`
- `tokens.txt`

The Android app uses the int8 streaming model through sherpa-onnx
`OnlineParaformerModelConfig`. The full upstream package also includes fp32
ONNX files; those are intentionally not bundled in the APK.
