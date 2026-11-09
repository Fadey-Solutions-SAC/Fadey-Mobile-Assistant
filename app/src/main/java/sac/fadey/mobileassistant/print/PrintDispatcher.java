package sac.fadey.mobileassistant.print;

import android.content.Context;

import org.json.JSONObject;

import sac.fadey.mobileassistant.FadeyApp;
import sac.fadey.mobileassistant.data.PrinterConfigStore;

public final class PrintDispatcher {
    private PrintDispatcher() {}

    public static void printModule(Context context, String module, JSONObject payload) throws Exception {
        PrinterConfigStore store = FadeyApp.get().printerConfigStore();
        JSONObject cfg = store.getModule(module);
        if (cfg == null) throw new IllegalStateException("Módulo sin configuración: " + module);
        if (!cfg.optBoolean("enabled", true)) throw new IllegalStateException("Módulo deshabilitado: " + module);
        int paper = cfg.optInt("paperWidth", 80);
        JSONObject data = payload == null ? new JSONObject() : new JSONObject(payload.toString());
        if (!data.has("paperWidth")) data.put("paperWidth", paper);
        byte[] bytes = EscPosBuilder.buildTicket(module, data);
        send(context, cfg, bytes);
    }

    public static void printTest(Context context, String module) throws Exception {
        PrinterConfigStore store = FadeyApp.get().printerConfigStore();
        JSONObject cfg = store.getModule(module);
        if (cfg == null) throw new IllegalStateException("Módulo sin configuración: " + module);
        int paper = cfg.optInt("paperWidth", 80);
        byte[] bytes = EscPosBuilder.buildTest(module, paper);
        send(context, cfg, bytes);
    }

    private static void send(Context context, JSONObject cfg, byte[] bytes) throws Exception {
        String tipo = cfg.optString("tipo", "bluetooth").toLowerCase();
        String address = cfg.optString("address", cfg.optString("nombre", ""));
        int port = cfg.optInt("port", 9100);
        if (address == null || address.trim().isEmpty()
                || "caja".equalsIgnoreCase(address.trim())
                || "cocina".equalsIgnoreCase(address.trim())
                || "bar".equalsIgnoreCase(address.trim())) {
            throw new IllegalStateException(
                    "Sin impresora configurada. En APK → Impresoras guarde tipo (bluetooth/wifi) y dirección MAC o IP. "
                            + "La cola del sistema ya funciona; al conectar la térmica imprimirá sola."
            );
        }
        switch (tipo) {
            case "wifi":
            case "red":
            case "tcp":
                TcpPrinter.print(address, port, bytes);
                break;
            case "usb":
                UsbEscPosPrinter.print(context, address, bytes);
                break;
            case "bluetooth":
            case "bt":
            default:
                BluetoothPrinter.print(context, address, bytes);
                break;
        }
    }
}
