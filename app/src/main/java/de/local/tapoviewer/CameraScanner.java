package de.local.tapoviewer;

import android.os.Handler;
import android.os.Looper;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public final class CameraScanner {
    public interface Listener {
        void onProgress(int checked, int total);
        void onCamera(Camera camera);
        void onFinished();
    }

    private final Handler main = new Handler(Looper.getMainLooper());
    private volatile boolean cancelled;
    private ExecutorService pool;

    public void scan(String prefix, Listener listener) {
        cancel();
        cancelled = false;
        pool = Executors.newFixedThreadPool(16);
        AtomicInteger checked = new AtomicInteger();
        AtomicInteger remaining = new AtomicInteger(254);

        for (int host = 1; host <= 254; host++) {
            final String ip = prefix + host;
            pool.submit(() -> {
                try {
                    if (!cancelled) {
                        Camera camera = probeRtsp(ip);
                        if (camera != null) main.post(() -> listener.onCamera(camera));
                    }
                } finally {
                    int done = checked.incrementAndGet();
                    if (done % 8 == 0 || done == 254) main.post(() -> listener.onProgress(done, 254));
                    if (remaining.decrementAndGet() == 0) main.post(listener::onFinished);
                }
            });
        }
        pool.shutdown();
    }

    public void cancel() {
        cancelled = true;
        if (pool != null) {
            pool.shutdownNow();
            try { pool.awaitTermination(100, TimeUnit.MILLISECONDS); } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private Camera probeRtsp(String ip) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(ip, 554), 1000);
            socket.setSoTimeout(1500);
            OutputStream out = socket.getOutputStream();
            // Probe the actual Tapo endpoint rather than merely treating every
            // RTSP server (for example a router media service) as a camera.
            String request = "DESCRIBE rtsp://" + ip + ":554/stream1 RTSP/1.0\r\n" +
                    "CSeq: 1\r\nAccept: application/sdp\r\nUser-Agent: CameraStreamer/"+BuildConfig.VERSION_NAME+"\r\n\r\n";
            out.write(request.getBytes(StandardCharsets.US_ASCII));
            out.flush();

            BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.US_ASCII));
            List<String> headers = new ArrayList<>();
            for (int i = 0; i < 20; i++) {
                String line = reader.readLine();
                if (line == null || line.isEmpty()) break;
                headers.add(line);
            }
            if (headers.isEmpty() || !headers.get(0).startsWith("RTSP/")) return null;
            String status = headers.get(0);
            if (!status.contains(" 200 ") && !status.contains(" 401 ")) return null;
            String all = String.join(" ", headers).toLowerCase(Locale.ROOT);
            boolean isTapo = all.contains("tp-link") || all.contains("tapo");
            String description = isTapo ? "Tapo-Kamera erkannt" : "RTSP-Gerät gefunden";
            return new Camera(ip, "Kamera " + ip.substring(ip.lastIndexOf('.') + 1), description, isTapo);
        } catch (Exception ignored) {
            return null;
        }
    }
}
