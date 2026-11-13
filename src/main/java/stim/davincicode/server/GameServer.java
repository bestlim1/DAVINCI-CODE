package stim.davincicode.server;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Random;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import stim.davincicode.common.Proto;

/**
 * DAVINCI CODE 게임 서버. 단독 실행(ServerMain)과 앱 안에 넣어서 실행(내 PC에서 서버 열기) 모두 이 클래스를 쓴다.
 *
 * 스레드 구조
 *  - AcceptServer 스레드 1개 : 접속을 받아 입구(Gate)로 넘긴다 (수업 코드의 AcceptServer).
 *    같은 포트로 PC 앱과 웹 브라우저가 모두 들어온다. 브라우저에는 웹 게임 화면도 보내 준다.
 *  - 연결마다 읽기/쓰기 스레드 2개 : Connection 참고.
 *  - 게임 로직 스레드 1개 : 모든 상태 변경과 타이머를 순서대로 처리한다 (Hub).
 */
public final class GameServer {

    public static final class Config {
        public int port = Proto.DEFAULT_PORT;
        public String bind;                 // null이면 모든 네트워크에서 받는다, "127.0.0.1"이면 이 PC만
        public boolean lan = true;          // 같은 네트워크에서 찾을 수 있게 알릴지
        public Path dataDir;                // 신고 기록 폴더 (null이면 저장 안 함)
        public String name = "DAVINCI CODE 서버";
        public boolean verbose;             // 받은 메시지를 모두 로그에 남길지
        public Long seed;                   // 테스트용 고정 난수
        public Consumer<String> logger = System.out::println;
    }

    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("MM-dd HH:mm:ss");

    private final Config cfg;
    private ServerSocket serverSocket;
    private ScheduledExecutorService logic;
    private Hub hub;
    private LanBeacon beacon;
    private volatile boolean running;
    private volatile int online;
    private int port;

    public GameServer(Config cfg) {
        this.cfg = cfg;
    }

    public synchronized int start() throws IOException {
        if (running) return port;
        ServerSocket ss = new ServerSocket();
        ss.setReuseAddress(true);
        InetSocketAddress addr = cfg.bind == null ? new InetSocketAddress(cfg.port)
                : new InetSocketAddress(InetAddress.getByName(cfg.bind), cfg.port);
        ss.bind(addr, 64);
        serverSocket = ss;
        port = ss.getLocalPort();
        logic = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "dc-logic");
            t.setDaemon(true);
            return t;
        });
        running = true;
        Random rnd = cfg.seed != null ? new Random(cfg.seed) : new Random();
        hub = new Hub(this, new Reports(cfg.dataDir, this::log), rnd);
        Thread accept = new AcceptServer();
        accept.start();
        if (cfg.lan) {
            beacon = new LanBeacon(this);
            if (beacon.open()) beacon.start();
            else beacon = null;
        }
        log("서버 시작: " + cfg.name + " / 포트 " + port + (cfg.bind != null ? " (" + cfg.bind + " 전용)" : "")
                + (beacon != null ? " / LAN 검색 켜짐" : ""));
        return port;
    }

    /** 접속 요청을 기다렸다가 클라이언트마다 Connection(읽기·쓰기 스레드)을 만든다. */
    private final class AcceptServer extends Thread {
        AcceptServer() {
            super("dc-accept");
            setDaemon(true);
        }

        @Override
        public void run() {
            while (running) {
                try {
                    Socket s = serverSocket.accept();
                    // 앱(TCP)인지 웹 브라우저(HTTP/WebSocket)인지는 입구(Gate)에서 가른다
                    Gate.dispatch(s, GameServer.this, hub);
                } catch (IOException e) {
                    if (running) log("accept 오류: " + e.getMessage());
                }
            }
        }
    }

    public void stop() {
        if (!running) return;
        running = false;
        try {
            serverSocket.close();
        } catch (IOException ignore) {
            // 닫는 중
        }
        if (beacon != null) beacon.close();
        try {
            Future<?> f = logic.submit(hub::shutdown);
            f.get(2, TimeUnit.SECONDS);
            Thread.sleep(150); // 마지막 메시지가 나갈 시간
        } catch (Exception ignore) {
            // 종료 중
        }
        logic.shutdownNow();
        log("서버 종료");
    }

    public boolean running() {
        return running;
    }

    public int port() {
        return port;
    }

    public String name() {
        return cfg.name;
    }

    public int online() {
        return online;
    }

    void setOnline(int n) {
        online = n;
    }

    /** 게임 로직 스레드에서 실행한다. 예외가 나도 서버가 멈추지 않게 잡아서 로그만 남긴다. */
    void post(Runnable r) {
        if (!running && logic.isShutdown()) return;
        try {
            logic.execute(guard(r));
        } catch (java.util.concurrent.RejectedExecutionException ignore) {
            // 종료 중
        }
    }

    ScheduledFuture<?> schedule(Runnable r, long ms) {
        return logic.schedule(guard(r), ms, TimeUnit.MILLISECONDS);
    }

    void repeat(Runnable r, long ms) {
        logic.scheduleWithFixedDelay(guard(r), ms, ms, TimeUnit.MILLISECONDS);
    }

    private Runnable guard(Runnable r) {
        return () -> {
            try {
                r.run();
            } catch (Throwable t) {
                log("처리 중 오류: " + t);
                for (StackTraceElement e : t.getStackTrace()) {
                    if (e.getClassName().startsWith("stim.")) log("    at " + e);
                }
            }
        };
    }

    /** 운영자 콘솔 명령을 게임 로직 스레드에서 실행한다. */
    public void command(String line) {
        post(() -> hub.admin(line, this::log));
    }

    public void log(String s) {
        cfg.logger.accept(LocalDateTime.now().format(TS) + "  " + s);
    }

    void debug(String s) {
        if (cfg.verbose) log(s);
    }
}
