# QuantizedBench

[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)](https://opensource.org/licenses/MIT)
[![Python 3.10+](https://img.shields.io/badge/python-3.10+-blue.svg)](https://www.python.org/downloads/)

QuantizedBench is an automated, configuration-driven evaluation framework designed to benchmark quantized Large Language Models (LLMs) across multiple high-performance inference backends (such as **[MLC-LLM](https://github.com/mlc-ai/mlc-llm)** and **[llama.cpp](https://github.com/ggerganov/llama.cpp)**). 

It dynamically spins up local OpenAI-compatible inference servers for the selected quantization formats, evaluates them against standardized tasks using the **[EleutherAI LM Evaluation Harness](https://github.com/EleutherAI/lm-evaluation-harness)**, and aggregates the results into comprehensive CSV and Markdown reports.

## Quickstart

### Prerequisites

* Python 3.10+
* [uv](https://github.com/astral-sh/uv) for dependencies management
* CUDA-compatible GPU (recommended)

### Local Installation

1. Clone the repository and install dependencies:
```bash
git clone https://github.com/yourusername/QuantizedBench.git
cd QuantizedBench
uv venv
uv sync
```

2. Clone and install the LM Evaluation Harness:
```bash
git clone https://github.com/EleutherAI/lm-evaluation-harness.git
cd lm-evaluation-harness
uv pip install -e .
cd ..
```

3. Copy the example configuration and adjust your paths:
```bash
cp bench/config.example.yaml bench/config.yaml
```

### Docker Usage

You can run QuantizedBench entirely within Docker for proper environment setup.

```bash
# Build the image
docker build -t quantizedbench .

# Run the benchmarks (mount your local models and results directories)
docker run --gpus all -it --rm \
  -v $(pwd)/bench/config.yaml:/app/bench/config.yaml \
  -v $(pwd)/results:/app/results \
  -v $(pwd)/gguf_models:/app/gguf_models \
  quantizedbench --config bench/config.yaml --model example_model
```

## Config

The framework is configured entirely in `bench/config.yaml`. Example structure:

```yaml
models:
  - id: qwen3_0_8b
    display_name: Qwen3.5-0.8B
    tasks: [] # Global tasks
    quantizations:
      - name: gguf_q4_0
        backend: llama_cpp
        model_path: ./gguf_models/Qwen_Qwen3.5-0.8B-IQ2_M.gguf
        n_gpu_layers: 99
        context_size: 4096
        tasks:
          - tinyBenchmarks
```

## Running Benchmarks

**List planned runs:**
```bash
./run_llama_benchmarks.sh --list
```

**Filter and execute for a specific model:**
```bash
./run_llama_benchmarks.sh qwen3_0_8b
./run_mlc_benchmarks.sh phi3_mini
```

**View Results:**
Results are aggregated automatically in the `results/` folder, neatly organized by `results/<model_id>/<quant_name>/`. You will find:
* Raw `lm_eval.json` outputs
* Formatted `_summary.md` markdown tables
* Compiled `lm_eval_summary.csv` CSV files for easy plotting
* `meta.json` capturing the exact configurations used for reproducibility.


