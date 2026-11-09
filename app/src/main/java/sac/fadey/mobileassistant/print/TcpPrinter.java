package sac.fadey.mobileassistant.print;

import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;

public final class TcpPrinter {
    private TcpPrinter() {}

    public static void print(String host, int port, byte[] data) throws Exception {
        if (host == null || host.trim().isEmpty()) {
            throw new IllegalArgumentException("IP de impresora vacía");
        }
        int p = port > 0 ? port : 9100;
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host.trim(), p), 8000);
            socket.setSoTimeout(15000);
            OutputStream out = socket.getOutputStream();
            out.write(data);
            out.flush();
        }
    }
}
