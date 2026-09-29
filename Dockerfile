FROM ghcr.io/ggml-org/llama.cpp:server-cuda@sha256:b98cf7adba78b3a8053724949fafcea8094182f1fd4d28e0de46e471dffe75d0 AS llama

FROM nvidia/cuda:12.8.1-devel-ubuntu24.04

LABEL maintainer="QuantizedBench"
LABEL description="Benchmarking framework for quantized LLMs (llama.cpp)"

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

COPY --from=llama /app /opt/llama-cpp
ENV LD_LIBRARY_PATH="/opt/llama-cpp:${LD_LIBRARY_PATH}"
ENV PATH="/opt/llama-cpp:${PATH}"

RUN curl -LsSf https://astral.sh/uv/install.sh | env UV_INSTALL_DIR="/usr/local/bin" sh

COPY pyproject.toml uv.lock .python-version ./
COPY patches/ ./patches/

ENV PATH="/app/.venv/bin:$PATH"
RUN uv venv && uv sync --frozen

ARG LM_EVAL_COMMIT=1323ffe16fe1b7df39e18d320dbe7a9509d51e83
RUN git clone https://github.com/EleutherAI/lm-evaluation-harness.git /app/lm-evaluation-harness && \
    cd /app/lm-evaluation-harness && \
    git checkout "$LM_EVAL_COMMIT" && \
    git apply /app/patches/lm_eval_gguf_logprobs.patch && \
    uv pip install -e .

ARG TINYBENCHMARKS_COMMIT=e9a8b1031b0340571beb6c9ca3a27891be09a8fd
RUN uv pip install "tinyBenchmarks @ git+https://github.com/felipemaiapolo/tinyBenchmarks@${TINYBENCHMARKS_COMMIT}"

COPY . .

RUN chmod +x run_llama_benchmarks.sh

ENTRYPOINT ["python3", "-m", "bench.runner"]
CMD ["--help"]
