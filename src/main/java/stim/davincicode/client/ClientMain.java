package stim.davincicode.client;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;

import javax.swing.SwingUtilities;
import javax.swing.ToolTipManager;
import javax.swing.UIManager;
import javax.swing.plaf.ColorUIResource;
import javax.swing.plaf.FontUIResource;

import stim.davincicode.client.ui.Modal;
import stim.davincicode.client.ui.Theme;
import stim.davincicode.client.ui.UI;

/** 앱 시작점 */
public final class ClientMain {
    private ClientMain() { }

    public static void main(String[] args) {
        System.setProperty("awt.useSystemAAFontSettings", "on");
        System.setProperty("swing.aatext", "true");
        System.setProperty("sun.java2d.d3d", "true");
        System.setProperty("apple.awt.application.name", "The Da Vinci Code");
        Prefs prefs = new Prefs();
        installCrashLog(prefs.dir().resolve("logs"));
        SwingUtilities.invokeLater(() -> {
            Theme.init();
            styleSwing();
            Sound sound = new Sound();
            App app = new App(prefs, sound);
            app.applyPrefs();
            app.setVisible(true);
        });
    }

    /** 기본 Swing 부품(툴팁 등)도 앱 색에 맞춘다 */
    static void styleSwing() {
        try {
            UIManager.setLookAndFeel(UIManager.getCrossPlatformLookAndFeelClassName());
        } catch (Exception ignore) {
            // 기본값 사용
        }
        UIManager.put("ToolTip.background", new ColorUIResource(0x232C4B));
        UIManager.put("ToolTip.foreground", new ColorUIResource(Theme.TEXT));
        UIManager.put("ToolTip.font", new FontUIResource(Theme.font(13)));
        UIManager.put("ToolTip.border", javax.swing.BorderFactory.createCompoundBorder(
                javax.swing.BorderFactory.createLineBorder(Theme.STROKE_HI), UI.pad(6, 10, 6, 10)));
        UIManager.put("Panel.background", new ColorUIResource(Theme.BG_TOP));
        UIManager.put("Label.font", new FontUIResource(Theme.font(14)));
        UIManager.put("Label.foreground", new ColorUIResource(Theme.TEXT));
        UIManager.put("Viewport.background", new ColorUIResource(Theme.BG_TOP));
        UIManager.put("ScrollPane.background", new ColorUIResource(Theme.BG_TOP));
        ToolTipManager.sharedInstance().setInitialDelay(450);
    }

    /** 예상하지 못한 오류가 나면 로그 파일에 남기고 사용자에게 알려 준다 */
    private static void installCrashLog(Path dir) {
        Thread.setDefaultUncaughtExceptionHandler((t, e) -> {
            StringWriter sw = new StringWriter();
            e.printStackTrace(new PrintWriter(sw));
            System.err.println(sw);
            try {
                Files.createDirectories(dir);
                Files.write(dir.resolve("crash.log"), (LocalDateTime.now() + " [" + t.getName() + "]\n" + sw + "\n").getBytes(StandardCharsets.UTF_8),
                        StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            } catch (Exception ignore) {
                // 로그를 못 남겨도 앱은 계속
            }
            if (SwingUtilities.isEventDispatchThread() && java.awt.Frame.getFrames().length > 0) {
                java.awt.Frame f = java.awt.Frame.getFrames()[0];
                if (f instanceof App && !Modal.anyOpen()) {
                    Modal.alert(f, "문제가 생겼어요", "예상하지 못한 오류가 생겼어요. 같은 문제가 계속되면 설정 폴더의 logs/crash.log 파일을 보내 주세요.");
                }
            }
        });
    }
}
