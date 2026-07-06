from __future__ import annotations

from dataclasses import dataclass
from pathlib import Path
from typing import Any, Dict, Iterable, List, Optional

import yaml


@dataclass(frozen=True)
class QuantizationConfig:
    name: str
    backend: str
    model_uri: Optional[str] = None
    model_path: Optional[str] = None
    model_lib: Optional[str] = None
    tokenizer_path: Optional[str] = None
    device: Optional[str] = None
    n_gpu_layers: Optional[int] = None
    context_size: Optional[int] = None
    extra_args: List[str] = None
    tasks: Optional[List[str]] = None
    num_fewshot: Optional[int] = None
    limit: Optional[int] = None
    mlc_python: Optional[str] = None

@dataclass(frozen=True)
class ModelConfig:
    id: str
    display_name: str
    tasks: List[str]
    quantizations: List[QuantizationConfig]


@dataclass(frozen=True)
class LmEvalConfig:
    harness_path: Path
    batch_size: int = 1
    num_fewshot: Optional[int] = None
    limit: Optional[int] = None
    num_concurrent: int = 1
    python: str = "python3"
    mlc_python: Optional[str] = None


@dataclass(frozen=True)
class BenchConfig:
    models: List[ModelConfig]
    lm_eval: LmEvalConfig


def load_config(path: Path) -> BenchConfig:
    raw = _load_yaml(path)
    lm_eval = _parse_lm_eval(raw.get("lm_eval", {}))
    models = [_parse_model(item) for item in raw.get("models", [])]
    if not models:
        raise ValueError("No models defined in config")
    return BenchConfig(models=models, lm_eval=lm_eval)


def _load_yaml(path: Path) -> Dict[str, Any]:
    if not path.exists():
        raise FileNotFoundError(f"Config not found: {path}")
    with path.open("r", encoding="utf-8") as handle:
        data = yaml.safe_load(handle) or {}
    if not isinstance(data, dict):
        raise ValueError("Top-level config must be a mapping")
    return data


def _parse_lm_eval(raw: Dict[str, Any]) -> LmEvalConfig:
    harness_path = Path(raw.get("harness_path", "")).expanduser()
    if not harness_path:
        raise ValueError("lm_eval.harness_path is required")
    return LmEvalConfig(
        harness_path=harness_path,
        batch_size=int(raw.get("batch_size", 1)),
        num_fewshot=_optional_int(raw.get("num_fewshot")),
        limit=_optional_int(raw.get("limit")),
        num_concurrent=int(raw.get("num_concurrent", 1)),
        python=str(raw.get("python", "python3")),
        mlc_python=raw.get("mlc_python"),
    )


def _parse_model(raw: Dict[str, Any]) -> ModelConfig:
    model_id = raw.get("id")
    display_name = raw.get("display_name", model_id)
    tasks = list(raw.get("tasks", []))
    if not model_id:
        raise ValueError("models[].id is required")
    quantizations = [_parse_quant(q, model_id) for q in raw.get("quantizations", [])]
    if not quantizations:
        raise ValueError(f"models[{model_id}].quantizations is required")
    return ModelConfig(
        id=model_id,
        display_name=display_name,
        tasks=tasks,
        quantizations=quantizations,
    )


def _parse_quant(raw: Dict[str, Any], model_id: str) -> QuantizationConfig:
    name = raw.get("name")
    backend = raw.get("backend")
    if not name:
        raise ValueError(f"models[{model_id}].quantizations[].name is required")
    if not backend:
        raise ValueError(f"models[{model_id}].quantizations[{name}].backend is required")
    if backend not in {"mlc", "llama_cpp"}:
        raise ValueError(
            f"models[{model_id}].quantizations[{name}].backend must be one of mlc, llama_cpp"
        )
    model_uri = raw.get("model_uri")
    model_path = raw.get("model_path")
    if backend == "mlc" and not model_uri:
        raise ValueError(f"models[{model_id}].quantizations[{name}].model_uri is required")
    if backend == "llama_cpp" and not model_path:
        raise ValueError(f"models[{model_id}].quantizations[{name}].model_path is required")
    extra_args = list(raw.get("extra_args", []))
    tasks = raw.get("tasks")
    num_fewshot = _optional_int(raw.get("num_fewshot"))
    limit = _optional_int(raw.get("limit"))
    return QuantizationConfig(
        name=name,
        backend=backend,
        model_uri=model_uri,
        model_path=model_path,
        model_lib=raw.get("model_lib"),
        tokenizer_path=raw.get("tokenizer_path"),
        device=raw.get("device"),
        n_gpu_layers=_optional_int(raw.get("n_gpu_layers")),
        context_size=_optional_int(raw.get("context_size")),
        extra_args=extra_args,
        tasks=tasks,
        num_fewshot=num_fewshot,
        limit=limit,
        mlc_python=raw.get("mlc_python"),
    )


def _optional_int(value: Any) -> Optional[int]:
    if value is None:
        return None
    return int(value)


def iter_runs(
    config: BenchConfig,
    *,
    model_ids: Optional[Iterable[str]] = None,
    quant_names: Optional[Iterable[str]] = None,
    backend: Optional[str] = None,
) -> List[Dict[str, Any]]:
    selected_models = set(model_ids or [])
    selected_quants = set(quant_names or [])
    runs: List[Dict[str, Any]] = []
    for model in config.models:
        if selected_models and model.id not in selected_models:
            continue
        for quant in model.quantizations:
            if backend and quant.backend != backend:
                continue
            if selected_quants and quant.name not in selected_quants:
                continue
            runs.append({"model": model, "quant": quant})
    if not runs:
        raise ValueError("No runs matched the selected filters")
    return runs
