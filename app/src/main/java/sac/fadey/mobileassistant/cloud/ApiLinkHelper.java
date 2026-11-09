package sac.fadey.mobileassistant.cloud;

import org.json.JSONObject;

import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/** Normaliza API/JWT y prueba el mismo endpoint que usa el poll (como el asistente Windows). */
public final class ApiLinkHelper {
    private static final OkHttpClient HTTP = new OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .build();

    private ApiLinkHelper() {}

    public static String normalizeApiBase(String raw) {
        String s = raw == null ? "" : raw.trim();
        if (s.isEmpty()) return "";
        s = s.replaceAll("/+$", "");
        if (!s.matches("(?i)^https?://.+")) {
            s = "https://" + s;
        }
        // El poll usa {apiBase}/billing/... → apiBase debe terminar en /api
        if (!s.matches("(?i).*/api$")) {
            s = s + "/api";
        }
        return s;
    }

    public static String normalizeToken(String raw) {
        String t = raw == null ? "" : raw.trim();
        if (t.regionMatches(true, 0, "Bearer ", 0, 7)) {
            t = t.substring(7).trim();
        }
        return t;
    }

    public static final class ProbeResult {
        public final boolean ok;
        public final String message;
        public ProbeResult(boolean ok, String message) {
            this.ok = ok;
            this.message = message;
        }
    }

    public static ProbeResult probe(String apiBase, String token) {
        String base = normalizeApiBase(apiBase);
        String jwt = normalizeToken(token);
        if (base.isEmpty() || jwt.isEmpty()) {
            return new ProbeResult(false, "Faltan URL API o token JWT");
        }
        String url = base + "/billing/pending-local";
        Request req = new Request.Builder()
                .url(url)
                .header("Authorization", "Bearer " + jwt)
                .header("Accept", "application/json")
                .get()
                .build();
        try (Response resp = HTTP.newCall(req).execute()) {
            String body = resp.body() != null ? resp.body().string() : "";
            if (resp.code() == 401 || resp.code() == 403) {
                return new ProbeResult(false, "Token inválido o sin permiso (use cajero/admin). HTTP " + resp.code());
            }
            if (!resp.isSuccessful()) {
                String snip = body.length() > 120 ? body.substring(0, 120) : body;
                return new ProbeResult(false, "HTTP " + resp.code() + " en " + url + " · " + snip);
            }
            try {
                JSONObject json = new JSONObject(body.isEmpty() ? "{}" : body);
                if (!json.has("documents") && json.has("error")) {
                    return new ProbeResult(false, json.optString("error", "Respuesta inesperada"));
                }
            } catch (Exception ignored) {}
            return new ProbeResult(true, "Vinculado al servidor · " + base);
        } catch (Exception e) {
            return new ProbeResult(false, "No alcanzó el API: " + e.getMessage());
        }
    }
}
