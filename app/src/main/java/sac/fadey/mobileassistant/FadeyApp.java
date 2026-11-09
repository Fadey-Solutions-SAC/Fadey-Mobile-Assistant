package sac.fadey.mobileassistant;

import android.app.Application;
import android.util.Log;

import sac.fadey.mobileassistant.data.AppDatabase;
import sac.fadey.mobileassistant.data.PrinterConfigStore;
import sac.fadey.mobileassistant.data.SecureStore;
import sac.fadey.mobileassistant.service.AssistantForegroundService;

public class FadeyApp extends Application {
    private static final String TAG = "FadeyApp";
    private static FadeyApp instance;
    private SecureStore secureStore;
    private PrinterConfigStore printerConfigStore;
    private AppDatabase database;

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
        try {
            secureStore = new SecureStore(this);
            printerConfigStore = new PrinterConfigStore(secureStore);
            database = AppDatabase.get(this);
            if (secureStore.isServiceEnabled()) {
                // Tras actualización / proceso en memoria: reactivar FGS sin esperar a la UI.
                AssistantForegroundService.start(this);
            }
        } catch (Throwable t) {
            Log.e(TAG, "Init parcial falló", t);
            if (secureStore == null) {
                secureStore = new SecureStore(this);
            }
            if (printerConfigStore == null) {
                printerConfigStore = new PrinterConfigStore(secureStore);
            }
            try {
                database = AppDatabase.get(this);
            } catch (Throwable ignored) {}
        }
    }

    public static FadeyApp get() { return instance; }
    public SecureStore secureStore() { return secureStore; }
    public PrinterConfigStore printerConfigStore() { return printerConfigStore; }
    public AppDatabase db() { return database; }
}
