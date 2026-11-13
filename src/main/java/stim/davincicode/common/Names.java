package stim.davincicode.common;

import java.util.regex.Pattern;

/** 닉네임·방 제목 규칙. 클라이언트에서 미리 검사하고, 서버에서 한 번 더 검사한다. */
public final class Names {
    private Names() { }

    public static final int NAME_MIN = 2;
    public static final int NAME_MAX = 10;
    public static final int TITLE_MAX = 24;
    public static final int CHAT_MAX = 200;
    public static final int AVATARS = 8;
    private static final Pattern NAME = Pattern.compile("[가-힣a-zA-Z0-9_]+");

    /** 문제가 없으면 null, 있으면 사용자에게 보여 줄 이유를 돌려준다. */
    public static String checkName(String name) {
        if (name == null || name.trim().isEmpty()) return "닉네임을 입력해 주세요.";
        String n = name.trim();
        if (n.length() < NAME_MIN || n.length() > NAME_MAX) return "닉네임은 2~10글자로 정해 주세요.";
        if (!NAME.matcher(n).matches()) return "닉네임에는 한글, 영문, 숫자, _만 쓸 수 있어요.";
        if (!ChatFilter.isClean(n)) return "쓸 수 없는 단어가 들어 있어요.";
        String lower = n.toLowerCase();
        if (lower.startsWith("ai") || lower.contains("admin") || n.contains("운영자") || n.contains("관리자")) {
            return "AI, 운영자처럼 보이는 닉네임은 쓸 수 없어요.";
        }
        return null;
    }

    public static String cleanTitle(String title, String fallback) {
        String t = ChatFilter.filter(Proto.clean(title, TITLE_MAX));
        return t.isEmpty() ? fallback : t;
    }
}
