package sac.fadey.mobileassistant;

import android.app.Application;

import sac.fadey.mobileassistant.data.AppDatabase;
import sac.fadey.mobileassistant.data.PrinterConfigStore;
import sac.fadey.mobileassistant.data.SecureStore;

public class FadeyApp extends Application {
    private static FadeyApp instance;
    private SecureStore secureStore;
    private PrinterConfigStore printerConfigStore;
    private AppDatabase database;

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
        secureStore = new SecureStore(this);
        printerConfigStore = new PrinterConfigStore(secureStore);
        database = AppDatabase.get(this);
    }

    public static FadeyApp get() { return instance; }
    public SecureStore secureStore() { return secureStore; }
    public PrinterConfigStore printerConfigStore() { return printerConfigStore; }
    public AppDatabase db() { return database; }
}
