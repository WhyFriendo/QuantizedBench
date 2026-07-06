FROM ghcr.io/ggerganov/llama.cpp:server-cuda AS llama

FROM nvidia/cuda:12.1.1-devel-ubuntu22.04

LABEL maintainer="QuantizedBench"
LABEL description="Benchmarking framework for quantized LLMs (MLC and Llama.cpp)"

ENV DEBIAN_FRONTEND=noninteractive
ENV PYTHONUNBUFFERED=1

WORKDIR /app

RUN apt-get update && apt-get install -y \
    python3 \
    python3-pip \
    python3-venv \
    git \
    curl \
    && rm -rf /var/lib/apt/lists/*

COPY --from=llama /app/llama-server /usr/local/bin/llama-server

RUN curl -LsSf https://astral.sh/uv/install.sh | env UV_INSTALL_DIR="/usr/local/bin" sh

COPY pyproject.toml uv.lock ./


ENV PATH="/app/.venv/bin:$PATH"
RUN uv venv && uv sync --frozen

# Install MLC-LLM directly from official wheels matching the CUDA 12.1 base image
RUN uv pip install --pre --force-reinstall mlc-ai-nightly-cu121 mlc-llm-nightly-cu121 -f https://mlc.ai/wheels

RUN git clone https://github.com/EleutherAI/lm-evaluation-harness.git /app/lm-evaluation-harness && \
    cd /app/lm-evaluation-harness && \
    uv pip install -e .

COPY . .

RUN chmod +x run_llama_benchmarks.sh run_mlc_benchmarks.sh

ENTRYPOINT ["python3", "-m", "bench.runner"]
CMD ["--help"]
