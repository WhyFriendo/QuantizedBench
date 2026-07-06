from __future__ import annotations

from dataclasses import dataclass

from bench.backends.base import ServerHandle
from llama_server_wrapper import run_llama_server


@dataclass(frozen=True)
class LlamaCppHandle(ServerHandle):
    wrapper: object

    def stop(self) -> None:
        self.wrapper.stop()


def start_llama_cpp(
    *,
    model_path: str,
    host: str,
    port: int,
    n_gpu_layers: int,
    context_size: int,
    extra_args: list[str],
) -> LlamaCppHandle:
    wrapper = run_llama_server(
        model_path=model_path,
        host=host,
        port=port,
        n_gpu_layers=n_gpu_layers,
        context_size=context_size,
        extra_args=extra_args,
    )
    return LlamaCppHandle(base_url=f"http://{host}:{port}", wrapper=wrapper)
