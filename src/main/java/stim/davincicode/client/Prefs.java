package stim.davincicode.client;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;

/**
 * 사용자 설정 (닉네임, 캐릭터, 소리, 최근 서버, 차단 목록 등).
 * Windows는 %APPDATA%\DavinciCode, macOS는 ~/Library/Application Support/DavinciCode, 그 밖은 ~/.config/davincicode 에 저장한다.
 * 예전 이름(HiddenCode)으로 저장된 설정이 있으면 처음 실행할 때 새 폴더로 복사해 온다.
 * 한 PC에서 창을 여러 개 띄워 시연할 때는 -Ddc.profile=2 처럼 프로필을 나누면 설정이 섞이지 않는다.
 */
public final class Prefs {
    private final Properties p = new Properties();
    private final Path dir;
    private final Path file;

    public Prefs() {
        dir = configDir();
        file = dir.resolve("settings.properties");
        try {
            Files.createDirectories(dir);
            if (Files.exists(file)) {
                try (Reader r = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                    p.load(r);
                }
            }
        } catch (IOException e) {
            System.err.println("설정을 읽지 못함: " + e.getMessage());
        }
        if (get("deviceId", "").isEmpty()) {
            set("deviceId", UUID.randomUUID().toString());
            save();
        }
    }

    public static Path configDir() {
        String os = System.getProperty("os.name", "").toLowerCase();
        String home = System.getProperty("user.home");
        Path parent;
        String name, oldName;
        if (os.contains("win")) {
            String appData = System.getenv("APPDATA");
            parent = Paths.get(appData != null ? appData : home);
            name = "DavinciCode";
            oldName = "HiddenCode";
        } else if (os.contains("mac")) {
            parent = Paths.get(home, "Library", "Application Support");
            name = "DavinciCode";
            oldName = "HiddenCode";
        } else {
            parent = Paths.get(home, ".config");
            name = "davincicode";
            oldName = "hiddencode";
        }
        Path base = parent.resolve(name);
        Path old = parent.resolve(oldName);
        String profile = System.getProperty("dc.profile");
        if (profile != null && !profile.isEmpty()) {
            String sub = "profile-" + profile.replaceAll("[^a-zA-Z0-9]", "");
            base = base.resolve(sub);
            old = old.resolve(sub);
        }
        copyOldSettings(old, base);
        return base;
    }

    /** 예전 이름(HiddenCode) 때 저장한 설정이 있고 새 폴더에는 아직 없으면 복사해 온다 (예전 폴더는 그대로 둔다). */
    private static void copyOldSettings(Path oldDir, Path newDir) {
        Path from = oldDir.resolve("settings.properties");
        Path to = newDir.resolve("settings.properties");
        if (Files.exists(to) || !Files.isRegularFile(from)) return;
        try {
            Files.createDirectories(newDir);
            Files.copy(from, to);
        } catch (IOException e) {
            System.err.println("예전 설정을 가져오지 못함 (새로 시작): " + e.getMessage());
        }
    }

    public Path dir() {
        return dir;
    }

    public synchronized void save() {
        try (Writer w = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
            p.store(w, "DAVINCI CODE settings");
        } catch (IOException e) {
            System.err.println("설정을 저장하지 못함: " + e.getMessage());
        }
    }

    private String get(String k, String def) {
        return p.getProperty(k, def);
    }

    private void set(String k, String v) {
        p.setProperty(k, v);
    }

    private boolean flag(String k, boolean def) {
        return Boolean.parseBoolean(get(k, String.valueOf(def)));
    }

    private int num(String k, int def) {
        try {
            return Integer.parseInt(get(k, String.valueOf(def)));
        } catch (NumberFormatException e) {
            return def;
        }
    }

    public String deviceId() { return get("deviceId", ""); }
    public String name() { return get("name", ""); }
    public void name(String v) { set("name", v); }
    public int avatar() { return num("avatar", 0); }
    public void avatar(int v) { set("avatar", String.valueOf(v)); }
    public boolean sound() { return flag("sound", true); }
    public void sound(boolean v) { set("sound", String.valueOf(v)); }
    public int volume() { return num("volume", 70); }
    public void volume(int v) { set("volume", String.valueOf(v)); }
    public boolean chatSound() { return flag("chatSound", true); }
    public void chatSound(boolean v) { set("chatSound", String.valueOf(v)); }
    public boolean turnAlert() { return flag("turnAlert", true); }
    public void turnAlert(boolean v) { set("turnAlert", String.valueOf(v)); }
    public boolean reduceMotion() { return flag("reduceMotion", false); }
    public void reduceMotion(boolean v) { set("reduceMotion", String.valueOf(v)); }
    public boolean seenTutorial() { return flag("seenTutorial", false); }
    public void seenTutorial(boolean v) { set("seenTutorial", String.valueOf(v)); }
    public boolean agreed() { return flag("agreedTerms", false); }
    public void agreed(boolean v) { set("agreedTerms", String.valueOf(v)); }

    public String bounds() { return get("bounds", ""); }
    public void bounds(String v) { set("bounds", v); }

    /** 최근 접속한 서버 "주소:포트" (최대 5개, 최근 것이 앞) */
    public List<String> recent() {
        String v = get("recent", "");
        List<String> out = new ArrayList<>();
        for (String s : v.split(",")) if (!s.trim().isEmpty()) out.add(s.trim());
        return out;
    }

    public void addRecent(String hostPort) {
        List<String> r = recent();
        r.remove(hostPort);
        r.add(0, hostPort);
        while (r.size() > 5) r.remove(r.size() - 1);
        set("recent", String.join(",", r));
    }

    public Set<String> blocked() {
        Set<String> out = new LinkedHashSet<>();
        for (String s : get("blocked", "").split(",")) if (!s.trim().isEmpty()) out.add(s.trim());
        return out;
    }

    public void blocked(Set<String> names) {
        set("blocked", String.join(",", names));
    }

    public boolean isBlocked(String name) {
        return blocked().contains(name);
    }

    public void block(String name, boolean on) {
        Set<String> b = blocked();
        if (on) b.add(name);
        else b.remove(name);
        blocked(b);
        save();
    }

    @Override
    public String toString() {
        return Arrays.toString(p.keySet().toArray());
    }
}
