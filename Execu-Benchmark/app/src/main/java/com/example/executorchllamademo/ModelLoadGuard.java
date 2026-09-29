package com.example.executorchllamademo;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.UUID;

/** Stops an automatic retry loop if the process dies while opening a model. */
final class ModelLoadGuard {
    private static final String PREFS = "model_load_guard";
    private static final String MODEL = "model";
    private static final String PROCESS = "process";
    private static final String PROCESS_ID = UUID.randomUUID().toString();

    private ModelLoadGuard() {}

    static void begin(Context context, String modelName) {
        // Persist before entering JNI: a native crash will not run a Java catch/finally block.
        prefs(context).edit().putString(MODEL, modelName)
                .putString(PROCESS, PROCESS_ID).commit();
    }

    static void finish(Context context) {
        prefs(context).edit().clear().commit();
    }

    static String consumeInterruptedModel(Context context) {
        SharedPreferences preferences = prefs(context);
        String modelName = preferences.getString(MODEL, null);
        if (modelName == null || PROCESS_ID.equals(preferences.getString(PROCESS, null))) {
            return null;
        }
        preferences.edit().clear().commit();
        return modelName;
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
