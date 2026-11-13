package stim.davincicode.client.ui;

import java.awt.Component;
import java.util.ArrayList;
import java.util.Map;
import java.util.WeakHashMap;

import javax.swing.Timer;

/**
 * 애니메이션 시계. 컴포넌트가 "앞으로 몇 ms 동안 움직인다"고 등록하면 그동안 60fps로 다시 그려 준다.
 * 실제 움직임은 각 컴포넌트가 시작 시각과 현재 시각으로 계산한다 (그래서 프레임이 밀려도 속도가 일정하다).
 */
public final class Anim {
    private Anim() { }

    private static final Map<Component, Long> active = new WeakHashMap<>();
    private static final Timer timer = new Timer(15, e -> tick());
    public static boolean reduced; // 설정의 "애니메이션 줄이기"

    public static long now() {
        return System.nanoTime() / 1_000_000L;
    }

    public static void run(Component c, long ms) {
        long end = now() + ms;
        active.merge(c, end, Math::max);
        if (!timer.isRunning()) timer.start();
    }

    public static void forever(Component c) {
        active.put(c, Long.MAX_VALUE);
        if (!timer.isRunning()) timer.start();
    }

    public static void stop(Component c) {
        active.remove(c);
    }

    private static void tick() {
        long t = now();
        for (Map.Entry<Component, Long> e : new ArrayList<>(active.entrySet())) {
            Component c = e.getKey();
            if (c == null) continue;
            c.repaint();
            if (e.getValue() < t) active.remove(c);
        }
        if (active.isEmpty()) timer.stop();
    }

    /** 0~1 진행도 */
    public static double progress(long start, long dur) {
        if (dur <= 0) return 1;
        double p = (now() - start) / (double) dur;
        return Math.max(0, Math.min(1, p));
    }

    public static double easeOut(double t) {
        double u = 1 - t;
        return 1 - u * u * u;
    }

    public static double easeInOut(double t) {
        return t < 0.5 ? 4 * t * t * t : 1 - Math.pow(-2 * t + 2, 3) / 2;
    }

    /** 살짝 넘쳤다가 돌아오는 움직임 */
    public static double easeBack(double t) {
        double c1 = 1.70158, c3 = c1 + 1;
        return 1 + c3 * Math.pow(t - 1, 3) + c1 * Math.pow(t - 1, 2);
    }

    /** 부드럽게 목표값을 따라가는 값 (버튼 hover 등) */
    public static final class Tween {
        private double from, to;
        private long start;
        private long dur = 160;

        public Tween(double v) {
            from = to = v;
        }

        public void set(double target, Component c) {
            if (target == to) return;
            from = get();
            to = target;
            start = now();
            run(c, dur + 20);
        }

        public Tween dur(long d) {
            dur = d;
            return this;
        }

        public void jump(double v) {
            from = to = v;
        }

        public double get() {
            if (reduced) return to;
            double p = progress(start, dur);
            return from + (to - from) * easeOut(p);
        }

        public double target() {
            return to;
        }
    }
}
