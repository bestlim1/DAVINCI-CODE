package stim.davincicode.server;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 서버 포트로 들어온 연결의 "입구". 첫 바이트를 보고 누가 왔는지 가른다.
 *  - PC 앱: writeUTF는 길이 2바이트로 시작하므로 첫 바이트가 0 → 게임 연결 (TCP)
 *  - 웹 브라우저: "GET /..." 처럼 영문 대문자로 시작 → HTTP
 *      · GET /ws + Upgrade: websocket → WebSocket 게임 연결
 *      · GET /, /app.js … → 웹 게임 화면 파일을 보내 준다
 * 그래서 친구에게 알려 준 "주소:포트"를 브라우저 주소창에 넣으면 바로 웹으로 할 수 있다.
 */
final class Gate {
    private Gate() { }

    private static final String WS_GUID = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11";
    private static final int MAX_HEADER = 16 * 1024;
    private static final Map<String, String[]> FILES = new HashMap<>();
    private static final Map<String, byte[]> cache = new ConcurrentHashMap<>();

    static {
        String html = "text/html; charset=utf-8", js = "text/javascript; charset=utf-8", css = "text/css; charset=utf-8";
        FILES.put("/", new String[] { "index.html", html });
        FILES.put("/index.html", new String[] { "index.html", html });
        FILES.put("/app.js", new String[] { "app.js", js });
        FILES.put("/proto.js", new String[] { "proto.js", js });
        FILES.put("/style.css", new String[] { "style.css", css });
        FILES.put("/icon-192.png", new String[] { "icon-192.png", "image/png" });
        FILES.put("/icon-512.png", new String[] { "icon-512.png", "image/png" });
        FILES.put("/favicon.ico", new String[] { "icon-192.png", "image/png" });
        FILES.put("/manifest.webmanifest", new String[] { "manifest.webmanifest", "application/manifest+json; charset=utf-8" });
    }

    /** 접속마다 짧게 쓰는 스레드에서 입구 처리를 한다 (느린 상대가 접속 스레드를 막지 못하게). */
    static void dispatch(Socket s, GameServer server, Hub hub) {
        Thread t = new Thread(() -> handle(s, server, hub), "dc-gate");
        t.setDaemon(true);
        t.start();
    }

    private static void handle(Socket s, GameServer server, Hub hub) {
        try {
            s.setTcpNoDelay(true);
            s.setSoTimeout(15_000); // 15초 안에 아무것도 안 보내면 끊는다
            BufferedInputStream in = new BufferedInputStream(s.getInputStream(), 8192);
            in.mark(1);
            int first = in.read();
            in.reset();
            if (first < 0) {
                s.close();
                return;
            }
            if (first >= 'A' && first <= 'Z') {
                http(s, in, server, hub);
                return;
            }
            if (first != 0) {
                // 0x16 = https(TLS) 접속 시도. 크롬은 주소창에 "주소:포트"만 넣으면 https를 먼저 시도하는데,
                // 이 서버는 http만 하므로 바로 끊어서 크롬이 기다리지 않고 http로 다시 접속하게 한다.
                // 그 밖의 알 수 없는 접속도 PC 앱(첫 바이트 0)이 아니면 받지 않는다.
                quietClose(s);
                return;
            }
            s.setSoTimeout(0);
            Connection c = new Connection(s, new Transport.Tcp(s, in));
            server.post(() -> hub.onConnect(c));
            c.start(server, hub);
        } catch (IOException e) {
            quietClose(s);
        }
    }

    // ================================================================ HTTP

    private static void http(Socket s, InputStream in, GameServer server, Hub hub) throws IOException {
        String head = readHead(in);
        if (head == null) {
            quietClose(s);
            return;
        }
        String[] lines = head.split("\r\n");
        String[] req = lines[0].split(" ");
        Map<String, String> headers = new HashMap<>();
        for (int i = 1; i < lines.length; i++) {
            int c = lines[i].indexOf(':');
            if (c > 0) headers.put(lines[i].substring(0, c).trim().toLowerCase(Locale.ROOT), lines[i].substring(c + 1).trim());
        }
        OutputStream out = s.getOutputStream();
        if (req.length < 2) {
            respond(out, 400, "text/plain; charset=utf-8", "잘못된 요청".getBytes(StandardCharsets.UTF_8), false);
            quietClose(s);
            return;
        }
        String method = req[0];
        String path = req[1];
        int q = path.indexOf('?');
        if (q >= 0) path = path.substring(0, q);

        boolean upgrade = "websocket".equalsIgnoreCase(headers.getOrDefault("upgrade", ""))
                && headers.getOrDefault("connection", "").toLowerCase(Locale.ROOT).contains("upgrade");
        if (path.equals("/ws")) {
            String key = headers.get("sec-websocket-key");
            if (!method.equals("GET") || !upgrade || key == null) {
                respond(out, 400, "text/plain; charset=utf-8", "WebSocket 연결만 받아요".getBytes(StandardCharsets.UTF_8), false);
                quietClose(s);
                return;
            }
            String accept = Base64.getEncoder().encodeToString(sha1(key.trim() + WS_GUID));
            String resp = "HTTP/1.1 101 Switching Protocols\r\nUpgrade: websocket\r\nConnection: Upgrade\r\n"
                    + "Sec-WebSocket-Accept: " + accept + "\r\n\r\n";
            out.write(resp.getBytes(StandardCharsets.US_ASCII));
            out.flush();
            s.setSoTimeout(0);
            Connection c = new Connection(s, new Transport.WebSocket(in, out));
            server.post(() -> hub.onConnect(c));
            c.start(server, hub);
            return;
        }

        if (!method.equals("GET") && !method.equals("HEAD")) {
            respond(out, 405, "text/plain; charset=utf-8", "GET만 받아요".getBytes(StandardCharsets.UTF_8), false);
            quietClose(s);
            return;
        }
        boolean headOnly = method.equals("HEAD");
        if (path.equals("/health")) {
            String json = "{\"ok\":true,\"name\":\"" + server.name().replace("\"", "'") + "\",\"online\":" + server.online() + "}";
            respond(out, 200, "application/json; charset=utf-8", json.getBytes(StandardCharsets.UTF_8), headOnly);
        } else {
            String[] f = FILES.get(path);
            byte[] body = f == null ? null : load(f[0]);
            if (body == null) {
                respond(out, 404, "text/plain; charset=utf-8", "찾는 페이지가 없어요".getBytes(StandardCharsets.UTF_8), headOnly);
            } else {
                respond(out, 200, f[1], body, headOnly);
            }
        }
        quietClose(s);
    }

    /** 빈 줄(\r\n\r\n)까지 요청 머리를 읽는다. 너무 길면 null */
    private static String readHead(InputStream in) throws IOException {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        int state = 0;
        while (buf.size() < MAX_HEADER) {
            int b = in.read();
            if (b < 0) return null;
            buf.write(b);
            if ((state == 0 || state == 2) && b == '\r') state++;
            else if ((state == 1 || state == 3) && b == '\n') state++;
            else state = b == '\r' ? 1 : 0;
            if (state == 4) return new String(buf.toByteArray(), StandardCharsets.ISO_8859_1);
        }
        return null;
    }

    private static void respond(OutputStream out, int code, String type, byte[] body, boolean headOnly) throws IOException {
        String reason = code == 200 ? "OK" : code == 404 ? "Not Found" : code == 405 ? "Method Not Allowed" : "Bad Request";
        StringBuilder h = new StringBuilder();
        h.append("HTTP/1.1 ").append(code).append(' ').append(reason).append("\r\n");
        h.append("Content-Type: ").append(type).append("\r\n");
        h.append("Content-Length: ").append(body.length).append("\r\n");
        h.append("Cache-Control: no-cache\r\n");
        h.append("X-Content-Type-Options: nosniff\r\n");
        h.append("Referrer-Policy: no-referrer\r\n");
        if (type.startsWith("text/html")) {
            h.append("Content-Security-Policy: default-src 'self'; connect-src 'self' ws: wss:; img-src 'self' data:; "
                    + "style-src 'self' 'unsafe-inline'; script-src 'self'; frame-ancestors 'none'\r\n");
        }
        h.append("Connection: close\r\n\r\n");
        out.write(h.toString().getBytes(StandardCharsets.UTF_8));
        if (!headOnly) out.write(body);
        out.flush();
    }

    /** 앱(jar) 안에 들어 있는 웹 파일을 읽는다. 처음 한 번만 읽고 메모리에 둔다. */
    private static byte[] load(String name) {
        return cache.computeIfAbsent(name, n -> {
            try (InputStream r = Gate.class.getResourceAsStream("/stim/davincicode/web/" + n)) {
                return r == null ? null : r.readAllBytes();
            } catch (IOException e) {
                return null;
            }
        });
    }

    private static byte[] sha1(String s) {
        try {
            return MessageDigest.getInstance("SHA-1").digest(s.getBytes(StandardCharsets.US_ASCII));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static void quietClose(Socket s) {
        try {
            s.close();
        } catch (IOException ignore) {
            // 이미 닫힘
        }
    }
}
