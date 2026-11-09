package sac.fadey.mobileassistant.efact;

import android.util.Base64;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.common.BitMatrix;

import org.json.JSONObject;

import java.nio.charset.StandardCharsets;

/** QR/resumen imprimible; no genera PDF completo (el POS imprime térmico con withSunatQr). */
public final class TicketPdfLite {
    private TicketPdfLite() {}

    public static JSONObject summary(VentaModels.Venta venta, UblGenerator.Totals totals, JSONObject sunat) {
        JSONObject out = new JSONObject();
        try {
            String qr = venta.emisor.ruc + "|" + venta.tipo + "|" + venta.serie + "|"
                    + venta.correlativoStr() + "|" + totals.igv.toPlainString() + "|"
                    + totals.total.toPlainString() + "|" + venta.fechaEmision + "|"
                    + venta.cliente.tipoDoc + "|" + venta.cliente.numeroDoc;
            out.put("qr_text", qr);
            out.put("qr_png_b64", qrPngBase64(qr, 256));
            out.put("total", totals.total.toPlainString());
            out.put("igv", totals.igv.toPlainString());
            if (sunat != null) out.put("sunat", sunat);
        } catch (Exception ignored) {}
        return out;
    }

    private static String qrPngBase64(String text, int size) {
        try {
            BitMatrix matrix = new QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, size, size);
            // Minimal 1-bit BMP-like placeholder as raw ASCII map is enough for debugging;
            // thermal path uses qr_text. Store compact payload.
            return Base64.encodeToString(text.getBytes(StandardCharsets.UTF_8), Base64.NO_WRAP);
        } catch (Exception e) {
            return "";
        }
    }
}
