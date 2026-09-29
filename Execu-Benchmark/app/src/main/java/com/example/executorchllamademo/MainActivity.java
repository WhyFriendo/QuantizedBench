/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.example.executorchllamademo;

import android.app.ActivityManager;
import android.app.AlarmManager;
import android.app.AlertDialog;
import android.app.PendingIntent;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.system.ErrnoException;
import android.system.Os;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ListView;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class MainActivity extends AppCompatActivity implements CustomBenchmarkRunner.BenchmarkCallback {
  private ListView mModelsListView;
  private ModelListAdapter mModelListAdapter;
  private List<BenchmarkModel> mModels;
  private ImageButton mSettingsButton;
  private Button mRunAllButton;
  private TextView mMemoryView;
  private DemoSharedPreferences mDemoSharedPreferences;
  private Handler mMemoryUpdateHandler;
  private Runnable memoryUpdater;
  private CustomBenchmarkRunner mCurrentBenchmarkRunner = null;


  @Override
  public void onBenchmarkStarted(String modelName) {
    runOnUiThread(() -> {
      updateModelStatus(modelName, BenchmarkModel.BenchmarkStatus.LOADING);
      ETLogging.getInstance().log("Benchmark started for model: " + modelName);
    });
  }

  @Override
  public void onModelLoaded(String modelName, boolean success, long loadTimeMs) {
    runOnUiThread(() -> {
      if (success) {
        updateModelStatus(modelName, BenchmarkModel.BenchmarkStatus.RUNNING);
        ETLogging.getInstance().log("Model loaded successfully: " + modelName + " in " + loadTimeMs + "ms");
      } else {
        updateModelStatus(modelName, BenchmarkModel.BenchmarkStatus.ERROR);
        ETLogging.getInstance().log("Model loading failed: " + modelName);
      }
    });
  }

  @Override
  public void onInferenceStarted(String modelName, int inputTokens, int runNumber, int totalRuns) {
    runOnUiThread(() -> {
      updateModelStatus(modelName, BenchmarkModel.BenchmarkStatus.RUNNING);
      String logMessage = String.format("Inference started for %s: %d tokens (run %d/%d)", 
          modelName, inputTokens, runNumber, totalRuns);
      ETLogging.getInstance().log(logMessage);
    });
  }

  @Override
  public void onInferenceCompleted(String modelName, int inputTokens, String result, long inferenceTimeMs, double tokensPerSecond, int runNumber, int totalRuns) {
    runOnUiThread(() -> {
      String logMessage = String.format("Inference completed for %s: %d tokens, %dms, %.2f tokens/sec (run %d/%d)", 
          modelName, inputTokens, inferenceTimeMs, tokensPerSecond, runNumber, totalRuns);
      ETLogging.getInstance().log(logMessage);
    });
  }
  
  @Override
  public void onMetricsPosted(String modelName, int inputTokens, int runNumber, int totalRuns, boolean success) {
    runOnUiThread(() -> {
      String status = success ? "posted successfully" : "failed to post";
      String logMessage = String.format("Metrics %s for %s: %d tokens (run %d/%d)", 
          status, modelName, inputTokens, runNumber, totalRuns);
      ETLogging.getInstance().log(logMessage);
    });
  }
  
  @Override
  public void onBenchmarkCompleted(String modelName, int totalRuns) {
    runOnUiThread(() -> {
      updateModelStatus(modelName, BenchmarkModel.BenchmarkStatus.COMPLETED);
      String logMessage = String.format("Benchmark completed for %s: %d total runs", modelName, totalRuns);
      ETLogging.getInstance().log(logMessage);

      mDemoSharedPreferences.markModelCompleted(modelName);
      deleteModelFiles(modelName);
      advanceRunAllQueueAndRestart(modelName);
    });
  }

  private boolean deleteModelFiles(String modelName) {
    String basePath = getExternalFilesDir(null).getAbsolutePath() + "/llama/";
    for (BenchmarkModel m : mModels) {
        if (m.getFilename().equals(modelName)) {
            java.io.File modelFile = new java.io.File(basePath + m.getFilename());
            java.io.File tokenizerFile = new java.io.File(basePath + m.getTokenizerPath());
            boolean modelDeleted = !modelFile.exists() || modelFile.delete();
            boolean tokenizerDeleted = !tokenizerFile.exists() || tokenizerFile.delete();
            boolean success = modelDeleted && tokenizerDeleted;
            ETLogging.getInstance().log((success ? "Deleted files for " : "Could not delete all files for ")
                + modelName);
            return success;
        }
    }
    return false;
  }

  private void confirmDeleteModel(BenchmarkModel model) {
    if (mCurrentBenchmarkRunner != null || isAnyModelDownloading()) {
      new AlertDialog.Builder(this)
          .setMessage("Wait for the current benchmark or download to finish before deleting files.")
          .setPositiveButton("OK", null)
          .show();
      return;
    }
    new AlertDialog.Builder(this)
        .setTitle(R.string.delete_model_title)
        .setMessage(R.string.delete_model_message)
        .setNegativeButton("Cancel", null)
        .setPositiveButton(R.string.delete_model_files, (dialog, which) -> removeModelFiles(model))
        .show();
  }

  private boolean isAnyModelDownloading() {
    for (BenchmarkModel model : mModels) {
      if (model.getStatus() == BenchmarkModel.BenchmarkStatus.DOWNLOADING) {
        return true;
      }
    }
    return false;
  }

  private void removeModelFiles(BenchmarkModel model) {
    if (mDemoSharedPreferences.isRunAllActive()) {
      mDemoSharedPreferences.clearRunAllState();
      updateRunAllButtonState();
    }
    java.io.File modelFile = new java.io.File(
        new java.io.File(getExternalFilesDir(null), "llama"), model.getFilename());
    if (!modelFile.exists() || modelFile.delete()) {
      mDemoSharedPreferences.clearModelCompleted(model.getFilename());
      model.setStatus(BenchmarkModel.BenchmarkStatus.NOT_DOWNLOADED);
      mModelListAdapter.notifyDataSetChanged();
      ETLogging.getInstance().log("Deleted model file: " + model.getFilename());
    } else {
      new AlertDialog.Builder(this)
          .setMessage(R.string.delete_model_failed)
          .setPositiveButton("OK", null)
          .show();
    }
  }

  /**
   * Advances the persisted run-all queue (if active) to the next model, then restarts the app.
   * A full process restart is required after every benchmark since the native LlmModule
   * backing each run is intentionally leaked (see CustomBenchmarkRunner.sLeakedModules) and RAM
   * never comes back down otherwise.
   */
  private void advanceRunAllQueueAndRestart(String modelName) {
    if (mDemoSharedPreferences.isRunAllActive()) {
      List<String> queue = mDemoSharedPreferences.getRunAllQueue();
      int nextIndex = mDemoSharedPreferences.getRunAllIndex() + 1;
      if (nextIndex < queue.size()) {
        mDemoSharedPreferences.saveRunAllQueue(queue, nextIndex);
        ETLogging.getInstance().log("Run All Benchmarks: advancing to " + queue.get(nextIndex));
      } else {
        mDemoSharedPreferences.clearRunAllState();
        ETLogging.getInstance().log("Run All Benchmarks: completed all models.");
      }
    }
    scheduleAppRestart();
  }

  private void scheduleAppRestart() {
    // Use the actual launcher intent (ACTION_MAIN + CATEGORY_LAUNCHER), same as how the user
    // would open the app from the home screen, rather than a bare component Intent - matches the
    // approach verified to work in the companion Llama-Bench app.
    Intent intent = getPackageManager().getLaunchIntentForPackage(getPackageName());
    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
    PendingIntent pendingIntent = PendingIntent.getActivity(
        getApplicationContext(), 1001, intent,
        PendingIntent.FLAG_CANCEL_CURRENT | PendingIntent.FLAG_IMMUTABLE);

    // Always use a plain, non-exact alarm here - never setExactAndAllowWhileIdle()/
    // setAlarmClock(). Those rely on a temporary background-activity-launch exemption that
    // OEM battery/sleep management can override.
    AlarmManager alarmManager = (AlarmManager) getSystemService(ALARM_SERVICE);
    long triggerAt = System.currentTimeMillis() + 800;
    if (alarmManager != null) {
      alarmManager.set(AlarmManager.RTC, triggerAt, pendingIntent);
    }

    ETLogging.getInstance().saveLogs();
    finishAffinity();
    Runtime.getRuntime().exit(0);
  }

  @Override
  public void onResourcesReleased(String modelName) {
    runOnUiThread(() -> {
      mCurrentBenchmarkRunner = null;
      ETLogging.getInstance().log("Resources released for model: " + modelName);
    });
  }

  @Override
  public void onBenchmarkError(String modelName, String error, boolean fatal) {
    runOnUiThread(() -> {
      updateModelStatus(modelName, BenchmarkModel.BenchmarkStatus.ERROR);
      ETLogging.getInstance().log("Benchmark error for " + modelName + ": " + error
          + (fatal ? " (fatal)" : " (single run, continuing)"));

      if (!fatal) {
        // A single inference run failed (e.g. timed out); CustomBenchmarkRunner is already
        // moving on to the next run internally, so there's nothing to clean up or restart yet.
        return;
      }

      mCurrentBenchmarkRunner = null;

      boolean runAllActive = mDemoSharedPreferences.isRunAllActive();
      if (!runAllActive) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Benchmark Error")
               .setMessage("Error running benchmark for " + modelName + ":\n" + error)
               .setPositiveButton("OK", null)
               .show();
      }

      deleteModelFiles(modelName);
      advanceRunAllQueueAndRestart(modelName);
    });
  }

  private void updateModelStatus(String modelName, BenchmarkModel.BenchmarkStatus status) {
    for (BenchmarkModel model : mModels) {
      if (model.getFilename().equals(modelName)) {
        model.setStatus(status);
        mModelListAdapter.updateModel(model);
        break;
      }
    }
  }

  private void runBenchmark(BenchmarkModel model) {
    if (model.getStatus() == BenchmarkModel.BenchmarkStatus.DOWNLOADING
        || model.getStatus() == BenchmarkModel.BenchmarkStatus.LOADING
        || model.getStatus() == BenchmarkModel.BenchmarkStatus.RUNNING) {
      return;
    }
    if (mCurrentBenchmarkRunner != null) {
      new AlertDialog.Builder(this)
          .setTitle("Benchmark In Progress")
          .setMessage("Another benchmark is currently running. Please wait for it to complete.")
          .setPositiveButton("OK", null)
          .show();
      return;
    }
    String basePath = getExternalFilesDir(null).getAbsolutePath() + "/llama/";
    String modelPath = basePath + model.getFilename();
    String tokenizerPath = basePath + model.getTokenizerPath();
    java.io.File modelFile = new java.io.File(modelPath);

    if (model.getStatus() == BenchmarkModel.BenchmarkStatus.ERROR) {
      // A failed native load or old interrupted download may have left invalid files behind.
      if (!deleteModelFiles(model.getFilename())) {
        ETLogging.getInstance().log("Cannot replace damaged model file: " + model.getFilename());
        return;
      }
    }

    if (model.getStatus() == BenchmarkModel.BenchmarkStatus.NOT_DOWNLOADED || 
        model.getStatus() == BenchmarkModel.BenchmarkStatus.ERROR ||
        !modelFile.isFile() || modelFile.length() == 0
        || !new java.io.File(tokenizerPath).isFile()
        || new java.io.File(tokenizerPath).length() == 0) {
        startDownload(model);
        return;
    }

    
    ETLogging.getInstance().log("Starting benchmark for model: " + model.getFilename());
    ETLogging.getInstance().log("Model path: " + modelPath);
    ETLogging.getInstance().log("Tokenizer path: " + tokenizerPath);
    
    mCurrentBenchmarkRunner = new CustomBenchmarkRunner(
        this,
        modelPath, 
        tokenizerPath, 
        model.getFilename(), 
        this
    );
    
    mCurrentBenchmarkRunner.runBenchmark();
  }

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_main);

    if (Build.VERSION.SDK_INT >= 21) {
      getWindow().setStatusBarColor(ContextCompat.getColor(this, R.color.status_bar));
      getWindow().setNavigationBarColor(ContextCompat.getColor(this, R.color.nav_bar));
    }

    try {
      Os.setenv("ADSP_LIBRARY_PATH", getApplicationInfo().nativeLibraryDir, true);
      Os.setenv("LD_LIBRARY_PATH", getApplicationInfo().nativeLibraryDir, true);
    } catch (ErrnoException e) {
      finish();
    }


    mModelsListView = requireViewByIdCompat(R.id.models_list_view);
    mModels = new ArrayList<>();
    

    BenchmarkModel[] defaultModels = BenchmarkModel.getDefaultModels();
    for (BenchmarkModel model : defaultModels) {
      mModels.add(model);
    }
    
    mModelListAdapter = new ModelListAdapter(this, mModels);
    mModelsListView.setAdapter(mModelListAdapter);
    

    mModelListAdapter.setBenchmarkClickListener(this::runBenchmark);
    mModelListAdapter.setDeleteClickListener(this::confirmDeleteModel);
    
    mDemoSharedPreferences = new DemoSharedPreferences(this.getApplicationContext());
    ModelDownloader.cleanAbandonedDownloads(new java.io.File(getExternalFilesDir(null), "llama"));

    String interruptedModel = ModelLoadGuard.consumeInterruptedModel(this);
    if (interruptedModel != null) {
      // Pause Run All so the same suspect file cannot crash again on every app launch.
      mDemoSharedPreferences.clearRunAllState();
      BenchmarkModel suspect = findModelByFilename(interruptedModel);
      if (suspect != null) {
        suspect.setStatus(BenchmarkModel.BenchmarkStatus.ERROR);
        mModelListAdapter.notifyDataSetChanged();
      }
      ETLogging.getInstance().log("Previous model load ended unexpectedly: " + interruptedModel);
      AlertDialog.Builder recoveryDialog = new AlertDialog.Builder(this)
          .setTitle("Model load interrupted")
          .setMessage("The previous attempt to load " + interruptedModel
              + " ended unexpectedly. The downloaded file may be damaged.");
      if (suspect != null) {
        recoveryDialog.setPositiveButton(R.string.delete_model_files,
                (dialog, which) -> removeModelFiles(suspect))
            .setNegativeButton("Keep files", null);
      } else {
        recoveryDialog.setPositiveButton("OK", null);
      }
      recoveryDialog.show();
    }

    mSettingsButton = requireViewByIdCompat(R.id.settings);
    mSettingsButton.setOnClickListener(
        view -> {
          Intent myIntent = new Intent(MainActivity.this, SettingsActivity.class);
          MainActivity.this.startActivity(myIntent);
        });

    mRunAllButton = requireViewByIdCompat(R.id.run_all_button);
    mRunAllButton.setOnClickListener(view -> {
      if (mDemoSharedPreferences.isRunAllActive()) {
        cancelRunAllBenchmarks();
      } else {
        startRunAllBenchmarks();
      }
    });
    updateRunAllButtonState();
    resumeRunAllQueueIfActive();

    mMemoryUpdateHandler = new Handler(Looper.getMainLooper());
    startMemoryUpdate();
    setupShowLogsButton();
  }

  /**
   * If a "Run All Benchmarks" queue was in progress when the app was last restarted, picks up
   * where it left off with the next model in the persisted queue.
   */
  private void resumeRunAllQueueIfActive() {
    if (!mDemoSharedPreferences.isRunAllActive()) {
      return;
    }
    List<String> queue = mDemoSharedPreferences.getRunAllQueue();
    int index = mDemoSharedPreferences.getRunAllIndex();
    while (index < queue.size()) {
      BenchmarkModel candidate = findModelByFilename(queue.get(index));
      if (candidate != null && !isCompletedForRunAll(candidate)) {
        break;
      }
      ETLogging.getInstance().log("Run All Benchmarks: skipping completed or missing model "
          + queue.get(index));
      index++;
    }
    if (index >= queue.size()) {
      mDemoSharedPreferences.clearRunAllState();
      updateRunAllButtonState();
      return;
    }
    if (index != mDemoSharedPreferences.getRunAllIndex()) {
      mDemoSharedPreferences.saveRunAllQueue(queue, index);
    }
    BenchmarkModel next = findModelByFilename(queue.get(index));
    ETLogging.getInstance().log("Run All Benchmarks: resuming with " + next.getFilename());
    new Handler(Looper.getMainLooper()).postDelayed(() -> {
      if (isCurrentRunAllModel(next)) {
        if (isCompletedForRunAll(next)) {
          resumeRunAllQueueIfActive();
        } else {
          runBenchmark(next);
        }
      }
    }, 500);
  }

  private boolean isCompletedForRunAll(BenchmarkModel model) {
    return model.getStatus() == BenchmarkModel.BenchmarkStatus.COMPLETED
        || mDemoSharedPreferences.isModelCompleted(model.getFilename());
  }

  private BenchmarkModel findModelByFilename(String filename) {
    for (BenchmarkModel model : mModels) {
      if (model.getFilename().equals(filename)) {
        return model;
      }
    }
    return null;
  }

  private boolean isCurrentRunAllModel(BenchmarkModel model) {
    if (!mDemoSharedPreferences.isRunAllActive()) {
      return false;
    }
    List<String> queue = mDemoSharedPreferences.getRunAllQueue();
    int index = mDemoSharedPreferences.getRunAllIndex();
    return index < queue.size() && queue.get(index).equals(model.getFilename());
  }

  private void updateRunAllButtonState() {
    if (mDemoSharedPreferences.isRunAllActive()) {
      mRunAllButton.setEnabled(true);
      mRunAllButton.setText("Cancel Run All");
    } else {
      mRunAllButton.setEnabled(true);
      mRunAllButton.setText("Run All Benchmarks");
    }
  }

  private void cancelRunAllBenchmarks() {
    mDemoSharedPreferences.clearRunAllState();
    ETLogging.getInstance().log("Run All Benchmarks: cancelled by user.");
    updateRunAllButtonState();
  }

  /**
   * Queues unfinished models for an unattended benchmark run, smallest download first (so an OOM on a
   * large model doesn't happen before smaller models get a chance to run and report their
   * results). Size is determined via a HEAD request against each model's URL rather than trusting
   * static ordering, since quantization variants of the same architecture can differ a lot in size.
   */
  private void startRunAllBenchmarks() {
    List<BenchmarkModel> candidates = new ArrayList<>();
    for (BenchmarkModel model : mModels) {
      if (!isCompletedForRunAll(model)) {
        candidates.add(model);
      }
    }
    if (candidates.isEmpty()) {
      ETLogging.getInstance().log("Run All Benchmarks: all models are already completed.");
      new AlertDialog.Builder(this)
          .setMessage("All models have already been benchmarked.")
          .setPositiveButton("OK", null)
          .show();
      return;
    }

    mRunAllButton.setEnabled(false);
    mRunAllButton.setText("Computing order...");
    ETLogging.getInstance().log("Run All Benchmarks: fetching model sizes...");

    Map<String, Long> sizes = new ConcurrentHashMap<>();
    ExecutorService sizeExecutor = Executors.newFixedThreadPool(3);
    CountDownLatch latch = new CountDownLatch(candidates.size());

    for (BenchmarkModel m : candidates) {
      sizeExecutor.execute(() -> {
        try {
          sizes.put(m.getFilename(), ModelDownloader.fetchRemoteFileSize(m.getModelUrl()));
        } finally {
          latch.countDown();
        }
      });
    }

    new Thread(() -> {
      try {
        latch.await(60, TimeUnit.SECONDS);
      } catch (InterruptedException ignored) {
      }
      sizeExecutor.shutdownNow();

      List<BenchmarkModel> sorted = new ArrayList<>(candidates);
      sorted.sort((a, b) -> {
        long sa = sizes.getOrDefault(a.getFilename(), -1L);
        long sb = sizes.getOrDefault(b.getFilename(), -1L);
        if (sa < 0) sa = Long.MAX_VALUE;
        if (sb < 0) sb = Long.MAX_VALUE;
        return Long.compare(sa, sb);
      });

      runOnUiThread(() -> {
        sorted.removeIf(this::isCompletedForRunAll);
        if (sorted.isEmpty()) {
          ETLogging.getInstance().log("Run All Benchmarks: all models completed while ordering.");
          updateRunAllButtonState();
          return;
        }
        List<String> orderedFilenames = new ArrayList<>();
        for (BenchmarkModel m : sorted) {
          orderedFilenames.add(m.getFilename());
        }
        mDemoSharedPreferences.saveRunAllQueue(orderedFilenames, 0);
        ETLogging.getInstance().log("Run All Benchmarks: order = " + orderedFilenames);
        updateRunAllButtonState();
        runBenchmark(sorted.get(0));
      });
    }).start();
  }

  @Override
  protected void onPause() {
    super.onPause();
  }

  @Override
  protected void onResume() {
    super.onResume();

    String basePath = getExternalFilesDir(null).getAbsolutePath() + "/llama/";
    for (BenchmarkModel model : mModels) {
      if (model.getStatus() == BenchmarkModel.BenchmarkStatus.DOWNLOADING
          || model.getStatus() == BenchmarkModel.BenchmarkStatus.LOADING
          || model.getStatus() == BenchmarkModel.BenchmarkStatus.RUNNING) {
        continue;
      }
      java.io.File modelFile = new java.io.File(basePath + model.getFilename());
      java.io.File tokenizerFile = new java.io.File(basePath + model.getTokenizerPath());
      
      boolean filesExist = modelFile.isFile() && modelFile.length() > 0
          && tokenizerFile.isFile() && tokenizerFile.length() > 0;

      if (!filesExist) {
          model.setStatus(mDemoSharedPreferences.isModelCompleted(model.getFilename())
              ? BenchmarkModel.BenchmarkStatus.COMPLETED
              : BenchmarkModel.BenchmarkStatus.NOT_DOWNLOADED);
      } else if (model.getStatus() == BenchmarkModel.BenchmarkStatus.NOT_DOWNLOADED) {
          model.setStatus(BenchmarkModel.BenchmarkStatus.READY);
      }
    }
    mModelListAdapter.notifyDataSetChanged();
  }

  private void startDownload(BenchmarkModel model) {
    String basePath = getExternalFilesDir(null).getAbsolutePath() + "/llama/";
    java.io.File modelFile = new java.io.File(basePath + model.getFilename());
    java.io.File tokenizerFile = new java.io.File(basePath + model.getTokenizerPath());

    // Do not leave an older suspect final file available if this download is interrupted.
    if (modelFile.exists() && !modelFile.delete()) {
      model.setStatus(BenchmarkModel.BenchmarkStatus.ERROR);
      mModelListAdapter.notifyDataSetChanged();
      ETLogging.getInstance().log("Could not remove old model file: " + model.getFilename());
      return;
    }

    model.setStatus(BenchmarkModel.BenchmarkStatus.DOWNLOADING);
    model.setDownloadProgress(0);
    mModelListAdapter.notifyDataSetChanged();

    if (!tokenizerFile.exists() && model.getTokenizerUrl() != null && !model.getTokenizerUrl().isEmpty()) {
        ModelDownloader.downloadFile(model.getTokenizerUrl(), tokenizerFile.getAbsolutePath(), new ModelDownloader.DownloadCallback() {
            @Override
            public void onProgress(int percent) {
            }
            @Override
            public void onSuccess() {
                downloadModelFile(model, modelFile.getAbsolutePath());
            }
            @Override
            public void onError(String errorMsg) {
                runOnUiThread(() -> {
                    model.setStatus(BenchmarkModel.BenchmarkStatus.ERROR);
                    mModelListAdapter.notifyDataSetChanged();
                    ETLogging.getInstance().log("Tokenizer download failed: " + errorMsg);
                });
            }
        });
    } else {
        downloadModelFile(model, modelFile.getAbsolutePath());
    }
  }

  private void downloadModelFile(BenchmarkModel model, String destinationPath) {
    if (model.getModelUrl() == null || model.getModelUrl().isEmpty()) {
        runOnUiThread(() -> {
            model.setStatus(BenchmarkModel.BenchmarkStatus.ERROR);
            mModelListAdapter.notifyDataSetChanged();
            ETLogging.getInstance().log("Model download failed: URL is empty");
        });
        return;
    }
    ModelDownloader.downloadFile(model.getModelUrl(), destinationPath, new ModelDownloader.DownloadCallback() {
        @Override
        public void onProgress(int percent) {
            model.setDownloadProgress(percent);
            mModelListAdapter.notifyDataSetChanged();
        }
        @Override
        public void onSuccess() {
            model.setStatus(BenchmarkModel.BenchmarkStatus.READY);
            mModelListAdapter.notifyDataSetChanged();
            ETLogging.getInstance().log("Model downloaded successfully: " + model.getFilename());
            if (isCurrentRunAllModel(model)) {
                runBenchmark(model);
            }
        }
        @Override
        public void onError(String errorMsg) {
            model.setStatus(BenchmarkModel.BenchmarkStatus.ERROR);
            mModelListAdapter.notifyDataSetChanged();
            ETLogging.getInstance().log("Model download failed: " + errorMsg);
        }
    });
  }

  private void setupShowLogsButton() {
    ImageButton showLogsButton = requireViewByIdCompat(R.id.showLogsButton);
    showLogsButton.setOnClickListener(
        view -> {
          Intent myIntent = new Intent(MainActivity.this, LogsActivity.class);
          MainActivity.this.startActivity(myIntent);
        });
  }

  private String updateMemoryUsage() {
    ActivityManager.MemoryInfo memoryInfo = new ActivityManager.MemoryInfo();
    ActivityManager activityManager = (ActivityManager) getSystemService(ACTIVITY_SERVICE);
    if (activityManager == null) {
      return "---";
    }
    activityManager.getMemoryInfo(memoryInfo);
    long totalMem = memoryInfo.totalMem / (1024 * 1024);
    long availableMem = memoryInfo.availMem / (1024 * 1024);
    long usedMem = totalMem - availableMem;
    return usedMem + "MB";
  }

  private void startMemoryUpdate() {
    mMemoryView = requireViewByIdCompat(R.id.ram_usage_live);
    memoryUpdater =
        new Runnable() {
          @Override
          public void run() {
            mMemoryView.setText(updateMemoryUsage());
            mMemoryUpdateHandler.postDelayed(this, 1000);
          }
        };
    mMemoryUpdateHandler.post(memoryUpdater);
  }

  private <T extends View> T requireViewByIdCompat(int viewId) {
    T view = findViewById(viewId);
    if (view == null) {
      throw new IllegalStateException("Missing required view with ID: " + viewId);
    }
    return view;
  }

  @Override
  public void onBackPressed() {
    super.onBackPressed();

    if (mCurrentBenchmarkRunner != null) {
      mCurrentBenchmarkRunner.stop();
      mCurrentBenchmarkRunner = null;
    }
    finish();
  }

  @Override
  protected void onDestroy() {
    super.onDestroy();
    mMemoryUpdateHandler.removeCallbacks(memoryUpdater);
    
    if (mCurrentBenchmarkRunner != null) {
      mCurrentBenchmarkRunner.stop();
      mCurrentBenchmarkRunner = null;
    }
    
    ETLogging.getInstance().saveLogs();
  }
}
