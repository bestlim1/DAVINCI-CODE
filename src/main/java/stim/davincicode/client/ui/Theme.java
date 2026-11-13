package stim.davincicode.client.ui;

import java.awt.Color;
import java.awt.Font;
import java.awt.GraphicsEnvironment;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** 디자인 토큰: 색, 글꼴, 모서리 크기. 화면의 모든 색은 여기서만 정한다. */
public final class Theme {
    private Theme() { }

    // 배경과 면
    public static final Color BG_TOP = new Color(0x0B1020);
    public static final Color BG_BOTTOM = new Color(0x141B32);
    public static final Color SURFACE = new Color(0x182039);
    public static final Color SURFACE_HI = new Color(0x212B4A);
    public static final Color SURFACE_LO = new Color(0x10172C);
    public static final Color STROKE = new Color(255, 255, 255, 22);
    public static final Color STROKE_HI = new Color(255, 255, 255, 44);

    // 글자
    public static final Color TEXT = new Color(0xF1F3FA);
    public static final Color TEXT_2 = new Color(0xAAB3CB);
    public static final Color TEXT_3 = new Color(0x6F7A98);

    // 강조
    public static final Color CORAL = new Color(0xFF7A59);
    public static final Color CORAL_DEEP = new Color(0xE9583A);
    public static final Color AMBER = new Color(0xFFC15E);
    public static final Color MINT = new Color(0x3DD6B0);
    public static final Color SKY = new Color(0x5EA8FF);
    public static final Color VIOLET = new Color(0xA98BFF);
    public static final Color RED = new Color(0xFF5A6E);

    // 게임판
    public static final Color FELT_CENTER = new Color(0x18394A);
    public static final Color FELT_EDGE = new Color(0x0C1B28);
    public static final Color RACK = new Color(8, 16, 28, 150);

    // 타일
    public static final Color TILE_B_TOP = new Color(0x40465A);
    public static final Color TILE_B_BOTTOM = new Color(0x1A1D26);
    public static final Color TILE_B_TEXT = new Color(0xF7F4EC);
    public static final Color TILE_W_TOP = new Color(0xFFFEF9);
    public static final Color TILE_W_BOTTOM = new Color(0xE5DCCB);
    public static final Color TILE_W_TEXT = new Color(0x1D2030);

    public static final Color[] AVATARS = {
        new Color(0xFF7A59), new Color(0xFFC15E), new Color(0x3DD6B0), new Color(0x5EA8FF),
        new Color(0xA98BFF), new Color(0xFF7EB6), new Color(0xA6E35A), new Color(0x3CC7D9)
    };

    public static final int RADIUS = 14;

    private static String family = Font.DIALOG;
    private static String numFamily = Font.DIALOG;
    private static final Map<String, Font> cache = new HashMap<>();

    /** 설치된 글꼴 중 한글이 잘 나오는 것을 고른다. */
    public static void init() {
        Set<String> have = new HashSet<>(Arrays.asList(
                GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames()));
        List<String> prefer = Arrays.asList("Pretendard", "Pretendard Variable", "Malgun Gothic", "맑은 고딕",
                "Apple SD Gothic Neo", "Noto Sans KR", "Noto Sans CJK KR", "NanumBarunGothic", "NanumGothic");
        for (String f : prefer) {
            if (have.contains(f)) {
                family = f;
                break;
            }
        }
        List<String> nums = Arrays.asList("Segoe UI", "SF Pro Display", "Helvetica Neue", "Inter", "Roboto");
        numFamily = family;
        for (String f : nums) {
            if (have.contains(f)) {
                numFamily = f;
                break;
            }
        }
    }

    public static String family() {
        return family;
    }

    public static Font font(float size) {
        return get(family, Font.PLAIN, size);
    }

    public static Font bold(float size) {
        return get(family, Font.BOLD, size);
    }

    /** 타일 숫자·로고용 */
    public static Font num(float size) {
        return get(numFamily, Font.BOLD, size);
    }

    private static Font get(String fam, int style, float size) {
        String key = fam + style + size;
        Font f = cache.get(key);
        if (f == null) {
            f = new Font(fam, style, 12).deriveFont(size);
            cache.put(key, f);
        }
        return f;
    }

    public static Color avatar(int i) {
        return AVATARS[Math.floorMod(i, AVATARS.length)];
    }
}
