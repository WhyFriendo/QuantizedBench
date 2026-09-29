/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.example.executorchllamademo;

import android.content.Context;
import android.content.SharedPreferences;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class DemoSharedPreferences {
  Context context;
  SharedPreferences sharedPreferences;

  public DemoSharedPreferences(Context context) {
    this.context = context;
    this.sharedPreferences = getSharedPrefs();
  }

  private SharedPreferences getSharedPrefs() {
    return context.getSharedPreferences(
        context.getString(R.string.demo_pref_file_key), Context.MODE_PRIVATE);
  }

  public String getSavedMessages() {
    return sharedPreferences.getString(context.getString(R.string.saved_messages_json_key), "");
  }

  public void addMessages(MessageAdapter messageAdapter) {
    SharedPreferences.Editor editor = sharedPreferences.edit();
    Gson gson = new Gson();
    String msgJSON = gson.toJson(messageAdapter.getSavedMessages());
    editor.putString(context.getString(R.string.saved_messages_json_key), msgJSON);
    editor.apply();
  }

  public void removeExistingMessages() {
    SharedPreferences.Editor editor = sharedPreferences.edit();
    editor.remove(context.getString(R.string.saved_messages_json_key));
    editor.apply();
  }

  public void addSettings(SettingsFields settingsFields) {
    SharedPreferences.Editor editor = sharedPreferences.edit();
    Gson gson = new Gson();
    String settingsJSON = gson.toJson(settingsFields);
    editor.putString(context.getString(R.string.settings_json_key), settingsJSON);
    editor.apply();
  }

  public String getSettings() {
    return sharedPreferences.getString(context.getString(R.string.settings_json_key), "");
  }

  public void saveLogs() {
    SharedPreferences.Editor editor = sharedPreferences.edit();
    Gson gson = new Gson();
    String msgJSON = gson.toJson(ETLogging.getInstance().getLogs());
    editor.putString(context.getString(R.string.logs_json_key), msgJSON);
    // commit(), not apply(): this runs right before the app force-kills its own process to
    // restart (see MainActivity.scheduleAppRestart), and apply()'s disk write happens on a
    // background thread that Runtime.exit() would kill mid-flight, silently losing the write.
    editor.commit();
  }

  public void removeExistingLogs() {
    SharedPreferences.Editor editor = sharedPreferences.edit();
    editor.remove(context.getString(R.string.logs_json_key));
    editor.apply();
  }

  public ArrayList<AppLog> getSavedLogs() {
    String logsJSONString =
        sharedPreferences.getString(context.getString(R.string.logs_json_key), null);
    if (logsJSONString == null || logsJSONString.isEmpty()) {
      return new ArrayList<>();
    }
    Gson gson = new Gson();
    Type type = new TypeToken<ArrayList<AppLog>>() {}.getType();
    ArrayList<AppLog> appLogs = gson.fromJson(logsJSONString, type);
    if (appLogs == null) {
      return new ArrayList<>();
    }
    return appLogs;
  }

  public void saveRunAllQueue(List<String> filenames, int currentIndex) {
    SharedPreferences.Editor editor = sharedPreferences.edit();
    Gson gson = new Gson();
    editor.putString(context.getString(R.string.run_all_queue_json_key), gson.toJson(filenames));
    editor.putInt(context.getString(R.string.run_all_index_key), currentIndex);
    editor.putBoolean(context.getString(R.string.run_all_active_key), true);
    // commit(), not apply(): the caller restarts the process right after this returns, and
    // apply()'s async disk write would otherwise get killed before it lands (see saveLogs()).
    editor.commit();
  }

  public List<String> getRunAllQueue() {
    String queueJSONString =
        sharedPreferences.getString(context.getString(R.string.run_all_queue_json_key), null);
    if (queueJSONString == null || queueJSONString.isEmpty()) {
      return new ArrayList<>();
    }
    Gson gson = new Gson();
    Type type = new TypeToken<ArrayList<String>>() {}.getType();
    ArrayList<String> queue = gson.fromJson(queueJSONString, type);
    if (queue == null) {
      return new ArrayList<>();
    }
    return queue;
  }

  public int getRunAllIndex() {
    return sharedPreferences.getInt(context.getString(R.string.run_all_index_key), 0);
  }

  public boolean isRunAllActive() {
    return sharedPreferences.getBoolean(context.getString(R.string.run_all_active_key), false);
  }

  public void clearRunAllState() {
    SharedPreferences.Editor editor = sharedPreferences.edit();
    editor.remove(context.getString(R.string.run_all_queue_json_key));
    editor.remove(context.getString(R.string.run_all_index_key));
    editor.putBoolean(context.getString(R.string.run_all_active_key), false);
    // commit(): see saveRunAllQueue().
    editor.commit();
  }

  /**
   * Model files are deleted right after a benchmark finishes to save space, which would
   * otherwise make a completed model look "Not Downloaded" again after the next restart. This
   * persists which models have already been benchmarked, independent of whether their files are
   * still on disk.
   */
  public void markModelCompleted(String filename) {
    Set<String> completed = getCompletedModels();
    completed.add(filename);
    SharedPreferences.Editor editor = sharedPreferences.edit();
    Gson gson = new Gson();
    editor.putString(context.getString(R.string.completed_models_json_key), gson.toJson(new ArrayList<>(completed)));
    // commit(): see saveRunAllQueue().
    editor.commit();
  }

  public boolean isModelCompleted(String filename) {
    return getCompletedModels().contains(filename);
  }

  public void clearModelCompleted(String filename) {
    Set<String> completed = getCompletedModels();
    if (completed.remove(filename)) {
      sharedPreferences.edit().putString(
          context.getString(R.string.completed_models_json_key),
          new Gson().toJson(new ArrayList<>(completed))).commit();
    }
  }

  private Set<String> getCompletedModels() {
    String json = sharedPreferences.getString(context.getString(R.string.completed_models_json_key), null);
    if (json == null || json.isEmpty()) {
      return new HashSet<>();
    }
    Gson gson = new Gson();
    Type type = new TypeToken<ArrayList<String>>() {}.getType();
    ArrayList<String> list = gson.fromJson(json, type);
    return list == null ? new HashSet<>() : new HashSet<>(list);
  }
}
