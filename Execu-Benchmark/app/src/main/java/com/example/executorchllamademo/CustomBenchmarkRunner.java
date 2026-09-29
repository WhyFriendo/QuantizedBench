/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.example.executorchllamademo;

import android.content.Context;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.util.Log;
import com.google.gson.Gson;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.pytorch.executorch.extension.llm.LlmCallback;
import org.pytorch.executorch.extension.llm.LlmModule;


public class CustomBenchmarkRunner implements LlmCallback {
    
    public interface BenchmarkCallback {
        void onBenchmarkStarted(String modelName);
        void onModelLoaded(String modelName, boolean success, long loadTimeMs);
        void onInferenceStarted(String modelName, int inputTokens, int runNumber, int totalRuns);
        void onInferenceCompleted(String modelName, int inputTokens, String result, long inferenceTimeMs, double tokensPerSecond, int runNumber, int totalRuns);
        void onMetricsPosted(String modelName, int inputTokens, int runNumber, int totalRuns, boolean success);
        void onResourcesReleased(String modelName);
        void onBenchmarkCompleted(String modelName, int totalRuns);
        // fatal=true means the whole benchmark for this model has stopped (no further callbacks
        // will follow for it); fatal=false means this was a single failed run and the benchmark
        // is continuing on to the next one.
        void onBenchmarkError(String modelName, String error, boolean fatal);
    }
    
    private static final String TAG = "CustomBenchmarkRunner";
    private static final int TARGET_OUTPUT_TOKENS = 50;
    private static final int TOTAL_BENCHMARK_RUNS = 5;
    private static final int INFERENCE_TIMEOUT_SECONDS = 3000;
    
    // API Configuration
    private static final class Config {
        static final String API_URL = BuildConfig.BENCHMARK_API_URL;
        static final String API_KEY = BuildConfig.BENCHMARK_API_KEY;
        static final String VERSION = "ExecutorTorch-1.0.0";
    }
    
    /**
     * Generates a prompt with exactly the specified number of "Hello" tokens.
     * Single 'Hello' is exactly 1 token in Llama 3 tokenizer. So by building a prompt contaning of n - 2 'Hello' tokens, we can generate n tokens.
    */
    private static String generatePrompt(int numTokens) {
        StringBuilder prompt = new StringBuilder();
        for (int i = 0; i < numTokens; i++) {
            prompt.append("Hello");
        }
        return prompt.toString();
    }
    

    
    private Context mContext;
    private String mModelPath;
    private String mTokenizerPath;
    private String mModelName;
    private BenchmarkCallback mCallback;
    private LlmModule mModule;
    private HandlerThread mHandlerThread;
    private Handler mHandler;
    private Gson mGson;
    private Executor mNetworkExecutor;
    
    private int mCurrentRun = 0;
    private int mCurrentTokenCount = 0;
    private int[] mTokenCounts = {32, 64, 128};
    private long mLoadStartTime;
    private long mLoadTimeMs;
    private long mInferenceStartTime;
    private Long mFirstTokenTime = null;
    private long mPromptTokens = 0;
    private int mActualGeneratedTokens = 0;
    private String mStatsJson = null;
    private StringBuilder mGeneratedText = new StringBuilder();
    private boolean mInferenceCompleted = false;
    private CountDownLatch mCompletionLatch;
    private long mStartUsedMemMb;
    private Float mStartBatteryTemp;
    private String mStartThermals;
    private BatteryInfo.BatteryStats mStartBatteryStats;
    private float mStartBatteryLevel;
    
    private List<Long> mRamSpikes = new ArrayList<>();
    private Handler mMetricsHandler;
    private Runnable mMetricsCollector;
    private boolean mCollectingMetrics = false;
    
    public CustomBenchmarkRunner(Context context, String modelPath, String tokenizerPath, String modelName, BenchmarkCallback callback) {
        this.mContext = context;
        this.mModelPath = modelPath;
        this.mTokenizerPath = tokenizerPath;
        this.mModelName = modelName;
        this.mCallback = callback;
        this.mGson = new Gson();
        this.mNetworkExecutor = Executors.newSingleThreadExecutor();
        
        mHandlerThread = new HandlerThread("BenchmarkRunner-" + modelName);
        mHandlerThread.start();
        mHandler = new Handler(mHandlerThread.getLooper());
        
        mMetricsHandler = new Handler(Looper.getMainLooper());
    }
    
    public void runBenchmark() {
        mHandler.post(() -> {
            try {
                mCallback.onBenchmarkStarted(mModelName);
                
                runBenchmarkCycle();
                
            } catch (Exception e) {
                Log.e(TAG, "Benchmark error for " + mModelName, e);
                mCallback.onBenchmarkError(mModelName, e.getMessage(), true);
            }
        });
    }
    
    private void runBenchmarkCycle() {
        if (mCurrentRun >= TOTAL_BENCHMARK_RUNS) {
            mCallback.onBenchmarkCompleted(mModelName, TOTAL_BENCHMARK_RUNS);
            releaseResources();
            return;
        }

        mCurrentRun++;
        mCurrentTokenCount = 0;

        if (mModule == null) {
            // First run only: later runs reuse this same instance instead of reloading, since
            // releasing a loaded LlmModule can never safely happen mid-benchmark (see
            // sLeakedModules) and reloading repeatedly just stacks up native RAM for no benefit.
            loadModel();
        } else {
            mModule.resetContext();
            runNextTokenBenchmark();
        }
    }

    private void runNextTokenBenchmark() {
        if (mCurrentTokenCount >= mTokenCounts.length) {
            runBenchmarkCycle();
            return;
        }
        
        int inputTokens = mTokenCounts[mCurrentTokenCount];
        mCurrentTokenCount++;
        
        runInferenceWithTokens(inputTokens);
    }
    
    private void loadModel() {
        mLoadStartTime = System.currentTimeMillis();
        
        try {
            ModelLoadGuard.begin(mContext, mModelName);
            mModule = new LlmModule(mModelPath, mTokenizerPath, 0.8f);
            int loadResult = mModule.load();
            ModelLoadGuard.finish(mContext);
            
            long loadTime = System.currentTimeMillis() - mLoadStartTime;
            
            if (loadResult == 0) {
                mLoadTimeMs = loadTime;
                mCallback.onModelLoaded(mModelName, true, loadTime);
                
                runNextTokenBenchmark();
            } else {
                Log.e(TAG, "Model loading failed: " + mModelName + " with error code: " + loadResult);

                sLeakedModules.add(mModule);
                mModule = null;
                
                mCallback.onModelLoaded(mModelName, false, loadTime);
                mCallback.onBenchmarkError(mModelName, "Model loading failed with error code: " + loadResult, true);
            }
        } catch (Exception e) {
            ModelLoadGuard.finish(mContext);
            long loadTime = System.currentTimeMillis() - mLoadStartTime;
            Log.e(TAG, "Exception during model loading: " + mModelName, e);
            
            if (mModule != null) {
                sLeakedModules.add(mModule);
                mModule = null;
            }
            
            mCallback.onModelLoaded(mModelName, false, loadTime);
            mCallback.onBenchmarkError(mModelName, "Exception during model loading: " + e.getMessage(), true);
        }
    }
    
    private void runInferenceWithTokens(int inputTokens) {
        mCallback.onInferenceStarted(mModelName, inputTokens, mCurrentRun, TOTAL_BENCHMARK_RUNS);
        
        mStartUsedMemMb = MemoryInfo.getVmRssMb();
        mStartBatteryTemp = TemperatureUtils.getBatteryTemperature(mContext);
        mStartThermals = TemperatureUtils.getThermalInfo();
        mStartBatteryStats = BatteryInfo.getPreciseBatteryStats(mContext);
        mStartBatteryLevel = DeviceInfoUtils.getBatteryLevel(mContext);
        
        startMetricsCollection();
        
        mInferenceStartTime = System.currentTimeMillis();
        mFirstTokenTime = null;
        mActualGeneratedTokens = 0;
        mPromptTokens = 0;
        mStatsJson = null;
        mGeneratedText.setLength(0);
        mInferenceCompleted = false;
        mCompletionLatch = new CountDownLatch(1);
        mStatsJson = null;
        
        try {
            String prompt = generatePrompt(inputTokens-2);
            
            int maxSeqLen = inputTokens + TARGET_OUTPUT_TOKENS;
            
            mModule.generate(prompt, maxSeqLen, this, false);
            
            boolean completed = mCompletionLatch.await(INFERENCE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            
            stopMetricsCollection();
            
            if (!completed) {
                long elapsedTime = System.currentTimeMillis() - mInferenceStartTime;
                String errorMsg = String.format("Inference timed out after %d seconds for model: %s with %d input tokens. Generated %d tokens so far.", 
                    INFERENCE_TIMEOUT_SECONDS, mModelName, inputTokens, mActualGeneratedTokens);
                Log.w(TAG, errorMsg);
                
                try {
                    if (mModule != null) {
                        mModule.stop();
                    }
                } catch (Exception stopException) {
                    Log.e(TAG, "Error stopping model after timeout", stopException);
                }
                
                mCallback.onBenchmarkError(mModelName, errorMsg, false);

                runNextTokenBenchmark();
                return;
            }
            
            long inferenceTime = System.currentTimeMillis() - mInferenceStartTime;
            double tokensPerSecond = calculateActualTPS(inferenceTime);
            
            mCallback.onInferenceCompleted(mModelName, inputTokens, mGeneratedText.toString(), inferenceTime, tokensPerSecond, mCurrentRun, TOTAL_BENCHMARK_RUNS);
            
            collectAndSendMetrics(inputTokens, inferenceTime, tokensPerSecond, mActualGeneratedTokens);
            
        } catch (Exception e) {
            stopMetricsCollection();
            Log.e(TAG, "Exception during inference: " + mModelName, e);
            String errorMsg = "Exception during inference: " + e.getMessage();
            mCallback.onBenchmarkError(mModelName, errorMsg, false);

            runNextTokenBenchmark();
        }
    }
    
    // Retained forever: resetNative()/GC finalization of LlmModule crashes the process with SIGILL
    // (native destructor bug in libexecutorch.so, hit via fbjni's HybridData Dest thread). Once a
    // module is release()'d it must never lose its last strong reference, or GC will eventually
    // finalize it and crash. A single overwritten field only protects the latest module and lets
    // every earlier one become GC-eligible, so this must be an append-only list, not one field.
    private static final List<LlmModule> sLeakedModules = new ArrayList<>();

    private void releaseResources() {
        try {
            if (mModule != null) {
                mModule.stop();
                sLeakedModules.add(mModule);
                mModule = null;
            }
            
            if (mHandlerThread != null) {
                mHandlerThread.quitSafely();
                mHandlerThread = null;
            }
            
            if (mNetworkExecutor != null) {
                if (mNetworkExecutor instanceof java.util.concurrent.ExecutorService) {
                    ((java.util.concurrent.ExecutorService) mNetworkExecutor).shutdown();
                }
            }
            
            mCallback.onResourcesReleased(mModelName);
            
        } catch (Exception e) {
            Log.e(TAG, "Exception during resource cleanup: " + mModelName, e);
            mCallback.onBenchmarkError(mModelName, "Exception during resource cleanup: " + e.getMessage(), true);
        }
    }
    
    @Override
    public void onResult(String result) {
        if (mFirstTokenTime == null) {
            mFirstTokenTime = System.currentTimeMillis();
        }
        
        if (result != null) {
            mGeneratedText.append(result);
        }
    }
    
    @Override
    public void onStats(String stats) {
        mStatsJson = stats;
        
        Log.i(TAG, "=== ExecutorTorch Stats JSON (Complete) ===");
        Log.i(TAG, "Raw JSON: " + stats);
        
        try {
            org.json.JSONObject jsonObject = new org.json.JSONObject(stats);
            
            Log.i(TAG, "=== Parsed ExecutorTorch Stats Fields ===");
            java.util.Iterator<String> keys = jsonObject.keys();
            while (keys.hasNext()) {
                String key = keys.next();
                Object value = jsonObject.get(key);
                Log.i(TAG, String.format("  %s: %s (type: %s)", key, value.toString(), value.getClass().getSimpleName()));
            }
            
            mActualGeneratedTokens = jsonObject.getInt("generated_tokens");
            Log.i(TAG, "=== Key Metrics Extracted ===");
            Log.i(TAG, "Generated tokens: " + mActualGeneratedTokens);
            
            try {
                int inferenceEndMs = jsonObject.getInt("inference_end_ms");
                int promptEvalEndMs = jsonObject.getInt("prompt_eval_end_ms");
                int actualInferenceTimeMs = inferenceEndMs - promptEvalEndMs;
                Log.i(TAG, "Inference end time: " + inferenceEndMs + " ms");
                Log.i(TAG, "Prompt eval end time: " + promptEvalEndMs + " ms");
                Log.i(TAG, "Actual inference time: " + actualInferenceTimeMs + " ms");
            } catch (org.json.JSONException e) {
                Log.w(TAG, "Timing fields not available in stats JSON");
            }
            
            Log.i(TAG, "=== End ExecutorTorch Stats ===");
            
        } catch (org.json.JSONException e) {
            Log.e(TAG, "Failed to parse ExecutorTorch stats JSON: " + stats, e);
            mActualGeneratedTokens = TARGET_OUTPUT_TOKENS;
        }
        
        if (!mInferenceCompleted) {
            mInferenceCompleted = true;
            if (mCompletionLatch != null) {
                mCompletionLatch.countDown();
            }
        }
    }
    
    public void stop() {
        stopMetricsCollection();
        if (mModule != null) {
            mModule.stop();
        }
    }
    
    private double calculateActualTPS(long fallbackInferenceTimeMs) {
        if (mActualGeneratedTokens <= 0) {
            return 0.0;
        }
        
        if (mStatsJson != null) {
            try {
                org.json.JSONObject jsonObject = new org.json.JSONObject(mStatsJson);
                int inferenceEndMs = jsonObject.getInt("inference_end_ms");
                int promptEvalEndMs = jsonObject.getInt("prompt_eval_end_ms");
                int actualInferenceTimeMs = inferenceEndMs - promptEvalEndMs;
                
                if (actualInferenceTimeMs > 0) {
                    double tps = (mActualGeneratedTokens * 1000.0) / actualInferenceTimeMs;
                    Log.d(TAG, String.format("TPS calculated from ExecutorTorch timing: %.2f (tokens: %d, time: %d ms)", 
                        tps, mActualGeneratedTokens, actualInferenceTimeMs));
                    return tps;
                }
            } catch (org.json.JSONException e) {
                Log.w(TAG, "Could not extract precise timing from ExecutorTorch stats, using fallback", e);
            }
        }
        
        if (fallbackInferenceTimeMs > 0) {
            double tps = (mActualGeneratedTokens * 1000.0) / fallbackInferenceTimeMs;
            Log.d(TAG, String.format("TPS calculated from fallback timing: %.2f (tokens: %d, time: %d ms)", 
                tps, mActualGeneratedTokens, fallbackInferenceTimeMs));
            return tps;
        }
        
        return 0.0;
    }
    
    private void startMetricsCollection() {
        mRamSpikes.clear();
        mCollectingMetrics = true;
        
        mMetricsCollector = new Runnable() {
            @Override
            public void run() {
                if (mCollectingMetrics) {
                    try {
                        long usedMem = MemoryInfo.getVmRssMb();
                        mRamSpikes.add(usedMem);
                    } catch (Exception e) {
                    }
                    mMetricsHandler.postDelayed(this, 2000);
                }
            }
        };
        
        mMetricsHandler.post(mMetricsCollector);
    }
    
    private void stopMetricsCollection() {
        mCollectingMetrics = false;
        if (mMetricsCollector != null) {
            mMetricsHandler.removeCallbacks(mMetricsCollector);
        }
    }
    
    private void collectAndSendMetrics(int inputTokens, long inferenceTimeMs, double tokensPerSecond, int outputTokens) {
        mNetworkExecutor.execute(() -> {
            try {
                long endUsedMemMb = MemoryInfo.getVmRssMb();
                Float endBatteryTemp = TemperatureUtils.getBatteryTemperature(mContext);
                String endThermals = TemperatureUtils.getThermalInfo();
                BatteryInfo.BatteryStats endBatteryStats = BatteryInfo.getPreciseBatteryStats(mContext);
                float endBatteryLevel = DeviceInfoUtils.getBatteryLevel(mContext);
                long totalMemoryBytes = DeviceInfoUtils.getTotalMemory(mContext);
                double totalMemoryMb = totalMemoryBytes / 1024.0 / 1024.0;
                
                long ttftMs = (mFirstTokenTime != null) ? (mFirstTokenTime - mInferenceStartTime) : inferenceTimeMs;
                
                Map<String, Object> result = new HashMap<>();
                
                long startMs = mInferenceStartTime;
                long stopMs = mInferenceStartTime + inferenceTimeMs;
                result.put("startMs", startMs);
                result.put("stopMs", stopMs);
                result.put("tps", tokensPerSecond);
                result.put("ttft", ttftMs);
                result.put("inferenceTime", inferenceTimeMs);
                result.put("outputTokens", mActualGeneratedTokens);
                result.put("outputTokens2", outputTokens);
                result.put("inputTokens", inputTokens);
                
                List<Long> ramSeries = new ArrayList<>();
                ramSeries.add(mStartUsedMemMb);
                ramSeries.addAll(mRamSpikes);
                ramSeries.add(endUsedMemMb);
                result.put("ram", ramSeries);
                
                List<Float> battTempSeries = new ArrayList<>();
                battTempSeries.add(mStartBatteryTemp != null ? mStartBatteryTemp : Float.NaN);
                battTempSeries.add(endBatteryTemp != null ? endBatteryTemp : Float.NaN);
                result.put("batteryTempreture", battTempSeries);
                
                List<String> thermalSeries = new ArrayList<>();
                thermalSeries.add(mStartThermals);
                thermalSeries.add(endThermals);
                result.put("sensorTempreratures", thermalSeries);
                
                List<Float> batteryLevels = new ArrayList<>();
                batteryLevels.add(mStartBatteryLevel);
                batteryLevels.add(endBatteryLevel);
                result.put("battery", batteryLevels);
                
                List<BatteryInfo.BatteryStats> batteryInfos = new ArrayList<>();
                batteryInfos.add(mStartBatteryStats);
                batteryInfos.add(endBatteryStats);
                result.put("batteryInfos", batteryInfos);
                
                double startRamUsagePct = totalMemoryMb > 0 ? (mStartUsedMemMb / totalMemoryMb) * 100.0 : 0.0;
                double endRamUsagePct = totalMemoryMb > 0 ? (endUsedMemMb / totalMemoryMb) * 100.0 : 0.0;
                result.put("startRamUsagePct", startRamUsagePct);
                result.put("endRamUsagePct", endRamUsagePct);
                
                boolean success = sendMetricsToServer(inputTokens, result);
                
                mHandler.post(() -> {
                    mCallback.onMetricsPosted(mModelName, inputTokens, mCurrentRun, TOTAL_BENCHMARK_RUNS, success);
                    runNextTokenBenchmark();
                });
                
            } catch (Exception e) {
                Log.e(TAG, "Error collecting/sending metrics for " + mModelName, e);
                mHandler.post(() -> {
                    mCallback.onMetricsPosted(mModelName, inputTokens, mCurrentRun, TOTAL_BENCHMARK_RUNS, false);
                    runNextTokenBenchmark();
                });
            }
        });
    }
    
    private boolean sendMetricsToServer(int inputTokens, Map<String, Object> metricsData) {
        try {
            Map<String, Object> dataObject = new HashMap<>();
            dataObject.put("loadTime", mLoadTimeMs);
            List<Map<String, Object>> runs = new ArrayList<>();
            runs.add(metricsData);
            dataObject.put("runs", runs);
            
            Map<String, Object> phoneData = new HashMap<>();
            phoneData.put("model", DeviceInfoUtils.getModel());
            phoneData.put("brand", DeviceInfoUtils.getBrand());
            phoneData.put("systemName", DeviceInfoUtils.getSystemName());
            phoneData.put("systemVersion", DeviceInfoUtils.getSystemVersion());
            phoneData.put("totalMemory", DeviceInfoUtils.getTotalMemory(mContext));
            
            String family = extractModelFamily(mModelName);
            
            String deviceId = getDeviceId();
            String runId = UUID.randomUUID().toString();
            
            Map<String, Object> payload = new HashMap<>();
            payload.put("userId", deviceId);
            payload.put("model", mModelName);
            payload.put("family", family);
            payload.put("data", mGson.toJson(dataObject));
            payload.put("phoneData", mGson.toJson(phoneData));
            payload.put("version", Config.VERSION);
            payload.put("runId", runId);
            
            String json = mGson.toJson(payload);
            
            URL url = new URL(Config.API_URL + "/saveBenchmark");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(5000); // 5 seconds connect timeout
            conn.setReadTimeout(5000); // 5 seconds read timeout
            conn.setRequestMethod("POST");
            conn.setRequestProperty("X-PROTECT-KEY", Config.API_KEY);
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setDoOutput(true);
            
            conn.getOutputStream().write(json.getBytes(StandardCharsets.UTF_8));
            
            int responseCode = conn.getResponseCode();
            if (responseCode >= 200 && responseCode < 300) {
                String response = "";
                try (java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.InputStreamReader(conn.getInputStream()))) {
                    StringBuilder responseBuilder = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        responseBuilder.append(line);
                    }
                    response = responseBuilder.toString();
                }

                return true;
            } else {
                String errorResponse = "";
                try (java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.InputStreamReader(conn.getErrorStream()))) {
                    StringBuilder errorBuilder = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        errorBuilder.append(line);
                    }
                    errorResponse = errorBuilder.toString();
                } catch (Exception e) {
                    errorResponse = "Could not read error response";
                }
                Log.e(TAG, "Failed to post metrics: HTTP " + responseCode + ", Error: " + errorResponse);
                return false;
            }
            
        } catch (Exception e) {
            Log.e(TAG, "Exception sending metrics to server", e);
            return false;
        }
    }
    
    private String extractModelFamily(String modelId) {
        try {
            if (modelId.toLowerCase().contains("llama")) {
                java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("Llama[_-](\\d+\\.\\d+)[_-](\\d+)B", java.util.regex.Pattern.CASE_INSENSITIVE);
                java.util.regex.Matcher matcher = pattern.matcher(modelId);
                if (matcher.find()) {
                    String version = matcher.group(1);
                    String size = matcher.group(2);
                    return "Llama " + version + " " + size + "B";
                }
                return "Llama";
            }
            return "Unknown";
        } catch (Exception e) {
            return "Unknown";
        }
    }
    
    private String getDeviceId() {
        try {
            android.content.SharedPreferences prefs = mContext.getSharedPreferences("MLCChatPrefs", android.content.Context.MODE_PRIVATE);
            String deviceIdKey = "device_unique_id";
            
            String deviceId = prefs.getString(deviceIdKey, null);
            
            if (deviceId == null) {
                String androidId = android.provider.Settings.Secure.getString(mContext.getContentResolver(), android.provider.Settings.Secure.ANDROID_ID);
                
                if (androidId != null && !androidId.isEmpty() && !androidId.equals("9774d56d682e549c")) {
                    deviceId = androidId;
                } else {
                    deviceId = UUID.randomUUID().toString();
                }
                
                prefs.edit().putString(deviceIdKey, deviceId).apply();
            }
            
            return deviceId;
        } catch (Exception e) {
            Log.e(TAG, "Error getting device ID, using fallback UUID", e);
            return UUID.randomUUID().toString();
        }
    }
}
