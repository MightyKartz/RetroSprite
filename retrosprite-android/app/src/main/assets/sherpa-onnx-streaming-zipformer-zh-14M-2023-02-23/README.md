This directory contains the default RetroSprite local ASR model for M8.2.

Source:
https://huggingface.co/csukuangfj/sherpa-onnx-streaming-zipformer-zh-14M-2023-02-23

License:
Apache-2.0, as declared by the upstream Hugging Face model card.

Included files:
- encoder-epoch-99-avg-1.int8.onnx
- decoder-epoch-99-avg-1.onnx
- joiner-epoch-99-avg-1.int8.onnx
- tokens.txt

RetroSprite uses this model through sherpa-onnx for short, local, offline
Chinese question transcription. The transcribed text still flows through the
existing GKP/evidence/low-spoiler pipeline.
