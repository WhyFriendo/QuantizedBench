from __future__ import annotations

import os
import signal
import socket
import subprocess
import time
import urllib.error
import urllib.request
from dataclasses import dataclass, field
from typing import Iterable, List, Optional


#set params
@dataclass
class LlamaServerConfig:
	binary: str = "llama-server"
	model_path: str = ""
	host: str = "127.0.0.1"
	port: int = 8080
	n_gpu_layers: int = 99
	context_size: int = 4096
	extra_args: List[str] = field(default_factory=list)


class LlamaServerWrapper:
	def __init__(self, config: LlamaServerConfig) -> None:
		if not config.model_path:
			raise ValueError("model_path is required")
		self.config = config
		self._process: Optional[subprocess.Popen[str]] = None

	def build_command(self) -> List[str]:
		cfg = self.config
		cmd = [
			cfg.binary,
			"-m",
			cfg.model_path,
			"--host",
			cfg.host,
			"--port",
			str(cfg.port),
			"-ngl",
			str(cfg.n_gpu_layers),
			"-c",
			str(cfg.context_size),
		]
		cmd.extend(cfg.extra_args)
		return cmd

	def start(self, *, wait: bool = True, timeout_s: float = 30.0) -> None:
		if self._process and self._process.poll() is None:
			raise RuntimeError("llama-server is already running")

		cmd = self.build_command()
		self._process = subprocess.Popen(
			cmd,
			stdout=subprocess.DEVNULL,
			stderr=subprocess.DEVNULL,
		)

		if wait:
			self._wait_for_port(timeout_s=timeout_s)
			# An open port only means llama-server is accepting TCP
			# connections, not that it's actually ready to serve inference
			# (observed: 503s on the first burst of real requests even
			# after the port opened). Poll /health until it reports ready.
			self._wait_for_health(timeout_s=timeout_s)

	def stop(self, *, timeout_s: float = 10.0) -> None:
		if not self._process:
			return
		if self._process.poll() is None:
			self._process.send_signal(signal.SIGTERM)
			try:
				self._process.wait(timeout=timeout_s)
			except subprocess.TimeoutExpired:
				self._process.kill()
		self._process = None

	def is_running(self) -> bool:
		return self._process is not None and self._process.poll() is None

	def _wait_for_port(self, *, timeout_s: float) -> None:
		deadline = time.time() + timeout_s
		while time.time() < deadline:
			if self._port_open(self.config.host, self.config.port):
				return
			if self._process and self._process.poll() is not None:
				raise RuntimeError("llama-server exited before opening port")
			time.sleep(0.2)
		raise TimeoutError("timed out waiting for llama-server to open port")

	@staticmethod
	def _port_open(host: str, port: int) -> bool:
		with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as sock:
			sock.settimeout(0.5)
			return sock.connect_ex((host, port)) == 0

	def _wait_for_health(self, *, timeout_s: float) -> None:
		url = f"http://{self.config.host}:{self.config.port}/health"
		deadline = time.time() + timeout_s
		while time.time() < deadline:
			try:
				with urllib.request.urlopen(url, timeout=2) as resp:
					if resp.status == 200:
						return
			except (urllib.error.URLError, OSError):
				pass
			if self._process and self._process.poll() is not None:
				raise RuntimeError("llama-server exited before becoming healthy")
			time.sleep(0.5)
		raise TimeoutError("timed out waiting for llama-server to become healthy")

	def __enter__(self) -> "LlamaServerWrapper":
		self.start()
		return self

	def __exit__(self, exc_type, exc, tb) -> None:
		self.stop()


def _parse_extra_args(extra: Optional[Iterable[str]]) -> List[str]:
	if not extra:
		return []
	return list(extra)


def run_llama_server(
	model_path: str,
	*,
	host: str = "127.0.0.1",
	port: int = 8080,
	n_gpu_layers: int = 99,
	context_size: int = 4096,
	extra_args: Optional[Iterable[str]] = None,
) -> LlamaServerWrapper:
	config = LlamaServerConfig(
		model_path=model_path,
		host=host,
		port=port,
		n_gpu_layers=n_gpu_layers,
		context_size=context_size,
		extra_args=_parse_extra_args(extra_args),
	)
	wrapper = LlamaServerWrapper(config)
	wrapper.start()
	return wrapper
