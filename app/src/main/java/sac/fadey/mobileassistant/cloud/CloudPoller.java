package sac.fadey.mobileassistant.cloud;

import android.content.Context;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.concurrent.TimeUnit;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import sac.fadey.mobileassistant.FadeyApp;
import sac.fadey.mobileassistant.data.AppDatabase;
import sac.fadey.mobileassistant.data.JobEntity;
import sac.fadey.mobileassistant.data.SecureStore;
import sac.fadey.mobileassistant.efact.EfactEngine;
import sac.fadey.mobileassistant.print.PrintDispatcher;

public final class CloudPoller {
    private static final String TAG = "CloudPoller";
    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");
    private static final OkHttpClient HTTP = new OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .build();

    private CloudPoller() {}

    public static void pollOnce(Context context) {
        SecureStore store = FadeyApp.get().secureStore();
        if (!store.isLinked()) return;
        // Cada ciclo confirma con el servidor si el sistema sigue en operación.
        try {
            OperationSync.Result op = OperationSync.sync(context);
            if (op.ok && !op.operating && store.isFollowSystemOperation()) {
                Log.i(TAG, "sistema cerrado · se omite cola");
                return;
            }
        } catch (Exception e) {
            Log.w(TAG, "operation: " + e.getMessage());
        }
        String apiBase = store.getApiBase();
        String token = store.getToken();
        try {
            pollBilling(context, apiBase, token);
        } catch (Exception e) {
            Log.w(TAG, "billing poll: " + e.getMessage());
            store.setLastError("billing: " + e.getMessage());
        }
        try {
            pollPrintJobs(context, apiBase, token);
        } catch (Exception e) {
            Log.w(TAG, "print poll: " + e.getMessage());
            store.setLastError("print: " + e.getMessage());
        }
    }

    private static void pollBilling(Context context, String apiBase, String token) throws Exception {
        JSONObject list = getJson(apiBase + "/billing/pending-local", token);
        JSONArray docs = list.optJSONArray("documents");
        if (docs == null) return;
        AppDatabase db = FadeyApp.get().db();
        for (int i = 0; i < docs.length(); i++) {
            JSONObject row = docs.optJSONObject(i);
            if (row == null) continue;
            String id = row.optString("id", "");
            if (id.isEmpty()) continue;
            JobEntity existing = db.jobDao().byId("efact-" + id);
            if (existing != null && "accepted".equals(existing.status)) continue;

            boolean sent = false;
            try {
                JSONObject claimed = postJson(apiBase + "/billing/" + id + "/claim-local", token, new JSONObject());
                JSONObject sale = claimed.optJSONObject("sale");
                if (sale == null) sale = new JSONObject();
                rememberJob(db, "efact-" + id, "efact", "billing", "processing", sale.toString(), "");
                JSONObject bot = EfactEngine.emitir(sale);
                sent = true;
                JSONObject body = new JSONObject().put("bot", bot);
                postJson(apiBase + "/billing/" + id + "/local-result", token, body);
                String st = bot.optBoolean("ok", false) ? "accepted" : "error";
                db.jobDao().updateStatus("efact-" + id, st, bot.toString(), System.currentTimeMillis());
            } catch (Exception e) {
                if (!sent) {
                    try {
                        postJson(apiBase + "/billing/" + id + "/release-local", token, new JSONObject());
                    } catch (Exception ignored) {}
                }
                throw e;
            }
        }
    }

    private static void pollPrintJobs(Context context, String apiBase, String token) throws Exception {
        JSONObject list = getJson(apiBase + "/print-jobs/pending", token);
        JSONArray jobs = list.optJSONArray("jobs");
        if (jobs == null) return;
        AppDatabase db = FadeyApp.get().db();
        for (int i = 0; i < jobs.length(); i++) {
            JSONObject row = jobs.optJSONObject(i);
            if (row == null) continue;
            String id = row.optString("id", "");
            if (id.isEmpty()) continue;
            boolean printed = false;
            try {
                JSONObject claimed = postJson(apiBase + "/print-jobs/" + id + "/claim", token, new JSONObject());
                String module = claimed.optString("module", "caja");
                JSONObject payload = claimed.optJSONObject("payload");
                if (payload == null) payload = new JSONObject();
                rememberJob(db, "print-" + id, "print", module, "processing", payload.toString(), "");
                PrintDispatcher.printModule(context, module, payload);
                printed = true;
                JSONObject ok = new JSONObject().put("ok", true);
                postJson(apiBase + "/print-jobs/" + id + "/result", token, ok);
                db.jobDao().updateStatus("print-" + id, "printed", ok.toString(), System.currentTimeMillis());
            } catch (Exception e) {
                Log.w(TAG, "print job " + id + ": " + e.getMessage());
                FadeyApp.get().secureStore().setLastError("print: " + e.getMessage());
                if (!printed) {
                    try {
                        JSONObject fail = new JSONObject().put("ok", false).put("error", e.getMessage());
                        postJson(apiBase + "/print-jobs/" + id + "/result", token, fail);
                    } catch (Exception ignored) {
                        try {
                            postJson(apiBase + "/print-jobs/" + id + "/release", token, new JSONObject());
                        } catch (Exception ignored2) {}
                    }
                }
                /* Sigue con el siguiente trabajo: sin MAC/IP la cola igual avanza. */
            }
        }
    }

    private static void rememberJob(AppDatabase db, String id, String type, String module, String status,
                                    String payload, String result) {
        JobEntity j = new JobEntity();
        j.id = id;
        j.type = type;
        j.module = module;
        j.status = status;
        j.payloadJson = payload;
        j.resultJson = result;
        j.createdAt = System.currentTimeMillis();
        j.updatedAt = j.createdAt;
        db.jobDao().upsert(j);
    }

    private static JSONObject getJson(String url, String token) throws Exception {
        Request req = new Request.Builder()
                .url(url)
                .header("Authorization", "Bearer " + token)
                .header("Accept", "application/json")
                .get()
                .build();
        try (Response resp = HTTP.newCall(req).execute()) {
            String body = resp.body() != null ? resp.body().string() : "{}";
            if (!resp.isSuccessful()) throw new IllegalStateException("HTTP " + resp.code() + " " + body);
            return new JSONObject(body.isEmpty() ? "{}" : body);
        }
    }

    private static JSONObject postJson(String url, String token, JSONObject body) throws Exception {
        Request req = new Request.Builder()
                .url(url)
                .header("Authorization", "Bearer " + token)
                .header("Accept", "application/json")
                .post(RequestBody.create(body == null ? "{}" : body.toString(), JSON))
                .build();
        try (Response resp = HTTP.newCall(req).execute()) {
            String text = resp.body() != null ? resp.body().string() : "{}";
            if (!resp.isSuccessful()) throw new IllegalStateException("HTTP " + resp.code() + " " + text);
            return new JSONObject(text == null || text.isEmpty() ? "{}" : text);
        }
    }
}
