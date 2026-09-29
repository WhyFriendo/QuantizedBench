# Recorded benchmark environment

The five-repeat Gemma 3 270M sweep used the following pinned components:

- EC2 instance type: `g6.xlarge`
- GPU: NVIDIA L4, 23,034 MiB
- NVIDIA driver: `580.178.04`
- Base operating system: Amazon Linux 2023 ECS GPU AMI, kernel 6.1
- Container CUDA base: `nvidia/cuda:12.8.1-devel-ubuntu24.04`
- llama.cpp image digest: `sha256:b98cf7adba78b3a8053724949fafcea8094182f1fd4d28e0de46e471dffe75d0`
- llama.cpp build: 11065, commit `ce8caa6e6`
- lm-evaluation-harness commit: `1323ffe16fe1b7df39e18d320dbe7a9509d51e83`
- tinyBenchmarks commit: `e9a8b1031b0340571beb6c9ca3a27891be09a8fd`
- Python: 3.13.15
- Batch size: 4
- Context size: 4096
- GPU layers requested: 99
- Few-shot examples: 0
- Harness seeds: random 0, NumPy 1234, PyTorch 1234, few-shot 1234

The run evaluated the complete `tinyBenchmarks` task group. The five repeats
produced identical summary metrics for each quantization. Model file hashes and
download URLs are recorded in `bench/models_manifest.json`.

## Scoring limitation

The bundled GGUF adapter patch approximates continuation log likelihood using
the top 20 next-token candidates. A validation experiment found that tokens
outside the top 20 can alter multiple-choice rankings. See
`validation/scoring_validation.md` and the accompanying JSON files. This is a
methodological limitation of the recorded benchmark pipeline and should be
disclosed when reporting its results.

## Runtime asset

`tinyBenchmarks.pkl` is loaded by the upstream `tinyBenchmarks` Python package.
The packaged copy has SHA-256:

`c3b6e426dfe7b100fe6d0ee960398e10a8763254bcead3be80cc6bc15abca284`
