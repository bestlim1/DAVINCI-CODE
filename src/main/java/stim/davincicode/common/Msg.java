package stim.davincicode.common;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** 받은 한 줄을 명령어와 필드로 나눈 것. 없는 필드를 읽어도 예외 없이 기본값을 준다. */
public final class Msg {
    public final String cmd;
    private final String[] fields;

    private Msg(String cmd, String[] fields) {
        this.cmd = cmd;
        this.fields = fields;
    }

    public static Msg parse(String line) {
        String[] parts = line.split(String.valueOf(Proto.FS), -1);
        String[] f = new String[parts.length - 1];
        System.arraycopy(parts, 1, f, 0, f.length);
        return new Msg(parts[0].trim().toUpperCase(), f);
    }

    public int size() {
        return fields.length;
    }

    public String s(int i) {
        return i < fields.length ? fields[i] : "";
    }

    public int i(int i, int def) {
        try {
            return Integer.parseInt(s(i).trim());
        } catch (NumberFormatException e) {
            return def;
        }
    }

    public long l(int i, long def) {
        try {
            return Long.parseLong(s(i).trim());
        } catch (NumberFormatException e) {
            return def;
        }
    }

    public boolean b(int i) {
        return "1".equals(s(i));
    }

    /** 목록 필드를 레코드 배열 목록으로 나눈다. 빈 문자열이면 빈 목록. */
    public List<String[]> list(int i) {
        String v = s(i);
        if (v.isEmpty()) return Collections.emptyList();
        List<String[]> out = new ArrayList<>();
        for (String r : v.split(String.valueOf(Proto.RS), -1)) {
            out.add(r.split(String.valueOf(Proto.US), -1));
        }
        return out;
    }

    public static int num(String[] rec, int i, int def) {
        if (i >= rec.length) return def;
        try {
            return Integer.parseInt(rec[i].trim());
        } catch (NumberFormatException e) {
            return def;
        }
    }

    public static String str(String[] rec, int i) {
        return i < rec.length ? rec[i] : "";
    }

    public static boolean flag(String[] rec, int i) {
        return i < rec.length && "1".equals(rec[i]);
    }
}
