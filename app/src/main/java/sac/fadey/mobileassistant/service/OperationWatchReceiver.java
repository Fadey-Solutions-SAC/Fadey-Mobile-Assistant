package sac.fadey.mobileassistant.service;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import sac.fadey.mobileassistant.cloud.OperationSync;

/** Despierta cada pocos minutos para preguntar al servidor si el sistema está en operación. */
public class OperationWatchReceiver extends BroadcastReceiver {
    private static final String TAG = "OperationWatch";
    public static final String ACTION = "sac.fadey.mobileassistant.ACTION_OPERATION_WATCH";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null) return;
        String action = intent.getAction();
        if (!ACTION.equals(action)
                && !Intent.ACTION_BOOT_COMPLETED.equals(action)
                && !Intent.ACTION_MY_PACKAGE_REPLACED.equals(action)) {
            return;
        }
        Log.i(TAG, "tick action=" + action);
        final Context app = context.getApplicationContext();
        OperationWatchScheduler.ensureScheduled(app);
        final PendingResult pending = goAsync();
        new Thread(() -> {
            try {
                OperationSync.Result r = OperationSync.sync(app);
                Log.i(TAG, "operating=" + r.operating + " ok=" + r.ok + " " + r.detail);
            } catch (Throwable t) {
                Log.w(TAG, "sync failed", t);
            } finally {
                try { pending.finish(); } catch (Throwable ignored) {}
            }
        }, "operation-watch").start();
    }
}
