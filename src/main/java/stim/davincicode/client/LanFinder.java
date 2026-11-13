package stim.davincicode.client;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InterfaceAddress;
import java.net.NetworkInterface;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import stim.davincicode.common.Msg;
import stim.davincicode.common.Proto;

/** 같은 네트워크의 서버를 UDP 브로드캐스트로 찾는다. */
public final class LanFinder {
    private LanFinder() { }

    public static final class Found {
        public String name;
        public String host;
        public int port;
        public int users;
        public String version;
        public boolean local;
        public boolean compatible;
    }

    /** 이 PC의 IPv4 주소들 (친구에게 알려 줄 주소) */
    public static List<String> localAddresses() {
        Set<String> out = new TreeSet<>();
        try {
            for (NetworkInterface ni : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                if (!ni.isUp() || ni.isLoopback() || ni.isVirtual()) continue;
                String n = ni.getDisplayName().toLowerCase();
                if (n.contains("virtual") || n.contains("vmware") || n.contains("hyper-v") || n.contains("vethernet")) continue;
                for (InterfaceAddress ia : ni.getInterfaceAddresses()) {
                    if (ia.getAddress() instanceof Inet4Address) out.add(ia.getAddress().getHostAddress());
                }
            }
        } catch (Exception e) {
            // 네트워크 정보를 못 읽으면 빈 목록
        }
        return new ArrayList<>(out);
    }

    /** 브로드캐스트를 보내고 timeoutMs 동안 답을 모은다 (화면 스레드가 아닌 곳에서 부른다). */
    public static List<Found> scan(int timeoutMs) {
        Map<String, Found> found = new LinkedHashMap<>();
        List<String> mine = localAddresses();
        try (DatagramSocket s = new DatagramSocket()) {
            s.setBroadcast(true);
            byte[] q = Proto.LAN_QUERY.getBytes(StandardCharsets.UTF_8);
            List<InetAddress> targets = new ArrayList<>();
            targets.add(InetAddress.getByName("255.255.255.255"));
            targets.add(InetAddress.getLoopbackAddress());
            try {
                for (NetworkInterface ni : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                    if (!ni.isUp() || ni.isLoopback()) continue;
                    for (InterfaceAddress ia : ni.getInterfaceAddresses()) {
                        if (ia.getBroadcast() != null) targets.add(ia.getBroadcast());
                    }
                }
            } catch (Exception ignore) {
                // 기본 브로드캐스트만 쓴다
            }
            for (InetAddress t : targets) {
                try {
                    s.send(new DatagramPacket(q, q.length, t, Proto.LAN_PORT));
                } catch (Exception ignore) {
                    // 보낼 수 없는 주소는 건너뛴다
                }
            }
            long end = System.currentTimeMillis() + timeoutMs;
            byte[] buf = new byte[512];
            while (true) {
                int left = (int) (end - System.currentTimeMillis());
                if (left <= 0) break;
                s.setSoTimeout(left);
                DatagramPacket p = new DatagramPacket(buf, buf.length);
                try {
                    s.receive(p);
                } catch (SocketTimeoutException e) {
                    break;
                }
                Msg m = Msg.parse(new String(p.getData(), 0, p.getLength(), StandardCharsets.UTF_8));
                if (!m.cmd.equals(Proto.LAN_REPLY)) continue;
                Found f = new Found();
                f.name = m.s(0);
                f.port = m.i(1, Proto.DEFAULT_PORT);
                f.users = m.i(2, 0);
                f.compatible = m.i(3, 0) == Proto.VERSION;
                f.version = m.s(4);
                String addr = p.getAddress().getHostAddress();
                boolean onThisPc;
                try {
                    onThisPc = NetworkInterface.getByInetAddress(p.getAddress()) != null;
                } catch (Exception e) {
                    onThisPc = false;
                }
                f.local = p.getAddress().isLoopbackAddress() || mine.contains(addr) || onThisPc;
                f.host = f.local ? "127.0.0.1" : addr;
                String key = (f.local ? "local" : addr) + ":" + f.port;
                if (!found.containsKey(key) || !f.local) found.put(key, f);
            }
        } catch (Exception e) {
            // 네트워크가 없으면 빈 목록
        }
        return new ArrayList<>(found.values());
    }
}
