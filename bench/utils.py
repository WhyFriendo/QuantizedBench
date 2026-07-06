from __future__ import annotations

import socket
import time
from dataclasses import dataclass
from pathlib import Path


@dataclass(frozen=True)
class Endpoint:
    host: str
    port: int

    @property
    def base_url(self) -> str:
        return f"http://{self.host}:{self.port}"

    @property
    def chat_url(self) -> str:
        return f"http://{self.host}:{self.port}/v1/chat/completions"


def wait_for_port(host: str, port: int, timeout_s: float = 30.0) -> None:
    deadline = time.time() + timeout_s
    while time.time() < deadline:
        if _port_open(host, port):
            return
        time.sleep(0.2)
    raise TimeoutError(f"Timed out waiting for {host}:{port}")


def _port_open(host: str, port: int) -> bool:
    with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as sock:
        sock.settimeout(0.5)
        return sock.connect_ex((host, port)) == 0


def ensure_dir(path: Path) -> None:
    path.mkdir(parents=True, exist_ok=True)
