package sac.fadey.mobileassistant.print;

import org.json.JSONArray;
import org.json.JSONObject;

import java.nio.charset.Charset;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;

/** Port práctico de server/printing/escposBuilder.js (preformatted + layout simple). */
public final class EscPosBuilder {
    private static final Charset LATIN1 = Charset.forName("ISO-8859-1");
    private static final byte[] INIT_LEFT = new byte[]{0x1B, 0x40, 0x1B, 0x61, 0x00};
    private static final byte[] CUT_PARTIAL = new byte[]{0x1D, 0x56, 0x41};
    private static final byte[] FEED = "\n\n\n\n\n".getBytes(LATIN1);

    private EscPosBuilder() {}

    public static int charsPerLine(int paperWidth) {
        return paperWidth <= 58 ? 32 : 48;
    }

    public static byte[] buildTicket(String moduleName, JSONObject data) {
        int paperW = data.optInt("paperWidth", 80);
        if (paperW != 58 && paperW != 80) paperW = 80;
        int width = charsPerLine(paperW);
        boolean preformatted = data.optBoolean("preformatted", false);
        String text = data.optString("text", "");

        List<byte[]> chunks = new ArrayList<>();
        chunks.add(INIT_LEFT);

        if (preformatted && text != null && !text.isEmpty()) {
            String cleaned = stripDebug(text);
            StringBuilder body = new StringBuilder();
            for (String rawPart : cleaned.split("\n", -1)) {
                for (String line : wrapFixed(rawPart, width)) {
                    body.append(padLine(escPosAscii(line), width)).append('\n');
                }
            }
            body.append('\n');
            chunks.add(body.toString().getBytes(LATIN1));
        } else {
            StringBuilder body = new StringBuilder();
            String header = data.optString("restaurantHeader", data.optString("restaurantName", "RESTAURANTE"));
            for (String ln : wrapWords(header.toUpperCase(), width)) body.append(center(ln, width));
            if (data.has("title")) {
                for (String ln : wrapWords(data.optString("title").toUpperCase(), width)) body.append(center(ln, width));
            }
            if (data.has("mesa")) body.append(center("Mesa: " + data.optString("mesa"), width));
            if (data.has("orderNumber")) body.append(center("Pedido: #" + data.opt("orderNumber"), width));
            body.append(center(repeat('-', width), width));
            JSONArray items = data.optJSONArray("items");
            if (items != null) {
                for (int i = 0; i < items.length(); i++) {
                    JSONObject item = items.optJSONObject(i);
                    if (item == null) continue;
                    int qty = item.optInt("quantity", item.optInt("qty", 1));
                    String name = item.optString("product_name", item.optString("name", "Producto"));
                    for (String ln : wrapWords(qty + " " + name, width)) body.append(center(ln, width));
                }
            } else if (!text.isEmpty()) {
                for (String part : text.split("\n")) {
                    for (String ln : wrapWords(part, width)) body.append(center(ln, width));
                }
            }
            body.append('\n').append(center(repeat('-', width), width)).append('\n');
            chunks.add(body.toString().getBytes(LATIN1));
        }

        chunks.add(FEED);
        chunks.add(CUT_PARTIAL);
        return concat(chunks);
    }

    public static byte[] buildTest(String moduleName, int paperWidth) {
        JSONObject data = new JSONObject();
        try {
            data.put("preformatted", true);
            data.put("paperWidth", paperWidth);
            data.put("text", "FADEY MOBILE\nPrueba " + moduleName + "\nOK");
        } catch (Exception ignored) {}
        return buildTicket(moduleName, data);
    }

    private static String stripDebug(String raw) {
        String s = Normalizer.normalize(String.valueOf(raw), Normalizer.Form.NFKC)
                .replace("\r\n", "\n").replace('\r', '\n');
        StringBuilder out = new StringBuilder();
        for (String line : s.split("\n", -1)) {
            String t = line.replace("\uFEFF", "").replaceAll("\\s+$", "");
            String trim = t.trim();
            if (trim.matches("(?i)^m[oó]dulo\\b.*") || trim.matches("(?i)^module\\s*:.*")) continue;
            out.append(t).append('\n');
        }
        String r = out.toString();
        if (r.endsWith("\n")) r = r.substring(0, r.length() - 1);
        return r;
    }

    private static String escPosAscii(String s) {
        String n = Normalizer.normalize(String.valueOf(s).replace('\u00A0', ' '), Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .replace("…", "...");
        StringBuilder b = new StringBuilder(n.length());
        for (int i = 0; i < n.length(); i++) {
            char c = n.charAt(i);
            b.append(c >= 0x20 && c <= 0x7E ? c : '?');
        }
        return b.toString();
    }

    private static List<String> wrapFixed(String line, int width) {
        List<String> out = new ArrayList<>();
        String raw = line == null ? "" : line.replace("\r", "");
        if (raw.isEmpty()) {
            out.add("");
            return out;
        }
        for (int i = 0; i < raw.length(); i += width) {
            out.add(raw.substring(i, Math.min(raw.length(), i + width)));
        }
        return out;
    }

    private static List<String> wrapWords(String text, int width) {
        List<String> out = new ArrayList<>();
        String raw = text == null ? "" : text.trim();
        if (raw.isEmpty()) {
            out.add("");
            return out;
        }
        String[] words = raw.split("\\s+");
        StringBuilder line = new StringBuilder();
        for (String word : words) {
            String next = line.length() == 0 ? word : line + " " + word;
            if (next.length() <= width) {
                line.setLength(0);
                line.append(next);
            } else {
                if (line.length() > 0) out.add(line.toString());
                if (word.length() <= width) {
                    line.setLength(0);
                    line.append(word);
                } else {
                    for (int i = 0; i < word.length(); i += width) {
                        out.add(word.substring(i, Math.min(word.length(), i + width)));
                    }
                    line.setLength(0);
                }
            }
        }
        if (line.length() > 0) out.add(line.toString());
        return out.isEmpty() ? List.of("") : out;
    }

    private static String center(String text, int width) {
        String value = escPosAscii(text == null ? "" : text.trim());
        if (value.length() > width) value = value.substring(0, width);
        if (value.length() >= width) return value + "\n";
        int pad = width - value.length();
        int left = pad / 2;
        int right = pad - left;
        return repeat(' ', left) + value + repeat(' ', right) + "\n";
    }

    private static String padLine(String value, int width) {
        if (value.length() > width) return value.substring(0, width);
        if (value.length() < width) return value + repeat(' ', width - value.length());
        return value;
    }

    private static String repeat(char c, int n) {
        if (n <= 0) return "";
        StringBuilder b = new StringBuilder(n);
        for (int i = 0; i < n; i++) b.append(c);
        return b.toString();
    }

    private static byte[] concat(List<byte[]> parts) {
        int n = 0;
        for (byte[] p : parts) n += p.length;
        byte[] out = new byte[n];
        int o = 0;
        for (byte[] p : parts) {
            System.arraycopy(p, 0, out, o, p.length);
            o += p.length;
        }
        return out;
    }
}
