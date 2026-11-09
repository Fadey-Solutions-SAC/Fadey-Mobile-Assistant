package sac.fadey.mobileassistant.print;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Context;

import java.io.OutputStream;
import java.util.UUID;

public final class BluetoothPrinter {
    private static final UUID SPP = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");

    private BluetoothPrinter() {}

    public static void print(Context context, String macAddress, byte[] data) throws Exception {
        if (macAddress == null || macAddress.trim().isEmpty()) {
            throw new IllegalArgumentException(
                    "Configure la MAC Bluetooth en Impresoras (ej. AA:BB:CC:DD:EE:FF). El ticket queda listo; falta la impresora."
            );
        }
        BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
        if (adapter == null) throw new IllegalStateException("Bluetooth no disponible");
        if (!adapter.isEnabled()) throw new IllegalStateException("Bluetooth desactivado");
        BluetoothDevice device = adapter.getRemoteDevice(macAddress.trim());
        BluetoothSocket socket = device.createRfcommSocketToServiceRecord(SPP);
        adapter.cancelDiscovery();
        try {
            socket.connect();
            OutputStream out = socket.getOutputStream();
            out.write(data);
            out.flush();
        } finally {
            try { socket.close(); } catch (Exception ignored) {}
        }
    }
}
