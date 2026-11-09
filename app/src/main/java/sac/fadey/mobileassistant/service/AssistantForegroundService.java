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
        Intent i = new Intent(context, AssistantForegroundService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(i);
        } else {
            context.startService(i);
        }
    }

    public static void stop(Context context) {
        context.stopService(new Intent(context, AssistantForegroundService.class));
    }

    @Override
    public void onCreate() {
        super.onCreate();
        createChannel();
        startForeground(NOTIF_ID, buildNotification("Iniciando…"));
        try {
            int port = BuildConfig.DEFAULT_HTTP_PORT;
            server = new LocalHttpServer(this, port);
            server.startSafe();
            updateNotification("HTTP :" + port + " · " + LocalHttpServer.detectLanIp());
        } catch (Exception e) {
            Log.e(TAG, "HTTP start failed", e);
            FadeyApp.get().secureStore().setLastError("HTTP: " + e.getMessage());
            updateNotification("Error HTTP: " + e.getMessage());
        }
        handler.post(pollRunnable);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        FadeyApp.get().secureStore().setServiceEnabled(true);
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        handler.removeCallbacks(pollRunnable);
        if (server != null) {
            server.stop();
            server = null;
        }
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
        NotificationManager nm = getSystemService(NotificationManager.class);
        if (nm != null) nm.createNotificationChannel(ch);
    }

    private Notification buildNotification(String text) {
        Intent open = new Intent(this, MainActivity.class);
        PendingIntent pi = PendingIntent.getActivity(
                this, 0, open,
                Build.VERSION.SDK_INT >= 31 ? PendingIntent.FLAG_IMMUTABLE : 0
        );
        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle(getString(R.string.service_running))
                .setContentText(text)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentIntent(pi)
                .setOngoing(true)
                .build();
    }

    private void updateNotification(String text) {
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (nm != null) nm.notify(NOTIF_ID, buildNotification(text));
    }
}
