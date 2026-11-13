package stim.davincicode.client;

import java.util.EnumMap;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import javax.sound.sampled.FloatControl;
import javax.sound.sampled.LineEvent;

/**
 * 효과음. 소리 파일 없이 코드로 파형을 합성해서 만든다 (저작권 걱정이 없다).
 * 재생은 별도 스레드에서 하고, 소리 장치가 없는 PC에서도 오류 없이 조용히 넘어간다.
 */
public final class Sound {
    public enum Sfx { CLICK, DRAW, PLACE, CORRECT, WRONG, TURN, CHAT, WIN, LOSE, TICK, JOIN }

    private static final float RATE = 44100f;
    private final Map<Sfx, byte[]> clips = new EnumMap<>(Sfx.class);
    private final ExecutorService player = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "dc-sound");
        t.setDaemon(true);
        return t;
    });
    private volatile boolean enabled = true;
    private volatile int volume = 70;
    private volatile boolean broken;

    public Sound() {
        clips.put(Sfx.CLICK, render(tone(1700, 0.03, 0.25, Wave.SINE, 0.002, 0.025)));
        clips.put(Sfx.DRAW, render(mix(noise(0.07, 0.18), tone(520, 0.06, 0.3, Wave.TRIANGLE, 0.003, 0.05))));
        clips.put(Sfx.PLACE, render(mix(tone(260, 0.08, 0.45, Wave.SINE, 0.002, 0.07), tone(520, 0.05, 0.2, Wave.SINE, 0.002, 0.04))));
        clips.put(Sfx.CORRECT, render(seq(0.075, tone(1046, 0.12, 0.33, Wave.TRIANGLE, 0.004, 0.11),
                tone(1318, 0.12, 0.33, Wave.TRIANGLE, 0.004, 0.11), tone(1568, 0.24, 0.36, Wave.TRIANGLE, 0.004, 0.22))));
        clips.put(Sfx.WRONG, render(seq(0.13, tone(233, 0.14, 0.35, Wave.SQUARE_SOFT, 0.005, 0.12),
                tone(175, 0.22, 0.35, Wave.SQUARE_SOFT, 0.005, 0.2))));
        clips.put(Sfx.TURN, render(seq(0.11, bell(784, 0.3, 0.3), bell(1046, 0.45, 0.32))));
        clips.put(Sfx.CHAT, render(tone(880, 0.06, 0.2, Wave.SINE, 0.003, 0.055)));
        clips.put(Sfx.WIN, render(seq(0.11, tone(523, 0.14, 0.3, Wave.TRIANGLE, 0.004, 0.12), tone(659, 0.14, 0.3, Wave.TRIANGLE, 0.004, 0.12),
                tone(784, 0.14, 0.3, Wave.TRIANGLE, 0.004, 0.12),
                mix(bell(1046, 0.7, 0.3), bell(1318, 0.7, 0.18), bell(1568, 0.7, 0.14)))));
        clips.put(Sfx.LOSE, render(seq(0.17, tone(392, 0.2, 0.3, Wave.TRIANGLE, 0.005, 0.18), tone(330, 0.2, 0.3, Wave.TRIANGLE, 0.005, 0.18),
                tone(262, 0.45, 0.3, Wave.TRIANGLE, 0.005, 0.42))));
        clips.put(Sfx.TICK, render(tone(1250, 0.025, 0.22, Wave.SINE, 0.001, 0.02)));
        clips.put(Sfx.JOIN, render(seq(0.07, tone(660, 0.09, 0.25, Wave.SINE, 0.003, 0.08), tone(990, 0.14, 0.25, Wave.SINE, 0.003, 0.12))));
    }

    public void setEnabled(boolean on) {
        enabled = on;
    }

    public void setVolume(int v) {
        volume = Math.max(0, Math.min(100, v));
    }

    public void play(Sfx s) {
        if (!enabled || broken || volume == 0) return;
        byte[] data = clips.get(s);
        int vol = volume;
        player.execute(() -> {
            try {
                Clip clip = AudioSystem.getClip();
                clip.open(new AudioFormat(RATE, 16, 1, true, false), data, 0, data.length);
                if (clip.isControlSupported(FloatControl.Type.MASTER_GAIN)) {
                    FloatControl gain = (FloatControl) clip.getControl(FloatControl.Type.MASTER_GAIN);
                    float db = (float) (20 * Math.log10(Math.max(0.01, vol / 100.0)));
                    gain.setValue(Math.max(gain.getMinimum(), Math.min(gain.getMaximum(), db)));
                }
                clip.addLineListener(e -> {
                    if (e.getType() == LineEvent.Type.STOP) clip.close();
                });
                clip.start();
            } catch (Exception | LinkageError e) {
                broken = true; // 소리 장치가 없으면 더 시도하지 않는다
            }
        });
    }

    // ------------------------------------------------------------------ 합성

    private enum Wave { SINE, TRIANGLE, SQUARE_SOFT }

    private static double[] tone(double freq, double dur, double amp, Wave wave, double attack, double decay) {
        int n = (int) (dur * RATE);
        double[] out = new double[n];
        for (int i = 0; i < n; i++) {
            double t = i / RATE;
            double ph = 2 * Math.PI * freq * t;
            double v;
            switch (wave) {
                case TRIANGLE: v = 2 / Math.PI * Math.asin(Math.sin(ph)); break;
                case SQUARE_SOFT: v = Math.tanh(3 * Math.sin(ph)) * 0.7; break;
                default: v = Math.sin(ph);
            }
            double env = t < attack ? t / attack : Math.exp(-(t - attack) / Math.max(0.001, decay) * 3);
            out[i] = v * env * amp;
        }
        return out;
    }

    private static double[] bell(double freq, double dur, double amp) {
        return mix(tone(freq, dur, amp, Wave.SINE, 0.003, dur * 0.9), tone(freq * 2.01, dur * 0.6, amp * 0.3, Wave.SINE, 0.002, dur * 0.4),
                tone(freq * 3.02, dur * 0.3, amp * 0.12, Wave.SINE, 0.002, dur * 0.2));
    }

    private static double[] sweep(double f0, double f1, double dur, double amp) {
        int n = (int) (dur * RATE);
        double[] out = new double[n];
        double ph = 0;
        for (int i = 0; i < n; i++) {
            double t = i / (double) n;
            double f = f0 + (f1 - f0) * t * t;
            ph += 2 * Math.PI * f / RATE;
            double env = Math.sin(Math.PI * t);
            out[i] = Math.sin(ph) * env * amp;
        }
        return out;
    }

    private static double[] noise(double dur, double amp) {
        Random r = new Random(3);
        int n = (int) (dur * RATE);
        double[] out = new double[n];
        double lp = 0;
        for (int i = 0; i < n; i++) {
            lp += (r.nextDouble() * 2 - 1 - lp) * 0.25;
            double t = i / (double) n;
            out[i] = lp * amp * (1 - t) * (1 - t);
        }
        return out;
    }

    private static double[] mix(double[]... parts) {
        int n = 0;
        for (double[] p : parts) n = Math.max(n, p.length);
        double[] out = new double[n];
        for (double[] p : parts) for (int i = 0; i < p.length; i++) out[i] += p[i];
        return out;
    }

    /** gap초 간격으로 이어 붙인다 (겹쳐도 됨) */
    private static double[] seq(double gap, double[]... parts) {
        int step = (int) (gap * RATE);
        int n = 0;
        for (int i = 0; i < parts.length; i++) n = Math.max(n, i * step + parts[i].length);
        double[] out = new double[n];
        for (int i = 0; i < parts.length; i++) {
            for (int j = 0; j < parts[i].length; j++) out[i * step + j] += parts[i][j];
        }
        return out;
    }

    private static byte[] render(double[] s) {
        byte[] b = new byte[s.length * 2 + 400];
        for (int i = 0; i < s.length; i++) {
            int v = (int) Math.round(Math.max(-1, Math.min(1, s[i])) * 32000);
            b[i * 2] = (byte) v;
            b[i * 2 + 1] = (byte) (v >> 8);
        }
        return b;
    }
}
