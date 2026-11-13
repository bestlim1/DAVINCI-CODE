package stim.davincicode.common;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 채팅 욕설 필터. 글자 사이에 공백·숫자·기호를 끼워 넣어도 걸리도록 단어마다 정규식을 만든다.
 * 걸린 부분은 같은 길이의 ● 로 가린다. (사용자 제작 콘텐츠가 있는 앱은 스토어 심사에서 필터·신고·차단을 요구한다)
 */
public final class ChatFilter {
    private ChatFilter() { }

    private static final String[] WORDS = {
        "시발", "씨발", "씨빨", "시빨", "씨바", "ㅅㅂ", "ㅆㅂ", "병신", "븅신", "ㅂㅅ",
        "개새끼", "개새기", "개색기", "개색끼", "좆", "존나", "졸라", "ㅈㄴ", "미친놈", "미친년",
        "엠창", "니애미", "느금마", "지랄", "ㅈㄹ", "염병",
        "fuck", "shit", "bitch", "asshole", "bastard", "dick", "pussy", "cunt", "nigger", "faggot"
    };
    private static final List<Pattern> PATTERNS = new ArrayList<>();

    static {
        for (String w : WORDS) {
            StringBuilder re = new StringBuilder();
            for (int i = 0; i < w.length(); i++) {
                if (i > 0) re.append("[\\s\\p{Punct}0-9_·.]{0,3}");
                re.append(Pattern.quote(String.valueOf(w.charAt(i))));
            }
            PATTERNS.add(Pattern.compile(re.toString(), Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE));
        }
    }

    public static String filter(String text) {
        String out = text;
        for (Pattern p : PATTERNS) {
            Matcher m = p.matcher(out);
            if (!m.find()) continue;
            StringBuilder sb = new StringBuilder();
            int last = 0;
            m.reset();
            while (m.find()) {
                sb.append(out, last, m.start());
                for (int i = m.start(); i < m.end(); i++) sb.append('●');
                last = m.end();
            }
            sb.append(out.substring(last));
            out = sb.toString();
        }
        return out;
    }

    public static boolean isClean(String text) {
        for (Pattern p : PATTERNS) if (p.matcher(text).find()) return false;
        return true;
    }
}
