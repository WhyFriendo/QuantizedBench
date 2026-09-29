package com.llmquantbench;

import android.app.Activity;
import android.content.Intent;
import android.os.Process;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.Promise;

public class AppControlModule extends ReactContextBaseJavaModule {
    private final ReactApplicationContext reactContext;

    public AppControlModule(ReactApplicationContext reactContext) {
        super(reactContext);
        this.reactContext = reactContext;
    }

    @Override
    public String getName() {
        return "AppControlModule";
    }

    @ReactMethod
    public void forceCloseApp(Promise promise) {
        try {
            Activity currentActivity = getCurrentActivity();
            if (currentActivity != null) {
                currentActivity.finishAffinity();
                Process.killProcess(Process.myPid());
                promise.resolve(true);
            } else {
                promise.reject("ERROR", "No current activity found");
            }
        } catch (Exception e) {
            promise.reject("ERROR", "Failed to close app: " + e.getMessage());
        }
    }

    @ReactMethod
    public void restartApp(Promise promise) {
        try {
            Intent intent = reactContext.getPackageManager().getLaunchIntentForPackage(reactContext.getPackageName());
            if (intent == null) {
                promise.reject("ERROR", "Could not get launch intent");
                return;
            }
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            reactContext.startActivity(intent);
            Activity currentActivity = getCurrentActivity();
            if (currentActivity != null) {
                currentActivity.finish();
            }

            promise.resolve(true);
            System.exit(0);
        } catch (Exception e) {
            promise.reject("ERROR", "Failed to restart app: " + e.getMessage());
        }
    }
}
