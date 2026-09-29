import { CompletionParams } from 'llama.rn';

type BenchmarkRun = {
  INPUT_TOKENS: number;
};

type BenchmarkSettings = {
  WAIT_TIME: number;
  OUTPUT_TOKENS: number;
  RUNS: BenchmarkRun[];
  AUTO_DELETE_MODELS: boolean;
  REPEAT_TIMES?: number;
};

type Config = {
  API_URL: string;
  API_KEY: string;
  VERSION: string;
  MODEL_FAMILIES: Record<string, string[]>;
  BENCHMARK_SETTINGS: BenchmarkSettings;
  CONVERSATION_SETTINGS: CompletionParams;
};

export type { Config, BenchmarkSettings, BenchmarkRun };
