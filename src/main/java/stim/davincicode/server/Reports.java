package stim.davincicode.server;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.util.function.Consumer;

/**
 * 신고 기록. data/reports.log 에 한 줄씩 덧붙인다 (신고한 사람, 대상, 사유, 대상의 최근 채팅).
 * dataDir이 null이면 서버 로그에만 남긴다.
 */
final class Reports {
    private final Path file;
    private final Consumer<String> log;

    Reports(Path dataDir, Consumer<String> log) {
        this.log = log;
        Path f = null;
        if (dataDir != null) {
            try {
                Files.createDirectories(dataDir);
                f = dataDir.resolve("reports.log");
            } catch (IOException e) {
                log.accept("데이터 폴더를 만들지 못함: " + e.getMessage());
            }
        }
        file = f;
    }

    void add(String text) {
        log.accept("[신고] " + text);
        if (file == null) return;
        try {
            Files.write(file, (LocalDateTime.now() + " " + text + System.lineSeparator()).getBytes(StandardCharsets.UTF_8),
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            log.accept("신고 저장 실패: " + e.getMessage());
        }
    }
}
