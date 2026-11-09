package sac.fadey.mobileassistant.data;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKey;

public final class SecureStore {
    private static final String PREFS = "fadey_secure";
    private final SharedPreferences prefs;

    public SecureStore(Context context) {
        try {
            MasterKey key = new MasterKey.Builder(context)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build();
            prefs = EncryptedSharedPreferences.create(
                    context,
                    PREFS,
                    key,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            );
        } catch (Exception e) {
            throw new IllegalStateException("No se pudo abrir almacenamiento cifrado", e);
        }
    }

    public String getApiBase() { return prefs.getString("api_base", ""); }
    public void setApiBase(String v) { prefs.edit().putString("api_base", safe(v).replaceAll("/$", "")).apply(); }

    public String getToken() { return prefs.getString("jwt", ""); }
    public void setToken(String v) { prefs.edit().putString("jwt", safe(v)).apply(); }

    public boolean isLinked() {
        return !getApiBase().isEmpty() && !getToken().isEmpty();
    }

    public String getPfxBase64() { return prefs.getString("pfx_b64", ""); }
    public String getPfxPassword() { return prefs.getString("pfx_pwd", ""); }
    public void setPfx(String b64, String password) {
        prefs.edit().putString("pfx_b64", safe(b64)).putString("pfx_pwd", safe(password)).apply();
    }
    public void clearPfx() {
        prefs.edit().remove("pfx_b64").remove("pfx_pwd").apply();
    }

    public String getPrinterConfigJson() { return prefs.getString("printer_cfg", "{}"); }
    public void setPrinterConfigJson(String json) { prefs.edit().putString("printer_cfg", json == null ? "{}" : json).apply(); }

    public String getLastError() { return prefs.getString("last_error", ""); }
    public void setLastError(String msg) { prefs.edit().putString("last_error", safe(msg)).apply(); }

    public boolean isServiceEnabled() { return prefs.getBoolean("service_enabled", true); }
    public void setServiceEnabled(boolean v) { prefs.edit().putBoolean("service_enabled", v).apply(); }

    public void unlink() {
        prefs.edit()
                .remove("api_base")
                .remove("jwt")
                .remove("pfx_b64")
                .remove("pfx_pwd")
                .apply();
    }

    private static String safe(String v) { return v == null ? "" : v.trim(); }
}
