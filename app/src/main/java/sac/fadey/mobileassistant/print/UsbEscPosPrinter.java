package sac.fadey.mobileassistant.print;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.hardware.usb.UsbConstants;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbEndpoint;
import android.hardware.usb.UsbInterface;
import android.hardware.usb.UsbManager;
import android.os.Build;

import java.util.HashMap;

/** Envío ESC/POS básico por USB Host (bulk OUT). */
public final class UsbEscPosPrinter {
    public static final String ACTION_USB_PERMISSION = "sac.fadey.mobileassistant.USB_PERMISSION";

    private UsbEscPosPrinter() {}

    public static void print(Context context, String deviceNameHint, byte[] data) throws Exception {
        UsbManager manager = (UsbManager) context.getSystemService(Context.USB_SERVICE);
        if (manager == null) throw new IllegalStateException("USB no disponible");
        HashMap<String, UsbDevice> map = manager.getDeviceList();
        if (map == null || map.isEmpty()) throw new IllegalStateException("No hay dispositivos USB");
        UsbDevice target = null;
        String hint = deviceNameHint == null ? "" : deviceNameHint.trim().toLowerCase();
        for (UsbDevice d : map.values()) {
            String name = (d.getDeviceName() + " " + d.getProductName()).toLowerCase();
            if (hint.isEmpty() || name.contains(hint) || String.valueOf(d.getDeviceId()).equals(hint)) {
                target = d;
                break;
            }
        }
        if (target == null) target = map.values().iterator().next();
        if (!manager.hasPermission(target)) {
            int flags = Build.VERSION.SDK_INT >= 31 ? PendingIntent.FLAG_MUTABLE : 0;
            PendingIntent pi = PendingIntent.getBroadcast(context, 0, new Intent(ACTION_USB_PERMISSION), flags);
            manager.requestPermission(target, pi);
            throw new IllegalStateException("Permiso USB pendiente; reintente tras aceptar");
        }
        UsbInterface iface = null;
        UsbEndpoint epOut = null;
        for (int i = 0; i < target.getInterfaceCount(); i++) {
            UsbInterface cand = target.getInterface(i);
            for (int e = 0; e < cand.getEndpointCount(); e++) {
                UsbEndpoint ep = cand.getEndpoint(e);
                if (ep.getType() == UsbConstants.USB_ENDPOINT_XFER_BULK
                        && ep.getDirection() == UsbConstants.USB_DIR_OUT) {
                    iface = cand;
                    epOut = ep;
                    break;
                }
            }
            if (epOut != null) break;
        }
        if (iface == null || epOut == null) throw new IllegalStateException("Sin endpoint bulk OUT");
        UsbDeviceConnection conn = manager.openDevice(target);
        if (conn == null) throw new IllegalStateException("No se pudo abrir USB");
        try {
            if (!conn.claimInterface(iface, true)) {
                throw new IllegalStateException("No se pudo reclamar interfaz USB");
            }
            int offset = 0;
            while (offset < data.length) {
                int len = Math.min(epOut.getMaxPacketSize(), data.length - offset);
                int written = conn.bulkTransfer(epOut, data, offset, len, 5000);
                if (written < 0) throw new IllegalStateException("Error bulkTransfer USB");
                offset += written;
            }
        } finally {
            try { conn.releaseInterface(iface); } catch (Exception ignored) {}
            conn.close();
        }
    }
}
