package stim.davincicode.server;

import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

/**
 * 한 줄(메시지)을 주고받는 방법. 메시지 내용(프로토콜)은 둘 다 똑같고 포장만 다르다.
 *  - {@link Tcp}: PC 앱. DataOutputStream.writeUTF / readUTF (수업 코드와 같은 방식)
 *  - {@link WebSocket}: 웹 브라우저. RFC 6455 WebSocket 텍스트 프레임
 */
interface Transport {
    /** 다음 메시지 하나. 연결이 끝나면 IOException */
    String read() throws IOException;

    void write(String line) throws IOException;

    void flush() throws IOException;

    /** 닫기 직전에 보낼 인사 (WebSocket close 프레임) */
    default void goodbye() throws IOException {
    }

    String kind();

    // ================================================================ PC 앱

    final class Tcp implements Transport {
        private final DataInputStream in;
        private final DataOutputStream out;

        Tcp(Socket socket, InputStream bufferedIn) throws IOException {
            in = new DataInputStream(bufferedIn);
            out = new DataOutputStream(new BufferedOutputStream(socket.getOutputStream()));
        }

        @Override
        public String read() throws IOException {
            return in.readUTF();
        }

        @Override
        public void write(String line) throws IOException {
            out.writeUTF(line);
        }

        @Override
        public void flush() throws IOException {
            out.flush();
        }

        @Override
        public String kind() {
            return "앱";
        }
    }

    // ================================================================ 웹 브라우저

    /**
     * WebSocket 프레임 읽기/쓰기 (외부 라이브러리 없이 직접 구현).
     * 프레임: [FIN·opcode 1바이트][MASK·길이 1바이트][확장 길이 2/8바이트][마스크 키 4바이트][데이터]
     * 브라우저가 보내는 프레임은 반드시 마스크되어 있고, 서버가 보내는 프레임은 마스크하지 않는다.
     */
    final class WebSocket implements Transport {
        private static final int MAX_MESSAGE = 64 * 1024;
        private final InputStream in;
        private final OutputStream out;
        private final Object writeLock = new Object();

        WebSocket(InputStream bufferedIn, OutputStream rawOut) {
            this.in = bufferedIn;
            this.out = new BufferedOutputStream(rawOut);
        }

        @Override
        public String read() throws IOException {
            ByteArrayOutputStream message = new ByteArrayOutputStream();
            boolean inMessage = false;
            while (true) {
                int b0 = in.read();
                int b1 = in.read();
                if (b0 < 0 || b1 < 0) throw new EOFException();
                boolean fin = (b0 & 0x80) != 0;
                int opcode = b0 & 0x0F;
                boolean masked = (b1 & 0x80) != 0;
                long len = b1 & 0x7F;
                if (len == 126) len = (readByte() << 8) | readByte();
                else if (len == 127) {
                    len = 0;
                    for (int i = 0; i < 8; i++) len = (len << 8) | readByte();
                }
                if (!masked) throw new IOException("마스크되지 않은 프레임");
                if (len < 0 || len > MAX_MESSAGE || message.size() + len > MAX_MESSAGE) throw new IOException("너무 큰 메시지");
                byte[] mask = readFully(4);
                byte[] data = readFully((int) len);
                for (int i = 0; i < data.length; i++) data[i] ^= mask[i & 3];

                switch (opcode) {
                    case 0x8: // 닫기: 받은 상태 코드를 그대로 돌려주고 끝낸다
                        writeFrame(0x8, data.length >= 2 ? new byte[] { data[0], data[1] } : new byte[0]);
                        flush();
                        throw new EOFException();
                    case 0x9: // ping → pong
                        writeFrame(0xA, data);
                        flush();
                        continue;
                    case 0xA: // pong
                        continue;
                    case 0x1:
                    case 0x2:
                        if (inMessage) throw new IOException("메시지가 끝나기 전에 새 메시지");
                        message.reset();
                        message.write(data);
                        inMessage = !fin;
                        break;
                    case 0x0:
                        if (!inMessage) throw new IOException("이어지는 프레임만 왔음");
                        message.write(data);
                        inMessage = !fin;
                        break;
                    default:
                        throw new IOException("알 수 없는 프레임 " + opcode);
                }
                if (fin) return new String(message.toByteArray(), StandardCharsets.UTF_8);
            }
        }

        private int readByte() throws IOException {
            int b = in.read();
            if (b < 0) throw new EOFException();
            return b;
        }

        private byte[] readFully(int n) throws IOException {
            byte[] b = new byte[n];
            int off = 0;
            while (off < n) {
                int r = in.read(b, off, n - off);
                if (r < 0) throw new EOFException();
                off += r;
            }
            return b;
        }

        private void writeFrame(int opcode, byte[] payload) throws IOException {
            synchronized (writeLock) {
                out.write(0x80 | opcode);
                int n = payload.length;
                if (n < 126) {
                    out.write(n);
                } else if (n < 65536) {
                    out.write(126);
                    out.write(n >>> 8);
                    out.write(n);
                } else {
                    out.write(127);
                    for (int i = 7; i >= 0; i--) out.write((int) ((long) n >>> (8 * i)));
                }
                out.write(payload);
            }
        }

        @Override
        public void write(String line) throws IOException {
            writeFrame(0x1, line.getBytes(StandardCharsets.UTF_8));
        }

        @Override
        public void flush() throws IOException {
            synchronized (writeLock) {
                out.flush();
            }
        }

        @Override
        public void goodbye() throws IOException {
            writeFrame(0x8, new byte[] { 0x03, (byte) 0xE8 }); // 1000 정상 종료
            flush();
        }

        @Override
        public String kind() {
            return "웹";
        }
    }
}
