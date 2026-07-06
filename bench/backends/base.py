from __future__ import annotations

from dataclasses import dataclass


@dataclass(frozen=True)
class ServerHandle:
    base_url: str

    def stop(self) -> None:
        raise NotImplementedError
