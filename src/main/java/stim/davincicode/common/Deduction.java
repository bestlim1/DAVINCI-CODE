package stim.davincicode.common;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 숫자 계산. 한 사람이 볼 수 있는 정보(GameView)만으로
 * 상대의 숨은 타일마다 "들어갈 수 있는 숫자"를 계산한다. 숫자 고르기 창은 판 위에 이미 보이는 숫자를 지울 때 쓰고, 테스트의 자동 플레이어도 쓴다.
 *
 * 숫자 타일은 key = 숫자*2 + (흰색이면 1) 로 바꾸면 한 줄 안에서 key가 왼쪽부터 엄격하게 커진다.
 * 위치 i에 key k가 올 수 있는지는 "왼쪽에서 만들 수 있는 가장 작은 값 < k < 오른쪽에서 만들 수 있는 가장 큰 값"으로
 * 정확하게 판단할 수 있다 (앞뒤로 한 번씩 훑는 탐욕 계산).
 * 숨은 조커는 정렬 규칙을 따르지 않으므로, 조커가 어느 숨은 자리에 있을지 경우를 나눠 계산한 뒤 합친다.
 */
public final class Deduction {
    private Deduction() { }

    public static final class Result {
        /** [0 검정, 1 흰색][숫자]: 보는 사람이 이미 아는 숫자 */
        public final boolean[][] known = new boolean[2][12];
        public final boolean[] jokerKnown = new boolean[2];
        private final Map<Integer, int[]> candidates = new HashMap<>();

        /** 타일 uid의 후보 숫자 (조커는 -1). 계산 대상이 아니면 null. */
        public int[] of(int uid) {
            return candidates.get(uid);
        }

        /** 이 색·숫자가 아직 어디 있는지 모르는지 */
        public boolean unknown(char color, int value) {
            int c = color == 'W' ? 1 : 0;
            if (value == GameView.JOKER) return !jokerKnown[c];
            return !known[c][value];
        }
    }

    public static Result analyze(GameView v) {
        Result r = new Result();
        for (GameView.SeatView s : v.seats) {
            for (GameView.TileView t : s.tiles) markKnown(r, t);
        }
        if (v.pending != null) markKnown(r, v.pending);

        // 후보가 하나뿐인 타일은 "아는 숫자"로 보고 다시 계산하면 다른 타일 후보가 더 줄어든다
        int[][] assumed = new int[2][12]; // 후보가 하나로 좁혀진 타일의 uid (없으면 -1)
        for (int[] a : assumed) java.util.Arrays.fill(a, -1);
        for (int round = 0; round < 4; round++) {
            r.candidates.clear();
            boolean changed = false;
            for (GameView.SeatView s : v.seats) {
                if (s.pid == v.viewerPid) continue;
                analyzeRow(s, r, assumed);
            }
            for (Map.Entry<Integer, int[]> e : r.candidates.entrySet()) {
                int[] c = e.getValue();
                if (c.length == 1 && c[0] >= 0) {
                    GameView.TileView t = v.findTile(e.getKey());
                    int col = t.color == 'W' ? 1 : 0;
                    if (assumed[col][c[0]] == -1) {
                        assumed[col][c[0]] = e.getKey();
                        changed = true;
                    }
                }
            }
            if (!changed) break;
        }
        return r;
    }

    private static void markKnown(Result r, GameView.TileView t) {
        if (!t.known()) return;
        int c = t.color == 'W' ? 1 : 0;
        if (t.joker()) r.jokerKnown[c] = true;
        else r.known[c][t.value] = true;
    }

    private static int colorIndex(GameView.TileView t) {
        return t.color == 'W' ? 1 : 0;
    }

    private static void analyzeRow(GameView.SeatView s, Result r, int[][] assumed) {
        List<GameView.TileView> row = s.tiles;
        int n = row.size();
        // 위치마다 가능한 key 목록 (아는 조커는 정렬과 무관해서 null)
        List<int[]> keys = new ArrayList<>();
        for (GameView.TileView t : row) {
            if (t.known()) {
                keys.add(t.joker() ? null : new int[] { t.value * 2 + colorIndex(t) });
            } else {
                int c = colorIndex(t);
                List<Integer> ks = new ArrayList<>();
                for (int num = 0; num < 12; num++) {
                    if (!r.known[c][num] && (assumed[c][num] == -1 || assumed[c][num] == t.uid)) ks.add(num * 2 + c);
                }
                keys.add(toArray(ks));
            }
        }

        // 조커일 수 있는 숨은 자리 (색마다 조커는 하나). -1은 "이 줄에 그 색 조커 없음"
        List<Integer> jb = new ArrayList<>();
        List<Integer> jw = new ArrayList<>();
        jb.add(-1);
        jw.add(-1);
        for (int i = 0; i < n; i++) {
            GameView.TileView t = row.get(i);
            if (t.known()) continue;
            int c = colorIndex(t);
            if (!r.jokerKnown[c]) (c == 0 ? jb : jw).add(i);
        }
        // 조커 위치를 경우마다 나눠서 계산하고 합친다 (최대 7×7 경우)
        List<java.util.TreeSet<Integer>> union = new ArrayList<>();
        for (int i = 0; i < n; i++) union.add(new java.util.TreeSet<>());
        boolean any = false;
        for (int b : jb) {
            for (int w : jw) {
                int[][] res = chain(keys, n, b, w);
                if (res == null) continue;
                any = true;
                for (int i = 0; i < n; i++) {
                    if (row.get(i).known()) continue;
                    if (i == b || i == w) union.get(i).add(GameView.JOKER);
                    else for (int k : res[i]) union.get(i).add(k / 2);
                }
            }
        }
        for (int i = 0; i < n; i++) {
            GameView.TileView t = row.get(i);
            if (t.known()) continue;
            java.util.TreeSet<Integer> vals = union.get(i);
            if (!any || vals.isEmpty()) {
                for (int k : keys.get(i)) vals.add(k / 2);
                if (!r.jokerKnown[colorIndex(t)]) vals.add(GameView.JOKER);
            }
            r.candidates.put(t.uid, toArray(new ArrayList<>(vals)));
        }
    }

    /**
     * skip1, skip2 자리를 조커로 봤을 때 정렬 조건을 만족하는 위치별 후보 key. 모순이면 null.
     * 앞에서부터 "가장 작게 만들 수 있는 값", 뒤에서부터 "가장 크게 만들 수 있는 값"을 구하면
     * 위치 i에 k가 올 수 있는지는 (왼쪽 최솟값 < k < 오른쪽 최댓값)으로 정확히 정해진다.
     */
    private static int[][] chain(List<int[]> keys, int n, int skip1, int skip2) {
        boolean[] skip = new boolean[n];
        for (int i = 0; i < n; i++) skip[i] = keys.get(i) == null || i == skip1 || i == skip2;
        int[] lo = new int[n];
        int[] hi = new int[n];
        int prev = -1;
        for (int i = 0; i < n; i++) {
            if (skip[i]) continue;
            int best = Integer.MAX_VALUE;
            for (int k : keys.get(i)) if (k > prev && k < best) best = k;
            if (best == Integer.MAX_VALUE) return null;
            prev = lo[i] = best;
        }
        int next = 24;
        for (int i = n - 1; i >= 0; i--) {
            if (skip[i]) continue;
            int best = Integer.MIN_VALUE;
            for (int k : keys.get(i)) if (k < next && k > best) best = k;
            if (best == Integer.MIN_VALUE) return null;
            next = hi[i] = best;
        }
        int[][] out = new int[n][];
        for (int i = 0; i < n; i++) {
            if (skip[i]) continue;
            int left = -1, right = 24;
            for (int j = i - 1; j >= 0; j--) if (!skip[j]) { left = lo[j]; break; }
            for (int j = i + 1; j < n; j++) if (!skip[j]) { right = hi[j]; break; }
            List<Integer> ks = new ArrayList<>();
            for (int k : keys.get(i)) if (k > left && k < right) ks.add(k);
            out[i] = toArray(ks);
        }
        return out;
    }

    private static int[] toArray(List<Integer> list) {
        int[] a = new int[list.size()];
        for (int i = 0; i < a.length; i++) a[i] = list.get(i);
        return a;
    }
}
