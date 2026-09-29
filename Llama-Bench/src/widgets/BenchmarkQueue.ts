import { LlmModelBenchmarking, RunMetrics } from './BenchmarkContainer';
import { CloudflareAPI } from '../hooks/useCloudflare';
import { SQLiteContextType } from '../contexts/SQLiteContext';
import { calculateSha256, checkFileExists, downloadFile, getLocalPath, removeFile } from '../fsUtils';
import { CompletionParams, initLlama, LlamaContext, loadLlamaModelInfo, NativeCompletionResult, TokenData } from 'llama.rn';

import { getMetrics, getPhoneInfo, getUsedRam } from '../metrics';
import { stopDownload } from 'react-native-fs';
import seedrandom from 'seedrandom';
import { Alert, Platform, NativeModules } from 'react-native';
import { Config } from '../types/Config';
import { isInternetConnected } from '../checkInternetConnection';

const { AppControlModule } = NativeModules;



interface BenchmarkCallbacks {
  startBenchmarking: () => void;
  stopBenchmarking: () => void;
  setBenchmarkingModels: React.Dispatch<React.SetStateAction<LlmModelBenchmarking[]>>
  getLlamaContext: () => LlamaContext;
  setLlamaContext: (llamaContext: LlamaContext) => void;
  releaseLlamaContext: () => Promise<void>;
  releaseSharedContext: () => Promise<void>;
  onCancelingStarted: () => void;
  onCancelingFinished: () => void;
}

const deepCopy = <T extends any>(obj: T): T => JSON.parse(JSON.stringify(obj));

export class BenchmarkQueue {
  private queue: LlmModelBenchmarking[] = [];
  private isProcessing = false;
  private isCanceling = false;
  private needsRestart = false;
  private restartReason = '';

  private currentModel: LlmModelBenchmarking | null = null;
  private cancelQueue: LlmModelBenchmarking[] = [];

  constructor(
    private sqlite: SQLiteContextType,
    private cloudflare: CloudflareAPI,
    private callbacks: BenchmarkCallbacks,
    private config: Config,
  ) {
  }

  public async restorePendingBenchmarks() {
    try {
      const benchmarkState = await this.sqlite.getBenchmarkState();
      if (benchmarkState && benchmarkState.isBenchmarking) {
        if (benchmarkState.needsRestart) {
          // Restart was triggered by us as part of the benchmarking process, resume automatically
          await this.restoreBenchmarkState(benchmarkState);
        } else {
          this.showContinueBenchmarkingModal(benchmarkState);
        }
      }
    } catch (error) {
      console.error('Error checking for pending benchmarks:', error);
    }
  }

  private showContinueBenchmarkingModal(benchmarkState: any) {
    Alert.alert(
      'Continue Benchmarking?',
      'The app was restarted during the benchmarking process. Would you like to continue where you left off?',
      [
        {
          text: 'Stop',
          onPress: async () => {
            try {
              await this.sqlite.clearBenchmarkState();
              console.log('Benchmarking cancelled by user after restart');
            } catch (error) {
              console.error('Error clearing benchmark state:', error);
            }
          },
          style: 'cancel',
        },
        {
          text: 'Continue',
          onPress: async () => {
            try {
              await this.restoreBenchmarkState(benchmarkState);
            } catch (error) {
              console.error('Error restoring benchmark state:', error);
              await this.sqlite.clearBenchmarkState();
            }
          },
        },
      ]
    );
  }

  public static extractFamilyFromBenchmarkState(benchmarkState: any): string | null {
    try {
      if (benchmarkState.currentModelData) {
        const currentModel = JSON.parse(benchmarkState.currentModelData);
        if (currentModel && currentModel.model && currentModel.model.family) {
          return currentModel.model.family;
        }
      }
      
      if (benchmarkState.queueData) {
        const queue = JSON.parse(benchmarkState.queueData);
        if (queue && queue.length > 0 && queue[0].model && queue[0].model.family) {
          return queue[0].model.family;
        }
      }
      
      return null;
    } catch (error) {
      console.error('Error extracting family from benchmark state:', error);
      return null;
    }
  }

  private async restoreBenchmarkState(benchmarkState: any) {
    try {
      if (benchmarkState.queueData) {
        this.queue = JSON.parse(benchmarkState.queueData);
      }
      
      if (benchmarkState.currentModelData) {
        this.currentModel = JSON.parse(benchmarkState.currentModelData);
        
        if (this.currentModel && this.currentModel.status === 'waiting-restart') {
          if (benchmarkState.restartReason === 'after downloading to prepare for benchmarking') {
            this.currentModel.status = 'loading';
          } else if (benchmarkState.restartReason === 'before benchmarking to ensure clean memory state') {
            this.currentModel.status = 'loading';
          } else if (benchmarkState.restartReason === 'between benchmark runs to ensure clean memory state') {
            this.currentModel.status = 'benchmarking';
          } else {
            this.currentModel.status = 'loading';
          }
          
          // Put current model back at the front of the queue
          this.queue.unshift(this.currentModel);
          this.currentModel = null;
        }
      }

      // Update UI to show restored models as queued
      if (this.queue.length > 0) {
        this.updateViewState(this.queue);
      }

      // Clear the stored state since we're resuming
      await this.sqlite.clearBenchmarkState();

      this.needsRestart = false;
      this.restartReason = '';

      this.processQueue();
    } catch (error) {
      console.error('Error restoring benchmark state:', error);
      await this.sqlite.clearBenchmarkState();
    }
  }

  private async saveBenchmarkState() {
    try {
      const queueData = JSON.stringify(this.queue);
      const currentModelData = this.currentModel ? JSON.stringify(this.currentModel) : '';
      
      await this.sqlite.saveBenchmarkState(
        queueData,
        currentModelData,
        this.isProcessing,
        this.needsRestart,
        this.restartReason
      );
    } catch (error) {
      console.error('Error saving benchmark state:', error);
    }
  }

  addToQueue(models: LlmModelBenchmarking | LlmModelBenchmarking[]) {
    if (!Array.isArray(models)) {
      models = [models];
    }

    models = deepCopy(models);

    for (const model of models) {
      this.resetModelState(model);
      model.status = 'waiting';
      this.queue.push(model);
    }
    this.updateViewState(models);
    this.processQueue();
  }

  isRunning() {
    return this.isProcessing;
  }

  cancel() {
    if (!this.isProcessing) {return;}
    this.isCanceling = true;
    this.cancelQueue = [...this.queue];
    this.queue = [];
    this.callbacks.onCancelingStarted();
    if (this.currentModel) {this.forceStop(this.currentModel);}
  }

  size() {
    return this.queue.length;
  }

  private async processQueue() {
    if (this.isProcessing || this.queue.length === 0) {return;}
    this.isProcessing = true;
    this.callbacks.startBenchmarking();
    this.callbacks.releaseSharedContext();

    await this.saveBenchmarkState();

    while (this.queue.length > 0 && !this.isCanceling) {
      const model = this.queue.shift();
      if (!model) {continue;}

      this.currentModel = model;
      
      if (model.status === 'waiting' || model.status === 'stale') {
        model.status = 'downloading';
      }

      try {
        await this.benchmarkModel(model);

        if (this.needsRestart) {
          await this.handleRestart();
          return; // Exit processing, will resume after restart
        }

        if (this.queue.length > 0 && !this.isCanceling) {
          await this.wait(this.config.BENCHMARK_SETTINGS.WAIT_TIME);
        }

      } catch (error) {
        console.log('error occured during benchmarking', error);
        await this.onError(model);
      }

      this.currentModel = null;
    }

    for (const model of this.cancelQueue) {
      await this.resetModelState(model);
    }
    if (this.cancelQueue.length > 0) {
      this.updateViewState(this.cancelQueue);
      this.cancelQueue = [];
    }

    if (this.isCanceling) {
      this.callbacks.onCancelingFinished();
    }

    this.isCanceling = false;
    this.isProcessing = false;
    this.callbacks.stopBenchmarking();
    
    await this.sqlite.clearBenchmarkState();
  }

  private async handleRestart() {
    await this.saveBenchmarkState();

    console.log('Restart required:', this.restartReason);

    try {
      await AppControlModule.restartApp();
    } catch (error) {
      console.error('Error restarting app:', error);
      Alert.alert(
        'Manual Restart',
        'Please manually close and restart the application to continue.',
        [{ text: 'OK' }]
      );
    }
  }

  private async benchmarkModel(model: LlmModelBenchmarking) {
    if (model.status === 'downloading' || model.status === 'waiting' || model.status === 'stale') {
      await this.handleDownloading(model);
      if (this.needsRestart) return;
    }
    
    if (model.status === 'post-download' || model.status === 'pre-benchmark') {
      await this.handlePostDownloadRestart(model);
      if (this.needsRestart) return;
    }
    
    if (typeof this.config.BENCHMARK_SETTINGS.REPEAT_TIMES === 'number') {
      const repeatTimes = Math.max(1, this.config.BENCHMARK_SETTINGS.REPEAT_TIMES ?? 1);
      for (let i = 0; i < repeatTimes && !this.isCanceling; i++) {
        console.log('running cycle', i+1, 'of', repeatTimes);
        model.status = 'loading';
        await this.handleLoading(model);
        if (this.needsRestart || this.isCanceling) return;
        
        await this.handleBenchmarking(model);
        if (this.needsRestart || this.isCanceling) return;
        
        await this.handleSaving(model);
        if (this.needsRestart || this.isCanceling) return;
        
        await this.callbacks.releaseLlamaContext();
      }
      model.status = 'cleaning';
      await this.handleCleaning(model);
      if (this.needsRestart) return;
      await this.handleError(model);
      await this.handleCanceling(model);
      return;
    }
    
    if (model.status === 'loading') {
      await this.handleLoading(model);
      if (this.needsRestart) return;
    }
    
    if (model.status === 'benchmarking') {
      await this.handleBenchmarking(model);
      if (this.needsRestart) return;
    }
    
    if (model.status === 'saving') {
      await this.handleSaving(model);
      if (this.needsRestart) return;
    }
    
    if (model.status === 'cleaning') {
      await this.handleCleaning(model);
      if (this.needsRestart) return;
    }
    
    await this.handleError(model);
    await this.handleCanceling(model);
  }

  private async handleDownloading(model: LlmModelBenchmarking) {
    if (model.status !== 'downloading' || this.isCanceling) {return;}
    console.log('handle downloading...', model.model.name);

    try {
      const modelPath = getLocalPath(model.model.filename);
      console.log('model path', modelPath);
      const alreadyDownloaded = await checkFileExists(model.model.filename);
      console.log('already downloaded', alreadyDownloaded);
      let checksumMismatch = false;
      let wasActuallyDownloaded = false;

      if (alreadyDownloaded) {
        const sha256 = await calculateSha256(modelPath);
        console.log('sha256', sha256);
        console.log('model sha256', model.model.sha256);
        if (sha256 !== model.model.sha256) {
          checksumMismatch = true;
          await this.removeModel(model);
        }
      }

      if (!alreadyDownloaded || checksumMismatch) {
        console.log('downloading file');
        const result = await downloadFile(
          model.model.url,
          model.model.filename,
          (progress) => {
            model.wasDownloaded = true;
            model.isDownloading = true;
            model.downloadingPercent = progress;
            this.updateViewState(model);
          },
          (jobId) => {
            model.jobId = jobId;
            this.updateViewState(model);
          }
        );

        model.isDownloading = false;
        wasActuallyDownloaded = true;

        if (result.status === 'failed' && !this.isCanceling) {
          console.log('download failed', model.model.name);
          model.status = 'error';
          this.updateViewState(model);
          return;
        }

        if (!this.isCanceling) {
          const downloadedSha256 = await calculateSha256(modelPath);
          console.log('downloaded file sha256', downloadedSha256);
          console.log('expected sha256', model.model.sha256);
          if (downloadedSha256 !== model.model.sha256) {
            console.log('downloaded file failed checksum verification', model.model.name);
            await this.removeModel(model);
            model.wasDownloaded = false;
            model.status = 'error';
            this.updateViewState(model);
            return;
          }
        }
      }

      if (wasActuallyDownloaded) {
        model.status = 'post-download';
      } else {
        model.status = 'pre-benchmark';
      }
    }
    catch (err) {
      model.status = 'error';
      console.log('error occured during downloading', err);
    }

    this.updateViewState(model);
  }

  private async handlePostDownloadRestart(model: LlmModelBenchmarking) {
    if ((model.status !== 'post-download' && model.status !== 'pre-benchmark') || this.isCanceling) {return;}
    console.log('handle post-download restart...', model.model.name);

    if (model.status === 'post-download') {
      this.needsRestart = true;
      this.restartReason = 'after downloading to prepare for benchmarking';
      model.status = 'waiting-restart';
    } else if (model.status === 'pre-benchmark') {
      this.needsRestart = true;
      this.restartReason = 'before benchmarking to ensure clean memory state';
      model.status = 'waiting-restart';
    }
    
    this.updateViewState(model);
  }

  private async handleLoading(model: LlmModelBenchmarking) {
    if (model.status !== 'loading' || this.isCanceling) {return;}
    console.log('handle loading...', model.model.name);

    try {
      await this.callbacks.releaseLlamaContext();
      const modelPath = getLocalPath(model.model.filename);

      if (this.isCanceling) {return;}

      const loadStart = Date.now();

      const modelInfo = await loadLlamaModelInfo(modelPath);

      console.log('model info loaded?', !!modelInfo);

      if (!modelInfo) {
        throw new Error('Failed to load model info');
      }

      if (this.isCanceling) {return;}

      // Gemma 3+ models use Sliding Window Attention (iSWA), which is
      // incompatible with context shifting in llama.cpp. Without explicitly
      // disabling ctx_shift and enabling swa_full, context initialization
      // crashes at the native level with an unhelpful "Unknown error".
      const isGemmaFamily = model.model.family.toLowerCase().startsWith('gemma');
      const newContext = await initLlama({
          model: modelPath,
          use_mlock: true,
          n_gpu_layers: 0,
          n_ctx: 4096,
          ...(isGemmaFamily ? { ctx_shift: false, swa_full: true } : {}),
      });

      const loadEnd = Date.now();
      const loadTime = loadEnd - loadStart;

      model.status = 'benchmarking';
      model.data = { loadTime, runs: [] };
      this.callbacks.setLlamaContext(newContext);
    } catch (err) {
      model.status = 'error';
      console.log('error occured during model loading', err);
    }

    this.updateViewState(model);
  }

  private async handleBenchmarking(model: LlmModelBenchmarking) {
    if (model.status !== 'benchmarking' || this.isCanceling) {return;}
    console.log('handle benchmarking...', model.model.name);

    // Destructure out `messages` to prevent llama.rn from entering the chat
    // template formatting path. Empty arrays are truthy in JS, so spreading
    // `messages: []` from CONVERSATION_SETTINGS would cause the library to
    // attempt Jinja chat formatting with zero messages — which fails on models
    // like Qwen 3.5 whose templates require at least one message.
    // Benchmarking uses a raw `prompt` instead.
    const { messages: _ignoredMessages, ...conversationSettingsWithoutMessages } = this.config.CONVERSATION_SETTINGS;
    const baseConversationConfig = {
      ...conversationSettingsWithoutMessages,
      n_predict: this.config.BENCHMARK_SETTINGS.OUTPUT_TOKENS,
    };

    // Own copy: mutating this must not touch `model.data.runs` until the loop
    // finishes, otherwise a mid-loop failure leaves `model.data` looking like
    // it has results that were never actually persisted (see handleSaving).
    const runResults: RunMetrics[] = deepCopy(model.data?.runs || []);
    let runCounter = runResults.length;
    let fatalError: unknown = null;

    try {
      const llamaContext = this.callbacks.getLlamaContext();

      const remainingRuns = this.config.BENCHMARK_SETTINGS.RUNS.slice(runCounter);

      for (const run of remainingRuns) {
        if (this.isCanceling) {
          break;
        }

        console.log('starting run', runCounter, 'with settings: ', run.INPUT_TOKENS, 'output tokens: ', this.config.BENCHMARK_SETTINGS.OUTPUT_TOKENS);

        // Each run's inference is independent (different input size). A
        // failure on one run (e.g. a transient native completion error)
        // must not prevent the remaining runs from being attempted, and
        // must not discard runs that already succeeded.
        try {
          let firstTokenReceivedAt: number | null = null;
          let outputTokens = 0;
          const onTokenCallback = (data: TokenData) => {
            outputTokens += 1;
            if (firstTokenReceivedAt === null) {
              firstTokenReceivedAt = Date.now();
            }
          };

          const runMetricsStart = await getMetrics();

          const numTokens = run.INPUT_TOKENS;
          const tokens = this.generateRandomTokens(numTokens, 100, model.model.family + String(numTokens));
          const prompt = await llamaContext.detokenize(tokens);

          const conversationConfig = {
            ...baseConversationConfig,
            prompt: prompt,
          };

          const ramSpikes: number[] = [];

          const ramCollectorInterval = setInterval(() => {
            getUsedRam().then((usedRam) => {
              ramSpikes.push(usedRam);
            }).catch(() => {});
          }, 2000);

          const inferenceStart = Date.now();
          console.log('Sending config to completion: ', JSON.stringify(conversationConfig));
          let completionResult: NativeCompletionResult;
          try {
            completionResult = await this.noChatTemplateCompletion(llamaContext, conversationConfig, onTokenCallback);
          } finally {
            clearInterval(ramCollectorInterval);
          }
          const inferenceEnd = Date.now();

          if (this.isCanceling) {return;}

          const runMetricsEnd = await getMetrics();

          const tokensPerSecond = completionResult.timings.predicted_per_second;
          const timeToFirstToken = firstTokenReceivedAt !== null ? firstTokenReceivedAt - inferenceStart : -1;
          const inferenceTime = inferenceEnd - inferenceStart;

          const runMetrics: RunMetrics = {
            runId: runCounter + 1,
            inputTokens: numTokens,
            outputTokens: outputTokens,
            tps: tokensPerSecond,
            ttft: timeToFirstToken,
            inferenceTime: inferenceTime,
            ram: [runMetricsStart.usedMemory, ...ramSpikes, runMetricsEnd.usedMemory],
            batteryTempreture: [runMetricsStart.batteryTemperature, runMetricsEnd.batteryTemperature],
            sensorTempreratures: [runMetricsStart.allTemperatures, runMetricsEnd.allTemperatures],
            battery: [runMetricsStart.batteryLevel, runMetricsEnd.batteryLevel],
            batteryInfos: [runMetricsStart.batteryInfos, runMetricsEnd.batteryInfos],
          };

          runResults.push(runMetrics);
        } catch (runErr) {
          console.log(`error occurred during run ${runCounter} (input tokens: ${run.INPUT_TOKENS})`, runErr);
        }

        runCounter++;
      }
    } catch (err) {
      fatalError = err;
      console.log('error occurred during benchmarking', err);
    }

    if (runResults.length > 0) {
      model.status = 'saving';
      model.data = {
        ...model.data,
        runs: runResults,
      };
    } else if (fatalError !== null || runCounter > 0) {
      model.status = 'error';
    }

    this.updateViewState(model);
  }

  private async handleSaving(model: LlmModelBenchmarking) {
    if (model.status !== 'saving' || this.isCanceling) {return;}
    console.log('handle saving...', model.model.name);

    try {
      const userId = (await this.sqlite.selectUserData())[0].id;
      const dataJson = JSON.stringify(model.data);
      await this.sqlite.deleteBenchmarkByModelAndFamilyName(model.model.family, model.model.name);
      await this.sqlite.insertBenchmark(model.model.family, model.model.name, dataJson, 'completed');
      const phoneData = await getPhoneInfo();
      const phoneDataJson = JSON.stringify(phoneData);

      const isConnected = await isInternetConnected();
      if (!isConnected) {
        Alert.alert(
          'No Internet Connection',
          'Please connect to the internet to sync your results.',
          [{ text: 'OK', onPress: () => {} }]
        );
      }

      const result = await this.cloudflare.saveBenchmark(userId, model.model.name, model.model.family, dataJson, phoneDataJson);

      if (!result) {
        throw new Error('Failed to save benchmark');
      }

      model.status = 'cleaning';
    }
    catch (err) {
      model.status = 'error';
      console.log('Error saving benchmark:', err);
    }

    this.updateViewState(model);
  }

  private async handleCleaning(model: LlmModelBenchmarking) {
    if (model.status !== 'cleaning' || this.isCanceling) {return;}
    console.log('handle cleaning...', model.model.name);

    try {
      this.callbacks.releaseLlamaContext();
      if (model.wasDownloaded && this.config.BENCHMARK_SETTINGS.AUTO_DELETE_MODELS) {
        await this.removeModel(model);
        model.wasDownloaded = false;
      }

      model.status = 'completed';
    }
    catch (err)
    {
      model.status = 'error';
      console.log('error occured during cleaning', err);
    }

    this.updateViewState(model);
  }

  private async handleError(model: LlmModelBenchmarking) {
    if (model.status !== 'error') {return;}
    await this.onError(model);
  }

  private async onError(model: LlmModelBenchmarking) {
    console.log('on error...', model.model.name);
    try {
      await this.sqlite.deleteBenchmarkByModelAndFamilyName(model.model.family, model.model.name);
      await this.sqlite.insertBenchmark(model.model.family, model.model.name, '{}', 'error');

      await this.callbacks.releaseLlamaContext();

      if (model.wasDownloaded && this.config.BENCHMARK_SETTINGS.AUTO_DELETE_MODELS) {
        await this.removeModel(model);
      }
    }
    catch (err) {}

    if (this.config.BENCHMARK_SETTINGS.AUTO_DELETE_MODELS) {
      model.wasDownloaded = false;
    }
    model.status = 'post-error';

    this.updateViewState(model);
  }

  private async handleCanceling(model: LlmModelBenchmarking) {
    if (!this.isCanceling) {
      return;
    }

    if (model.wasDownloaded && this.config.BENCHMARK_SETTINGS.AUTO_DELETE_MODELS) {
      await this.removeModel(model);
    }

    this.resetModelState(model);
    this.updateViewState(model);
  }

  private async forceStop(model: LlmModelBenchmarking) {
    if (model.status === 'downloading' && model.jobId !== undefined) {
      stopDownload(model.jobId);
    }

    try {
      const llamaContext = this.callbacks.getLlamaContext();
      await llamaContext.stopCompletion();
      await this.callbacks.releaseLlamaContext();
    }
    catch (err) {}
  }

  private resetModelState(model: LlmModelBenchmarking) {
    model.status = 'stale';
    model.wasBenchmarked = false;
    model.wasDownloaded = false;
    model.isDownloading = false;
    model.downloadingPercent = 0;
    model.data = undefined;
    model.jobId = undefined;
  }

  private updateViewState(modelsToUpdate: LlmModelBenchmarking | LlmModelBenchmarking[]) {
    if (!Array.isArray(modelsToUpdate)) {
      modelsToUpdate = [modelsToUpdate];
    }

    this.callbacks.setBenchmarkingModels(models => {
      return models.map((m) => {
        const updatedModel = modelsToUpdate.find(update => update.model.name === m.model.name);
        return updatedModel ?? m;
      });
    });
  }

  private async removeModel(model: LlmModelBenchmarking) {
    const modelPath = getLocalPath(model.model.filename);
    await removeFile(modelPath);
  }

  private generateRandomTokens(n: number, MAX_TOKEN: number, seed?: string): number[] {
    const rng = seedrandom(seed);
    return Array.from({ length: n }, () => Math.floor(rng() * MAX_TOKEN) + 1000);
  }

  private async noChatTemplateCompletion(
    llamaContext: LlamaContext,
    params: CompletionParams,
    callback?: (data: TokenData) => void,
  ): Promise<NativeCompletionResult> {
    return llamaContext.completion(params, callback);
  }

  removeFromQueue(model: LlmModelBenchmarking) {
    const index = this.queue.findIndex(m => m.model.name === model.model.name);

    if (index !== -1) {
      const removedModel = this.queue.splice(index, 1)[0];
      this.resetModelState(removedModel);
      this.updateViewState(removedModel);
    }
  }

  private wait(ms: number): Promise<void> {
    return new Promise(resolve => setTimeout(resolve, ms));
  }
}
