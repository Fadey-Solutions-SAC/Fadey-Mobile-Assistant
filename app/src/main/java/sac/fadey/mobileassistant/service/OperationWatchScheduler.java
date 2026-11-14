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
 * Próxima revisión, hora de Lima. Igual que el asistente de PC.
 * Encendido: no pregunta cada 15 minutos. Cada 2 horas intenta apagarse y ahí consulta.
 *   Si queda alguien, sigue encendido. Si ya cerraron todos, se apaga.
 * Apagado: de 5:00 a 7:00 cada 30 minutos, para ver si ya entraron.
 *   El resto del tiempo, cada 2 horas hasta las 5:00.
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
            Log.i(TAG, (running ? "próximo intento de cierre en " : "próxima consulta de usuarios en ")
                    + (delay / 60000) + " min");
        } catch (Throwable t) {
            Log.w(TAG, "schedule failed", t);
        }
    }

    /** Milisegundos hasta la próxima revisión, alineada al reloj. */
    static long nextDelayMillis(ZonedDateTime now, boolean serviceRunning) {
        int minuteOfDay = now.getHour() * 60 + now.getMinute();
        if (serviceRunning) {
            return alignedDelayMillis(now, minuteOfDay, 120);
        }
        if (minuteOfDay >= 5 * 60 && minuteOfDay < 7 * 60) {
            return alignedDelayMillis(now, minuteOfDay, 30);
        }
        long twoHours = alignedDelayMillis(now, minuteOfDay, 120);
        long untilFive = delayUntilFiveMillis(now, minuteOfDay);
        return Math.min(twoHours, untilFive);
    }

    private static long alignedDelayMillis(ZonedDateTime now, int minuteOfDay, int stepMinutes) {
        int mod = minuteOfDay % stepMinutes;
        int add = mod == 0 ? stepMinutes : stepMinutes - mod;
        ZonedDateTime next = now.plusMinutes(add).withSecond(0).withNano(0);
        return Math.max(1000L, Duration.between(now, next).toMillis());
    }

    private static long delayUntilFiveMillis(ZonedDateTime now, int minuteOfDay) {
        ZonedDateTime atFive = minuteOfDay < 5 * 60
                ? now.toLocalDate().atTime(5, 0).atZone(LIMA)
                : now.toLocalDate().plusDays(1).atTime(5, 0).atZone(LIMA);
        return Math.max(1000L, Duration.between(now, atFive).toMillis());
    }

    public static void cancel(Context context) {
        try {
            AlarmManager am = (AlarmManager) context.getApplicationContext()
                    .getSystemService(Context.ALARM_SERVICE);
            if (am != null) am.cancel(pending(context));
        } catch (Throwable ignored) {}
    }
}
