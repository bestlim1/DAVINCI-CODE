package stim.davincicode.server;

import java.io.IOException;
import java.net.Socket;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * 클라이언트 1명과의 연결. 수업 코드의 UserService를 읽기/쓰기 두 스레드로 나눴다.
 *  - 읽기 스레드: 받은 줄을 게임 로직 스레드에 넘긴다.
 *  - 쓰기 스레드: 보낼 줄을 큐에서 꺼내 보낸다.
 *    게임 로직 스레드는 큐에 넣기만 하므로, 느린 클라이언트 한 명 때문에 서버 전체가 멈추지 않는다.
 *
 * 실제로 바이트를 주고받는 방법은 {@link Transport}가 맡는다.
 * PC 앱은 writeUTF/readUTF(TCP), 웹 브라우저는 WebSocket을 쓰지만 서버 로직(Hub)은 둘을 구분하지 않는다.
 */
final class Connection {
    private static final String CLOSE = new String("\u0000close");
    private static final int MAX_PER_SECOND = 40;

    final Socket socket;
    final String remote;
    final long connectedAt = System.currentTimeMillis();
    private final Transport transport;
    private final LinkedBlockingQueue<String> outbox = new LinkedBlockingQueue<>(4000);
    volatile boolean closed;
    volatile long lastSeen = System.currentTimeMillis();
    User user; // 게임 로직 스레드에서만 읽고 쓴다

    Connection(Socket socket, Transport transport) throws IOException {
        this.socket = socket;
        this.transport = transport;
        this.remote = String.valueOf(socket.getRemoteSocketAddress()).replace("/", "");
        socket.setTcpNoDelay(true);
        socket.setKeepAlive(true);
    }

    /** 앱(TCP)인지 웹(WebSocket)인지 (로그용) */
    String kind() {
        return transport.kind();
    }

    void start(GameServer server, Hub hub) {
        Thread reader = new Thread(() -> readLoop(server, hub), "dc-read-" + remote);
        reader.setDaemon(true);
        reader.start();
        Thread writer = new Thread(this::writeLoop, "dc-write-" + remote);
        writer.setDaemon(true);
        writer.start();
    }

    private void readLoop(GameServer server, Hub hub) {
        long windowStart = System.currentTimeMillis();
        int count = 0;
        try {
            while (!closed) {
                String line = transport.read();
                long now = System.currentTimeMillis();
                lastSeen = now;
                if (now - windowStart >= 1000) {
                    windowStart = now;
                    count = 0;
                }
                if (++count > MAX_PER_SECOND) {
                    server.log("도배로 연결 종료: " + remote);
                    break;
                }
                server.post(() -> hub.onMessage(this, line));
            }
        } catch (IOException e) {
            // 정상 종료(EOF)와 강제 종료(소켓 리셋) 모두 여기로 온다
        }
        close();
        server.post(() -> hub.onDisconnect(this));
    }

    private void writeLoop() {
        try {
            while (true) {
                String line = outbox.take();
                if (line == CLOSE) break;
                transport.write(line);
                if (outbox.isEmpty()) transport.flush();
            }
            transport.flush();
            transport.goodbye();
        } catch (IOException | InterruptedException e) {
            // 상대가 먼저 끊은 경우
        }
        closeSocket();
    }

    void send(String line) {
        if (closed) return;
        if (!outbox.offer(line)) {
            // 큐가 가득 찼다 = 받는 쪽이 너무 느리다. 연결을 끊고 재접속을 기다린다
            close();
        }
    }

    /** 이미 넣은 메시지와 마지막 메시지까지 보낸 뒤 연결을 닫는다. */
    void sendAndClose(String line) {
        if (closed) return;
        outbox.offer(line);
        closed = true;
        outbox.offer(CLOSE);
    }

    void close() {
        if (!closed) {
            closed = true;
            outbox.clear();
            outbox.offer(CLOSE);
        }
        closeSocket();
    }

    private void closeSocket() {
        try {
            socket.close();
        } catch (IOException ignore) {
            // 이미 닫힘
        }
    }
}
