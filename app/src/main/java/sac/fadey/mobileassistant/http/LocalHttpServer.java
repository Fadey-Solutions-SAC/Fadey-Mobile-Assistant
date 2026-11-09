package sac.fadey.mobileassistant.http;

import android.content.Context;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.Collections;
import java.util.Iterator;
import java.util.Map;

import fi.iki.elonen.NanoHTTPD;
import sac.fadey.mobileassistant.BuildConfig;
import sac.fadey.mobileassistant.FadeyApp;
import sac.fadey.mobileassistant.data.SecureStore;
import sac.fadey.mobileassistant.efact.EfactEngine;
import sac.fadey.mobileassistant.print.PrintDispatcher;

public class LocalHttpServer extends NanoHTTPD {
    private static final String TAG = "LocalHttpServer";
    private final Context appContext;
    private final int listenPort;

    public LocalHttpServer(Context context, int port) {
        super("0.0.0.0", port);
        this.appContext = context.getApplicationContext();
        this.listenPort = port;
    }

    public int getListenPort() { return listenPort; }

    public JSONObject healthPayload() {
        JSONObject o = new JSONObject();
        try {
            String ip = detectLanIp();
            String origin = "http://" + (ip.isEmpty() ? "127.0.0.1" : ip) + ":" + listenPort;
            o.put("status", "ok");
            o.put("mode", "assistant");
            o.put("port", listenPort);
            o.put("origin", origin);
            o.put("service", BuildConfig.ASSISTANT_SERVICE_NAME);
            o.put("platform", "android");
            o.put("features", new JSONArray().put("footer_image").put("cloud_poll"));
        } catch (Exception ignored) {}
        return o;
    }

    @Override
    public Response serve(IHTTPSession session) {
        Response preflight = cors(session);
        if (preflight != null) return preflight;
        String uri = session.getUri() == null ? "/" : session.getUri();
        String method = session.getMethod() == null ? "GET" : session.getMethod().name();
        try {
            if ("GET".equals(method) && ("/health".equals(uri) || "/api/health".equals(uri) || "/api/printing/bridge".equals(uri))) {
                return json(Response.Status.OK, healthPayload());
            }
            if ("GET".equals(method) && "/api/printing/config".equals(uri)) {
                return json(Response.Status.OK, FadeyApp.get().printerConfigStore().load());
            }
            if ("PUT".equals(method) && "/api/printing/config".equals(uri)) {
                JSONObject body = readJson(session);
                FadeyApp.get().printerConfigStore().save(body);
                return json(Response.Status.OK, FadeyApp.get().printerConfigStore().load());
            }
            if ("GET".equals(method) && ("/api/printers".equals(uri) || "/api/printing/printers".equals(uri) || "/api/printing/network-printers".equals(uri))) {
                return json(Response.Status.OK, listPrinters());
            }
            if ("GET".equals(method) && uri.startsWith("/api/printing/status/")) {
                String module = uri.substring("/api/printing/status/".length());
                JSONObject cfg = FadeyApp.get().printerConfigStore().getModule(module);
                JSONObject st = new JSONObject();
                st.put("module", module);
                st.put("connected", cfg != null && !cfg.optString("address").isEmpty());
                st.put("status", cfg == null ? "sin config" : cfg.optString("tipo") + " " + cfg.optString("address"));
                return json(Response.Status.OK, st);
            }
            if ("POST".equals(method) && uri.startsWith("/api/printing/print/")) {
                String module = uri.substring("/api/printing/print/".length());
                JSONObject body = readJson(session);
                PrintDispatcher.printModule(appContext, module, body);
                return json(Response.Status.OK, new JSONObject().put("ok", true));
            }
            if ("POST".equals(method) && uri.startsWith("/api/printing/test/")) {
                String module = uri.substring("/api/printing/test/".length());
                PrintDispatcher.printTest(appContext, module);
                return json(Response.Status.OK, new JSONObject().put("ok", true));
            }
            if ("POST".equals(method) && "/api/efact/cloud-link".equals(uri)) {
                JSONObject body = readJson(session);
                String apiBase = body.optString("apiBase", "").replaceAll("/$", "");
                String token = body.optString("token", "");
                if (apiBase.isEmpty() || token.isEmpty()) {
                    return json(Response.Status.BAD_REQUEST, new JSONObject().put("ok", false));
                }
                SecureStore store = FadeyApp.get().secureStore();
                store.setApiBase(apiBase);
                store.setToken(token);
                return json(Response.Status.OK, new JSONObject().put("ok", true));
            }
            if ("POST".equals(method) && ("/api/efact/emitir".equals(uri) || "/emitir".equals(uri))) {
                JSONObject body = readJson(session);
                JSONObject bot = EfactEngine.emitir(body);
                return json(bot.optBoolean("ok", false) ? Response.Status.OK : Response.Status.BAD_REQUEST, bot);
            }
            return json(Response.Status.NOT_FOUND, new JSONObject().put("error", "not found").put("path", uri));
        } catch (Exception e) {
            Log.e(TAG, "serve error", e);
            FadeyApp.get().secureStore().setLastError(String.valueOf(e.getMessage()));
            try {
                return json(Response.Status.INTERNAL_ERROR, new JSONObject().put("ok", false).put("error", e.getMessage()));
            } catch (Exception ex) {
                return newFixedLengthResponse(Response.Status.INTERNAL_ERROR, "text/plain", e.getMessage());
            }
        }
    }

    private JSONArray listPrinters() {
        JSONArray arr = new JSONArray();
        try {
            JSONObject root = FadeyApp.get().printerConfigStore().load();
            Iterator<String> keys = root.keys();
            while (keys.hasNext()) {
                String k = keys.next();
                JSONObject m = root.optJSONObject(k);
                if (m == null) continue;
                JSONObject p = new JSONObject();
                p.put("name", m.optString("nombre", k));
                p.put("module", k);
                p.put("tipo", m.optString("tipo"));
                p.put("address", m.optString("address"));
                arr.put(p);
            }
        } catch (Exception ignored) {}
        return arr;
    }

    private Response cors(IHTTPSession session) {
        if (Method.OPTIONS.equals(session.getMethod())) {
            Response r = newFixedLengthResponse(Response.Status.OK, MIME_PLAINTEXT, "");
            addCors(r);
            return r;
        }
        return null;
    }

    private Response json(Response.Status status, Object payload) {
        Response r = newFixedLengthResponse(status, "application/json", String.valueOf(payload));
        addCors(r);
        return r;
    }

    private void addCors(Response r) {
        r.addHeader("Access-Control-Allow-Origin", "*");
        r.addHeader("Access-Control-Allow-Methods", "GET,POST,PUT,PATCH,DELETE,OPTIONS");
        r.addHeader("Access-Control-Allow-Headers", "Content-Type, Authorization, X-Requested-With, Access-Control-Request-Private-Network");
        r.addHeader("Access-Control-Allow-Private-Network", "true");
    }

    private JSONObject readJson(IHTTPSession session) throws Exception {
        Map<String, String> files = new java.util.HashMap<>();
        session.parseBody(files);
        String raw = files.get("postData");
        if (raw == null || raw.isEmpty()) {
            Map<String, String> parms = session.getParms();
            if (parms != null && parms.containsKey("postData")) raw = parms.get("postData");
        }
        if (raw == null || raw.isEmpty()) return new JSONObject();
        return new JSONObject(raw);
    }

    public static String detectLanIp() {
        try {
            for (NetworkInterface ni : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                if (!ni.isUp() || ni.isLoopback()) continue;
                for (InetAddress addr : Collections.list(ni.getInetAddresses())) {
                    if (addr instanceof Inet4Address && !addr.isLoopbackAddress()) {
                        return addr.getHostAddress();
                    }
                }
            }
        } catch (Exception ignored) {}
        return "";
    }

    public void startSafe() throws IOException {
        start(NanoHTTPD.SOCKET_READ_TIMEOUT, false);
    }
}
