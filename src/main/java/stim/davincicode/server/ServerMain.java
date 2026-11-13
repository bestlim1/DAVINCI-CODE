package stim.davincicode.server;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.LocalDate;

import stim.davincicode.common.Proto;

/**
 * 창 없이 돌아가는 전용 서버 (클라우드 서버, 학교 실습실 PC 등에 띄워 두는 용도).
 *
 *   java -jar DavinciCode-Server.jar [--port 30000] [--data ./data] [--name "서버 이름"] [--no-lan] [--verbose]
 *
 * 실행 중에 콘솔에서 status, users, rooms, notice <내용>, kick <닉네임>, stop 명령을 쓸 수 있다.
 */
public final class ServerMain {
    private ServerMain() { }

    public static void main(String[] args) throws IOException {
        GameServer.Config cfg = new GameServer.Config();
        cfg.dataDir = Paths.get("data");
        for (int i = 0; i < args.length; i++) {
            String a = args[i];
            String next = i + 1 < args.length ? args[i + 1] : null;
            switch (a) {
                case "--port": cfg.port = Integer.parseInt(next); i++; break;
                case "--data": cfg.dataDir = Paths.get(next); i++; break;
                case "--name": cfg.name = next; i++; break;
                case "--bind": cfg.bind = next; i++; break;
                case "--seed": cfg.seed = Long.parseLong(next); i++; break;
                case "--no-lan": cfg.lan = false; break;
                case "--verbose": cfg.verbose = true; break;
                case "--help":
                case "-h":
                    System.out.println("사용법: java -jar DavinciCode-Server.jar [--port 30000] [--data ./data] [--name 이름] [--bind 주소] [--no-lan] [--verbose]");
                    return;
                default:
                    if (a.matches("\\d+")) cfg.port = Integer.parseInt(a); // 수업 코드처럼 포트만 줘도 된다
            }
        }
        Path logDir = cfg.dataDir.resolve("logs");
        Files.createDirectories(logDir);
        Path logFile = logDir.resolve("server-" + LocalDate.now() + ".log");
        PrintWriter fileLog = new PrintWriter(Files.newBufferedWriter(logFile, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.APPEND), true);
        cfg.logger = line -> {
            System.out.println(line);
            synchronized (fileLog) {
                fileLog.println(line);
            }
        };

        GameServer server = new GameServer(cfg);
        System.out.println(Proto.APP_NAME_EN + " Server " + Proto.APP_VERSION + " (protocol " + Proto.VERSION + ")");
        server.start();
        Runtime.getRuntime().addShutdownHook(new Thread(server::stop, "dc-shutdown"));
        System.out.println("명령어: status, users, rooms, notice <내용>, kick <닉네임>, stop");

        BufferedReader in = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
        String line;
        while ((line = in.readLine()) != null) {
            line = line.trim();
            if (line.isEmpty()) continue;
            if (line.equalsIgnoreCase("stop") || line.equalsIgnoreCase("exit")) break;
            server.command(line);
        }
        if (System.console() == null && line == null) {
            // 표준 입력이 없는 환경(서비스, 도커)에서는 계속 실행한다
            try {
                Thread.currentThread().join();
            } catch (InterruptedException ignore) {
                // 종료
            }
        }
        System.exit(0);
    }
}
