package sac.fadey.mobileassistant.data;

import org.json.JSONException;
import org.json.JSONObject;

public final class PrinterConfigStore {
    private final SecureStore store;

    public PrinterConfigStore(SecureStore store) {
        this.store = store;
    }

    public JSONObject load() {
        try {
            JSONObject root = new JSONObject(store.getPrinterConfigJson());
            ensureModule(root, "caja");
            ensureModule(root, "cocina");
            ensureModule(root, "bar");
            return root;
        } catch (JSONException e) {
            JSONObject root = new JSONObject();
            try {
                ensureModule(root, "caja");
                ensureModule(root, "cocina");
                ensureModule(root, "bar");
            } catch (JSONException ignored) {}
            return root;
        }
    }

    public void save(JSONObject root) {
        store.setPrinterConfigJson(root == null ? "{}" : root.toString());
    }

    public JSONObject getModule(String module) {
        JSONObject root = load();
        return root.optJSONObject(module == null ? "caja" : module.toLowerCase());
    }

    public void putModule(String module, JSONObject cfg) throws JSONException {
        JSONObject root = load();
        root.put(module.toLowerCase(), cfg);
        save(root);
    }

    private static void ensureModule(JSONObject root, String key) throws JSONException {
        if (!root.has(key)) {
            JSONObject m = new JSONObject();
            m.put("tipo", "bluetooth");
            m.put("nombre", key);
            m.put("address", "");
            m.put("port", 9100);
            m.put("paperWidth", 80);
            m.put("enabled", true);
            root.put(key, m);
        }
    }
}
