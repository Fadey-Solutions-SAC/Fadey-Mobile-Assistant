package sac.fadey.mobileassistant.efact;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/** Genera Invoice UBL 2.1 alineado al bot Python (EXO 9997 / INA 9998 / IGV 1000 + FormaPago). */
public final class UblGenerator {
    private UblGenerator() {}

    public static class Totals {
        public BigDecimal opGravadas = BigDecimal.ZERO;
        public BigDecimal opExoneradas = BigDecimal.ZERO;
        public BigDecimal opInafectas = BigDecimal.ZERO;
        public BigDecimal igv = BigDecimal.ZERO;
        public BigDecimal total = BigDecimal.ZERO;
        public BigDecimal valorVenta = BigDecimal.ZERO;
    }

    public static class Result {
        public byte[] xmlBytes;
        public String nombreBase;
        public Totals totals;
    }

    public static Totals calc(VentaModels.Venta venta) {
        Totals t = new Totals();
        BigDecimal rate = venta.porcentajeIgv.divide(new BigDecimal("100"), 6, RoundingMode.HALF_UP);
        for (VentaModels.Linea ln : venta.lineas) {
            BigDecimal ext = VentaModels.money(ln.cantidad.multiply(ln.precioUnitarioSinIgv));
            String code = ln.codigoAfectacionIgv;
            if ("10".equals(code)) {
                t.opGravadas = t.opGravadas.add(ext);
                t.igv = t.igv.add(VentaModels.money(ext.multiply(rate)));
            } else if ("30".equals(code)) {
                t.opInafectas = t.opInafectas.add(ext);
            } else {
                t.opExoneradas = t.opExoneradas.add(ext);
            }
        }
        t.valorVenta = t.opGravadas.add(t.opExoneradas).add(t.opInafectas);
        t.total = VentaModels.money(t.valorVenta.add(t.igv));
        t.opGravadas = VentaModels.money(t.opGravadas);
        t.opExoneradas = VentaModels.money(t.opExoneradas);
        t.opInafectas = VentaModels.money(t.opInafectas);
        t.igv = VentaModels.money(t.igv);
        return t;
    }

    public static Result generate(VentaModels.Venta venta) {
        if (venta.lineas == null || venta.lineas.isEmpty()) {
            throw new IllegalArgumentException("La venta debe tener al menos una línea");
        }
        Totals totals = calc(venta);
        String moneda = venta.moneda;
        StringBuilder xml = new StringBuilder();
        xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        xml.append("<Invoice xmlns=\"urn:oasis:names:specification:ubl:schema:xsd:Invoice-2\"");
        xml.append(" xmlns:cac=\"urn:oasis:names:specification:ubl:schema:xsd:CommonAggregateComponents-2\"");
        xml.append(" xmlns:cbc=\"urn:oasis:names:specification:ubl:schema:xsd:CommonBasicComponents-2\"");
        xml.append(" xmlns:ext=\"urn:oasis:names:specification:ubl:schema:xsd:CommonExtensionComponents-2\"");
        xml.append(" xmlns:ds=\"http://www.w3.org/2000/09/xmldsig#\">\n");
        xml.append("  <ext:UBLExtensions><ext:UBLExtension><ext:ExtensionContent/></ext:UBLExtension></ext:UBLExtensions>\n");
        xml.append("  <cbc:UBLVersionID>2.1</cbc:UBLVersionID>\n");
        xml.append("  <cbc:CustomizationID>2.0</cbc:CustomizationID>\n");
        xml.append("  <cbc:ProfileID>0101</cbc:ProfileID>\n");
        xml.append("  <cbc:ID>").append(esc(venta.numeroCompleto())).append("</cbc:ID>\n");
        xml.append("  <cbc:IssueDate>").append(esc(venta.fechaEmision)).append("</cbc:IssueDate>\n");
        xml.append("  <cbc:IssueTime>").append(esc(venta.horaEmision)).append("</cbc:IssueTime>\n");
        xml.append("  <cbc:InvoiceTypeCode listID=\"0101\" listAgencyName=\"PE:SUNAT\" listName=\"SUNAT:Identificador de Tipo de Documento\" listURI=\"urn:pe:gob:sunat:cpe:see:gem:catalogos:catalogo01\">")
                .append(venta.tipo).append("</cbc:InvoiceTypeCode>\n");
        xml.append("  <cbc:Note>").append(esc(venta.observaciones.isEmpty() ? "-" : venta.observaciones)).append("</cbc:Note>\n");
        xml.append("  <cbc:DocumentCurrencyCode listID=\"ISO 4217 Alpha\" listName=\"Currency\" listAgencyName=\"United Nations Economic Commission for Europe\">")
                .append(esc(moneda)).append("</cbc:DocumentCurrencyCode>\n");
        xml.append("  <cac:Signature><cbc:ID>IDSignKG</cbc:ID><cac:SignatoryParty><cac:PartyIdentification><cbc:ID>")
                .append(esc(venta.emisor.ruc)).append("</cbc:ID></cac:PartyIdentification></cac:SignatoryParty>");
        xml.append("<cac:DigitalSignatureAttachment><cac:ExternalReference><cbc:URI>#SignatureSP</cbc:URI></cac:ExternalReference></cac:DigitalSignatureAttachment></cac:Signature>\n");

        // Supplier
        xml.append("  <cac:AccountingSupplierParty><cac:Party>");
        xml.append("<cac:PartyIdentification><cbc:ID schemeID=\"6\" schemeName=\"Documento de Identidad\" schemeAgencyName=\"PE:SUNAT\">")
                .append(esc(venta.emisor.ruc)).append("</cbc:ID></cac:PartyIdentification>");
        xml.append("<cac:PartyLegalEntity><cbc:RegistrationName>").append(esc(venta.emisor.razonSocial)).append("</cbc:RegistrationName>");
        xml.append("<cac:RegistrationAddress>");
        xml.append("<cbc:ID schemeName=\"Ubigeos\" schemeAgencyName=\"PE:INEI\">").append(esc(venta.emisor.ubigeo)).append("</cbc:ID>");
        xml.append("<cbc:AddressTypeCode>0000</cbc:AddressTypeCode>");
        xml.append("<cbc:CityName>").append(esc(venta.emisor.distrito)).append("</cbc:CityName>");
        xml.append("<cbc:CountrySubentity>").append(esc(venta.emisor.departamento)).append("</cbc:CountrySubentity>");
        xml.append("<cbc:District>").append(esc(venta.emisor.distrito)).append("</cbc:District>");
        xml.append("<cac:AddressLine><cbc:Line>").append(esc(venta.emisor.direccion)).append("</cbc:Line></cac:AddressLine>");
        xml.append("<cac:Country><cbc:IdentificationCode listID=\"ISO 3166-1\" listAgencyName=\"United Nations Economic Commission for Europe\" listName=\"Country\">PE</cbc:IdentificationCode></cac:Country>");
        xml.append("</cac:RegistrationAddress></cac:PartyLegalEntity></cac:Party></cac:AccountingSupplierParty>\n");

        // Customer
        xml.append("  <cac:AccountingCustomerParty><cac:Party>");
        xml.append("<cac:PartyIdentification><cbc:ID schemeID=\"").append(esc(venta.cliente.tipoDoc))
                .append("\" schemeName=\"Documento de Identidad\" schemeAgencyName=\"PE:SUNAT\">")
                .append(esc(venta.cliente.numeroDoc)).append("</cbc:ID></cac:PartyIdentification>");
        xml.append("<cac:PartyLegalEntity><cbc:RegistrationName>").append(esc(venta.cliente.razonSocial)).append("</cbc:RegistrationName>");
        if (!venta.cliente.direccion.isEmpty()) {
            xml.append("<cac:RegistrationAddress><cac:AddressLine><cbc:Line>").append(esc(venta.cliente.direccion))
                    .append("</cbc:Line></cac:AddressLine><cac:Country><cbc:IdentificationCode listID=\"ISO 3166-1\" listAgencyName=\"United Nations Economic Commission for Europe\" listName=\"Country\">PE</cbc:IdentificationCode></cac:Country></cac:RegistrationAddress>");
        }
        xml.append("</cac:PartyLegalEntity></cac:Party></cac:AccountingCustomerParty>\n");

        xml.append("  <cac:PaymentTerms><cbc:ID>FormaPago</cbc:ID><cbc:PaymentMeansID>Contado</cbc:PaymentMeansID></cac:PaymentTerms>\n");

        // Tax total grouped
        Map<String, BigDecimal[]> groups = new LinkedHashMap<>();
        BigDecimal rate = venta.porcentajeIgv.divide(new BigDecimal("100"), 6, RoundingMode.HALF_UP);
        for (VentaModels.Linea ln : venta.lineas) {
            String code = ln.codigoAfectacionIgv;
            BigDecimal ext = VentaModels.money(ln.cantidad.multiply(ln.precioUnitarioSinIgv));
            BigDecimal tax = "10".equals(code) ? VentaModels.money(ext.multiply(rate)) : BigDecimal.ZERO;
            BigDecimal[] g = groups.getOrDefault(code, new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO});
            g[0] = g[0].add(ext);
            g[1] = g[1].add(tax);
            groups.put(code, g);
        }
        xml.append("  <cac:TaxTotal><cbc:TaxAmount currencyID=\"").append(moneda).append("\">")
                .append(money(totals.igv)).append("</cbc:TaxAmount>");
        for (String code : new String[]{"10", "20", "30"}) {
            if (!groups.containsKey(code)) continue;
            BigDecimal[] g = groups.get(code);
            xml.append(taxSubtotal(g[0], g[1], venta.porcentajeIgv, code, moneda));
        }
        xml.append("</cac:TaxTotal>\n");

        xml.append("  <cac:LegalMonetaryTotal>");
        xml.append("<cbc:LineExtensionAmount currencyID=\"").append(moneda).append("\">").append(money(totals.valorVenta)).append("</cbc:LineExtensionAmount>");
        xml.append("<cbc:TaxExclusiveAmount currencyID=\"").append(moneda).append("\">").append(money(totals.valorVenta)).append("</cbc:TaxExclusiveAmount>");
        xml.append("<cbc:TaxInclusiveAmount currencyID=\"").append(moneda).append("\">").append(money(totals.total)).append("</cbc:TaxInclusiveAmount>");
        xml.append("<cbc:PayableAmount currencyID=\"").append(moneda).append("\">").append(money(totals.total)).append("</cbc:PayableAmount>");
        xml.append("</cac:LegalMonetaryTotal>\n");

        int idx = 1;
        for (VentaModels.Linea ln : venta.lineas) {
            BigDecimal lineExt = VentaModels.money(ln.cantidad.multiply(ln.precioUnitarioSinIgv));
            boolean gravado = "10".equals(ln.codigoAfectacionIgv);
            BigDecimal taxAmt = gravado ? VentaModels.money(lineExt.multiply(rate)) : BigDecimal.ZERO;
            BigDecimal puRef = gravado
                    ? VentaModels.money(ln.precioUnitarioSinIgv.multiply(BigDecimal.ONE.add(rate)))
                    : VentaModels.money(ln.precioUnitarioSinIgv);
            xml.append("  <cac:InvoiceLine>");
            xml.append("<cbc:ID>").append(idx++).append("</cbc:ID>");
            xml.append("<cbc:InvoicedQuantity unitCode=\"NIU\" unitCodeListID=\"UN/ECE rec 20\" unitCodeListAgencyName=\"United Nations Economic Commission for Europe\">")
                    .append(money(ln.cantidad)).append("</cbc:InvoicedQuantity>");
            xml.append("<cbc:LineExtensionAmount currencyID=\"").append(moneda).append("\">").append(money(lineExt)).append("</cbc:LineExtensionAmount>");
            xml.append("<cac:PricingReference><cac:AlternativeConditionPrice><cbc:PriceAmount currencyID=\"")
                    .append(moneda).append("\">").append(money(puRef)).append("</cbc:PriceAmount>");
            xml.append("<cbc:PriceTypeCode listName=\"Tipo de Precio\" listAgencyName=\"PE:SUNAT\" listURI=\"urn:pe:gob:sunat:cpe:see:gem:catalogos:catalogo16\">01</cbc:PriceTypeCode>");
            xml.append("</cac:AlternativeConditionPrice></cac:PricingReference>");
            xml.append("<cac:TaxTotal><cbc:TaxAmount currencyID=\"").append(moneda).append("\">").append(money(taxAmt)).append("</cbc:TaxAmount>");
            xml.append(taxSubtotal(lineExt, taxAmt, venta.porcentajeIgv, ln.codigoAfectacionIgv, moneda));
            xml.append("</cac:TaxTotal>");
            xml.append("<cac:Item><cbc:Description>").append(esc(ln.descripcion)).append("</cbc:Description></cac:Item>");
            xml.append("<cac:Price><cbc:PriceAmount currencyID=\"").append(moneda).append("\">")
                    .append(money(ln.precioUnitarioSinIgv)).append("</cbc:PriceAmount></cac:Price>");
            xml.append("</cac:InvoiceLine>\n");
        }
        xml.append("</Invoice>\n");

        Result r = new Result();
        r.xmlBytes = xml.toString().getBytes(StandardCharsets.UTF_8);
        r.nombreBase = venta.emisor.ruc + "-" + venta.tipo + "-" + venta.serie + "-" + venta.correlativoStr();
        r.totals = totals;
        return r;
    }

    private static String taxSubtotal(BigDecimal taxable, BigDecimal tax, BigDecimal pct, String code, String moneda) {
        String cat = "10".equals(code) ? "S" : ("30".equals(code) ? "O" : "E");
        String schemeId = "10".equals(code) ? "1000" : ("30".equals(code) ? "9998" : "9997");
        String schemeName = "10".equals(code) ? "IGV" : ("30".equals(code) ? "INA" : "EXO");
        String taxType = "10".equals(code) ? "VAT" : ("30".equals(code) ? "FRE" : "VAT");
        String pctText = "10".equals(code) ? money(pct) : "0.00";
        return "<cac:TaxSubtotal>"
                + "<cbc:TaxableAmount currencyID=\"" + moneda + "\">" + money(taxable) + "</cbc:TaxableAmount>"
                + "<cbc:TaxAmount currencyID=\"" + moneda + "\">" + money(tax) + "</cbc:TaxAmount>"
                + "<cac:TaxCategory>"
                + "<cbc:ID schemeID=\"UN/ECE 5305\" schemeName=\"Tax Category Identifier\" schemeAgencyName=\"United Nations Economic Commission for Europe\">" + cat + "</cbc:ID>"
                + "<cbc:Percent>" + pctText + "</cbc:Percent>"
                + "<cbc:TaxExemptionReasonCode listAgencyName=\"PE:SUNAT\" listName=\"Afectacion del IGV\" listURI=\"urn:pe:gob:sunat:cpe:see:gem:catalogos:catalogo07\">" + code + "</cbc:TaxExemptionReasonCode>"
                + "<cac:TaxScheme>"
                + "<cbc:ID schemeID=\"UN/ECE 5153\" schemeName=\"Tax Scheme Identifier\" schemeAgencyName=\"United Nations Economic Commission for Europe\">" + schemeId + "</cbc:ID>"
                + "<cbc:Name>" + schemeName + "</cbc:Name>"
                + "<cbc:TaxTypeCode>" + taxType + "</cbc:TaxTypeCode>"
                + "</cac:TaxScheme></cac:TaxCategory></cac:TaxSubtotal>";
    }

    private static String money(BigDecimal d) {
        return VentaModels.money(d).toPlainString();
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
