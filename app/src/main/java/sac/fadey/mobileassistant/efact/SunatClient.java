package sac.fadey.mobileassistant.efact;

import android.util.Base64;

import org.json.JSONObject;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public final class SunatClient {
    public static final String BETA_URL = "https://e-beta.sunat.gob.pe/ol-ti-itcpfegem-beta/billService";
    public static final String PROD_URL = "https://e-factura.sunat.gob.pe/ol-ti-itcpfegem/billService";

    private static final OkHttpClient HTTP = new OkHttpClient.Builder().build();
    private static final MediaType XML = MediaType.parse("text/xml; charset=utf-8");

    private SunatClient() {}

    public static byte[] zipXml(byte[] xmlBytes, String xmlFileName) throws Exception {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(bos)) {
            zos.putNextEntry(new ZipEntry(xmlFileName));
            zos.write(xmlBytes);
            zos.closeEntry();
        }
        return bos.toByteArray();
    }

    public static JSONObject sendBill(String ambiente, String ruc, String usuarioSol, String claveSol,
                                      String nombreBase, byte[] signedXml) throws Exception {
        String url = "produccion".equalsIgnoreCase(ambiente) ? PROD_URL : BETA_URL;
        String zipName = nombreBase + ".zip";
        byte[] zip = zipXml(signedXml, nombreBase + ".xml");
        String b64 = Base64.encodeToString(zip, Base64.NO_WRAP);
        String user = (ruc == null ? "" : ruc) + (usuarioSol == null ? "" : usuarioSol);
        String soap = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<soapenv:Envelope xmlns:soapenv=\"http://schemas.xmlsoap.org/soap/envelope/\" xmlns:ser=\"http://service.sunat.gob.pe\" xmlns:wsse=\"http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-wssecurity-secext-1.0.xsd\">"
                + "<soapenv:Header><wsse:Security><wsse:UsernameToken>"
                + "<wsse:Username>" + esc(user) + "</wsse:Username>"
                + "<wsse:Password Type=\"http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-username-token-profile-1.0#PasswordText\">" + esc(claveSol) + "</wsse:Password>"
                + "</wsse:UsernameToken></wsse:Security></soapenv:Header>"
                + "<soapenv:Body><ser:sendBill>"
                + "<fileName>" + esc(zipName) + "</fileName>"
                + "<contentFile>" + b64 + "</contentFile>"
                + "</ser:sendBill></soapenv:Body></soapenv:Envelope>";

        Request req = new Request.Builder()
                .url(url)
                .post(RequestBody.create(soap, XML))
                .header("SOAPAction", "")
                .build();
        try (Response resp = HTTP.newCall(req).execute()) {
            String body = resp.body() != null ? resp.body().string() : "";
            return parseResponse(body, resp.isSuccessful());
        }
    }

    private static JSONObject parseResponse(String xmlText, boolean httpOk) throws Exception {
        JSONObject out = new JSONObject();
        out.put("raw", xmlText == null ? "" : xmlText.substring(0, Math.min(xmlText.length(), 4000)));
        if (xmlText == null) {
            out.put("ok", false);
            out.put("mensaje", "Respuesta vacía");
            return out;
        }
        if (xmlText.contains("faultstring")) {
            String fault = extractTag(xmlText, "faultstring");
            out.put("ok", false);
            out.put("mensaje", fault.isEmpty() ? "SOAP Fault" : fault);
            return out;
        }
        String app = extractTag(xmlText, "applicationResponse");
        if (app.isEmpty()) {
            out.put("ok", false);
            out.put("mensaje", "Sin applicationResponse");
            return out;
        }
        byte[] cdrZip = Base64.decode(app, Base64.DEFAULT);
        String cdrXml = extractFirstXmlFromZip(cdrZip);
        String codigo = extractTag(cdrXml, "ResponseCode");
        String mensaje = extractTag(cdrXml, "Description");
        boolean accepted = codigo.equals("0") || codigo.equals("00");
        out.put("ok", accepted || httpOk);
        out.put("codigo", codigo);
        out.put("mensaje", mensaje.isEmpty() ? (accepted ? "Aceptado" : "Respuesta SUNAT") : mensaje);
        out.put("cdr_zip_b64", Base64.encodeToString(cdrZip, Base64.NO_WRAP));
        if (!cdrXml.isEmpty()) out.put("cdr_xml_b64", Base64.encodeToString(cdrXml.getBytes(StandardCharsets.UTF_8), Base64.NO_WRAP));
        return out;
    }

    private static String extractFirstXmlFromZip(byte[] zipBytes) {
        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(zipBytes))) {
            ZipEntry e;
            while ((e = zis.getNextEntry()) != null) {
                if (e.getName() != null && e.getName().toLowerCase().endsWith(".xml")) {
                    ByteArrayOutputStream bos = new ByteArrayOutputStream();
                    byte[] buf = new byte[4096];
                    int n;
                    while ((n = zis.read(buf)) > 0) bos.write(buf, 0, n);
                    return bos.toString(StandardCharsets.UTF_8.name());
                }
            }
        } catch (Exception ignored) {}
        return "";
    }

    private static String extractTag(String xml, String tag) {
        String open = "<" + tag;
        int i = xml.indexOf(open);
        if (i < 0) {
            // namespaced
            i = xml.indexOf(":" + tag);
            if (i < 0) return "";
            i = xml.lastIndexOf('<', i);
        }
        int gt = xml.indexOf('>', i);
        if (gt < 0) return "";
        int close = xml.indexOf("</", gt);
        if (close < 0) return "";
        return xml.substring(gt + 1, close).trim();
    }

    private static String esc(String s) {
        return String.valueOf(s == null ? "" : s)
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }
}
