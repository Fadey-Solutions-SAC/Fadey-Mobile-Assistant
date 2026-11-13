package sac.fadey.mobileassistant.service;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.SystemClock;
import android.util.Log;

import java.time.Duration;
import java.time.ZoneId;
import java.time.ZonedDateTime;

import sac.fadey.mobileassistant.FadeyApp;

/**
 * Consulta usuarios activos en hora de Lima.
 * Con usuarios: de 5:00 a 7:00 cada 30 minutos, desde las 7:00 cada 15.
 * Si sigue encendido de madrugada, cada 15 minutos para poder apagarse.
 * Si ya cerraron todos: cada 2 horas hasta las 5:00. Ahí arranca el ciclo de la mañana.
 */
public final class OperationWatchScheduler {
    private static final String TAG = "OperationWatchSched";
    private static final ZoneId LIMA = ZoneId.of("America/Lima");

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
            boolean running = false;
            try {
                running = FadeyApp.get() != null
                        && FadeyApp.get().secureStore() != null
                        && FadeyApp.get().secureStore().isServiceEnabled();
            } catch (Throwable ignored) {}
            long delay = nextDelayMillis(ZonedDateTime.now(LIMA), running);
            PendingIntent pi = pending(app);
            long trigger = SystemClock.elapsedRealtime() + delay;
            if (Build.VERSION.SDK_INT >= 31 && !am.canScheduleExactAlarms()) {
                am.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, trigger, pi);
            } else if (Build.VERSION.SDK_INT >= 23) {
                am.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, trigger, pi);
            } else {
                am.set(AlarmManager.ELAPSED_REALTIME_WAKEUP, trigger, pi);
            }
            Log.i(TAG, "próxima consulta en " + (delay / 60000) + " min");
        } catch (Throwable t) {
            Log.w(TAG, "schedule failed", t);
        }
    }

    /** Milisegundos hasta la próxima consulta, alineada al reloj. */
    static long nextDelayMillis(ZonedDateTime now, boolean serviceRunning) {
        int minuteOfDay = now.getHour() * 60 + now.getMinute();
        if (!serviceRunning && (minuteOfDay < 5 * 60 || minuteOfDay >= 7 * 60)) {
            int mod = minuteOfDay % 120;
            int add = mod == 0 ? 120 : 120 - mod;
            ZonedDateTime nextTwo = now.plusMinutes(add).withSecond(0).withNano(0);
            ZonedDateTime atFive = minuteOfDay < 5 * 60
                    ? now.toLocalDate().atTime(5, 0).atZone(LIMA)
                    : now.toLocalDate().plusDays(1).atTime(5, 0).atZone(LIMA);
            long twoMs = Duration.between(now, nextTwo).toMillis();
            long fiveMs = Duration.between(now, atFive).toMillis();
            return Math.max(1000L, Math.min(twoMs, fiveMs));
        }
        int step = minuteOfDay < 7 * 60 ? 30 : 15;
        if (minuteOfDay < 5 * 60) step = 15;
        int mod = minuteOfDay % step;
        int add = mod == 0 ? step : (step - mod);
        ZonedDateTime next = now.plusMinutes(add).withSecond(0).withNano(0);
        return Math.max(1000L, Duration.between(now, next).toMillis());
    }

    public static void cancel(Context context) {
        try {
            AlarmManager am = (AlarmManager) context.getApplicationContext()
                    .getSystemService(Context.ALARM_SERVICE);
            if (am != null) am.cancel(pending(context));
        } catch (Throwable ignored) {}
    }
}
