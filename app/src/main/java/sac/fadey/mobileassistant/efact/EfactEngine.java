package sac.fadey.mobileassistant.efact;

import android.util.Base64;

import org.json.JSONObject;

import sac.fadey.mobileassistant.FadeyApp;
import sac.fadey.mobileassistant.data.SecureStore;

public final class EfactEngine {
    private EfactEngine() {}

    public static JSONObject emitir(JSONObject sale) {
        JSONObject result = new JSONObject();
        try {
            if (sale == null) throw new IllegalArgumentException("sale vacío");
            VentaModels.Venta venta = VentaModels.fromJson(sale);
            UblGenerator.Result ubl = UblGenerator.generate(venta);

            byte[] pfx = resolvePfx(sale);
            String pwd = resolvePfxPassword(sale);
            if (pfx == null || pfx.length == 0) {
                throw new IllegalStateException("Falta certificado .pfx (panel_certificado o importado en app)");
            }
            byte[] signed = XmlSigner.sign(ubl.xmlBytes, pfx, pwd);

            JSONObject panel = sale.optJSONObject("panel_credenciales");
            if (panel == null) panel = new JSONObject();
            String ambiente = panel.optString("ambiente", "beta");
            String ruc = panel.optString("ruc", venta.emisor.ruc);
            String usuario = panel.optString("usuario_sol", "");
            String clave = panel.optString("clave_sol", "");
            boolean dryRun = sale.optBoolean("dry_run", false) || sale.optBoolean("skip_sunat", false);

            JSONObject sunat;
            if (dryRun) {
                sunat = new JSONObject();
                sunat.put("ok", true);
                sunat.put("mensaje", "dry_run");
                sunat.put("codigo", "0");
            } else {
                sunat = SunatClient.sendBill(ambiente, ruc, usuario, clave, ubl.nombreBase, signed);
            }

            JSONObject files = new JSONObject();
            files.put("xml_sin_firma", Base64.encodeToString(ubl.xmlBytes, Base64.NO_WRAP));
            files.put("xml_firmado", Base64.encodeToString(signed, Base64.NO_WRAP));
            if (sunat.has("cdr_zip_b64")) files.put("cdr_zip", sunat.optString("cdr_zip_b64"));
            if (sunat.has("cdr_xml_b64")) files.put("cdr_xml", sunat.optString("cdr_xml_b64"));

            JSONObject ticket = TicketPdfLite.summary(venta, ubl.totals, sunat);

            result.put("ok", sunat.optBoolean("ok", false));
            result.put("nombre_base", ubl.nombreBase);
            result.put("mensaje", sunat.optString("mensaje", ""));
            result.put("sunat", sunat);
            result.put("files_base64", files);
            result.put("ticket", ticket);
            result.put("totals", new JSONObject()
                    .put("op_gravadas", ubl.totals.opGravadas.toPlainString())
                    .put("op_exoneradas", ubl.totals.opExoneradas.toPlainString())
                    .put("op_inafectas", ubl.totals.opInafectas.toPlainString())
                    .put("igv", ubl.totals.igv.toPlainString())
                    .put("total", ubl.totals.total.toPlainString()));
        } catch (Exception e) {
            try {
                result.put("ok", false);
                result.put("error", e.getMessage() == null ? e.toString() : e.getMessage());
            } catch (Exception ignored) {}
            FadeyApp app = FadeyApp.get();
            if (app != null) app.secureStore().setLastError(String.valueOf(e.getMessage()));
        }
        return result;
    }

    private static byte[] resolvePfx(JSONObject sale) {
        try {
            JSONObject cert = sale.optJSONObject("panel_certificado");
            if (cert != null) {
                String b64 = cert.optString("pfx_base64", "");
                if (!b64.isEmpty()) return Base64.decode(b64, Base64.DEFAULT);
            }
            SecureStore store = FadeyApp.get().secureStore();
            String stored = store.getPfxBase64();
            if (!stored.isEmpty()) return Base64.decode(stored, Base64.DEFAULT);
        } catch (Exception ignored) {}
        return null;
    }

    private static String resolvePfxPassword(JSONObject sale) {
        JSONObject cert = sale.optJSONObject("panel_certificado");
        if (cert != null) {
            String pwd = cert.optString("password", "");
            if (!pwd.isEmpty()) return pwd;
        }
        return FadeyApp.get().secureStore().getPfxPassword();
    }
}
