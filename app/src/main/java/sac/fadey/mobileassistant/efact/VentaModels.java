package sac.fadey.mobileassistant.efact;

import org.json.JSONArray;
import org.json.JSONObject;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

public final class VentaModels {
    private VentaModels() {}

    public static class Cliente {
        public String tipoDoc = "1";
        public String numeroDoc = "";
        public String razonSocial = "";
        public String direccion = "";
    }

    public static class Emisor {
        public String ruc = "";
        public String razonSocial = "";
        public String nombreComercial = "";
        public String ubigeo = "150101";
        public String direccion = "";
        public String provincia = "LIMA";
        public String departamento = "LIMA";
        public String distrito = "LIMA";
    }

    public static class Linea {
        public String descripcion = "";
        public BigDecimal cantidad = BigDecimal.ONE;
        public BigDecimal precioUnitarioSinIgv = BigDecimal.ZERO;
        public String codigoAfectacionIgv = "10";
    }

    public static class Venta {
        public String tipo = "03";
        public String serie = "B001";
        public int correlativo = 1;
        public String fechaEmision = "";
        public String horaEmision = "12:00:00";
        public String moneda = "PEN";
        public Emisor emisor = new Emisor();
        public Cliente cliente = new Cliente();
        public List<Linea> lineas = new ArrayList<>();
        public BigDecimal porcentajeIgv = new BigDecimal("18");
        public String observaciones = "";

        public String numeroCompleto() {
            return serie + "-" + String.format("%08d", correlativo);
        }

        public String correlativoStr() {
            return String.format("%08d", correlativo);
        }
    }

    public static String normalizarSerie(String serie, String tipo) {
        String prefix = ("01".equals(tipo) || "1".equals(tipo)) ? "F" : "B";
        String raw = String.valueOf(serie == null ? "" : serie).toUpperCase().replaceAll("[^A-Z0-9]", "");
        if (raw.length() == 4 && raw.startsWith(prefix)) return raw;
        String digits = raw.replaceAll("\\D", "");
        if (digits.isEmpty()) digits = "001";
        if (digits.length() > 3) digits = digits.substring(digits.length() - 3);
        while (digits.length() < 3) digits = "0" + digits;
        return prefix + digits;
    }

    public static Venta fromJson(JSONObject data) {
        Venta v = new Venta();
        if (data == null) return v;
        String tipo = String.valueOf(data.opt("tipo"));
        if ("01".equals(tipo) || "1".equals(tipo) || "factura".equalsIgnoreCase(tipo)) v.tipo = "01";
        else v.tipo = "03";
        v.serie = normalizarSerie(data.optString("serie", v.tipo.equals("01") ? "F001" : "B001"), v.tipo);
        v.correlativo = data.optInt("correlativo", 1);
        v.fechaEmision = data.optString("fecha_emision", data.optString("fechaEmision", ""));
        v.horaEmision = data.optString("hora_emision", data.optString("horaEmision", "12:00:00"));
        v.moneda = data.optString("moneda", "PEN");
        v.porcentajeIgv = bd(data.opt("porcentaje_igv"), "18");
        v.observaciones = data.optString("observaciones", "");

        JSONObject em = data.optJSONObject("emisor");
        if (em != null) {
            v.emisor.ruc = em.optString("ruc", "");
            v.emisor.razonSocial = em.optString("razon_social", em.optString("razonSocial", ""));
            v.emisor.nombreComercial = em.optString("nombre_comercial", em.optString("nombreComercial", v.emisor.razonSocial));
            v.emisor.ubigeo = em.optString("ubigeo", "150101");
            v.emisor.direccion = em.optString("direccion", "");
            v.emisor.provincia = em.optString("provincia", "LIMA");
            v.emisor.departamento = em.optString("departamento", "LIMA");
            v.emisor.distrito = em.optString("distrito", "LIMA");
        }

        JSONObject cl = data.optJSONObject("cliente");
        if (cl != null) {
            v.cliente.tipoDoc = cl.optString("tipo_doc", cl.optString("tipoDoc", "1"));
            v.cliente.numeroDoc = cl.optString("numero_doc", cl.optString("numeroDoc", ""));
            v.cliente.razonSocial = cl.optString("razon_social", cl.optString("razonSocial", ""));
            v.cliente.direccion = cl.optString("direccion", "");
        }

        JSONArray arr = data.optJSONArray("lineas");
        if (arr != null) {
            for (int i = 0; i < arr.length(); i++) {
                JSONObject ln = arr.optJSONObject(i);
                if (ln == null) continue;
                Linea line = new Linea();
                line.descripcion = ln.optString("descripcion", "Item");
                line.cantidad = bd(ln.opt("cantidad"), "1");
                line.precioUnitarioSinIgv = bd(ln.opt("precio_unitario_sin_igv"), "0");
                String code = ln.optString("codigo_afectacion_igv", ln.optString("codigoAfectacionIgv", "10"));
                if (!code.equals("10") && !code.equals("20") && !code.equals("30")) code = "10";
                line.codigoAfectacionIgv = code;
                v.lineas.add(line);
            }
        }
        return v;
    }

    public static BigDecimal money(BigDecimal d) {
        return d.setScale(2, RoundingMode.HALF_UP);
    }

    private static BigDecimal bd(Object raw, String def) {
        try {
            if (raw == null) return new BigDecimal(def);
            return new BigDecimal(String.valueOf(raw).trim());
        } catch (Exception e) {
            return new BigDecimal(def);
        }
    }
}
