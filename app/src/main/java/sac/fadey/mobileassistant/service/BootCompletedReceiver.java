package sac.fadey.mobileassistant.service;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import sac.fadey.mobileassistant.FadeyApp;

/** Arranque tras reboot, update del APK y quickboot Xiaomi. */
public class BootCompletedReceiver extends BroadcastReceiver {
    private static final String TAG = "BootReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null) return;
        String action = intent.getAction();
        Log.i(TAG, "action=" + action);
        if (Intent.ACTION_BOOT_COMPLETED.equals(action)
                || Intent.ACTION_LOCKED_BOOT_COMPLETED.equals(action)
                || Intent.ACTION_MY_PACKAGE_REPLACED.equals(action)
                || "android.intent.action.QUICKBOOT_POWERON".equals(action)
                || "com.htc.intent.action.QUICKBOOT_POWERON".equals(action)) {
            try {
                boolean enabled = true;
                try {
                    if (FadeyApp.get() != null && FadeyApp.get().secureStore() != null) {
                        enabled = FadeyApp.get().secureStore().isServiceEnabled();
                    }
                } catch (Throwable ignored) {}
                if (enabled) {
                    AssistantForegroundService.start(context.getApplicationContext());
                }
            } catch (Throwable t) {
                Log.e(TAG, "No se pudo iniciar servicio", t);
            }
        }
    }
}
