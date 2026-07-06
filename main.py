import time

import llama_server_wrapper


MODEL_PATH = "/home/friendo/Desktop/QuantizedBench/gguf_models/Qwen3_5/Qwen_Qwen3.5-0.8B-IQ2_M.gguf"


def main() -> None:
    print("Starting llama-server...")
    wrapper = llama_server_wrapper.run_llama_server(
        model_path=MODEL_PATH,
        host="127.0.0.1",
        port=8080,
        n_gpu_layers=99,
        context_size=4096,
    )
    print("llama-server is running. Press Ctrl+C to stop.")

    try:
        while True:
            time.sleep(1.0)
    except KeyboardInterrupt:
        pass
    finally:
        wrapper.stop()


if __name__ == "__main__":
    main()
