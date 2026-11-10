package sac.fadey.mobileassistant.update;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.util.Log;

import androidx.core.app.NotificationCompat;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import sac.fadey.mobileassistant.BuildConfig;
import sac.fadey.mobileassistant.R;

/**
 * Busca en GitHub una versión más nueva, descarga el APK y avisa para instalar.
 * Android no permite instalar en silencio: el usuario confirma una vez.
 */
public final class AppUpdater {
    private static final String TAG = "AppUpdater";
    private static final String PREFS = "fadey_updates";
    private static final String RELEASES =
            "https://api.github.com/repos/Fadey-Solutions-SAC/Fadey-Mobile-Assistant/releases/latest";
    private static final long INTERVAL_MS = 6L * 60L * 60L * 1000L;
    private static final String CHANNEL = "fadey_updates";
    public static final int NOTIF_ID = 2108;
    private static final AtomicBoolean BUSY = new AtomicBoolean(false);

    private static final OkHttpClient HTTP = new OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .followRedirects(true)
            .build();

    private AppUpdater() {}

    public static File apkFile(Context context) {
        File dir = new File(context.getCacheDir(), "updates");
        if (!dir.exists()) dir.mkdirs();
        return new File(dir, "update.apk");
    }

    public static boolean hasReadyUpdate(Context context) {
        SharedPreferences p = prefs(context);
        int code = p.getInt("ready_code", 0);
        File apk = apkFile(context);
        return code > BuildConfig.VERSION_CODE && apk.exists() && apk.length() > 200_000L;
    }

    public static String readyName(Context context) {
        return prefs(context).getString("ready_name", "");
    }

    /** Como mucho una vez cada 6 horas. Sirve desde el servicio y la alarma. */
    public static void maybeCheck(Context context) {
        long last = prefs(context).getLong("checked_at", 0L);
        if (System.currentTimeMillis() - last < INTERVAL_MS) return;
        check(context);
    }

    public static void check(Context context) {
        if (!BUSY.compareAndSet(false, true)) return;
        final Context app = context.getApplicationContext();
        new Thread(() -> {
            try {
                runCheck(app);
            } catch (Throwable t) {
                Log.w(TAG, "check: " + t.getMessage());
            } finally {
                prefs(app).edit().putLong("checked_at", System.currentTimeMillis()).apply();
                BUSY.set(false);
            }
        }, "app-update").start();
    }

    private static void runCheck(Context app) throws Exception {
        Request req = new Request.Builder()
                .url(RELEASES)
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", "FadeyMobileAssistant")
                .build();
        JSONObject release;
        try (Response res = HTTP.newCall(req).execute()) {
            if (!res.isSuccessful() || res.body() == null) {
                Log.w(TAG, "releases HTTP " + res.code());
                return;
            }
            release = new JSONObject(res.body().string());
        }
        JSONArray assets = release.optJSONArray("assets");
        if (assets == null) return;

        int remoteCode = 0;
        String remoteName = release.optString("tag_name", "");
        String versionJsonUrl = assetUrl(assets, "mobile-version.json");
        if (!versionJsonUrl.isEmpty()) {
            JSONObject meta = getJson(versionJsonUrl);
            remoteCode = meta.optInt("versionCode", 0);
            remoteName = meta.optString("versionName", remoteName);
        }
        if (remoteCode <= BuildConfig.VERSION_CODE) {
            Log.i(TAG, "al día " + BuildConfig.VERSION_CODE + " remote=" + remoteCode);
            clearReady(app);
            return;
        }
        String apkUrl = "";
        for (int i = 0; i < assets.length(); i++) {
            JSONObject a = assets.optJSONObject(i);
            if (a == null) continue;
            String name = a.optString("name", "");
            if (name.endsWith(".apk")) {
                apkUrl = a.optString("browser_download_url", "");
                break;
            }
        }
        if (apkUrl.isEmpty()) return;

        File dest = apkFile(app);
        File tmp = new File(dest.getParentFile(), "update.apk.part");
        download(apkUrl, tmp);
        if (tmp.length() < 200_000L) {
            tmp.delete();
            return;
        }
        if (dest.exists()) dest.delete();
        if (!tmp.renameTo(dest)) return;
        prefs(app).edit()
                .putInt("ready_code", remoteCode)
                .putString("ready_name", remoteName)
                .apply();
        notifyReady(app, remoteName);
        Log.i(TAG, "APK listo " + remoteName + " (" + remoteCode + ")");
    }

    private static void clearReady(Context app) {
        prefs(app).edit().remove("ready_code").remove("ready_name").apply();
        File apk = apkFile(app);
        if (apk.exists()) apk.delete();
        NotificationManager nm = (NotificationManager) app.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null) nm.cancel(NOTIF_ID);
    }

    private static String assetUrl(JSONArray assets, String name) {
        for (int i = 0; i < assets.length(); i++) {
            JSONObject a = assets.optJSONObject(i);
            if (a != null && name.equals(a.optString("name", ""))) {
                return a.optString("browser_download_url", "");
            }
        }
        return "";
    }

    private static JSONObject getJson(String url) throws Exception {
        Request req = new Request.Builder().url(url).header("User-Agent", "FadeyMobileAssistant").build();
        try (Response res = HTTP.newCall(req).execute()) {
            if (!res.isSuccessful() || res.body() == null) return new JSONObject();
            return new JSONObject(res.body().string());
        }
    }

    private static void download(String url, File dest) throws Exception {
        Request req = new Request.Builder()
                .url(url)
                .header("User-Agent", "FadeyMobileAssistant")
                .header("Accept", "application/octet-stream")
                .build();
        try (Response res = HTTP.newCall(req).execute()) {
            if (!res.isSuccessful()) throw new IllegalStateException("HTTP " + res.code());
            ResponseBody body = res.body();
            if (body == null) throw new IllegalStateException("sin cuerpo");
            try (InputStream in = body.byteStream(); FileOutputStream out = new FileOutputStream(dest)) {
                byte[] buf = new byte[8192];
                int n;
                while ((n = in.read(buf)) >= 0) out.write(buf, 0, n);
            }
        }
    }

    private static void notifyReady(Context app, String versionName) {
        NotificationManager nm = (NotificationManager) app.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) return;
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel ch = new NotificationChannel(
                    CHANNEL, "Actualizaciones", NotificationManager.IMPORTANCE_HIGH);
            nm.createNotificationChannel(ch);
        }
        Intent open = new Intent(app, InstallUpdateActivity.class);
        open.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        PendingIntent pi = PendingIntent.getActivity(
                app, 8, open,
                Build.VERSION.SDK_INT >= 31
                        ? PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT
                        : PendingIntent.FLAG_UPDATE_CURRENT
        );
        String label = versionName == null || versionName.isEmpty() ? "nueva versión" : versionName;
        NotificationCompat.Builder b = new NotificationCompat.Builder(app, CHANNEL)
                .setSmallIcon(R.drawable.ic_stat_notify)
                .setContentTitle("Actualización lista")
                .setContentText("Toca para instalar " + label)
                .setContentIntent(pi)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH);
        nm.notify(NOTIF_ID, b.build());
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
