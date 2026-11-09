package sac.fadey.mobileassistant.service;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import sac.fadey.mobileassistant.FadeyApp;

public class BootCompletedReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null) return;
        String action = intent.getAction();
        if (Intent.ACTION_BOOT_COMPLETED.equals(action)
                || Intent.ACTION_MY_PACKAGE_REPLACED.equals(action)) {
            try {
                if (FadeyApp.get() != null && FadeyApp.get().secureStore().isServiceEnabled()) {
                    AssistantForegroundService.start(context.getApplicationContext());
                }
            } catch (Exception ignored) {}
        }
    }
}
