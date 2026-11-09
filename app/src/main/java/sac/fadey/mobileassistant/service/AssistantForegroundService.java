package sac.fadey.mobileassistant.service;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;

import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;

import sac.fadey.mobileassistant.BuildConfig;
import sac.fadey.mobileassistant.FadeyApp;
import sac.fadey.mobileassistant.R;
import sac.fadey.mobileassistant.cloud.CloudPoller;
import sac.fadey.mobileassistant.http.LocalHttpServer;
import sac.fadey.mobileassistant.ui.MainActivity;

public class AssistantForegroundService extends Service {
    public static final String CHANNEL_ID = "fadey_assistant";
    public static final int NOTIF_ID = 1002;
    private static final String TAG = "AssistantFGS";

    private LocalHttpServer server;
    private boolean httpStarted;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable pollRunnable = new Runnable() {
        @Override
        public void run() {
            new Thread(() -> {
                try {
                    CloudPoller.pollOnce(getApplicationContext());
                } catch (Exception e) {
                    Log.w(TAG, "poll: " + e.getMessage());
                }
            }, "cloud-poll").start();
            handler.postDelayed(this, 5000);
        }
    };

    public static void start(Context context) {
        try {
            Context app = context.getApplicationContext();
            Intent i = new Intent(app, AssistantForegroundService.class);
            ContextCompat.startForegroundService(app, i);
        } catch (Throwable t) {
            Log.e(TAG, "start failed", t);
        }
    }

    public static void stop(Context context) {
        try {
            context.getApplicationContext().stopService(new Intent(context, AssistantForegroundService.class));
        } catch (Throwable ignored) {}
    }

    @Override
    public void onCreate() {
        super.onCreate();
        createChannel();
        // Debe llamarse en ~5s o Android mata el servicio (crash loop en OEM).
        startForeground(NOTIF_ID, buildNotification("Iniciando…"));
        startHttpAndPoll();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        try {
            startForeground(NOTIF_ID, buildNotification(httpStarted
                    ? ("HTTP :" + BuildConfig.DEFAULT_HTTP_PORT)
                    : "Asistente activo"));
        } catch (Throwable t) {
            Log.e(TAG, "startForeground", t);
        }
        try {
            if (FadeyApp.get() != null && FadeyApp.get().secureStore() != null) {
                FadeyApp.get().secureStore().setServiceEnabled(true);
            }
        } catch (Throwable ignored) {}
        if (!httpStarted) startHttpAndPoll();
        return START_STICKY;
    }

    private void startHttpAndPoll() {
        if (!httpStarted) {
            try {
                int port = BuildConfig.DEFAULT_HTTP_PORT;
                if (server == null) {
                    server = new LocalHttpServer(this, port);
                    server.startSafe();
                }
                httpStarted = true;
                updateNotification("HTTP :" + port + " · " + LocalHttpServer.detectLanIp());
            } catch (Exception e) {
                Log.e(TAG, "HTTP start failed", e);
                try {
                    if (FadeyApp.get() != null && FadeyApp.get().secureStore() != null) {
                        FadeyApp.get().secureStore().setLastError("HTTP: " + e.getMessage());
                    }
                } catch (Throwable ignored) {}
                updateNotification("Activo (HTTP: " + e.getMessage() + ")");
            }
        }
        handler.removeCallbacks(pollRunnable);
        handler.post(pollRunnable);
    }

    @Override
    public void onDestroy() {
        handler.removeCallbacks(pollRunnable);
        if (server != null) {
            try { server.stop(); } catch (Throwable ignored) {}
            server = null;
        }
        httpStarted = false;
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;
        NotificationChannel ch = new NotificationChannel(
                CHANNEL_ID,
                getString(R.string.service_channel),
                NotificationManager.IMPORTANCE_LOW
        );
        ch.setShowBadge(false);
        NotificationManager nm = getSystemService(NotificationManager.class);
        if (nm != null) nm.createNotificationChannel(ch);
    }

    private Notification buildNotification(String text) {
        Intent open = new Intent(this, MainActivity.class);
        open.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent pi = PendingIntent.getActivity(
                this, 0, open,
                Build.VERSION.SDK_INT >= 31
                        ? PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT
                        : PendingIntent.FLAG_UPDATE_CURRENT
        );
        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle(getString(R.string.service_running))
                .setContentText(text == null ? "" : text)
                .setSmallIcon(R.drawable.ic_stat_notify)
                .setContentIntent(pi)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build();
    }

    private void updateNotification(String text) {
        try {
            NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            if (nm != null) nm.notify(NOTIF_ID, buildNotification(text));
        } catch (Throwable t) {
            Log.w(TAG, "notify", t);
        }
    }
}
