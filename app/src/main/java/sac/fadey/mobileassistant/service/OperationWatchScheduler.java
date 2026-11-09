package sac.fadey.mobileassistant.service;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.SystemClock;
import android.util.Log;

import sac.fadey.mobileassistant.FadeyApp;

/** Programa alarmas inexactas para reactivar el APK cuando el POS vuelve a operar. */
public final class OperationWatchScheduler {
    private static final String TAG = "OperationWatchSched";
    /** Intervalo corto: al abrir caja el asistente reacciona en ~1–2 min sin FCM. */
    private static final long INTERVAL_MS = 90_000L;

    private OperationWatchScheduler() {}

    private static PendingIntent pending(Context context) {
        Intent i = new Intent(context, OperationWatchReceiver.class);
        i.setAction(OperationWatchReceiver.ACTION);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= 23) flags |= PendingIntent.FLAG_IMMUTABLE;
        return PendingIntent.getBroadcast(context, 44, i, flags);
    }

    public static void ensureScheduled(Context context) {
        try {
            Context app = context.getApplicationContext();
            if (FadeyApp.get() != null && FadeyApp.get().secureStore() != null
                    && !FadeyApp.get().secureStore().isLinked()) {
                cancel(app);
                return;
            }
            AlarmManager am = (AlarmManager) app.getSystemService(Context.ALARM_SERVICE);
            if (am == null) return;
            PendingIntent pi = pending(app);
            long trigger = SystemClock.elapsedRealtime() + INTERVAL_MS;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                // El receiver reprograma el siguiente tick al despertar.
                am.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, trigger, pi);
            } else {
                am.setInexactRepeating(
                        AlarmManager.ELAPSED_REALTIME_WAKEUP,
                        trigger,
                        INTERVAL_MS,
                        pi
                );
            }
            Log.i(TAG, "scheduled in ~" + (INTERVAL_MS / 1000) + "s");
        } catch (Throwable t) {
            Log.w(TAG, "schedule failed", t);
        }
    }

    public static void cancel(Context context) {
        try {
            AlarmManager am = (AlarmManager) context.getApplicationContext()
                    .getSystemService(Context.ALARM_SERVICE);
            if (am != null) am.cancel(pending(context));
        } catch (Throwable ignored) {}
    }
}
