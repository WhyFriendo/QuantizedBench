import { Config } from '../types/Config';

const config: Config = {
  API_URL: 'https://benchmark-collector-worker.whyfriendo.workers.dev',
  // Supply the collector key locally; never commit a production credential.
  API_KEY: '',
  VERSION: '0.0.1-dev',
  MODEL_FAMILIES: {
    'Llama 3.1 8B': [],
    'Llama 3.2 1B': [],
    'Llama 3.2 3B': [],
    'Qwen 3 0.6B': [],
    'Qwen 3 1.7B': [],
    'Qwen 3 4B': [],
    'Qwen 3 8B': [],
    'Phi-4-mini': [],
    'Gemma 3 270M': [],
    'Gemma 3 1B': [],
    'Gemma 3 4B': []
  },
  BENCHMARK_SETTINGS: {
    WAIT_TIME: 30_000,
    OUTPUT_TOKENS: 50,
    RUNS: [
      { INPUT_TOKENS: 32 },
      { INPUT_TOKENS: 64 },
      { INPUT_TOKENS: 128 },
    ],
    AUTO_DELETE_MODELS: true,
  },
  CONVERSATION_SETTINGS: {
    messages: [],
    n_predict: 50,
    grammar: undefined,
    seed: 2137,
    temperature: 0,
    stop: [],
  },
};

export default config;
