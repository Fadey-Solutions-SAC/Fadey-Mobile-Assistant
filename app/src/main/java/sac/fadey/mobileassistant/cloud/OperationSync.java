package sac.fadey.mobileassistant.cloud;

import android.content.Context;
import android.util.Log;

import org.json.JSONObject;

import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import sac.fadey.mobileassistant.FadeyApp;
import sac.fadey.mobileassistant.data.SecureStore;
import sac.fadey.mobileassistant.service.AssistantForegroundService;
import sac.fadey.mobileassistant.service.OperationWatchScheduler;

/**
 * Consulta al servidor si el POS/caja está en operación y arranca o detiene el FGS.
 * Así, al cerrar caja el asistente puede dormir; al abrir el sistema, el alarm/poll lo reactiva.
 */
public final class OperationSync {
    private static final String TAG = "OperationSync";
    private static final OkHttpClient HTTP = new OkHttpClient.Builder()
            .connectTimeout(12, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .build();

    private OperationSync() {}

    public static final class Result {
        public final boolean ok;
        public final boolean operating;
        public final String detail;
        public Result(boolean ok, boolean operating, String detail) {
            this.ok = ok;
            this.operating = operating;
            this.detail = detail == null ? "" : detail;
        }
    }

    public static Result sync(Context context) {
        Context app = context.getApplicationContext();
        FadeyApp fadey = FadeyApp.get();
        if (fadey == null) return new Result(false, false, "app null");
        SecureStore store = fadey.secureStore();
        if (store == null || !store.isLinked()) {
            return new Result(false, false, "sin vínculo");
        }
        OperationWatchScheduler.ensureScheduled(app);
        String base = ApiLinkHelper.normalizeApiBase(store.getApiBase());
        String token = ApiLinkHelper.normalizeToken(store.getToken());
        if (base.isEmpty() || token.isEmpty()) {
            return new Result(false, false, "api/token vacío");
        }
        String url = base + "/assistant/operation-status";
        Request req = new Request.Builder()
                .url(url)
                .header("Authorization", "Bearer " + token)
                .header("Accept", "application/json")
                .get()
                .build();
        try (Response resp = HTTP.newCall(req).execute()) {
            String body = resp.body() != null ? resp.body().string() : "";
            if (!resp.isSuccessful()) {
                Log.w(TAG, "HTTP " + resp.code() + " " + body);
                // No apagar el servicio por un fallo de red.
                return new Result(false, false, "HTTP " + resp.code());
            }
            JSONObject json = new JSONObject(body.isEmpty() ? "{}" : body);
            boolean operating = json.optBoolean("operating", false);
            int open = json.optInt("openRegisters", 0);
            store.setLastOperationOperating(operating);
            store.setLastOperationDetail(
                    operating
                            ? ("En operación · cajas abiertas: " + open)
                            : "Sistema cerrado (sin caja / sin heartbeat POS)"
            );
            if (operating) {
                store.setServiceEnabled(true);
                AssistantForegroundService.start(app);
            } else if (store.isFollowSystemOperation()) {
                AssistantForegroundService.stop(app);
                store.setServiceEnabled(false);
            }
            return new Result(true, operating, store.getLastOperationDetail());
        } catch (Exception e) {
            Log.w(TAG, "sync: " + e.getMessage());
            return new Result(false, false, e.getMessage());
        }
    }
}
