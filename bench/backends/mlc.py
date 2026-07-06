from __future__ import annotations

import os
import signal
import subprocess
import threading
from pathlib import Path
from typing import Optional

from bench.backends.base import ServerHandle


class MlcHandle(ServerHandle):
    """Handle for a locally-spawned ``mlc_llm serve`` process."""

    def __init__(
        self,
        base_url: str,
        process: subprocess.Popen[str],
        log_file: Optional[Path] = None,
    ) -> None:
        super().__init__(base_url=base_url)
        self._process = process
        self._log_file = log_file
        self._drain_thread: Optional[threading.Thread] = None

    def _start_drain(self, log_path: Path) -> None:
        """Drain the subprocess stdout into a log file in a background thread.

        This prevents the OS pipe buffer from filling up and blocking the
        server process (the classic subprocess.PIPE deadlock).
        """
        def _drain() -> None:
            try:
                with log_path.open("w", encoding="utf-8") as fh:
                    for line in self._process.stdout:
                        fh.write(line)
                        fh.flush()
            except Exception:
                pass

        t = threading.Thread(target=_drain, daemon=True)
        t.start()
        self._drain_thread = t

    def stop(self) -> None:
        if self._process.poll() is None:
            self._process.send_signal(signal.SIGTERM)
            try:
                self._process.wait(timeout=10)
            except subprocess.TimeoutExpired:
                self._process.kill()
                self._process.wait()
        if self._drain_thread is not None:
            self._drain_thread.join(timeout=5)


def start_mlc(
    *,
    python: Path,
    model_uri: str,
    model_lib: str | None = None,
    device: str = "cuda",
    host: str = "127.0.0.1",
    port: int = 8000,
    mode: str = "local",
    log_dir: Path | None = None,
) -> MlcHandle:
    """Launch ``mlc_llm serve`` as a local subprocess.

    Parameters
    ----------
    python : Path
        Absolute path to the Python binary inside the conda env that has
        ``mlc_llm`` installed (e.g. ``mlc-chat-venv``).
    model_uri : str
        Path to the MLC-compiled model directory (must contain
        ``mlc-chat-config.json``).
    model_lib : str, optional
        Path to the pre-compiled model library (``.so`` file).  When *None*,
        ``mlc_llm`` will try JIT compilation.
    device : str
        Device string, e.g. ``"cuda"`` or ``"cuda:0"``.
    host / port : str / int
        Bind address for the OpenAI-compatible REST server.
    mode : str
        One of ``"local"``, ``"interactive"``, ``"server"``.
    log_dir : Path, optional
        Directory to write server logs into.  Defaults to ``results/``.
    """
    env = os.environ.copy()
    env.setdefault("PYTHONUNBUFFERED", "1")
    cmd = [
        str(python),
        "-m", "mlc_llm", "serve",
        model_uri,
        "--device", device,
        "--host", host,
        "--port", str(port),
        "--mode", mode,
    ]
    if model_lib:
        cmd.extend(["--model-lib", model_lib])
    process = subprocess.Popen(
        cmd,
        stdout=subprocess.PIPE,
        stderr=subprocess.STDOUT,
        text=True,
        env=env,
        bufsize=1,
    )

    # Drain server stdout into a log file so the pipe buffer never fills up.
    if log_dir is None:
        log_dir = Path("results")
    log_dir.mkdir(parents=True, exist_ok=True)
    log_path = log_dir / "mlc_server.log"
    handle = MlcHandle(base_url=f"http://{host}:{port}", process=process, log_file=log_path)
    handle._start_drain(log_path)
    print(f"[mlc] Server logs → {log_path}")
    return handle
