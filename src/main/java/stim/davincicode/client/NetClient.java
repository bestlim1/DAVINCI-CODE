package stim.davincicode.client;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import javax.swing.SwingUtilities;

import stim.davincicode.common.Msg;
import stim.davincicode.common.Proto;

/**
 * 서버와의 연결. 읽기 스레드는 받은 메시지를 Swing 화면 스레드(EDT)로 넘기고,
 * 쓰기 스레드는 큐에 쌓인 메시지를 보낸다. 10초마다 PING을 보내 연결 상태와 지연 시간(ping)을 확인한다.
 */
public final class NetClient {
    public interface Listener {
        void onMessage(Msg m);

        void onClosed(NetClient source, String reason);
    }

    private static final String CLOSE = new String("\u0000close");

    private final Socket socket;
    private final DataInputStream in;
    private final DataOutputStream out;
    private final LinkedBlockingQueue<String> outbox = new LinkedBlockingQueue<>();
    private final Listener listener;
    private final ScheduledExecutorService pinger;
    private volatile boolean closed;
    private volatile boolean closedByMe;
    private volatile long rtt = -1;
    public final String host;
    public final int port;

    private NetClient(Socket socket, String host, int port, Listener listener) throws IOException {
        this.socket = socket;
        this.host = host;
        this.port = port;
        this.listener = listener;
        socket.setTcpNoDelay(true);
        socket.setKeepAlive(true);
        in = new DataInputStream(new BufferedInputStream(socket.getInputStream()));
        out = new DataOutputStream(new BufferedOutputStream(socket.getOutputStream()));
        pinger = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "dc-ping");
            t.setDaemon(true);
            return t;
        });
    }

    /** 연결한다 (시간이 걸리므로 화면 스레드가 아닌 곳에서 부른다). */
    public static NetClient connect(String host, int port, int timeoutMs, Listener l) throws IOException {
        Socket s = new Socket();
        s.connect(new InetSocketAddress(host, port), timeoutMs);
        NetClient c = new NetClient(s, host, port, l);
        c.start();
        return c;
    }

    private void start() {
        Thread r = new Thread(this::readLoop, "dc-client-read");
        r.setDaemon(true);
        r.start();
        Thread w = new Thread(this::writeLoop, "dc-client-write");
        w.setDaemon(true);
        w.start();
        pinger.scheduleWithFixedDelay(() -> send(Proto.PING, System.nanoTime()), 2, 10, TimeUnit.SECONDS);
    }

    private void readLoop() {
        String reason = "서버와 연결이 끊어졌어요.";
        try {
            while (!closed) {
                String line = in.readUTF();
                Msg m = Msg.parse(line);
                if (m.cmd.equals(Proto.PONG)) {
                    long sent = m.l(0, 0);
                    if (sent > 0) rtt = (System.nanoTime() - sent) / 1_000_000L;
                    continue;
                }
                SwingUtilities.invokeLater(() -> {
                    if (!closedByMe) listener.onMessage(m);
                });
            }
        } catch (IOException e) {
            // 연결 끊김
        }
        shutdown();
        if (!closedByMe) {
            String r = reason;
            SwingUtilities.invokeLater(() -> listener.onClosed(this, r));
        }
    }

    private void writeLoop() {
        try {
            while (true) {
                String line = outbox.take();
                if (line == CLOSE) break;
                out.writeUTF(line);
                if (outbox.isEmpty()) out.flush();
            }
        } catch (IOException | InterruptedException e) {
            // 끊김
        }
        shutdown();
    }

    public void send(String cmd, Object... fields) {
        if (closed) return;
        outbox.offer(Proto.encode(cmd, fields));
    }

    /** 내가 닫는 경우 (onClosed가 불리지 않는다) */
    public void close() {
        closedByMe = true;
        if (closed) return;
        // 쓰기 스레드가 남은 메시지(LEAVE 등)를 다 보낸 뒤 CLOSE를 만나면 연결을 닫는다.
        // 혹시 쓰기가 막혀 있을 때를 대비해 0.5초 뒤에 한 번 더 닫는다 (이미 닫혔으면 예약이 거절되어도 괜찮다).
        try {
            pinger.schedule(this::shutdown, 500, TimeUnit.MILLISECONDS);
        } catch (java.util.concurrent.RejectedExecutionException ignore) {
            // 이미 정리됨
        }
        outbox.offer(CLOSE);
    }

    private void shutdown() {
        closed = true;
        pinger.shutdownNow();
        try {
            socket.close();
        } catch (IOException ignore) {
            // 이미 닫힘
        }
    }

    public boolean isClosed() {
        return closed;
    }

    public long rtt() {
        return rtt;
    }
}
