# QuantizedBench thesis benchmark package

This repository is the reproducible code package used to evaluate 253 GGUF
quantizations with the `tinyBenchmarks` task group through llama.cpp and
EleutherAI's lm-evaluation-harness. It also contains the repeat-run and scoring
validation utilities used to check the method.

Large GGUF weights, generated benchmark results, credentials, virtual
environments, and the external lm-evaluation-harness checkout are deliberately
excluded.

## Included files

- `bench/`: configuration loader, runner, llama.cpp backend, result parser, the
  exact benchmark configuration, and the 253-file download manifest.
- `patches/lm_eval_gguf_logprobs.patch`: GGUF adapter patch applied to the
  pinned lm-evaluation-harness revision.
- `scripts/download_models.py`: resumable, SHA-256-verified model downloader.
- `scripts/generate_config.py`: regenerates the 253-run configuration directly
  from the model manifest.
- `scripts/repeat_sweep.py`: resumable multi-quantization repeat runner used for
  the five-repeat Gemma 3 270M sweep.
- `scripts/repeat_gemma3_270m_tinybenchmarks.py`: focused repeat and reference
  comparison utility. Its reference and output paths are command-line options
  in this repository so it is portable.
- `scripts/validate_gguf_scoring.py`: comparison of the patched top-20 scorer
  against forced-token scoring from llama-server.
- `scripts/ec2/`: EC2 bootstrap and incremental rsync helper.
- `validation/`: the scoring validation summary and machine-readable reports.
- `Dockerfile`, `uv.lock`, and `.python-version`: pinned execution environment.
- `tinyBenchmarks.pkl`: runtime data loaded by the tinyBenchmarks package.

## Android applications

This repository also contains two Android benchmarking applications, each kept in
its own top-level folder:

- [`Llama-Bench/`](Llama-Bench/README.md) — the React Native llama.cpp benchmark application.
- [`Execu-Benchmark/`](Execu-Benchmark/README.md) — the native Android ExecuTorch benchmark application.

Each application has its own build files, dependencies, documentation, and
application-specific `.gitignore` rules. Run build commands from the relevant
application folder.

## Reproduce the Docker environment

Requirements: Docker with the NVIDIA Container Toolkit, an NVIDIA GPU, and
enough storage for the selected GGUF files.

```bash
docker build -t quantizedbench-thesis .
```

The build pins the llama.cpp server image by digest and checks out
lm-evaluation-harness commit
`1323ffe16fe1b7df39e18d320dbe7a9509d51e83` before applying the bundled patch.
The tinyBenchmarks dependency is pinned to commit
`e9a8b1031b0340571beb6c9ca3a27891be09a8fd`.

## Download model files

List the files for one model family:

```bash
uv run python scripts/download_models.py --family gemma3_270m --list
```

Download and verify them:

```bash
uv run python scripts/download_models.py --family gemma3_270m
```

The complete manifest contains 253 GGUF files across 11 model families. Model
weights are saved under `gguf_models/`, which is ignored by Git.

To regenerate `bench/config.yaml` from the manifest:

```bash
uv run python scripts/generate_config.py
```

## Run one benchmark

```bash
docker run --gpus all --rm \
  -v "$PWD/gguf_models:/app/gguf_models:ro" \
  -v "$PWD/results:/app/results" \
  quantizedbench-thesis \
  --config /app/bench/config.yaml \
  --model gemma3_270m \
  --quantization iq3_xxs
```

List matching jobs without running them by appending `--list`.

## Run the repeat sweep

The sweep runner writes each repeat to its own directory and skips jobs carrying
a `COMPLETED` marker when restarted:

```bash
docker run --gpus all --rm \
  -v "$PWD/gguf_models:/app/gguf_models:ro" \
  -v "$PWD/results_ec2_repeats:/output" \
  --entrypoint python3 quantizedbench-thesis \
  /app/scripts/repeat_sweep.py \
  --config /app/bench/config.yaml \
  --model gemma3_270m \
  --repeats 5 \
  --output-root /output/gemma3_270m_5x
```

## Run the focused repeat comparison

```bash
uv run python scripts/repeat_gemma3_270m_tinybenchmarks.py \
  --repeats 3 \
  --reference /path/to/lm_eval_tinyBenchmarks_summary.csv
```

## Validate GGUF scoring

Start llama-server for a test GGUF, then run:

```bash
uv run python scripts/validate_gguf_scoring.py \
  --base-url http://127.0.0.1:8080 \
  --task tinyArc \
  --questions 100 \
  --output validation/scoring_validation_new.json
```

This requires the pinned, patched `lm-evaluation-harness` checkout. The Docker
image creates it automatically at `/app/lm-evaluation-harness`.

## EC2 result synchronization

Copy `.ec2-instance.env.example` to `.ec2-instance.env`, enter the instance
address and SSH key path, then run:

```bash
scripts/ec2/pull_results.sh
```

The helper uses rsync with partial transfers and a local lock, making repeated
incremental synchronization safe.

## Recorded environment and limitations

See [REPRODUCIBILITY.md](REPRODUCIBILITY.md). The scoring approximation
documented there is relevant when interpreting small differences between runs
made with different llama.cpp or hardware environments.
