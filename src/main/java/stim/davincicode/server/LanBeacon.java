package stim.davincicode.server;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

import stim.davincicode.common.Proto;

/**
 * 같은 네트워크(같은 Wi-Fi)의 앱이 서버를 자동으로 찾을 수 있게 하는 UDP 응답기.
 * 앱이 브로드캐스트로 "DAVINCICODE?"를 보내면 서버 이름·포트·접속자 수를 답한다.
 */
final class LanBeacon extends Thread {
    private final GameServer server;
    private DatagramSocket socket;

    LanBeacon(GameServer server) {
        super("dc-lan");
        setDaemon(true);
        this.server = server;
    }

    boolean open() {
        try {
            socket = new DatagramSocket(null);
            socket.setReuseAddress(true);
            socket.bind(new InetSocketAddress(Proto.LAN_PORT));
            return true;
        } catch (IOException e) {
            server.log("LAN 검색 응답을 켜지 못함 (포트 " + Proto.LAN_PORT + "): " + e.getMessage());
            return false;
        }
    }

    @Override
    public void run() {
        byte[] buf = new byte[256];
        while (!socket.isClosed()) {
            try {
                DatagramPacket p = new DatagramPacket(buf, buf.length);
                socket.receive(p);
                String q = new String(p.getData(), 0, p.getLength(), StandardCharsets.UTF_8);
                if (!q.equals(Proto.LAN_QUERY)) continue;
                String reply = Proto.encode(Proto.LAN_REPLY, Proto.clean(server.name(), 40), server.port(),
                        server.online(), Proto.VERSION, Proto.APP_VERSION);
                byte[] b = reply.getBytes(StandardCharsets.UTF_8);
                socket.send(new DatagramPacket(b, b.length, p.getSocketAddress()));
            } catch (IOException e) {
                if (!socket.isClosed()) server.log("LAN 응답 오류: " + e.getMessage());
            }
        }
    }

    void close() {
        if (socket != null) socket.close();
    }
}
